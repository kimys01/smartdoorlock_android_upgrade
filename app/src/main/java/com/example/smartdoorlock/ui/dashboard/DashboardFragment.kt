package com.example.smartdoorlock.ui.dashboard

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.smartdoorlock.R
import com.example.smartdoorlock.databinding.FragmentDashboardBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance()

    private var statusListener: ValueEventListener? = null
    private var statusRef: DatabaseReference? = null
    private var currentDoorlockId: String? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnAddDevice.setOnClickListener {
            try { findNavController().navigate(R.id.action_dashboard_to_scan) } catch (e: Exception) {}
        }

        binding.btnUnlock.setOnClickListener {
            toggleDoorLock()
        }

        checkAndMonitorDoorlock()
    }

    private fun checkAndMonitorDoorlock() {
        val prefs = requireActivity().getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
        val userId = prefs.getString("saved_id", null)

        if (userId == null) {
            updateDashboardUI("로그인 필요", false)
            return
        }

        database.getReference("users").child(userId).child("my_doorlocks")
            .limitToFirst(1).get().addOnSuccessListener { snapshot ->
                if (_binding == null) return@addOnSuccessListener
                if (snapshot.exists() && snapshot.childrenCount > 0) {
                    currentDoorlockId = snapshot.children.first().key
                    if (currentDoorlockId != null) {
                        startRealtimeMonitoring(currentDoorlockId!!)
                    }
                } else {
                    updateDashboardUI("등록된 도어락 없음", false)
                }
            }
    }

    private fun startRealtimeMonitoring(doorlockId: String) {
        if (statusRef != null && statusListener != null) {
            statusRef?.removeEventListener(statusListener!!)
        }

        statusRef = database.getReference("doorlocks").child(doorlockId).child("status")

        statusListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (_binding == null) return
                if (snapshot.exists()) {
                    // Firebase에서 상태가 바뀌면 즉시 호출됨
                    val state = snapshot.child("state").getValue(String::class.java) ?: "UNKNOWN"
                    Log.d("Dashboard", "상태 변경 감지: $state")

                    if (state == "UNLOCK") {
                        updateDashboardUI("문이 열려 있습니다 🔓", true, true)
                    } else {
                        updateDashboardUI("문이 잠겨 있습니다 🔒", true, false)
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        statusRef?.addValueEventListener(statusListener!!)
    }

    private fun updateDashboardUI(statusText: String, isEnabled: Boolean, isUnlocked: Boolean = false) {
        if (_binding == null) return
        binding.txtStatus.text = statusText
        binding.btnUnlock.isEnabled = isEnabled

        if (isEnabled) {
            if (isUnlocked) {
                binding.txtStatus.setTextColor(Color.parseColor("#2196F3")) // 파란색
                binding.btnUnlock.text = "문 잠그기 🔒"
                binding.btnUnlock.setBackgroundResource(R.drawable.gradient_button_background) // 배경 리소스 확인 필요
            } else {
                binding.txtStatus.setTextColor(Color.parseColor("#4CAF50")) // 초록색
                binding.btnUnlock.text = "문 열기 🔓"
            }
        }
    }

    private fun toggleDoorLock() {
        if (currentDoorlockId == null) return

        // 1. 현재 상태 확인 없이 버튼 텍스트 기반으로 명령 결정 (더 빠른 반응)
        val isCurrentlyUnlock = binding.btnUnlock.text.toString().contains("잠그기")
        val newState = if (isCurrentlyUnlock) "LOCK" else "UNLOCK"

        // 2. 명령 전송 (ESP32가 수신)
        database.getReference("doorlocks").child(currentDoorlockId!!).child("command").setValue(newState)
            .addOnSuccessListener {
                Toast.makeText(context, "명령 전송: $newState", Toast.LENGTH_SHORT).show()
            }

        // 3. (옵션) UI 미리 업데이트 (낙관적 업데이트) - 실제 하드웨어 응답은 리스너가 처리
        // updateDashboardUI(if(newState=="UNLOCK") "문이 열려 있습니다" else "문이 잠겨 있습니다", true, newState=="UNLOCK")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (statusListener != null && statusRef != null) {
            statusRef?.removeEventListener(statusListener!!)
        }
        _binding = null
    }
}