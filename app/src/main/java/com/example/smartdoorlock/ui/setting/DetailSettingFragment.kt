package com.example.smartdoorlock.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.smartdoorlock.databinding.FragmentDetailSettingBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class DetailSettingFragment : Fragment() {

    private var _binding: FragmentDetailSettingBinding? = null
    private val binding get() = _binding!!

    // Realtime Database & Auth
    private val database = FirebaseDatabase.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDetailSettingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. 현재 로그인된 사용자 UID 가져오기 (SharedPreferences보다 안전함)
        val uid = auth.currentUser?.uid
        if (uid == null) {
            Toast.makeText(context, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
            return
        }

        // DB 경로: users/{uid}/detailSettings
        val settingsRef = database.getReference("users").child(uid).child("detailSettings")

        // 2. 화면이 켜질 때: DB에서 설정값 불러와서 스위치에 반영 (실시간 동기화)
        // addValueEventListener를 쓰면 앱을 켜놓은 상태에서 웹에서 DB를 바꿔도 앱 스위치가 바뀝니다.
        settingsRef.get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                val autoLock = snapshot.child("autoLockEnabled").getValue(Boolean::class.java) ?: false
                val notify = snapshot.child("notifyOnLock").getValue(Boolean::class.java) ?: false

                // 리스너가 작동하지 않도록 임시로 null 처리 후 설정 (무한 루프 방지)
                binding.switchAutoLock.setOnCheckedChangeListener(null)
                binding.switchNotifyOnLock.setOnCheckedChangeListener(null)

                binding.switchAutoLock.isChecked = autoLock
                binding.switchNotifyOnLock.isChecked = notify

                // 설정 후 리스너 재연결
                setupListeners(settingsRef)
            } else {
                // 데이터가 없으면 리스너만 연결
                setupListeners(settingsRef)
            }
        }
    }

    private fun setupListeners(settingsRef: com.google.firebase.database.DatabaseReference) {
        // 3. 스위치 조작 시: 실시간으로 DB 업데이트 (버튼 누를 필요 없음)

        // [자동 잠금 스위치]
        binding.switchAutoLock.setOnCheckedChangeListener { _, isChecked ->
            // 요청하신 로직: 켜지면 5초, 꺼지면 0초로 저장
            val updates = mapOf(
                "autoLockEnabled" to isChecked,
                "autoLockTime" to if (isChecked) 5 else 0
            )

            settingsRef.updateChildren(updates)
                .addOnFailureListener {
                    Toast.makeText(context, "설정 실패", Toast.LENGTH_SHORT).show()
                    // 실패 시 스위치 원상복구
                    binding.switchAutoLock.isChecked = !isChecked
                }
        }

        // [잠금 알림 스위치]
        binding.switchNotifyOnLock.setOnCheckedChangeListener { _, isChecked ->
            settingsRef.child("notifyOnLock").setValue(isChecked)
                .addOnFailureListener {
                    Toast.makeText(context, "설정 실패", Toast.LENGTH_SHORT).show()
                    binding.switchNotifyOnLock.isChecked = !isChecked
                }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}