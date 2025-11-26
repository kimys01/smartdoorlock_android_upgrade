package com.example.smartdoorlock.service

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.example.smartdoorlock.R
import com.example.smartdoorlock.data.DoorlockLog
import com.example.smartdoorlock.data.LocationLog
import com.example.smartdoorlock.data.UwbLog
import com.example.smartdoorlock.utils.LocationUtils
import com.google.android.gms.location.*
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.*

class LocationService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    private val database = FirebaseDatabase.getInstance()
    private lateinit var uwbManager: UwbServiceManager

    private val CHANNEL_ID = "location_channel"
    private val NOTIFICATION_ID = 1

    // 위치 업데이트 주기 (3분마다 저장)
    private val UPDATE_INTERVAL_MS: Long = 10 * 1000L
    private val SAVE_INTERVAL_MS: Long = 3 * 60 * 1000L
    private var lastSavedTime: Long = 0

    private var targetMac: String? = null
    private var fixedLocation: Location? = null
    private var isUwbAuthEnabled = false
    private var isInside = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // [수정] LocationCallback 초기화
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    processLocation(location)
                }
            }
        }

        uwbManager = UwbServiceManager(this)
        uwbManager.init()

        uwbManager.onUnlockRangeEntered = {
            unlockDoor()
            isInside = true
            Log.d("LocationService", "🏠 귀가 완료 (UWB OFF)")
        }

        uwbManager.onLogUpdate = { frontDist, backDist ->
            saveUwbLogToDB(frontDist, backDist)
        }

        loadDoorlockInfo()
    }

    private fun processLocation(location: Location) {
        val currentTime = System.currentTimeMillis()

        // [핵심] 앱 실행 여부와 관계없이 주기적으로 위치 로그 저장
        if (currentTime - lastSavedTime >= SAVE_INTERVAL_MS) {
            saveLocationToDB(location)
            lastSavedTime = currentTime
            Log.d("LocationService", "📍 백그라운드 위치 저장 완료: ${location.latitude}, ${location.longitude}")
        }

        checkDistanceAndControlUwb(location)
    }

    private fun saveUwbLogToDB(front: Double, back: Double) {
        val prefs = getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
        val username = prefs.getString("saved_id", null) ?: return

        val timestamp = SimpleDateFormat("yyyy.MM.dd H:mm:ss", Locale.getDefault()).format(Date())
        val log = UwbLog(front_distance = front, back_distance = back, timestamp = timestamp)
        val uwbLogsRef = database.getReference("users").child(username).child("uwb_logs")

        uwbLogsRef.push().setValue(log).addOnSuccessListener {
            uwbLogsRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val count = snapshot.childrenCount
                    if (count > 100) {
                        val toRemoveCount = (count - 100).toInt()
                        var removed = 0
                        for (child in snapshot.children) {
                            if (removed < toRemoveCount) {
                                child.ref.removeValue()
                                removed++
                            } else break
                        }
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notificationIntent = Intent(this, com.example.smartdoorlock.MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("스마트 도어락 위치 서비스")
            .setContentText("백그라운드에서 위치 정보를 수집 중입니다.")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        startLocationUpdates()
        return START_STICKY
    }

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            UPDATE_INTERVAL_MS
        ).apply {
            setMinUpdateIntervalMillis(5000L)
            setWaitForAccurateLocation(false)
        }.build()

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
    }

    private fun checkDistanceAndControlUwb(currentLoc: Location) {
        if (fixedLocation == null || !isUwbAuthEnabled) return
        val distance = LocationUtils.calculateDistance3D(currentLoc, fixedLocation!!)

        if (distance > 150) {
            if (isInside) isInside = false
            uwbManager.stopRanging()
        } else if (distance <= 100) {
            if (!isInside) uwbManager.startRanging()
            else uwbManager.stopRanging()
        }
    }

    private fun unlockDoor() {
        if (targetMac == null) return

        val prefs = getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
        val userId = prefs.getString("saved_id", "AutoSystem") ?: "AutoSystem"

        val commandRef = database.getReference("doorlocks").child(targetMac!!).child("command")
        val statusRef = database.getReference("doorlocks").child(targetMac!!).child("status")
        val sharedLogsRef = database.getReference("doorlocks").child(targetMac!!).child("logs")
        val userLogsRef = database.getReference("users").child(userId).child("doorlock").child("logs")

        val currentTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val method = "UWB_AUTO"
        val newState = "UNLOCK"

        commandRef.setValue(newState)

        statusRef.updateChildren(mapOf(
            "state" to newState,
            "last_method" to method,
            "last_time" to currentTime,
            "door_closed" to false
        ))

        val logData = DoorlockLog(
            method = method,
            state = newState,
            time = currentTime,
            user = userId
        )

        sharedLogsRef.push().setValue(logData)
        userLogsRef.push().setValue(logData)
    }

    private fun loadDoorlockInfo() {
        val prefs = getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
        val username = prefs.getString("saved_id", null) ?: return

        database.getReference("users").child(username).child("authMethod")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val method = snapshot.getValue(String::class.java)
                    isUwbAuthEnabled = (method == "UWB")
                    if (!isUwbAuthEnabled) uwbManager.stopRanging()
                }
                override fun onCancelled(error: DatabaseError) {}
            })

        database.getReference("users").child(username).child("my_doorlocks")
            .limitToFirst(1).get().addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    targetMac = snapshot.children.first().key
                    if (targetMac != null) fetchFixedLocation(targetMac!!)
                }
            }
    }

    private fun fetchFixedLocation(mac: String) {
        database.getReference("doorlocks").child(mac).child("location")
            .get().addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    val lat = snapshot.child("latitude").getValue(Double::class.java) ?: 0.0
                    val lon = snapshot.child("longitude").getValue(Double::class.java) ?: 0.0
                    val alt = snapshot.child("altitude").getValue(Double::class.java) ?: 0.0
                    val loc = Location("fixed")
                    loc.latitude = lat; loc.longitude = lon; loc.altitude = alt
                    fixedLocation = loc
                }
            }
    }

    private fun saveLocationToDB(location: Location) {
        val prefs = getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
        val username = prefs.getString("saved_id", null) ?: return
        val timestamp = SimpleDateFormat("yyyy.MM.dd H:mm", Locale.getDefault()).format(Date())
        val log = LocationLog(location.altitude, location.latitude, location.longitude, timestamp)
        database.getReference("users").child(username).child("location_logs").push().setValue(log)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(CHANNEL_ID, "스마트 도어락 위치 서비스", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 서비스 종료 시 업데이트 제거
        if (::fusedLocationClient.isInitialized && ::locationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
        if (::uwbManager.isInitialized) {
            uwbManager.stopRanging()
        }
        Log.d("LocationService", "서비스 종료됨")
    }
}