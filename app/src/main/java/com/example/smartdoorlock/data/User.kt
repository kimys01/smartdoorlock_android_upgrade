package com.example.smartdoorlock.data

/**
 * Firebase Realtime Database 구조 정의
 * users/{username} 아래에 이 구조대로 저장됩니다.
 */
data class User(
    val username: String = "",
    val password: String = "",
    val name: String = "",
    val authMethod: String = "BLE",

    // [이미지 1, 2, 3 반영]
    val doorlock: UserDoorlock = UserDoorlock(),
    val location_logs: HashMap<String, LocationLog> = HashMap(),

    val detailSettings: DetailSettings = DetailSettings(),
    val app_logs: AppLogs = AppLogs(),
    val createdAt: Long = System.currentTimeMillis()
)

// --- 도어락 관련 구조 (이미지 2, 3) ---
data class UserDoorlock(
    val status: DoorlockStatus = DoorlockStatus(),
    val logs: HashMap<String, DoorlockLog> = HashMap()
)

data class DoorlockStatus(
    val door_closed: Boolean = true,
    val last_method: String = "NONE", // 예: DOOR_BTN, APP
    val last_time: String = "",       // 예: 2025-11-23 21:50:40
    val state: String = "LOCK"        // LOCK / UNLOCK
)

data class DoorlockLog(
    val method: String = "",
    val state: String = "",
    val time: String = ""
)

// --- 위치 로그 구조 (이미지 1) ---
data class LocationLog(
    val altitude: Double = 0.0,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val timestamp: String = ""
)

// --- 기존 설정 구조 ---
data class DetailSettings(
    val autoLockEnabled: Boolean = true,
    val autoLockTime: Int = 5,
    val notifyOnLock: Boolean = true
)

data class AppLogs(val change: ChangeLogs = ChangeLogs())
data class ChangeLogs(
    val auth: AuthLog? = null,
    val name: NameLog? = null,
    val password: PasswordLog? = null,
    val detail: DetailLog? = null
)
data class AuthLog(val new_auth: String = "", val timestamp: String = "")
data class NameLog(val new_name: String = "", val timestamp: String = "")
data class PasswordLog(val new_pw: String = "", val timestamp: String = "")
data class DetailLog(val new_detail: String = "", val timestamp: String = "")