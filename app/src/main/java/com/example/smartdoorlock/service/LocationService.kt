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
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*
import com.google.firebase.auth.FirebaseAuth

// Firestore 사용을 위해 패키지 이름을 notifications에서 service로 변경했습니다. (일반적인 구조)
public class LocationService : Service(), LocationListener {

    private lateinit var locationManager: LocationManager
    private val db = FirebaseFirestore.getInstance() // Firestore 인스턴스
    private val CHANNEL_ID = "location_channel"
    private val NOTIFICATION_ID = 1

    // 위치 업데이트 주기: 5분 (5 * 60 * 1000L)
    private val MIN_TIME_MS: Long = 5 * 60 * 1000L
    private val MIN_DISTANCE_M: Float = 10f

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("LocationService", "🟢 서비스 시작됨 (5분 주기)");

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("위치 추적 중")
            .setContentText("스마트 도어락 위치 추적 서비스 실행 중 (5분 주기)")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .build()

        // 포그라운드 서비스 시작
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        if (!hasRequiredPermissions()) {
            Log.e("LocationService", "❌ 위치 권한 부족 → 서비스 종료")
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            // [권한 체크 보완]
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
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
            } else {
                Log.e("LocationService", "❌ ACCESS_FINE_LOCATION 권한 부족으로 업데이트 요청 실패")
                stopSelf()
            }
        } catch (e: Exception) {
            Log.e("LocationService", "❌ 위치 요청 실패: ${e.localizedMessage}")
            stopSelf()
        }

        return START_STICKY
    }

    private fun hasRequiredPermissions(): Boolean {
        // [필수 권한] ACCESS_FINE_LOCATION 하나만 체크해도 충분
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    // [핵심 로직] 위치 변경 시 Firestore에 저장
    override fun onLocationChanged(location: Location) {
        Log.d("LocationService", "📍 위치 변경됨: ${location.latitude}, ${location.longitude}, 고도: ${location.altitude}")

        // Firebase Auth에서 현재 로그인된 사용자 ID 가져오기
        val userId = FirebaseAuth.getInstance().currentUser?.uid

        if (userId.isNullOrEmpty()) {
            Log.e("LocationService", "❌ Firebase Auth User ID 없음 → 로그 저장 불가")
            return
        }

        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

        val locationLog = hashMapOf(
            "user_id" to userId,
            "latitude" to location.latitude,
            "longitude" to location.longitude,
            "altitude" to location.altitude,
            "timestamp" to timestamp
        )

        // Firestore 경로: artifacts/{appId}/users/{userId}/location_logs/{docId}
        val logCollectionRef = db.collection("artifacts").document("default-app-id")
            .collection("users").document(userId)
            .collection("location_logs")

        logCollectionRef.add(locationLog) // add()를 사용하여 새 문서 자동 생성
            .addOnSuccessListener {
                Log.d("LocationService", "✅ Firestore users/${userId}/location_logs 저장 성공")
            }
            .addOnFailureListener {
                Log.e("LocationService", "❌ Firestore users/${userId}/location_logs 저장 실패: ${it.message}")
            }
    }

    override fun onProviderEnabled(provider: String) {
        Log.d("LocationService", "📡 위치 제공자 사용 가능: $provider")
    }

    override fun onProviderDisabled(provider: String) {
        Log.w("LocationService", "📡 위치 제공자 비활성화: $provider")
    }

    // [수정] onStatusChanged는 Deprecated 되었으므로 onLocationChanged를 사용
    // 이 메서드는 Android 12 이상에서 더 이상 호출되지 않습니다.

    override fun onDestroy() {
        super.onDestroy()
        try {
            // [권한 체크] 권한이 있을 때만 removeUpdates 호출
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                locationManager.removeUpdates(this)
            }
        } catch (e: Exception) {
            Log.e("LocationService", "❌ 위치 업데이트 해제 실패: ${e.localizedMessage}")
        }
    }

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
}