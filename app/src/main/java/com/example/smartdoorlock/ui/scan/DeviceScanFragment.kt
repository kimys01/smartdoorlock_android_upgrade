package com.example.smartdoorlock.ui.scan

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController // [수정] 네비게이션 컨트롤러 import
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.smartdoorlock.R // [필수] R 클래스 import
import com.example.smartdoorlock.databinding.FragmentDeviceScanBinding

class DeviceScanFragment : Fragment() {

    private var _binding: FragmentDeviceScanBinding? = null
    private val binding get() = _binding!!

    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var isScanning = false
    private val handler = Handler(Looper.getMainLooper())
    private val scanResults = ArrayList<BluetoothDevice>()
    private lateinit var deviceAdapter: DeviceAdapter
    private val SCAN_PERIOD: Long = 10000 // 10초 스캔

    // 권한 요청 처리
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.entries.all { it.value }) {
            startScan()
        } else {
            Toast.makeText(context, "블루투스 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDeviceScanBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 블루투스 어댑터 초기화
        val bluetoothManager = context?.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        // 리스트 어댑터 설정 (클릭 시 WifiSettingFragment로 이동)
        deviceAdapter = DeviceAdapter(scanResults) { device ->
            stopScan()
            navigateToWifiSetting(device)
        }
        binding.recyclerViewDevices.layoutManager = LinearLayoutManager(context)
        binding.recyclerViewDevices.adapter = deviceAdapter

        binding.btnStartScan.setOnClickListener {
            if (checkPermissions()) startScan()
            else requestPermissionLauncher.launch(getRequiredPermissions())
        }
    }

    @SuppressLint("MissingPermission")
    private fun startScan() {
        if (isScanning) return

        if (!bluetoothAdapter.isEnabled) {
            Toast.makeText(context, "블루투스를 켜주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        scanResults.clear()
        deviceAdapter.notifyDataSetChanged()

        isScanning = true
        binding.progressBarScanning.visibility = View.VISIBLE
        binding.tvScanStatus.text = "주변 도어락 찾는 중..."
        binding.btnStartScan.isEnabled = false

        bluetoothAdapter.bluetoothLeScanner?.startScan(leScanCallback)

        // 10초 후 스캔 자동 중지
        handler.postDelayed({ stopScan() }, SCAN_PERIOD)
    }

    @SuppressLint("MissingPermission")
    private fun stopScan() {
        if (!isScanning) return
        isScanning = false
        bluetoothAdapter.bluetoothLeScanner?.stopScan(leScanCallback)
        binding.progressBarScanning.visibility = View.INVISIBLE
        binding.tvScanStatus.text = "스캔 완료"
        binding.btnStartScan.isEnabled = true
    }

    private val leScanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            if (device.name == null) return

            if (!scanResults.contains(device)) {
                scanResults.add(device)
                deviceAdapter.notifyItemInserted(scanResults.size - 1)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Toast.makeText(context, "스캔 실패: $errorCode", Toast.LENGTH_SHORT).show()
            stopScan()
        }
    }

    @SuppressLint("MissingPermission")
    private fun navigateToWifiSetting(device: BluetoothDevice) {
        val bundle = Bundle()
        bundle.putString("DEVICE_ADDRESS", device.address)

        // [핵심 수정] fragment_container 대신 NavController 사용
        // 주의: mobile_navigation.xml에 WifiSettingFragment의 id가 "wifiSettingFragment"로 정의되어 있어야 합니다.
        try {
            findNavController().navigate(R.id.wifiSettingFragment, bundle)
        } catch (e: Exception) {
            // ID가 없을 경우를 대비한 예외 처리
            Toast.makeText(context, "이동 실패: mobile_navigation.xml을 확인하세요.", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    private fun checkPermissions(): Boolean {
        return getRequiredPermissions().all {
            ActivityCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun getRequiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopScan()
        _binding = null
    }
}