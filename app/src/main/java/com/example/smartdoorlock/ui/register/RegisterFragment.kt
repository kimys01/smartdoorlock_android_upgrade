package com.example.smartdoorlock.ui.register

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.smartdoorlock.R
import com.example.smartdoorlock.data.*
import com.example.smartdoorlock.databinding.FragmentRegisterBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.*

class RegisterFragment : Fragment() {

    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!
    private lateinit var auth: FirebaseAuth
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
        val fakeEmail = if(username.contains("@")) username else "$username@doorlock.com"

        auth.createUserWithEmailAndPassword(fakeEmail, password)
            .addOnSuccessListener { authResult ->
                val uid = authResult.user?.uid
                if (uid != null) {
                    saveFullUserStructure(username, password, name)
                }
            }
            .addOnFailureListener { e ->
                binding.buttonRegister.isEnabled = true
                Toast.makeText(context, "가입 실패: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun saveFullUserStructure(username: String, password: String, name: String) {
        val currentTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

        // 초기 로그
        val initialLogs = AppLogs(
            change = ChangeLogs(
                auth = AuthLog("Initial: BLE", currentTime),
                name = NameLog("Initial: $name", currentTime),
                password = PasswordLog("Initial Set", currentTime),
                detail = DetailLog("Initial Set", currentTime)
            )
        )

        // 도어락 초기 상태 (이미지 3 반영)
        val initialDoorlock = UserDoorlock(
            status = DoorlockStatus(
                door_closed = true,
                last_method = "INIT",
                last_time = currentTime,
                state = "LOCK"
            )
        )

        val newUser = User(
            username = username,
            password = password,
            name = name,
            authMethod = "BLE",
            detailSettings = DetailSettings(true, 5, true),
            app_logs = initialLogs,
            doorlock = initialDoorlock // 추가됨
        )

        // DB 저장
        database.getReference("users").child(username).setValue(newUser)
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