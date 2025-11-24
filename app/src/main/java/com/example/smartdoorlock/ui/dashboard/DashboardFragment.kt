package com.example.smartdoorlock.ui.dashboard

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.smartdoorlock.R
import com.example.smartdoorlock.data.DoorlockLog
import com.example.smartdoorlock.databinding.FragmentDashboardBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.*

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance()

    private var statusListener: ValueEventListener? = null
    private var statusRef: DatabaseReference? = null
    private var currentMacAddress: String? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnAddDevice.setOnClickListener {
            try { findNavController().navigate(R.id.action_dashboard_to_scan) }
            catch (e: Exception) { showSafeToast("이동 오류") }
        }

        binding.btnUnlock.setOnClickListener { unlockDoor() }

        checkAndMonitorDoorlock()
    }

    private fun checkAndMonitorDoorlock() {
        val prefs = requireActivity().getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
        val userId = prefs.getString("saved_id", null)

        if (userId == null) {
            updateDashboardUI("로그인이 필요합니다", false)
            return
        }

        val myLocksRef = database.getReference("users").child(userId).child("my_doorlocks")
        myLocksRef.get().addOnSuccessListener { snapshot ->
            if (_binding == null) return@addOnSuccessListener
            if (snapshot.exists() && snapshot.childrenCount > 0) {
                currentMacAddress = snapshot.children.first().key
                if (currentMacAddress != null) startRealtimeMonitoring(currentMacAddress!!)
            } else {
                updateDashboardUI("등록된 도어락이 없습니다", false)
            }
        }.addOnFailureListener {
            updateDashboardUI("데이터 로드 실패", false)
        }
    }

    private fun startRealtimeMonitoring(mac: String) {
        if (statusRef != null && statusListener != null) {
            statusRef?.removeEventListener(statusListener!!)
        }
        statusRef = database.getReference("doorlocks").child(mac).child("status")
        statusListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (_binding == null) return
                if (snapshot.exists()) {
                    val state = snapshot.child("state").getValue(String::class.java) ?: "UNKNOWN"
                    if (state == "UNLOCK") updateDashboardUI("문이 열려 있습니다 (UNLOCKED)", true, true)
                    else updateDashboardUI("문이 잠겨 있습니다 (LOCKED)", true, false)
                } else {
                    updateDashboardUI("도어락 연결됨 (대기 중)", true)
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
                binding.txtStatus.setTextColor(Color.parseColor("#2196F3"))
                binding.btnUnlock.text = "문 잠그기 (LOCK)"
            } else {
                binding.txtStatus.setTextColor(Color.parseColor("#4CAF50"))
                binding.btnUnlock.text = "문 열기 (UNLOCK)"
            }
            binding.btnUnlock.alpha = 1.0f
        } else {
            binding.txtStatus.setTextColor(Color.parseColor("#888888"))
            binding.btnUnlock.text = "도어락 연결 필요"
            binding.btnUnlock.alpha = 0.5f
        }
    }

    private fun unlockDoor() {
        if (currentMacAddress == null) {
            showSafeToast("도어락 정보를 불러오는 중입니다.")
            return
        }
        val prefs = requireActivity().getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
        val userId = prefs.getString("saved_id", "UnknownUser") ?: "UnknownUser"

        val statusRef = database.getReference("doorlocks").child(currentMacAddress!!).child("status")
        val sharedLogsRef = database.getReference("doorlocks").child(currentMacAddress!!).child("logs")
        // [핵심] 개인 로그 경로 (users/{id}/doorlock/logs)
        val userLogsRef = database.getReference("users").child(userId).child("doorlock").child("logs")

        val currentTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

        statusRef.get().addOnSuccessListener { snapshot ->
            val currentState = snapshot.child("state").getValue(String::class.java)
            val newState = if (currentState == "UNLOCK") "LOCK" else "UNLOCK"
            val method = "APP"

            val updates = mapOf(
                "state" to newState,
                "last_method" to method,
                "last_time" to currentTime,
                "door_closed" to (newState == "LOCK")
            )

            statusRef.updateChildren(updates).addOnSuccessListener {
                val action = if (newState == "UNLOCK") "열림" else "잠김"
                showSafeToast("문 $action 신호를 보냈습니다.")
            }

            val logData = DoorlockLog(
                method = method,
                state = newState,
                time = currentTime,
                user = userId
            )

            // 공용 로그 저장 (알림용)
            sharedLogsRef.push().setValue(logData)

            // 개인 로그 저장 (출입기록용)
            userLogsRef.push().setValue(logData)
        }
    }

    private fun showSafeToast(message: String) {
        if (context != null && isAdded) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (statusListener != null && statusRef != null) {
            statusRef?.removeEventListener(statusListener!!)
        }
        _binding = null
    }
}