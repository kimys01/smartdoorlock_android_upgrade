package com.example.smartdoorlock.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.smartdoorlock.R
import com.example.smartdoorlock.databinding.FragmentSettingBinding

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        // XML 파일 이름이 fragment_setting.xml 이라고 가정합니다.
        _binding = FragmentSettingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. [디바이스 연결] 버튼 클릭 -> 스캔 화면으로 이동
        // XML에서 버튼 ID가 'deviceScanFragment'로 되어 있었습니다.
        binding.deviceScanFragment.setOnClickListener {
            try {
                // mobile_navigation.xml에 정의된 action ID로 이동
                findNavController().navigate(R.id.action_settings_to_scan)
            } catch (e: Exception) {
                Toast.makeText(context, "이동 오류: mobile_navigation.xml을 확인하세요.", Toast.LENGTH_SHORT).show()
                e.printStackTrace()
            }
        }

        // 2. [Wi-Fi 설정] 버튼 클릭
        binding.buttonWifiSetting.setOnClickListener {
            // 바로 와이파이 설정으로 가거나, 스캔을 먼저 하라고 안내할 수 있습니다.
            // 여기서는 바로 이동하도록 설정했습니다.
            try {
                // mobile_navigation.xml에 정의된 action ID (action_settings_to_wifi)가 필요합니다.
                // 만약 ID가 없다면 R.id.wifiSettingFragment 로 직접 이동 시도
                findNavController().navigate(R.id.wifiSettingFragment)
            } catch (e: Exception) {
                Toast.makeText(context, "이동 오류: 경로를 확인하세요.", Toast.LENGTH_SHORT).show()
            }
        }

        // 3. 기타 버튼들 연결
        binding.buttonAuthMethod.setOnClickListener {
            try { findNavController().navigate(R.id.navigation_auth_method) }
            catch (e: Exception) { showToast("인증 방식 화면이 아직 없습니다.") }
        }

        binding.buttonDetailSetting.setOnClickListener {
            try { findNavController().navigate(R.id.navigation_detail_setting) }
            catch (e: Exception) { showToast("상세 설정 화면이 아직 없습니다.") }
        }

        binding.buttonHelp.setOnClickListener {
            try { findNavController().navigate(R.id.navigation_help) }
            catch (e: Exception) { showToast("도움말 화면이 아직 없습니다.") }
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}