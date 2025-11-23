package com.example.smartdoorlock.data

// 1. 전체 사용자 데이터 구조
data class UserData(
    val username: String = "",
    val password: String = "",
    val name: String = "",
    val authMethod: String = "BLE",
    val detailSettings: DetailSettings = DetailSettings(),

    // 이미지의 app_logs 구조 반영
    val app_logs: AppLogs = AppLogs(),

    val createdAt: Long = System.currentTimeMillis()
)

// 2. 상세 설정
data class DetailSettings(
    val autoLockEnabled: Boolean = true,
    val autoLockTime: Int = 5,
    val notifyOnLock: Boolean = true
)

// 3. 앱 로그 구조
data class AppLogs(
    val change: ChangeLogs = ChangeLogs()
)

// 변경 내역 카테고리 (auth, name, password)
data class ChangeLogs(
    val auth: AuthLog? = null,      // 인증 방식 변경 로그
    val name: NameLog? = null,      // 이름 변경 로그
    val password: PasswordLog? = null // 비밀번호 변경 로그
)

// --- [오류 해결] 아래 클래스들이 꼭 있어야 합니다 ---

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