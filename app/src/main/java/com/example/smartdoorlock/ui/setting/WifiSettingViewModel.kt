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

    // 저장된 사용자 ID 가져오기
    private fun getSavedUserId(): String? {
        val prefs = getApplication<Application>().getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
        return prefs.getString("saved_id", null)
    }

    fun connectToDevice(address: String) {
        targetAddress = address
        _statusText.value = "도어락에 연결을 시도합니다..."
        connectGatt(address)
    }

    // 관리자 로그인 (DB 대조)
    fun verifyAppAdmin(inputId: String, inputPw: String) {
        val trimId = inputId.trim()
        val trimPw = inputPw.trim()

        if (trimId == "123456" && trimPw == "1234qwer") {
            _statusText.value = "테스트 계정 승인. 설정 진행..."
            _currentStep.value = 2
            return
        }

        // Auth UID 대신 저장된 ID 사용
        val userId = getSavedUserId()
        if (userId == null) {
            _statusText.value = "오류: 앱 로그인 정보 없음. 다시 로그인하세요."
            return
        }

        _statusText.value = "서버 정보 확인 중..."

        // users/{userId} 경로 조회
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

    // 3. 와이파이 정보 전송 및 [공유 도어락 저장]
    fun sendWifiSettings(ssid: String, pass: String) {
        if (_isBleConnected.value != true) {
            _statusText.value = "오류: 도어락 연결 끊김. 다시 연결해주세요."
            return
        }

        // [핵심 변경] 개인 DB가 아닌 공용 DB에 저장 및 연결
        registerSharedDoorlock(targetAddress, ssid, pass)

        val payload = "ssid:$ssid,password:$pass"

        Log.d("BLE_CHECK", "🚀 [전송 요청] $payload")
        _statusText.value = "설정값 전송 시도..."

        val result = writeCharacteristic(WIFI_CTRL_UUID, payload)
        if (!result) {
            _statusText.value = "전송 실패: UUID를 찾을 수 없습니다."
        }
    }

    // --- [신규] 공용 도어락 등록 및 사용자 연결 로직 ---
    private fun registerSharedDoorlock(mac: String, ssid: String, pass: String) {
        val userId = getSavedUserId() ?: return // 현재 로그인한 사용자 ID (예: user1)
        val currentTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

        val doorlocksRef = db.getReference("doorlocks").child(mac)
        val userDoorlocksRef = db.getReference("users").child(userId).child("my_doorlocks")

        // 1. 도어락이 이미 등록되어 있는지 확인
        doorlocksRef.get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                // A. 이미 등록된 도어락인 경우 -> 나(User)를 멤버로 추가 (공유)
                Log.d("DB_SHARE", "이미 등록된 도어락입니다. 멤버로 참여합니다.")

                // 도어락의 멤버 리스트에 나 추가
                doorlocksRef.child("members").child(userId).setValue("member")

                // 내 목록에 도어락 추가
                userDoorlocksRef.child(mac).setValue(true)

                // 와이파이 정보 업데이트 (선택 사항: 이미 연결된 경우 생략 가능하나 여기선 갱신)
                doorlocksRef.child("ssid").setValue(ssid)
                doorlocksRef.child("pw").setValue(pass)
                doorlocksRef.child("lastUpdated").setValue(currentTime)

            } else {
                // B. 처음 등록하는 도어락인 경우 -> 새로 생성 및 관리자 권한 부여
                Log.d("DB_SHARE", "새로운 도어락을 등록합니다.")

                val members = HashMap<String, String>()
                members[userId] = "admin" // 최초 등록자는 관리자

                val newLock = Doorlock(
                    mac = mac,
                    ssid = ssid,
                    pw = pass,
                    detailSettings = DetailSettings(true, 5, true), // 초기 설정
                    members = members,
                    lastUpdated = currentTime
                )

                // 공용 폴더에 저장
                doorlocksRef.setValue(newLock)

                // 내 목록에 추가
                userDoorlocksRef.child(mac).setValue(true)
            }
        }.addOnFailureListener {
            Log.e("DB_SHARE", "DB 접근 실패", it)
        }
    }

    // --- BLE 내부 로직 ---

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