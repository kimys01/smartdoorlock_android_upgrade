package com.example.smartdoorlock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log // Log 클래스 사용을 위한 import 추가
import android.os.Build // Build 클래스 사용을 위한 import 추가
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent

// 이 리시버는 50m 지오펜스 구역 진입 시 시스템에 의해 호출됩니다.
class GeofenceBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent)

        if (geofencingEvent?.hasError() == true) {
            Log.e("GeofenceReceiver", "Geofencing Error: ${geofencingEvent.errorCode}")
            return
        }

        // 지오펜스 진입 이벤트인지 확인
        val geofenceTransition = geofencingEvent?.geofenceTransition

        if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER) {
            Log.d("GeofenceReceiver", "✅ 50m Geofence Entered! Starting Doorlock Service.")

            // DoorlockService를 포그라운드 서비스로 시작하여 UWB/BLE 통신 시작
            val serviceIntent = Intent(context, DoorlockService::class.java)
            // Android 8.0 (Oreo) 이상에서는 startForegroundService를 사용해야 합니다.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }
}