package com.example.smartdoorlock.ui.settings

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.smartdoorlock.data.DetailLog // 추가된 데이터 모델
import com.example.smartdoorlock.databinding.FragmentDetailSettingBinding
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.*

class DetailSettingFragment : Fragment() {

    private var _binding: FragmentDetailSettingBinding? = null
    private val binding get() = _binding!!

    private val database = FirebaseDatabase.getInstance()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDetailSettingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val prefs = requireActivity().getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
        val userId = prefs.getString("saved_id", null)

        if (userId == null) {
            Toast.makeText(context, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
            return
        }

        // 경로: users/{아이디} (하위 detailSettings와 logs를 모두 건드려야 하므로 상위 참조)
        val userRef = database.getReference("users").child(userId)

        // 1. 초기값 불러오기
        userRef.child("detailSettings").get().addOnSuccessListener { snapshot ->
            if (_binding == null) return@addOnSuccessListener

            if (snapshot.exists()) {
                val autoLock = snapshot.child("autoLockEnabled").getValue(Boolean::class.java) ?: false
                val notify = snapshot.child("notifyOnLock").getValue(Boolean::class.java) ?: false

                binding.switchAutoLock.setOnCheckedChangeListener(null)
                binding.switchNotifyOnLock.setOnCheckedChangeListener(null)

                binding.switchAutoLock.isChecked = autoLock
                binding.switchNotifyOnLock.isChecked = notify

                setupListeners(userRef)
            } else {
                setupListeners(userRef)
            }
        }
    }

    private fun setupListeners(userRef: com.google.firebase.database.DatabaseReference) {
        if (_binding == null) return

        // [자동 잠금 스위치]
        binding.switchAutoLock.setOnCheckedChangeListener { _, isChecked ->
            val timestamp = getCurrentTime()
            val statusText = if (isChecked) "ON" else "OFF"

            // 로그 객체 생성
            val log = DetailLog(new_detail = "AutoLock->$statusText", timestamp = timestamp)

            // 설정값과 로그를 한번에 업데이트
            val updates = mapOf<String, Any>(
                "detailSettings/autoLockEnabled" to isChecked,
                "detailSettings/autoLockTime" to if (isChecked) 5 else 0,
                "app_logs/change/detail" to log
            )

            userRef.updateChildren(updates).addOnFailureListener {
                if (_binding != null) {
                    Toast.makeText(context, "설정 실패", Toast.LENGTH_SHORT).show()
                    binding.switchAutoLock.isChecked = !isChecked
                }
            }
        }

        // [잠금 알림 스위치]
        binding.switchNotifyOnLock.setOnCheckedChangeListener { _, isChecked ->
            val timestamp = getCurrentTime()
            val statusText = if (isChecked) "ON" else "OFF"

            val log = DetailLog(new_detail = "Notify->$statusText", timestamp = timestamp)

            val updates = mapOf<String, Any>(
                "detailSettings/notifyOnLock" to isChecked,
                "app_logs/change/detail" to log
            )

            userRef.updateChildren(updates).addOnFailureListener {
                if (_binding != null) {
                    Toast.makeText(context, "설정 실패", Toast.LENGTH_SHORT).show()
                    binding.switchNotifyOnLock.isChecked = !isChecked
                }
            }
        }
    }

    private fun getCurrentTime(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}