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
import com.example.smartdoorlock.api.RetrofitClient // [추가] RetrofitClient 임포트
import com.example.smartdoorlock.data.DoorlockLog
import com.example.smartdoorlock.data.LocationLog
import com.example.smartdoorlock.data.LoginResponse // [추가] 응답 모델 임포트
import com.example.smartdoorlock.data.UwbLog
import com.example.smartdoorlock.utils.LocationUtils
import com.google.android.gms.location.*
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import retrofit2.Call // [추가] Retrofit Call 임포트
import retrofit2.Callback // [추가] Retrofit Callback 임포트
import retrofit2.Response // [추가] Retrofit Response 임포트
import java.text.SimpleDateFormat
import java.util.*

class LocationService : Service() {

    // ... (기존 변수 선언 유지)

    // ... (onCreate, onStartCommand 등 기존 메서드 유지)

    // [수정됨] 위치 로그 DB 저장 및 서버 전송 쿼리
    private fun saveLocationToDB(location: Location) {
        val prefs = getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
        val username = prefs.getString("saved_id", null) ?: return

        // [수정 1] 날짜 포맷 변경 (yyyy.MM.dd -> yyyy-MM-dd)
        // DB(MySQL 등) 호환성을 위해 표준 포맷 사용 권장
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

        // 1. Firebase에 저장 (기존 로직)
        val log = LocationLog(
            altitude = location.altitude,
            latitude = location.latitude,
            longitude = location.longitude,
            timestamp = timestamp
        )

        database.getReference("users").child(username).child("location_logs").push().setValue(log)
            .addOnSuccessListener {
                Log.d("LocationService", "Firebase 저장 성공")
            }
            .addOnFailureListener { e ->
                Log.e("LocationService", "Firebase 저장 실패: ${e.message}")
            }

        // 2. [추가됨] 외부 서버(MySQL/PHP)로 전송 (ApiService 활용)
        RetrofitClient.instance.sendLocation(username, location.latitude, location.longitude, timestamp)
            .enqueue(object : Callback<LoginResponse> {
                override fun onResponse(call: Call<LoginResponse>, response: Response<LoginResponse>) {
                    if (response.isSuccessful) {
                        Log.d("LocationService", "서버 전송 성공: ${response.body()?.message}")
                    } else {
                        Log.e("LocationService", "서버 전송 실패 (코드): ${response.code()}")
                    }
                }

                override fun onFailure(call: Call<LoginResponse>, t: Throwable) {
                    Log.e("LocationService", "서버 통신 오류: ${t.message}")
                }
            })
    }

    // ... (나머지 코드 유지)
}