package com.example.smartdoorlock.ui.register

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.smartdoorlock.R
import com.example.smartdoorlock.data.* // UserModels 사용
import com.example.smartdoorlock.databinding.FragmentRegisterBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase // Realtime Database import

class RegisterFragment : Fragment() {

    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!
    private lateinit var auth: FirebaseAuth

    // [변경] Realtime Database 인스턴스
    private val database = FirebaseDatabase.getInstance()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = FirebaseAuth.getInstance()

        binding.buttonRegister.setOnClickListener {
            val id = binding.editTextId.text.toString().trim()
            val pw = binding.editTextPassword.text.toString().trim()
            val name = binding.editTextName.text.toString().trim()

            if (id.isEmpty() || pw.isEmpty() || name.isEmpty()) {
                Toast.makeText(context, "모든 정보를 입력하세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (pw.length < 6) {
                Toast.makeText(context, "비밀번호는 6자리 이상이어야 합니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            registerUser(id, pw, name)
        }
    }

    private fun registerUser(username: String, password: String, name: String) {
        binding.buttonRegister.isEnabled = false

        // 일반 아이디를 이메일 형식으로 변환
        val fakeEmail = if(username.contains("@")) username else "$username@doorlock.com"

        auth.createUserWithEmailAndPassword(fakeEmail, password)
            .addOnSuccessListener { authResult ->
                val uid = authResult.user?.uid
                if (uid != null) {
                    saveFullUserStructure(uid, username, password, name)
                }
            }
            .addOnFailureListener { e ->
                binding.buttonRegister.isEnabled = true
                Toast.makeText(context, "가입 실패: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun saveFullUserStructure(uid: String, username: String, password: String, name: String) {
        val currentTime = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())

        // 1. 사진 속 구조대로 초기 데이터 생성
        val initialLogs = AppLogs(
            change = ChangeLogs(
                auth = AuthLog(new_auth = "Initial: BLE", timestamp = currentTime),
                name = NameLog(new_name = "Initial: $name", timestamp = currentTime),
                password = PasswordLog(new_pw = "Initial Set", timestamp = currentTime)
            )
        )

        val newUser = User(
            username = username,
            password = password,
            name = name,
            authMethod = "BLE",
            detailSettings = DetailSettings(true, 5, true), // 기본 설정
            app_logs = initialLogs // 로그 구조 포함
        )

        // 2. Realtime Database에 저장 (users/UID 경로)
        // setValue를 쓰면 해당 경로의 데이터를 통째로 덮어씁니다.
        database.getReference("users").child(uid).setValue(newUser)
            .addOnSuccessListener {
                Toast.makeText(context, "회원가입 완료!", Toast.LENGTH_SHORT).show()
                findNavController().navigate(R.id.navigation_login)
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "DB 저장 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                binding.buttonRegister.isEnabled = true
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}