package com.example.smartdoorlock.data

import java.util.HashMap

/**
 * Firebase Realtime Database 전체 구조
 * 경로: users/{username}
 */
data class User(
    val username: String = "",
    val password: String = "",
    val name: String = "",
    val authMethod: String = "BLE",
    val phoneNumber: String = "",

    val doorlock: UserDoorlock = UserDoorlock(),

    // [수정] LocationLog 클래스 대신 Any(HashMap) 사용
    val location_logs: HashMap<String, Any> = HashMap(),

    val uwb_logs: HashMap<String, UwbLog> = HashMap(),
    val detailSettings: DetailSettings = DetailSettings(),
    val app_logs: HashMap<String, AppLogItem> = HashMap(),

    val createdAt: Long = System.currentTimeMillis()
)

// --- 하위 데이터 모델 ---

data class AppLogItem(
    val message: String = "",
    val timestamp: String = ""
)

data class UwbLog(
    val front_distance: Double = 0.0,
    val back_distance: Double = 0.0,
    val timestamp: String = ""
)

data class DetailSettings(
    val autoLockEnabled: Boolean = true,
    val autoLockTime: Int = 5,
    val notifyOnLock: Boolean = true
)

data class UserDoorlock(
    val status: DoorlockStatus = DoorlockStatus(),
    val logs: HashMap<String, DoorlockLog> = HashMap()
)

data class DoorlockStatus(
    val door_closed: Boolean = true,
    val last_method: String = "NONE",
    val last_time: String = "",
    val state: String = "LOCK"
)

data class DoorlockLog(
    val method: String = "",
    val state: String = "",
    val time: String = "",
    val user: String = ""
)