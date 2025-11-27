package com.example.smartdoorlock.ui.notifications

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.smartdoorlock.data.DoorlockLog
import com.example.smartdoorlock.databinding.FragmentNotificationsBinding
import com.google.firebase.database.*
import kotlin.collections.ArrayList

class NotificationsFragment : Fragment() {

    private var _binding: FragmentNotificationsBinding? = null
    private val binding get() = _binding!!
    private val database = FirebaseDatabase.getInstance()

    private lateinit var adapter: NotificationAdapter
    private val logList = ArrayList<DoorlockLog>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentNotificationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = NotificationAdapter(logList)
        binding.recyclerViewNotifications.layoutManager = LinearLayoutManager(context)
        binding.recyclerViewNotifications.adapter = adapter

        // 공용 도어락 로그 불러오기
        loadLogsFromSharedDoorlock()
    }

    private fun loadLogsFromSharedDoorlock() {
        val prefs = requireActivity().getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
        val userId = prefs.getString("saved_id", null)

        if (userId == null) {
            showNoData()
            return
        }

        // 1. 내 도어락 목록 가져오기
        database.getReference("users")
            .child(userId)
            .child("my_doorlocks")
            .limitToFirst(1)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.exists() && snapshot.childrenCount > 0) {
                    val doorlockId = snapshot.children.first().key

                    if (doorlockId != null) {
                        monitorSharedLogs(doorlockId)
                    } else {
                        showNoData()
                    }
                } else {
                    showNoData()
                }
            }
            .addOnFailureListener {
                showNoData()
            }
    }

    private fun monitorSharedLogs(doorlockId: String) {
        val logsRef = database.getReference("doorlocks")
            .child(doorlockId)
            .child("logs")

        logsRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                logList.clear()

                for (data in snapshot.children) {
                    try {
                        val method = data.child("method").getValue(String::class.java) ?: "UNKNOWN"
                        val state = data.child("state").getValue(String::class.java) ?: "UNKNOWN"
                        val time = data.child("time").getValue(String::class.java) ?: ""
                        val user = data.child("user").getValue(String::class.java) ?: "알 수 없음"

                        val log = DoorlockLog(
                            method = method,
                            state = state,
                            time = time,
                            user = user
                        )

                        logList.add(log)
                    } catch (e: Exception) {
                        Log.e("Notifications", "로그 파싱 오류: ${e.message}")
                    }
                }

                logList.reverse()
                adapter.notifyDataSetChanged()

                if (logList.isEmpty()) {
                    showNoData()
                } else {
                    hideNoData()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                showNoData()
            }
        })
    }

    private fun showNoData() {
        if (_binding == null) return
        // [수정] txtNoData 대신 layoutNoData 레이아웃 전체를 제어 (디자인 개선 반영)
        binding.layoutNoData.visibility = View.VISIBLE
        binding.recyclerViewNotifications.visibility = View.GONE
    }

    private fun hideNoData() {
        if (_binding == null) return
        binding.layoutNoData.visibility = View.GONE
        binding.recyclerViewNotifications.visibility = View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}