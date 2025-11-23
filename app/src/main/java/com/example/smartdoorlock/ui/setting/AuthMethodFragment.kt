package com.example.smartdoorlock.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.smartdoorlock.data.AuthLog
import com.example.smartdoorlock.databinding.FragmentAuthMethodBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.*

class AuthMethodFragment : Fragment() {

    private var _binding: FragmentAuthMethodBinding? = null
    private val binding get() = _binding!!

    private val database = FirebaseDatabase.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAuthMethodBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val uid = auth.currentUser?.uid
        if (uid == null) {
            Toast.makeText(context, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
            return
        }

        val userRef = database.getReference("users").child(uid)

        // 1. 기존 설정 불러오기
        userRef.child("authMethod").get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                val method = snapshot.getValue(String::class.java)
                when (method) {
                    "BLE" -> binding.radioBle.isChecked = true
                    "UWB" -> binding.radioUwb.isChecked = true // [추가] UWB 체크
                    "RFID" -> binding.radioRfid.isChecked = true
                    "Password" -> binding.radioPassword.isChecked = true
                }
            }
        }

        // 2. 저장 버튼 클릭
        binding.buttonUpdateAuthMethod.setOnClickListener {
            val newMethod = when {
                binding.radioBle.isChecked -> "BLE"
                binding.radioUwb.isChecked -> "UWB" // [추가] UWB 선택 시
                binding.radioRfid.isChecked -> "RFID"
                binding.radioPassword.isChecked -> "Password"
                else -> ""
            }

            if (newMethod.isEmpty()) {
                Toast.makeText(context, "인증 방식을 선택하세요", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 3. 로그 기록 및 저장
            userRef.child("authMethod").get().addOnSuccessListener { snapshot ->
                val currentMethod = snapshot.getValue(String::class.java) ?: "None"

                if (currentMethod == newMethod) {
                    Toast.makeText(context, "변경 사항이 없습니다.", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                val log = AuthLog(new_auth = "$currentMethod->$newMethod", timestamp = timestamp)

                val updates = mapOf<String, Any>(
                    "authMethod" to newMethod,
                    "app_logs/change/auth" to log
                )

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