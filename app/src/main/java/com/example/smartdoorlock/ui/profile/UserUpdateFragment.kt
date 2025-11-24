package com.example.smartdoorlock.ui.profile

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.smartdoorlock.data.NameLog
import com.example.smartdoorlock.data.PasswordLog
import com.example.smartdoorlock.databinding.FragmentUserUpdateBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest // [필수]
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.*

class UserUpdateFragment : Fragment() {

    private var _binding: FragmentUserUpdateBinding? = null
    private val binding get() = _binding!!

    private val database = FirebaseDatabase.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentUserUpdateBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val prefs = requireActivity().getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
        val userId = prefs.getString("saved_id", null)

        if (userId == null) {
            Toast.makeText(context, "로그인 정보가 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        val userRef = database.getReference("users").child(userId)

        // [이름 변경]
        binding.buttonUpdateName.setOnClickListener {
            val newName = binding.editTextNewName.text.toString().trim()
            if (newName.isEmpty()) {
                Toast.makeText(context, "새 이름을 입력하세요", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            userRef.child("name").get().addOnSuccessListener { snapshot ->
                val currentName = snapshot.getValue(String::class.java) ?: ""
                if (newName == currentName) return@addOnSuccessListener

                val timestamp = getCurrentTime()
                val updates = mutableMapOf<String, Any>()
                updates["name"] = newName
                val log = NameLog(new_name = "$currentName->$newName", timestamp = timestamp)
                updates["app_logs/change/name"] = log

                // 1. DB 업데이트
                userRef.updateChildren(updates)
                    .addOnSuccessListener {
                        // 2. [핵심 수정] Firebase Auth 프로필 이름 업데이트
                        val profileUpdates = UserProfileChangeRequest.Builder()
                            .setDisplayName(newName)
                            .build()

                        auth.currentUser?.updateProfile(profileUpdates)?.addOnCompleteListener {
                            prefs.edit().putString("user_name", newName).apply()
                            Toast.makeText(context, "이름 변경 완료 (웹 동기화됨)", Toast.LENGTH_SHORT).show()
                            binding.editTextNewName.text.clear()
                        }
                    }
            }
        }

        // [비밀번호 변경] (기존 로직 유지)
        binding.buttonUpdatePassword.setOnClickListener {
            val currentInputPw = binding.editTextCurrentPassword.text.toString().trim()
            val newPw = binding.editTextNewPassword.text.toString().trim()

            if (currentInputPw.isEmpty() || newPw.isEmpty()) return@setOnClickListener
            if (newPw.length < 6) return@setOnClickListener

            userRef.child("password").get().addOnSuccessListener { snapshot ->
                val dbPassword = snapshot.getValue(String::class.java) ?: ""

                if (dbPassword == currentInputPw) {
                    val timestamp = getCurrentTime()
                    val updates = mutableMapOf<String, Any>()
                    updates["password"] = newPw
                    val log = PasswordLog(new_pw = "$dbPassword->$newPw", timestamp = timestamp)
                    updates["app_logs/change/password"] = log

                    userRef.updateChildren(updates)
                        .addOnSuccessListener {
                            auth.currentUser?.updatePassword(newPw)
                            Toast.makeText(context, "비밀번호 변경 완료", Toast.LENGTH_SHORT).show()
                            binding.editTextCurrentPassword.text.clear()
                            binding.editTextNewPassword.text.clear()
                        }
                } else {
                    Toast.makeText(context, "현재 비밀번호 불일치", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun getCurrentTime(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}