package com.example.smartdoorlock.ui.dashboard

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
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.*

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    // Firebase
    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. [+ 새 도어락 등록] 버튼
        binding.btnAddDevice.setOnClickListener {
            try {
                findNavController().navigate(R.id.action_dashboard_to_scan)
            } catch (e: Exception) {
                showSafeToast("이동 오류: 네비게이션을 확인하세요.")
            }
        }

        // 2. [잠금 해제] 버튼 (팅김 방지 로직 적용)
        binding.btnUnlock.setOnClickListener {
            try {
                unlockDoor()
            } catch (e: Exception) {
                Log.e("Dashboard", "버튼 클릭 처리 중 오류", e)
                showSafeToast("오류가 발생했습니다: ${e.message}")
            }
        }

        monitorDoorlockStatus()
    }

    private fun unlockDoor() {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            showSafeToast("로그인이 필요합니다.")
            return
        }

        // 내 도어락 목록 가져오기
        val myLocksRef = database.getReference("users").child(uid).child("my_doorlocks")

        myLocksRef.get().addOnSuccessListener { snapshot ->
            // [안전장치 1] 비동기 작업 후 화면이 살아있는지 확인
            if (!isAdded || _binding == null) return@addOnSuccessListener

            // [안전장치 2] 데이터가 있고, 자식 노드(목록)가 실제로 존재하는지 확인
            if (snapshot.exists() && snapshot.hasChildren()) {
                try {
                    // 첫 번째 도어락의 MAC 주소 가져오기 (없으면 예외 발생 가능하므로 try-catch)
                    val firstLockMac = snapshot.children.first().key

                    if (!firstLockMac.isNullOrEmpty()) {
                        sendUnlockCommand(firstLockMac)
                    } else {
                        showSafeToast("도어락 정보가 잘못되었습니다.")
                    }
                } catch (e: NoSuchElementException) {
                    showSafeToast("등록된 도어락 목록이 비어있습니다.")
                }
            } else {
                showSafeToast("등록된 도어락이 없습니다. 먼저 등록해주세요.")
            }
        }.addOnFailureListener { e ->
            showSafeToast("데이터 불러오기 실패: ${e.message}")
        }
    }

    private fun sendUnlockCommand(macAddress: String) {
        val statusRef = database.getReference("doorlocks").child(macAddress).child("status")
        val logsRef = database.getReference("doorlocks").child(macAddress).child("logs")

        val currentTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

        val updates = mapOf(
            "state" to "UNLOCK",
            "last_method" to "APP",
            "last_time" to currentTime,
            "door_closed" to false
        )

        statusRef.updateChildren(updates).addOnSuccessListener {
            showSafeToast("문 열림 신호를 보냈습니다.")
        }.addOnFailureListener {
            showSafeToast("명령 전송 실패: ${it.message}")
        }

        // 로그 기록
        val newLogKey = logsRef.push().key
        if (newLogKey != null) {
            val logData = mapOf(
                "method" to "APP",
                "state" to "UNLOCK",
                "time" to currentTime
            )
            logsRef.child(newLogKey).setValue(logData)
        }
    }

    private fun monitorDoorlockStatus() {
        val uid = auth.currentUser?.uid ?: return
        // 실시간 모니터링 로직 (필요 시 구현)
    }

    // [안전장치 3] Context가 null일 때 토스트 띄우면 앱 죽음 방지
    private fun showSafeToast(message: String) {
        if (context != null && isAdded) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        } else {
            Log.w("Dashboard", "화면이 닫혀서 토스트를 띄우지 못함: $message")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}