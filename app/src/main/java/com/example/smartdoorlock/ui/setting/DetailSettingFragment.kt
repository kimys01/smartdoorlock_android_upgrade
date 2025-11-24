package com.example.smartdoorlock.ui.settings

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.smartdoorlock.databinding.FragmentDetailSettingBinding
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.*

class DetailSettingFragment : Fragment() {

    private var _binding: FragmentDetailSettingBinding? = null
    private val binding get() = _binding!!
    private val database = FirebaseDatabase.getInstance()

    // 현재 제어 중인 도어락의 MAC 주소
    private var currentMacAddress: String? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDetailSettingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val prefs = requireActivity().getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
        val userId = prefs.getString("saved_id", null) ?: return

        // 1. 내 도어락 목록에서 MAC 주소 찾기 (첫 번째 도어락 기준)
        database.getReference("users").child(userId).child("my_doorlocks")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists() && snapshot.hasChildren()) {
                        // 첫 번째 도어락 선택
                        currentMacAddress = snapshot.children.first().key

                        if (currentMacAddress != null) {
                            // 2. 공용 도어락 설정 불러오기
                            loadSharedSettings(currentMacAddress!!)
                        }
                    } else {
                        Toast.makeText(context, "연결된 도어락이 없습니다.", Toast.LENGTH_SHORT).show()
                        // UI 비활성화 처리 등 가능
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun loadSharedSettings(mac: String) {
        // [핵심] 공용 경로: doorlocks/{mac}/detailSettings
        val settingsRef = database.getReference("doorlocks").child(mac).child("detailSettings")

        settingsRef.get().addOnSuccessListener { snapshot ->
            if (_binding == null) return@addOnSuccessListener

            if (snapshot.exists()) {
                val autoLock = snapshot.child("autoLockEnabled").getValue(Boolean::class.java) ?: true
                val notify = snapshot.child("notifyOnLock").getValue(Boolean::class.java) ?: true

                // 리스너 충돌 방지
                binding.switchAutoLock.setOnCheckedChangeListener(null)
                binding.switchNotifyOnLock.setOnCheckedChangeListener(null)

                binding.switchAutoLock.isChecked = autoLock
                binding.switchNotifyOnLock.isChecked = notify

                setupListeners(settingsRef, mac)
            } else {
                // 설정이 없으면 기본값으로 리스너 연결
                setupListeners(settingsRef, mac)
            }
        }
    }

    private fun setupListeners(settingsRef: com.google.firebase.database.DatabaseReference, mac: String) {
        if (_binding == null) return

        // [자동 잠금]
        binding.switchAutoLock.setOnCheckedChangeListener { _, isChecked ->
            val statusText = if (isChecked) "ON" else "OFF"
            val logMsg = "설정변경(자동잠금): $statusText"

            // 1. 공용 설정 업데이트
            settingsRef.child("autoLockEnabled").setValue(isChecked)
            settingsRef.child("autoLockTime").setValue(if (isChecked) 5 else 0)

            // 2. 공용 로그 남기기 (모든 사용자 알림용)
            saveSharedLog(mac, logMsg)
        }

        // [잠금 알림]
        binding.switchNotifyOnLock.setOnCheckedChangeListener { _, isChecked ->
            val statusText = if (isChecked) "ON" else "OFF"
            val logMsg = "설정변경(잠금알림): $statusText"

            settingsRef.child("notifyOnLock").setValue(isChecked)
            saveSharedLog(mac, logMsg)
        }
    }

    // [공용 로그 저장 함수]
    private fun saveSharedLog(mac: String, message: String) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

        // doorlocks/{mac}/logs 경로에 저장해야 NotificationService가 감지함
        val logsRef = database.getReference("doorlocks").child(mac).child("logs")

        val logData = mapOf(
            "method" to "APP_SETTING",
            "state" to message,
            "time" to timestamp
        )

        logsRef.push().setValue(logData)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}