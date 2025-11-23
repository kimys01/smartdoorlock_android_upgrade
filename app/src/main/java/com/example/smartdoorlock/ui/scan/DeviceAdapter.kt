package com.example.smartdoorlock.ui.scan

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.smartdoorlock.R // <--- R 클래스 import는 유지

/**
 * 블루투스 기기 스캔 결과를 RecyclerView에 표시하는 어댑터입니다.
 * 기기를 클릭하면 MAC 주소를 WifiSettingFragment로 전달합니다.
 */
class DeviceAdapter(
    private val devices: List<BluetoothDevice>,
    private val onClick: (BluetoothDevice) -> Unit
) : RecyclerView.Adapter<DeviceAdapter.DeviceViewHolder>() {

    class DeviceViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        // R.id.deviceName 및 R.id.deviceAddress는 item_device.xml에 정의되어야 합니다.
        val name: TextView = view.findViewById(R.id.deviceName)
        val address: TextView = view.findViewById(R.id.deviceAddress)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        // item_device.xml 레이아웃을 인플레이트
        val view = LayoutInflater.from(parent.context).inflate(com.example.smartdoorlock.R.layout.item_device, parent, false)
        // ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ R.layout에 명시적 패키지 경로 사용
        return DeviceViewHolder(view)
    }

    @SuppressLint("MissingPermission")
    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val device = devices[position]

        holder.name.text = device.name ?: "이름 없음"
        holder.address.text = device.address

        holder.itemView.setOnClickListener {
            onClick(device)
        }
    }

    override fun getItemCount() = devices.size
}