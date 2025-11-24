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
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.HashMap

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

            if (id.isEmpty() || pw.isEmpty() || name.isEmpty()) return@setOnClickListener
            if (pw.length < 6) return@setOnClickListener

            registerUser(id, pw, name)
        }
    }

    private fun registerUser(username: String, password: String, name: String) {
        binding.buttonRegister.isEnabled = false
        val fakeEmail = if(username.contains("@")) username else "$username@doorlock.com"

        auth.createUserWithEmailAndPassword(fakeEmail, password)
            .addOnSuccessListener { authResult ->
                val user = authResult.user
                val uid = user?.uid

                if (uid != null) {
                    val profileUpdates = UserProfileChangeRequest.Builder().setDisplayName(name).build()
                    user.updateProfile(profileUpdates).addOnCompleteListener {
                        saveFullUserStructure(username, password, name)
                    }
                }
            }
            .addOnFailureListener { e ->
                binding.buttonRegister.isEnabled = true
                Toast.makeText(context, "가입 실패: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun saveFullUserStructure(username: String, password: String, name: String) {
        val currentTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

        // [수정] 초기 로그 생성 (change 없이 바로 리스트에 추가)
        val initialLogs = HashMap<String, AppLogItem>()
        val logKey = database.reference.push().key ?: "init_log"
        initialLogs[logKey] = AppLogItem("계정 생성: Initial Set", currentTime)

        val initialDoorlock = UserDoorlock(
            status = DoorlockStatus(true, "INIT", currentTime, "LOCK")
        )

        val newUser = User(
            username = username,
            password = password,
            name = name,
            authMethod = "BLE",
            detailSettings = DetailSettings(true, 5, true),
            app_logs = initialLogs, // 단순화된 로그 구조 적용
            doorlock = initialDoorlock,
            uwb_logs = HashMap(),
            location_logs = HashMap()
        )

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