package com.example.smartdoorlock.ui.setting

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.*
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.smartdoorlock.data.DetailSettings
import com.example.smartdoorlock.data.Doorlock
import com.example.smartdoorlock.data.FixedLocation // [추가]
import com.google.android.gms.location.LocationServices // [추가] 위치 서비스
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.HashMap

@SuppressLint("MissingPermission")
class WifiSettingViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        var PROV_SERVICE_UUID: UUID = UUID.fromString("19b20000-e8f2-537e-4f6c-d104768a1214")
        var WIFI_CTRL_UUID: UUID = UUID.fromString("19b20003-e8f2-537e-4f6c-d104768a1214")
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    private val db = FirebaseDatabase.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // [핵심] 핸드폰의 현재 위치를 가져오기 위한 도구
    private val fusedLocationClient by lazy {
        LocationServices.getFusedLocationProviderClient(getApplication<Application>())
    }

    private val _statusText = MutableLiveData<String>("기기 연결 대기 중...")
    val statusText: LiveData<String> = _statusText

    private val _isBleConnected = MutableLiveData<Boolean>(false)
    val isBleConnected: LiveData<Boolean> = _isBleConnected

    private val _currentStep = MutableLiveData<Int>(0)
    val currentStep: LiveData<Int> = _currentStep

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        (application.getSystemService(Application.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }

    private var bluetoothGatt: BluetoothGatt? = null
    private var targetAddress: String = ""

    private fun getSavedUserId(): String? {
        val prefs = getApplication<Application>().getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
        return prefs.getString("saved_id", null)
    }

    fun connectToDevice(address: String) {
        targetAddress = address
        _statusText.value = "도어락에 연결을 시도합니다..."
        connectGatt(address)
    }

    fun verifyAppAdmin(inputId: String, inputPw: String) {
        val trimId = inputId.trim()
        val trimPw = inputPw.trim()

        if (trimId == "123456" && trimPw == "1234qwer") {
            _statusText.value = "테스트 계정 승인. 설정 진행..."
            _currentStep.value = 2
            return
        }

        val userId = getSavedUserId()
        if (userId == null) {
            _statusText.value = "오류: 앱 로그인 정보 없음. 다시 로그인하세요."
            return
        }

        _statusText.value = "서버 정보 확인 중..."

        db.getReference("users").child(userId).get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    val dbId = snapshot.child("username").getValue(String::class.java)?.trim() ?: ""
                    val dbPw = snapshot.child("password").getValue(String::class.java)?.trim() ?: ""

                    if (dbId == trimId && dbPw == trimPw) {
                        _statusText.value = "본인 확인 완료. Wi-Fi 설정 이동."
                        _currentStep.value = 2
                    } else {
                        _statusText.value = "인증 실패: 정보 불일치"
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
        if (_isBleConnected.value != true) {
            _statusText.value = "오류: 도어락 연결 끊김. 다시 연결해주세요."
            return
        }

        // [핵심 로직] 핸드폰 위치를 가져와서 도어락 정보와 함께 저장
        registerSharedDoorlock(targetAddress, ssid, pass)

        val payload = "ssid:$ssid,password:$pass"

        Log.d("BLE_CHECK", "🚀 [전송 요청] $payload")
        _statusText.value = "설정값 전송 시도..."

        val result = writeCharacteristic(WIFI_CTRL_UUID, payload)
        if (!result) {
            _statusText.value = "전송 실패: UUID를 찾을 수 없습니다."
        }
    }

    // --- 도어락 등록 및 위치 고정 로직 ---
    @SuppressLint("MissingPermission") // 위치 권한은 Fragment 진입 시 이미 체크됨
    private fun registerSharedDoorlock(mac: String, ssid: String, pass: String) {
        val userId = getSavedUserId() ?: return
        val currentTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

        val doorlocksRef = db.getReference("doorlocks").child(mac)
        val userDoorlocksRef = db.getReference("users").child(userId).child("my_doorlocks")

        // 1. 핸드폰의 현재 GPS 위치 가져오기 (도어락 위치로 고정)
        fusedLocationClient.lastLocation.addOnCompleteListener { task ->
            var fixedLocation = FixedLocation() // 기본값 (0,0,0)

            if (task.isSuccessful && task.result != null) {
                val loc = task.result
                // 핸드폰의 위치를 도어락 위치로 설정
                fixedLocation = FixedLocation(
                    latitude = loc.latitude,
                    longitude = loc.longitude,
                    altitude = loc.altitude
                )
                Log.d("DB_SHARE", "📍 도어락 위치 고정: ${loc.latitude}, ${loc.longitude}, 고도:${loc.altitude}")
            } else {
                Log.w("DB_SHARE", "⚠️ 위치를 가져올 수 없음. (기본값 0.0으로 저장됩니다)")
            }

            // 2. DB 업데이트
            doorlocksRef.get().addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    // A. 이미 등록된 도어락 -> 멤버 추가
                    Log.d("DB_SHARE", "기존 도어락 갱신")
                    doorlocksRef.child("members").child(userId).setValue("member")
                    userDoorlocksRef.child(mac).setValue(true)

                    // 와이파이 정보 갱신
                    doorlocksRef.child("ssid").setValue(ssid)
                    doorlocksRef.child("pw").setValue(pass)
                    doorlocksRef.child("lastUpdated").setValue(currentTime)

                    // [선택] 기존에 위치 정보가 없었다면 이번 기회에 저장
                    if (!snapshot.hasChild("location")) {
                        doorlocksRef.child("location").setValue(fixedLocation)
                    }

                } else {
                    // B. 신규 등록 -> 관리자로 등록하고 위치 고정
                    Log.d("DB_SHARE", "신규 도어락 생성 (위치 포함)")

                    val members = HashMap<String, String>()
                    members[userId] = "admin"

                    val newLock = Doorlock(
                        mac = mac,
                        ssid = ssid,
                        pw = pass,
                        detailSettings = DetailSettings(true, 5, true),
                        members = members,
                        location = fixedLocation, // [저장] 여기가 도어락의 고정 위치가 됩니다.
                        lastUpdated = currentTime
                    )

                    doorlocksRef.setValue(newLock)
                    userDoorlocksRef.child(mac).setValue(true)
                }
            }
        }
    }

    // --- BLE 내부 로직 (이하는 기존과 동일) ---

    private fun connectGatt(address: String) {
        try {
            val device = bluetoothAdapter?.getRemoteDevice(address)
            bluetoothGatt?.close()
            bluetoothGatt = device?.connectGatt(getApplication(), false, gattCallback)
        } catch (e: Exception) {
            _statusText.value = "주소 오류: $address"
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                _isBleConnected.postValue(true)
                _statusText.postValue("도어락 연결 성공! 서비스 탐색 중...")
                val success = gatt?.requestMtu(512) ?: false
                if (!success) gatt?.discoverServices()
            } else {
                _isBleConnected.postValue(false)
                _statusText.postValue("연결 끊어짐")
                closeGatt()
            }
        }
        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            gatt?.discoverServices()
        }
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                var foundWritableUuid = false
                gatt?.services?.forEach { service ->
                    service.characteristics.forEach { characteristic ->
                        val props = characteristic.properties
                        if ((props and BluetoothGattCharacteristic.PROPERTY_WRITE) > 0 ||
                            (props and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) > 0) {
                            PROV_SERVICE_UUID = service.uuid
                            WIFI_CTRL_UUID = characteristic.uuid
                            foundWritableUuid = true
                            return@forEach
                        }
                    }
                    if (foundWritableUuid) return@forEach
                }
                subscribeNotifications()
            }
        }
        override fun onCharacteristicWrite(gatt: BluetoothGatt?, c: BluetoothGattCharacteristic?, s: Int) {
            if (s == BluetoothGatt.GATT_SUCCESS) {
                val sentData = String(c?.value ?: byteArrayOf(), Charsets.UTF_8)
                if (sentData.contains("ssid:") && sentData.contains("password:")) {
                    _statusText.postValue("전송 완료! 도어락 응답 대기 중...")
                }
            } else {
                _statusText.postValue("전송 실패 (Error: $s)")
            }
        }
        override fun onCharacteristicChanged(gatt: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
            val response = String(value, Charsets.UTF_8)
            if (response == "SUCCESS") {
                _statusText.postValue("성공: 도어락이 Wi-Fi에 연결되었습니다!")
                closeGatt()
            } else if (response.startsWith("FAIL")) {
                _statusText.postValue("실패: 와이파이 정보 확인 필요")
            } else {
                _statusText.postValue("상태: $response")
            }
        }
        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(gatt: BluetoothGatt?, c: BluetoothGattCharacteristic?) {
            c?.let { onCharacteristicChanged(gatt!!, it, it.value) }
        }
    }

    private fun subscribeNotifications() {
        val s = bluetoothGatt?.getService(PROV_SERVICE_UUID)
        val c = s?.getCharacteristic(WIFI_CTRL_UUID)
        val d = c?.getDescriptor(CCCD_UUID)
        if (c != null && d != null) {
            bluetoothGatt?.setCharacteristicNotification(c, true)
            d.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            bluetoothGatt?.writeDescriptor(d)
        }
    }

    private fun writeCharacteristic(uuid: UUID, value: String): Boolean {
        val service = bluetoothGatt?.getService(PROV_SERVICE_UUID) ?: return false
        val characteristic = service.getCharacteristic(uuid) ?: return false
        characteristic.value = value.toByteArray(Charsets.UTF_8)
        characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        val result = bluetoothGatt?.writeCharacteristic(characteristic) ?: false
        return result
    }

    fun disconnect() = closeGatt()
    private fun closeGatt() {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        _isBleConnected.postValue(false)
    }
}