package com.example.smartdoorlock

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.ParcelUuid
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.uwb.UwbManager
import androidx.core.uwb.UwbClientSessionScope
// [FIX] RangingParameters와 RangingResult만 임포트하고, 나머지는 완전 경로로 사용합니다.
import androidx.core.uwb.RangingParameters
import androidx.core.uwb.RangingResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.UUID


class DoorlockService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    // [하드웨어와 약속된 UUID]
    private val SERVICE_UUID = UUID.fromString("12345678-1234-1234-1234-1234567890ab")
    private val CHAR_UUID = UUID.fromString("abcd1234-5678-90ab-cdef-1234567890ab")

    private lateinit var uwbManager: UwbManager
    private var uwbSession: UwbClientSessionScope? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var isReadySent = false

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "DOORLOCK_SERVICE_CHANNEL"
        const val NOTIFICATION_ID = 101
        const val UWB_THRESHOLD_CM = 300.0 // 3m
    }

    // =========================================================
    // 1. 서비스 라이프사이클
    // =========================================================

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("DoorLockService", "🚀 Doorlock Service Started. Initiating BLE Scan.")
        startBleScan()
        return START_STICKY
    }

    override fun onDestroy() {
        Log.d("DoorLockService", "🛑 Service Stopped. Cleaning up resources.")
        bluetoothGatt?.close()
        job.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // =========================================================
    // 2. 포그라운드 알림 설정
    // =========================================================

    private fun createNotification() = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
        .setContentTitle("Smart Doorlock")
        .setContentText("집 근처에서 자동 연결 및 잠금 해제 준비 중입니다.")
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setOngoing(true)
        .build()
        .also { createNotificationChannel() }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Doorlock Control"
            val descriptionText = "백그라운드 UWB/BLE 통신을 위한 채널"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    // =========================================================
    // 3. BLE 스캔 및 연결
    // =========================================================

    private fun startBleScan() {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
        val scanner = adapter.bluetoothLeScanner ?: return

        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanner.startScan(listOf(filter), settings, bleScanCallback)
    }

    private val bleScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            Log.d("DoorLockService", "✅ 도어락 발견: ${result.device.address}")

            BluetoothAdapter.getDefaultAdapter().bluetoothLeScanner.stopScan(this)
            result.device.connectGatt(this@DoorlockService, false, gattCallback)
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d("DoorLockService", "🔗 BLE 연결 성공. UWB 준비.")
                bluetoothGatt = gatt
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d("DoorLockService", "❌ BLE 연결 해제.")
                stopSelf()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                startUwbRanging(gatt)
            }
        }
    }

    // =========================================================
    // 4. UWB 거리 측정 및 READY 전송
    // =========================================================

    private fun startUwbRanging(gatt: BluetoothGatt) = scope.launch {
        try {
            uwbManager = UwbManager.createInstance(this@DoorlockService)
            uwbSession = uwbManager.clientSessionScope()

            // ★★★ [FIXED] Builder와 UwbConfigType을 완전 경로로 작성하여 참조 오류 해결 ★★★
            val rangingParams = RangingParameters.Builder()
                .setUwbConfigType(RangingParameters.UwbConfigType.CONFIG_UNICAST_DS_TWR)
                .build()

            uwbSession!!.prepareSession(rangingParams).collect { result ->
                if (result is RangingResult.RangingResultPosition) {
                    // m 단위를 cm로 변환
                    val distanceCm = result.position.distance?.value!! * 100
                    Log.d("UWB_RANGING", "거리: ${"%.2f".format(distanceCm)} cm")

                    if (distanceCm < UWB_THRESHOLD_CM) {
                        if (!isReadySent) {
                            sendBleCommand(gatt, "READY")
                            isReadySent = true
                            Log.d("DoorLockService", "✅ UWB 3m 진입! READY 신호 전송.")
                        }
                    } else {
                        isReadySent = false
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("UWB_RANGING", "UWB 세션 에러: ${e.message}")
        }
    }

    private fun sendBleCommand(gatt: BluetoothGatt, command: String) {
        val service = gatt.getService(SERVICE_UUID)
        val characteristic = service?.getCharacteristic(CHAR_UUID)

        if (characteristic != null) {
            characteristic.value = command.toByteArray()
            gatt.writeCharacteristic(characteristic)
        }
    }
}