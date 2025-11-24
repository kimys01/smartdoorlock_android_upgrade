package com.example.smartdoorlock.service

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.example.smartdoorlock.data.LocationLog
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.*

class LocationService : Service(), LocationListener {

    private lateinit var locationManager: LocationManager
    private val database = FirebaseDatabase.getInstance()

    private val CHANNEL_ID = "location_channel"
    private val NOTIFICATION_ID = 1

    // [핵심] 3분 간격 설정 (3 * 60 * 1000ms)
    private val MIN_TIME_MS: Long = 3 * 60 * 1000L
    private val MIN_DISTANCE_M: Float = 0f // 거리 변화가 없어도 시간 되면 저장

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("LocationService", "🟢 위치 서비스 시작 (3분 주기)")

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("위치 추적 중")
            .setContentText("3분마다 위치를 기록합니다.")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
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
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e("LocationService", "❌ 위치 권한 없음")
            stopSelf()
            return
        }

        try {
            // GPS 및 네트워크 제공자 모두 요청
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                MIN_TIME_MS,
                MIN_DISTANCE_M,
                this
            )
            locationManager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
                MIN_TIME_MS,
                MIN_DISTANCE_M,
                this
            )
        } catch (e: Exception) {
            Log.e("LocationService", "❌ 위치 요청 실패: ${e.message}")
        }
    }

    override fun onLocationChanged(location: Location) {
        Log.d("LocationService", "📍 위치 업데이트: ${location.latitude}, ${location.longitude}")
        saveLocationToDB(location)
    }

    private fun saveLocationToDB(location: Location) {
        val prefs = getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
        val username = prefs.getString("saved_id", null)

        if (username == null) {
            Log.w("LocationService", "사용자 아이디 없음. 저장 건너뜀.")
            return
        }

        val timestamp = SimpleDateFormat("yyyy.MM.dd H:mm", Locale.getDefault()).format(Date())

        // [이미지 양식] LocationLog 객체 생성
        val log = LocationLog(
            altitude = location.altitude,
            latitude = location.latitude,
            longitude = location.longitude,
            timestamp = timestamp
        )

        // users/{username}/location_logs 아래에 자동 키(push)로 저장
        database.getReference("users").child(username)
            .child("location_logs")
            .push()
            .setValue(log)
            .addOnSuccessListener {
                Log.d("LocationService", "✅ 위치 저장 완료")
            }
            .addOnFailureListener {
                Log.e("LocationService", "❌ 위치 저장 실패: ${it.message}")
            }
    }

    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}
    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "위치 추적 서비스",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        locationManager.removeUpdates(this)
    }
}