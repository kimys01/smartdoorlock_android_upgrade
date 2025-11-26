package com.example.smartdoorlock

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.smartdoorlock.databinding.ActivityMainBinding
import com.example.smartdoorlock.service.LocationService
import com.example.smartdoorlock.service.UwbServiceManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    private lateinit var uwbManager: UwbServiceManager
    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance()

    private var authListener: ValueEventListener? = null
    private var authRef: DatabaseReference? = null

    private val REQUEST_ALL_PERMISSIONS = 1001

    private val REQUIRED_PERMISSIONS = mutableListOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.BLUETOOTH_ADMIN,
        Manifest.permission.BLUETOOTH
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        uwbManager = UwbServiceManager(this)
        uwbManager.init()

        supportActionBar?.let {
            val gradient = ContextCompat.getDrawable(this, R.drawable.gradient_actionbar_background)
            it.setBackgroundDrawable(gradient)
        }

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
        navController = navHostFragment.navController

        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_dashboard,
                R.id.navigation_profile,
                R.id.navigation_notifications,
                R.id.navigation_settings
            )
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        binding.navView.setupWithNavController(navController)

        // [수정] BottomNavigationView 아이템 선택 리스너 재정의
        // 탭을 누를 때마다 해당 탭의 시작점(홈)으로 이동하고 백스택을 정리하여
        // '수정 화면' 등이 남아있는 문제를 해결합니다.
        binding.navView.setOnItemSelectedListener { item ->
            // 이미 선택된 탭을 다시 누른 경우가 아닐 때만 처리 (혹은 다시 눌러도 초기화하고 싶으면 조건 제거)
            if (item.itemId != binding.navView.selectedItemId) {
                val navOptions = NavOptions.Builder()
                    .setLaunchSingleTop(true)
                    .setPopUpTo(item.itemId, true) // 현재 탭의 스택을 모두 지움
                    .build()

                try {
                    // 네비게이션 그래프의 ID와 메뉴 ID가 일치해야 함
                    navController.navigate(item.itemId, null, navOptions)
                    return@setOnItemSelectedListener true
                } catch (e: IllegalArgumentException) {
                    return@setOnItemSelectedListener false
                }
            }
            // 같은 탭을 다시 누른 경우: 스택을 비우고 최상위 화면으로 이동 (선택 사항)
            // navController.popBackStack(item.itemId, false)
            true
        }

        // 화면 전환 시 UI 제어 및 로그인 상태 체크
        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.navigation_login,
                R.id.navigation_register,
                R.id.findPasswordFragment -> {
                    binding.navView.visibility = View.GONE
                    supportActionBar?.hide()
                }
                else -> {
                    if (auth.currentUser == null) {
                        val navOptions = NavOptions.Builder()
                            .setPopUpTo(R.id.mobile_navigation, true)
                            .build()
                        navController.navigate(R.id.navigation_login, null, navOptions)
                    } else {
                        binding.navView.visibility = View.VISIBLE
                        supportActionBar?.show()
                        observeAuthMethod()
                    }
                }
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val currentDestination = navController.currentDestination?.id
                if (currentDestination == R.id.navigation_login && auth.currentUser == null) {
                    finish()
                } else {
                    if (!navController.popBackStack()) {
                        finish()
                    }
                }
            }
        })

        if (!hasAllPermissions()) {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_ALL_PERMISSIONS)
        } else {
            startLocationTrackingService()
        }

        if (auth.currentUser == null) {
            val navOptions = NavOptions.Builder()
                .setPopUpTo(R.id.mobile_navigation, true)
                .build()
            navController.navigate(R.id.navigation_login, null, navOptions)
        }
    }

    private fun observeAuthMethod() {
        if (authListener != null) return
        val uid = auth.currentUser?.uid ?: return
        authRef = database.getReference("users").child(uid).child("authMethod")

        authListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val method = snapshot.getValue(String::class.java) ?: "BLE"
                if (method == "UWB") uwbManager.startRanging()
                else uwbManager.stopRanging()
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        authRef?.addValueEventListener(authListener!!)
    }

    private fun hasAllPermissions(): Boolean {
        return REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun startLocationTrackingService() {
        val serviceIntent = Intent(this, LocationService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_ALL_PERMISSIONS) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                startLocationTrackingService()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        uwbManager.stopRanging()
        if (authListener != null && authRef != null) {
            authRef?.removeEventListener(authListener!!)
            authListener = null
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }
}