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
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. 사용자 정보(이름, 아이디) 불러오기
        loadUserProfile()

        // 2. 등록된 기기 확인 (도어락 카드 표시 여부 결정)
        checkRegisteredDevice()

        // 버튼 리스너 연결
        binding.btnEditProfile.setOnClickListener { safeNavigate(R.id.navigation_user_update) }
        binding.btnConnectDevice.setOnClickListener { safeNavigate(R.id.action_profile_to_scan) }
        binding.btnLogout.setOnClickListener { showLogoutConfirmationDialog() }
    }

    // [신규] 사용자 프로필 정보 로드
    private fun loadUserProfile() {
        val prefs = requireActivity().getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
        val userId = prefs.getString("saved_id", null)

        if (userId == null) {
            binding.tvUserName.text = "게스트"
            binding.tvUserId.text = "로그인 정보 없음"
            return
        }

        // DB에서 이름 가져오기
        database.getReference("users").child(userId).child("name").get()
            .addOnSuccessListener { snapshot ->
                if (_binding == null) return@addOnSuccessListener
                val name = snapshot.getValue(String::class.java) ?: "사용자"

                // UI 업데이트
                binding.tvUserName.text = name
                binding.tvUserId.text = "ID: $userId"
            }
            .addOnFailureListener {
                if (_binding != null) {
                    binding.tvUserName.text = "사용자"
                    binding.tvUserId.text = "ID: $userId"
                }
            }
    }

    // 등록된 도어락 확인
    private fun checkRegisteredDevice() {
        val prefs = requireActivity().getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
        val userId = prefs.getString("saved_id", null) ?: return

        val myLocksRef = database.getReference("users").child(userId).child("my_doorlocks")

        myLocksRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (_binding == null) return

                if (snapshot.exists() && snapshot.childrenCount > 0) {
                    // 도어락이 있으면 카드 보이기
                    binding.cardViewRegistered.visibility = View.VISIBLE
                    val firstMac = snapshot.children.first().key
                    binding.tvRegisteredMac.text = "MAC: $firstMac"
                } else {
                    // 없으면 숨기기
                    binding.cardViewRegistered.visibility = View.GONE
                }
            }

            override fun onCancelled(error: DatabaseError) {
                if (_binding != null) binding.cardViewRegistered.visibility = View.GONE
            }
        })
    }

    // 로그아웃 확인창
    private fun showLogoutConfirmationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("로그아웃")
            .setMessage("정말 로그아웃 하시겠습니까?")
            .setPositiveButton("확인") { _, _ -> performLogout() }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun performLogout() {
        FirebaseAuth.getInstance().signOut()
        val prefs = requireActivity().getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        findNavController().navigate(R.id.action_global_login)
    }

    private fun safeNavigate(actionId: Int) {
        val navController = findNavController()
        if (navController.currentDestination?.id == R.id.navigation_profile) {
            try { navController.navigate(actionId) }
            catch (e: Exception) { e.printStackTrace() }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}