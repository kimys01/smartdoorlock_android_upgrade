package com.example.smartdoorlock.ui.profile

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.smartdoorlock.data.NameLog
import com.example.smartdoorlock.data.PasswordLog
import com.example.smartdoorlock.databinding.FragmentUserUpdateBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.text.SimpleDateFormat
import java.util.*

class UserUpdateFragment : Fragment() {

    private var _binding: FragmentUserUpdateBinding? = null
    private val binding get() = _binding!!

    // Firebase 인스턴스
    private val database = FirebaseDatabase.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUserUpdateBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val prefs = requireActivity().getSharedPreferences("login_prefs", Context.MODE_PRIVATE)

        // 1. FirebaseAuth에서 UID 가져오기 (가장 안전)
        val uid = auth.currentUser?.uid
        if (uid == null) {
            Toast.makeText(context, "로그인 정보가 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        val userRef = database.getReference("users").child(uid)

        // ✅ 이름 변경 버튼 클릭
        binding.buttonUpdateName.setOnClickListener {
            val newName = binding.editTextNewName.text.toString().trim()

            if (newName.isEmpty()) {
                Toast.makeText(context, "새 이름을 입력하세요", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 기존 이름 가져오기 (로그 기록용)
            userRef.child("name").get().addOnSuccessListener { snapshot ->
                val currentName = snapshot.getValue(String::class.java) ?: ""

                if (newName == currentName) {
                    Toast.makeText(context, "변경 사항이 없습니다.", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                // [핵심] 이름 변경 + 로그 생성 (동시 업데이트)
                val timestamp = getCurrentTime()
                val updates = mutableMapOf<String, Any>()

                // 1. 이름 필드 업데이트
                updates["name"] = newName

                // 2. 로그 기록 ("기존->신규")
                val log = NameLog(new_name = "$currentName->$newName", timestamp = timestamp)
                updates["app_logs/change/name"] = log

                userRef.updateChildren(updates)
                    .addOnSuccessListener {
                        prefs.edit().putString("user_name", newName).apply()
                        Toast.makeText(context, "이름이 변경되었습니다", Toast.LENGTH_SHORT).show()
                        binding.editTextNewName.text.clear()
                    }
                    .addOnFailureListener {
                        Toast.makeText(context, "이름 변경 실패: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
            }
        }

        // ✅ 비밀번호 변경 버튼 클릭
        binding.buttonUpdatePassword.setOnClickListener {
            val currentInputPw = binding.editTextCurrentPassword.text.toString().trim()
            val newPw = binding.editTextNewPassword.text.toString().trim()

            if (currentInputPw.isEmpty() || newPw.isEmpty()) {
                Toast.makeText(context, "모든 필드를 입력하세요", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (newPw.length < 6) {
                Toast.makeText(context, "비밀번호는 6자리 이상이어야 합니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // DB에서 현재 비밀번호 확인
            userRef.child("password").get().addOnSuccessListener { snapshot ->
                val dbPassword = snapshot.getValue(String::class.java) ?: ""

                if (dbPassword == currentInputPw) {
                    // [핵심] 비밀번호 변경 + 로그 생성 + Auth 업데이트
                    val timestamp = getCurrentTime()
                    val updates = mutableMapOf<String, Any>()

                    // 1. 비밀번호 필드 업데이트
                    updates["password"] = newPw

                    // 2. 로그 기록
                    val log = PasswordLog(new_pw = "$dbPassword->$newPw", timestamp = timestamp)
                    updates["app_logs/change/password"] = log

                    userRef.updateChildren(updates)
                        .addOnSuccessListener {
                            // 3. Firebase Auth 비밀번호도 실제 변경
                            auth.currentUser?.updatePassword(newPw)?.addOnCompleteListener { task ->
                                if (task.isSuccessful) {
                                    Toast.makeText(context, "비밀번호가 변경되었습니다", Toast.LENGTH_SHORT).show()
                                    // 입력창 초기화
                                    binding.editTextCurrentPassword.text.clear()
                                    binding.editTextNewPassword.text.clear()
                                } else {
                                    Toast.makeText(context, "DB는 변경됐으나 로그인은 실패했습니다. 재로그인 해주세요.", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                        .addOnFailureListener {
                            Toast.makeText(context, "변경 실패: ${it.message}", Toast.LENGTH_SHORT).show()
                        }
                } else {
                    Toast.makeText(context, "현재 비밀번호가 일치하지 않습니다", Toast.LENGTH_SHORT).show()
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