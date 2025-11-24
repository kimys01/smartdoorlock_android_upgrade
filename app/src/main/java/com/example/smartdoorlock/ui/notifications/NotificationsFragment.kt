package com.example.smartdoorlock.ui.notifications

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.smartdoorlock.data.DoorlockLog
import com.example.smartdoorlock.databinding.FragmentNotificationsBinding
import com.google.firebase.database.*
import java.util.*
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

        loadLogs()
    }

    private fun loadLogs() {
        val prefs = requireActivity().getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
        val userId = prefs.getString("saved_id", null)
        if (userId == null) return

        // 개인 로그 경로 조회
        val logsRef = database.getReference("users").child(userId).child("doorlock").child("logs")

        logsRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                logList.clear()
                for (data in snapshot.children) {
                    val log = data.getValue(DoorlockLog::class.java)
                    if (log != null) logList.add(log)
                }
                logList.reverse() // 최신순
                adapter.notifyDataSetChanged()

                if (logList.isEmpty()) {
                    binding.txtNoData.visibility = View.VISIBLE
                    binding.recyclerViewNotifications.visibility = View.GONE
                } else {
                    binding.txtNoData.visibility = View.GONE
                    binding.recyclerViewNotifications.visibility = View.VISIBLE
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}