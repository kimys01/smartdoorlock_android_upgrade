package com.example.smartdoorlock.ui.setting

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.smartdoorlock.databinding.FragmentWifiSettingBinding

class WifiSettingFragment : Fragment() {

    private var _binding: FragmentWifiSettingBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: WifiSettingViewModel
    private var targetDeviceAddress: String = ""

    private val requestBlePermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.entries.all { it.value }) {
            viewModel.connectToDevice(targetDeviceAddress)
        } else {
            Toast.makeText(requireContext(), "권한 필요", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentWifiSettingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        arguments?.getString("DEVICE_ADDRESS")?.let { targetDeviceAddress = it }
        viewModel = ViewModelProvider(this).get(WifiSettingViewModel::class.java)

        // 시작 시 BLE 연결 시도
        if (targetDeviceAddress.isNotEmpty()) {
            if (checkBlePermissions()) viewModel.connectToDevice(targetDeviceAddress)
            else requestBlePermissions.launch(getRequiredBlePermissions())
        }

        // --- UI 관찰 ---
        viewModel.statusText.observe(viewLifecycleOwner) { status ->
            binding.textViewStatus.text = status
        }

        viewModel.currentStep.observe(viewLifecycleOwner) { step ->
            updateUiStep(step)
            // [추가] 2단계(와이파이)로 바로 넘어오면 SSID 자동 입력
            if (step == 2) fetchCurrentWifiSsid()
        }

        // --- 버튼 리스너 ---

        // 1. 앱 로그인
        binding.buttonLoginApp.setOnClickListener {
            val id = binding.editTextUserId.text.toString().trim()
            val pw = binding.editTextUserPw.text.toString().trim()
            if (id.isNotEmpty() && pw.isNotEmpty()) viewModel.verifyAppAdmin(id, pw)
            else Toast.makeText(context, "정보를 입력하세요", Toast.LENGTH_SHORT).show()
        }

        // 2. 와이파이 설정 (중간 PIN 인증 과정 삭제됨)
        binding.buttonConnectWifi.setOnClickListener {
            val ssid = binding.editTextSsid.text.toString()
            val pw = binding.editTextPassword.text.toString()
            if (ssid.isNotEmpty() && pw.isNotEmpty()) viewModel.sendWifiSettings(ssid, pw)
            else Toast.makeText(context, "입력 필요", Toast.LENGTH_SHORT).show()
        }
    }

    private fun fetchCurrentWifiSsid() {
        try {
            if (binding.editTextSsid.text.toString().isNotEmpty()) return
            val wifiManager = requireContext().applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val info = wifiManager.connectionInfo
            if (info != null && info.ssid != null && info.ssid != "<unknown ssid>") {
                binding.editTextSsid.setText(info.ssid.replace("\"", ""))
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun updateUiStep(step: Int) {
        binding.layoutLoginSection.visibility = View.GONE
        binding.layoutWifiSection.visibility = View.GONE

        when (step) {
            0 -> binding.layoutLoginSection.visibility = View.VISIBLE
            2 -> binding.layoutWifiSection.visibility = View.VISIBLE // 바로 2단계로
        }
    }

    private fun checkBlePermissions(): Boolean {
        return getRequiredBlePermissions().all {
            ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun getRequiredBlePermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onStop() {
        super.onStop()
        viewModel.disconnect()
    }
}