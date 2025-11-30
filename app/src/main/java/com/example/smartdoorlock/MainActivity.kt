package com.example.smartdoorlock

import android.Manifest
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
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
import com.example.smartdoorlock.service.NotificationService
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private lateinit var geofencingClient: GeofencingClient

    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance()

    // ★★★ [중요] 이미지에 있는 도어락 ID (나중에 동적으로 변경 가능) ★★★
    private val targetDoorlockId = "test1"

    private val REQUEST_ALL_PERMISSIONS = 1001
    private val REQUEST_BACKGROUND_LOCATION = 1002

    private val geofencePendingIntent: PendingIntent by lazy {
        val intent = Intent(this, GeofenceBroadcastReceiver::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
        } else {
            PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        }
    }

    private fun getRequiredPermissions(): Array<String> {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH)
            permissions.add(Manifest.permission.BLUETOOTH_ADMIN)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.UWB_RANGING)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return permissions.toTypedArray()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        geofencingClient = LocationServices.getGeofencingClient(this)

        supportActionBar?.let {
            val gradient = ContextCompat.getDrawable(this, R.drawable.gradient_actionbar_background)
            it.setBackgroundDrawable(gradient)
        }

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
        navController = navHostFragment.navController

        val appBarConfiguration = AppBarConfiguration(
            setOf(R.id.navigation_dashboard, R.id.navigation_profile, R.id.navigation_notifications, R.id.navigation_settings)
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        binding.navView.setupWithNavController(navController)

        // 화면 전환 리스너
        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.navigation_login, R.id.navigation_register, R.id.findPasswordFragment -> {
                    binding.navView.visibility = View.GONE
                    supportActionBar?.hide()
                }
                else -> {
                    if (auth.currentUser == null) {
                        navController.navigate(R.id.navigation_login)
                    } else {
                        binding.navView.visibility = View.VISIBLE
                        supportActionBar?.show()
                    }
                }
            }
        }

        // 권한 체크
        checkAndRequestPermissions()

        if (auth.currentUser == null) {
            navController.navigate(R.id.navigation_login)
        }
    }

    private fun checkAndRequestPermissions() {
        val missingPermissions = getRequiredPermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isEmpty()) {
            startAllServices()
            checkBackgroundLocationPermission()
        } else {
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), REQUEST_ALL_PERMISSIONS)
        }
    }

    private fun checkBackgroundLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                AlertDialog.Builder(this)
                    .setTitle("백그라운드 위치 권한")
                    .setMessage("집 근처에 오면 자동으로 연결하려면 '항상 허용'을 선택해주세요.")
                    .setPositiveButton("설정") { _, _ ->
                        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), REQUEST_BACKGROUND_LOCATION)
                    }
                    .setNegativeButton("취소", null)
                    .show()
            } else {
                fetchLocationAndSetupGeofence()
            }
        } else {
            fetchLocationAndSetupGeofence()
        }
    }

    // ★★★ [핵심 수정] DB 구조(doorlocks -> test1 -> location)에 맞춰 수정 ★★★
    private fun fetchLocationAndSetupGeofence() {
        if (auth.currentUser == null) return

        // 경로 수정: doorlocks/{test1}/location
        val locationRef = database.getReference("doorlocks").child(targetDoorlockId).child("location")

        locationRef.get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                // 키 이름 수정: latitude, longitude (이미지 기준)
                val lat = snapshot.child("latitude").getValue(Double::class.java)
                val lng = snapshot.child("longitude").getValue(Double::class.java)

                if (lat != null && lng != null) {
                    Log.d("Geofence", "DB 좌표 수신: $lat, $lng")
                    setupGeofencing(lat, lng)
                } else {
                    Log.e("Geofence", "좌표 데이터가 올바르지 않습니다.")
                }
            } else {
                Log.e("Geofence", "해당 도어락($targetDoorlockId)의 위치 정보가 없습니다.")
            }
        }.addOnFailureListener {
            Log.e("Geofence", "DB 읽기 실패: ${it.message}")
        }
    }

    private fun setupGeofencing(latitude: Double, longitude: Double) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        val geofence = Geofence.Builder()
            .setRequestId("MY_HOME_DOORLOCK")
            .setCircularRegion(
                latitude, longitude,
                50f // 50m 반경
            )
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
            .build()

        val geofencingRequest = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(geofence)
            .build()

        geofencingClient.addGeofences(geofencingRequest, geofencePendingIntent)
            .addOnSuccessListener {
                Log.d("Geofence", "✅ 지오펜싱 등록 성공 ($latitude, $longitude)")
            }
            .addOnFailureListener {
                Log.e("Geofence", "❌ 지오펜싱 등록 실패: ${it.message}")
            }
    }

    private fun startAllServices() {
        startLocationService()
        startNotificationService()
    }

    private fun startLocationService() {
        val serviceIntent = Intent(this, LocationService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(serviceIntent)
        else startService(serviceIntent)
    }

    private fun startNotificationService() {
        val serviceIntent = Intent(this, NotificationService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(serviceIntent)
        else startService(serviceIntent)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_ALL_PERMISSIONS -> {
                if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    startAllServices()
                    checkBackgroundLocationPermission()
                }
            }
            REQUEST_BACKGROUND_LOCATION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    fetchLocationAndSetupGeofence()
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }
}