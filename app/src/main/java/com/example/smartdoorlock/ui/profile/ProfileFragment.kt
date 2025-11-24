package com.example.smartdoorlock.ui.profile

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.smartdoorlock.R
import com.example.smartdoorlock.databinding.FragmentProfileBinding
import com.google.firebase.auth.FirebaseAuth
// import com.google.firebase.database.* // 현재 레이아웃에서 사용하지 않는 DB 기능 주석 처리

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    // binding 변수는 onCreateView와 onDestroyView 사이에서만 유효합니다.
    private val binding get() = _binding!!

    private val auth = FirebaseAuth.getInstance()

    // 새로운 디자인에서는 아직 DB 데이터를 바인딩할 뷰 ID가 지정되지 않았으므로 주석 처리합니다.
    // private val database = FirebaseDatabase.getInstance()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // [삭제됨] checkRegisteredDevice()
        // 이유: 새로운 XML 레이아웃(fragment_profile.xml)에는 'cardViewRegistered', 'tvRegisteredMac' ID가 없습니다.
        // 현재 디자인은 정적인 'User Info' 카드(홍길동)를 보여주고 있습니다.

        // 1. 내 정보 수정 버튼
        binding.btnEditProfile.setOnClickListener {
            // 네비게이션 액션 ID가 nav_graph.xml에 정의되어 있어야 합니다.
            safeNavigate(R.id.navigation_user_update)
        }

        // 2. 새 도어락 연결 버튼
        binding.btnConnectDevice.setOnClickListener {
            // 네비게이션 액션 ID가 nav_graph.xml에 정의되어 있어야 합니다.
            safeNavigate(R.id.action_profile_to_scan)
        }

        // 3. 로그아웃 버튼 (다이얼로그 띄우기)
        binding.btnLogout.setOnClickListener {
            showLogoutConfirmationDialog()
        }
    }

    private fun showLogoutConfirmationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("로그아웃")
            .setMessage("정말 로그아웃 하시겠습니까?")
            .setPositiveButton("확인") { _, _ ->
                performLogout()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun performLogout() {
        // 1. Firebase 로그아웃
        auth.signOut()

        // 2. 자동 로그인 정보 삭제 (SharedPreferences)
        val prefs = requireActivity().getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()

        // 3. 로그인 화면으로 이동
        // action_global_login이 nav_graph.xml에 정의되어 있어야 합니다.
        try {
            findNavController().navigate(R.id.action_global_login)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // 안전한 네비게이션 이동 함수
    private fun safeNavigate(actionId: Int) {
        val navController = findNavController()
        // 현재 목적지가 ProfileFragment일 때만 이동 (중복 클릭 방지)
        if (navController.currentDestination?.id == R.id.navigation_profile) {
            try {
                navController.navigate(actionId)
            } catch (e: IllegalArgumentException) {
                e.printStackTrace()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}