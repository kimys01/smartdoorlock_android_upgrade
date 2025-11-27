package com.example.smartdoorlock.ui.notifications

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.smartdoorlock.R
import com.example.smartdoorlock.data.DoorlockLog
import java.text.SimpleDateFormat
import java.util.*

class NotificationAdapter(private val logs: List<DoorlockLog>) :
    RecyclerView.Adapter<NotificationAdapter.LogViewHolder>() {

    class LogViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val frameIconBackground: FrameLayout = view.findViewById(R.id.frameIconBackground)
        val imgIcon: ImageView = view.findViewById(R.id.imgLogIcon)
        val txtTitle: TextView = view.findViewById(R.id.txtLogTitle)
        val txtUser: TextView = view.findViewById(R.id.txtLogUser)
        val txtMethod: TextView = view.findViewById(R.id.txtLogMethod)
        val txtDate: TextView = view.findViewById(R.id.txtLogDate)
        val txtTime: TextView = view.findViewById(R.id.txtLogTime)
        val txtState: TextView = view.findViewById(R.id.txtLogState)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_notification, parent, false)
        return LogViewHolder(view)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        val log = logs[position]

        // 상태에 따라 UI 변경
        if (log.state == "UNLOCK") {
            // 🔓 열림 상태
            holder.txtTitle.text = "🔓 문이 열렸습니다"
            holder.txtTitle.setTextColor(Color.parseColor("#2563EB")) // 파란색
            holder.imgIcon.setImageResource(android.R.drawable.ic_lock_lock)
            holder.txtState.text = "UNLOCK"
            holder.txtState.setTextColor(Color.parseColor("#10B981")) // 초록색

        } else {
            // 🔒 잠김 상태
            holder.txtTitle.text = "🔒 문이 잠겼습니다"
            holder.txtTitle.setTextColor(Color.parseColor("#DC2626")) // 빨간색
            holder.imgIcon.setImageResource(android.R.drawable.ic_lock_idle_lock)
            holder.txtState.text = "LOCK"
            holder.txtState.setTextColor(Color.parseColor("#EF4444")) // 빨간색
        }

        // 사용자 정보
        holder.txtUser.text = log.user

        // 방법 배지 (APP, RFID, BLE, AUTO 등)
        holder.txtMethod.text = log.method

        // 방법에 따라 배지 색상 변경
        when (log.method) {
            "APP" -> {
                holder.txtMethod.setTextColor(Color.parseColor("#6366F1")) // 보라색
                holder.txtMethod.setBackgroundColor(Color.parseColor("#EEF2FF"))
            }
            "RFID" -> {
                holder.txtMethod.setTextColor(Color.parseColor("#F59E0B")) // 주황색
                holder.txtMethod.setBackgroundColor(Color.parseColor("#FEF3C7"))
            }
            "BLE" -> {
                holder.txtMethod.setTextColor(Color.parseColor("#10B981")) // 초록색
                holder.txtMethod.setBackgroundColor(Color.parseColor("#D1FAE5"))
            }
            "AUTO_LOCK" -> {
                holder.txtMethod.setTextColor(Color.parseColor("#6B7280")) // 회색
                holder.txtMethod.setBackgroundColor(Color.parseColor("#F3F4F6"))
            }
            else -> {
                holder.txtMethod.setTextColor(Color.parseColor("#6B7280"))
                holder.txtMethod.setBackgroundColor(Color.parseColor("#F3F4F6"))
            }
        }

        // 전체 날짜/시간 표시
        holder.txtDate.text = log.time

        // 시간만 추출 (HH:mm 형식)
        try {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val date = sdf.parse(log.time)
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            holder.txtTime.text = timeFormat.format(date ?: Date())
        } catch (e: Exception) {
            holder.txtTime.text = log.time.substring(11, 16) // 간단한 추출
        }
    }

    override fun getItemCount() = logs.size
}