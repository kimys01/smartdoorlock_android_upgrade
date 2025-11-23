package com.example.smartdoorlock.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.smartdoorlock.data.AuthLog // UserModels.kt에 정의된 클래스
import com.example.smartdoorlock.databinding.FragmentAuthMethodBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.*

class AuthMethodFragment : Fragment() {

    private var _binding: FragmentAuthMethodBinding? = null
    private val binding get() = _binding!!

    // Firebase 인스턴스
    private val database = FirebaseDatabase.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAuthMethodBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. 사용자 UID 가져오기
        val uid = auth.currentUser?.uid
        if (uid == null) {
            Toast.makeText(context, "로그인 정보가 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        val userRef = database.getReference("users").child(uid)

        // 2. 화면 진입 시: DB에서 현재 설정된 인증 방식 불러와서 체크하기
        userRef.child("authMethod").get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                val method = snapshot.getValue(String::class.java)
                when (method) {
                    "BLE" -> binding.radioBle.isChecked = true
                    "RFID" -> binding.radioRfid.isChecked = true
                    "Password" -> binding.radioPassword.isChecked = true
                }
            }
        }

        // 3. [저장 버튼 클릭] -> 변경 사항 저장 및 로그 기록
        binding.buttonUpdateAuthMethod.setOnClickListener {
            val newMethod = when {
                binding.radioBle.isChecked -> "BLE"
                binding.radioRfid.isChecked -> "RFID"
                binding.radioPassword.isChecked -> "Password"
                else -> ""
            }

            if (newMethod.isEmpty()) {
                Toast.makeText(context, "인증 방식을 선택하세요", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // [핵심 로직] 변경 전 DB 값을 읽어와서 로그(Old->New)를 만듭니다.
            userRef.child("authMethod").get().addOnSuccessListener { snapshot ->
                val currentMethod = snapshot.getValue(String::class.java) ?: "None"

                // 변경사항이 없으면 저장하지 않음
                if (currentMethod == newMethod) {
                    Toast.makeText(context, "변경된 내용이 없습니다.", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                // 1. 로그 데이터 생성
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                val log = AuthLog(
                    new_auth = "$currentMethod->$newMethod", // 예: "BLE->RFID"
                    timestamp = timestamp
                )

                // 2. 업데이트할 데이터 맵 구성 (설정값 + 로그)
                val updates = mapOf<String, Any>(
                    "authMethod" to newMethod,           // 실제 설정 변경
                    "app_logs/change/auth" to log        // 로그 기록
                )

                // 3. DB에 한 번에 업데이트 (Atomic Update)
                userRef.updateChildren(updates)
                    .addOnSuccessListener {
                        Toast.makeText(context, "인증 방식이 변경되었습니다.", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener {
                        Toast.makeText(context, "저장 실패: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}