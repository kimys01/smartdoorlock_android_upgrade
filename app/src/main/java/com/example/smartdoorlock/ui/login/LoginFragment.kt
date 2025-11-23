package com.example.smartdoorlock.ui.login

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.example.smartdoorlock.R
import com.example.smartdoorlock.databinding.FragmentLoginBinding
import com.google.firebase.auth.FirebaseAuth

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!
    private lateinit var auth: FirebaseAuth

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = FirebaseAuth.getInstance()

        val prefs = requireActivity().getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
        val savedId = prefs.getString("saved_id", "")
        val autoLogin = prefs.getBoolean("auto_login", false)

        binding.editTextId.setText(savedId)
        binding.checkboxSaveId.isChecked = !savedId.isNullOrEmpty()
        binding.checkboxAutoLogin.isChecked = autoLogin

        // 자동 로그인 (이미 로그인된 상태라면 패스)
        if (autoLogin && auth.currentUser != null) {
            navigateToDashboard()
            return
        }

        // [로그인 버튼 클릭]
        binding.buttonLogin.setOnClickListener {
            val inputId = binding.editTextId.text.toString().trim() // 사용자가 입력한 일반 아이디
            val password = binding.editTextPassword.text.toString().trim()

            if (inputId.isEmpty() || password.isEmpty()) {
                Toast.makeText(context, "아이디와 비밀번호를 입력하세요", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // [핵심 수정] 일반 아이디 뒤에 가짜 도메인을 붙여서 이메일 형식으로 만듭니다.
            // 예: 사용자가 "admin" 입력 -> "admin@doorlock.com"으로 로그인 시도
            val emailForAuth = "$inputId@doorlock.com"

            auth.signInWithEmailAndPassword(emailForAuth, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        // 로그인 성공 시 저장 로직
                        val editor = prefs.edit()
                        if (binding.checkboxSaveId.isChecked) {
                            editor.putString("saved_id", inputId) // 원래 아이디 저장
                        } else {
                            editor.remove("saved_id")
                        }
                        editor.putBoolean("auto_login", binding.checkboxAutoLogin.isChecked)
                        editor.apply()

                        Toast.makeText(context, "로그인 성공", Toast.LENGTH_SHORT).show()
                        navigateToDashboard()
                    } else {
                        Toast.makeText(context, "로그인 실패: 아이디나 비밀번호를 확인하세요.", Toast.LENGTH_SHORT).show()
                        Log.e("LoginError", "Error: ${task.exception?.message}")
                    }
                }
        }

        binding.buttonSignUp.setOnClickListener {
            findNavController().navigate(R.id.navigation_register)
        }
    }

    private fun navigateToDashboard() {
        val navOptions = NavOptions.Builder()
            .setPopUpTo(R.id.navigation_login, true)
            .build()
        findNavController().navigate(R.id.navigation_dashboard, null, navOptions)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}