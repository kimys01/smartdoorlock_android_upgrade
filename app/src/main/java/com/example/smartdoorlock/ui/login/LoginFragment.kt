package com.example.smartdoorlock.ui.login

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
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

        // 이미 로그인된 경우 대시보드로 이동
        if (auth.currentUser != null) {
            navigateToDashboard()
            return
        }

        binding.buttonLogin.setOnClickListener {
            val inputId = binding.editTextId.text.toString().trim()
            val password = binding.editTextPassword.text.toString().trim()

            if (inputId.isEmpty() || password.isEmpty()) {
                Toast.makeText(context, "아이디와 비밀번호를 입력하세요", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val emailForAuth = "$inputId@doorlock.com"

            auth.signInWithEmailAndPassword(emailForAuth, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val editor = prefs.edit()
                        if (binding.checkboxSaveId.isChecked) {
                            editor.putString("saved_id", inputId)
                        } else {
                            editor.remove("saved_id")
                        }
                        editor.putBoolean("auto_login", binding.checkboxAutoLogin.isChecked)
                        editor.apply()

                        Toast.makeText(context, "로그인 성공", Toast.LENGTH_SHORT).show()

                        // 로그인 성공 시 이동
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

    // [핵심 수정] 내비게이션 그래프에 정의된 액션(Action)을 사용하여 이동
    private fun navigateToDashboard() {
        try {
            // mobile_navigation.xml에 정의된 action_login_to_dashboard 액션을 실행합니다.
            // 이 액션에는 app:popUpTo="@id/mobile_navigation" app:popUpToInclusive="true"가
            // 설정되어 있어 이전 화면 기록을 모두 지우고 대시보드로 이동합니다.
            findNavController().navigate(R.id.action_login_to_dashboard)
        } catch (e: Exception) {
            // 만약 액션을 찾지 못할 경우를 대비한 예외 처리 (보통 XML 설정이 잘 되어있으면 발생하지 않음)
            Log.e("NavError", "네비게이션 이동 실패", e)
            // 비상 시 직접 이동 (스택 정리는 덜 될 수 있음)
            findNavController().navigate(R.id.navigation_dashboard)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}