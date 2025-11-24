package com.example.smartdoorlock.ui.notifications

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.smartdoorlock.R
import com.example.smartdoorlock.data.DoorlockLog

class NotificationAdapter(private val logs: List<DoorlockLog>) : RecyclerView.Adapter<NotificationAdapter.LogViewHolder>() {

    class LogViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imgIcon: ImageView = view.findViewById(R.id.imgLogIcon)
        val txtTitle: TextView = view.findViewById(R.id.txtLogTitle)
        val txtTime: TextView = view.findViewById(R.id.txtLogTime)
        val txtUser: TextView = view.findViewById(R.id.txtLogUser)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_notification, parent, false)
        return LogViewHolder(view)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        val log = logs[position]
        holder.txtTime.text = log.time
        holder.txtUser.text = "사용자: ${log.user}"

        if (log.state == "UNLOCK") {
            holder.txtTitle.text = "문이 열렸습니다 (${log.method})"
            holder.txtTitle.setTextColor(Color.parseColor("#2196F3"))
            holder.imgIcon.setImageResource(R.drawable.ic_launcher_foreground)
            holder.imgIcon.setColorFilter(Color.parseColor("#2196F3"))
        } else {
            holder.txtTitle.text = "문이 잠겼습니다 (${log.method})"
            holder.txtTitle.setTextColor(Color.parseColor("#4CAF50"))
            holder.imgIcon.setImageResource(R.drawable.ic_launcher_foreground)
            holder.imgIcon.setColorFilter(Color.parseColor("#4CAF50"))
        }
    }

    override fun getItemCount() = logs.size
}