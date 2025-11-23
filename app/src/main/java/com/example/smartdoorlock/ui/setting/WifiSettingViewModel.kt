package com.example.smartdoorlock.ui.setting

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.*
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase // Realtime Database
import java.util.*

@SuppressLint("MissingPermission")
class WifiSettingViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        val PROV_SERVICE_UUID: UUID = UUID.fromString("19b20000-e8f2-537e-4f6c-d104768a1214")
        val WIFI_CTRL_UUID: UUID = UUID.fromString("19b20003-e8f2-537e-4f6c-d104768a1214")
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    // [변경] Realtime Database
    private val db = FirebaseDatabase.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _statusText = MutableLiveData<String>("기기 연결 대기 중...")
    val statusText: LiveData<String> = _statusText

    private val _currentStep = MutableLiveData<Int>(0)
    val currentStep: LiveData<Int> = _currentStep

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        (application.getSystemService(Application.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }

    private var bluetoothGatt: BluetoothGatt? = null
    private var targetAddress: String = ""

    fun connectToDevice(address: String) {
        targetAddress = address
        connectGatt(address)
    }

    // 관리자 로그인 (Realtime Database 조회)
    fun verifyAppAdmin(inputId: String, inputPw: String) {
        val trimId = inputId.trim()
        val trimPw = inputPw.trim()

        // 테스트 계정
        if (trimId == "123456" && trimPw == "1234qwer") {
            _statusText.value = "테스트 계정 승인. 설정 진행..."
            _currentStep.value = 2
            return
        }

        val currentUser = auth.currentUser
        if (currentUser == null) {
            _statusText.value = "오류: 앱 로그인 정보 없음"
            return
        }

        _statusText.value = "서버 정보 확인 중..."

        // users/{uid} 경로 조회
        db.getReference("users").child(currentUser.uid).get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    val dbId = snapshot.child("username").getValue(String::class.java)?.trim() ?: ""
                    val dbPw = snapshot.child("password").getValue(String::class.java)?.trim() ?: ""

                    if (dbId == trimId && dbPw == trimPw) {
                        _statusText.value = "본인 확인 완료. Wi-Fi 설정 이동."
                        _currentStep.value = 2
                    } else {
                        _statusText.value = "인증 실패: 정보 불일치"
                        Log.w("AuthCheck", "입력($trimId) != DB($dbId)")
                    }
                } else {
                    _statusText.value = "오류: 회원 정보를 찾을 수 없습니다."
                }
            }
            .addOnFailureListener { e ->
                _statusText.value = "서버 연결 실패: ${e.message}"
            }
    }

    fun sendWifiSettings(ssid: String, pass: String) {
        saveToRealtimeDB(targetAddress, ssid, pass)
        val payload = "ssid:$ssid,password:$pass;"
        _statusText.value = "설정값 전송 중..."
        writeCharacteristic(WIFI_CTRL_UUID, payload)
    }

    // ... (BLE 관련 코드는 기존과 동일하여 생략, 필요시 위 코드 참고) ...

    private fun connectGatt(address: String) { /* 기존과 동일 */ }

    // [변경] Realtime Database에 저장
    private fun saveToRealtimeDB(mac: String, ssid: String, pass: String) {
        val uid = auth.currentUser?.uid ?: return
        val data = mapOf(
            "mac" to mac,
            "ssid" to ssid,
            "pw" to pass,
            "date" to java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date())
        )
        // doorlock 경로 하위에 저장
        db.getReference("users").child(uid).child("doorlock").setValue(data)
    }

    // ... (나머지 BLE 함수들) ...
    private fun writeCharacteristic(uuid: UUID, value: String) { /* ... */ }
    fun disconnect() { /* ... */ }
}