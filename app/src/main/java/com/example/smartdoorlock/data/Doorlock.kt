package com.example.smartdoorlock.data

/**
 * 공유 도어락 데이터 구조
 * 경로: doorlocks/{macAddress}
 */
data class Doorlock(
    val mac: String = "",
    val ssid: String = "",
    val pw: String = "",

    // 상세 설정 (공유됨)
    val detailSettings: DetailSettings = DetailSettings(),

    // 이 도어락을 사용할 수 있는 사용자 목록 (uid: 권한)
    // 예: { "user1_uid": "admin", "user2_uid": "member" }
    val members: HashMap<String, String> = HashMap(),

    val lastUpdated: String = ""
)