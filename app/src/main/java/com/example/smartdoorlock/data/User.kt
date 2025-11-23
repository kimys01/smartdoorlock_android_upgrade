package com.example.smartdoorlock.data

/**
 * Firebase Realtime Database의 전체 사용자 구조를 정의하는 데이터 클래스입니다.
 * users/{uid} 경로 아래에 이 구조대로 데이터가 저장됩니다.
 */
data class User(
    val username: String = "",
    val password: String = "",
    val name: String = "",
    val authMethod: String = "BLE", // 기본 인증 방식

    // 상세 설정 (자동 잠금, 알림 등)
    val detailSettings: DetailSettings = DetailSettings(),

    // 앱 로그 (변경 이력) - 이게 있어야 로그 기능이 작동합니다!
    val app_logs: AppLogs = AppLogs(),

    val createdAt: Long = System.currentTimeMillis()
)

// --- 하위 데이터 클래스들 (중첩 구조) ---

data class DetailSettings(
    val autoLockEnabled: Boolean = true,
    val autoLockTime: Int = 5,
    val notifyOnLock: Boolean = true
)

data class AppLogs(
    val change: ChangeLogs = ChangeLogs()
)

data class ChangeLogs(
    val auth: AuthLog? = null,      // 인증 방식 변경 로그
    val name: NameLog? = null,      // 이름 변경 로그
    val password: PasswordLog? = null // 비밀번호 변경 로그
)

data class AuthLog(
    val new_auth: String = "",
    val timestamp: String = ""
)

data class NameLog(
    val new_name: String = "",
    val timestamp: String = ""
)

data class PasswordLog(
    val new_pw: String = "",
    val timestamp: String = ""
)