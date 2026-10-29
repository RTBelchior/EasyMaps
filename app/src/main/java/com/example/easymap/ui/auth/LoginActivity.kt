package com.example.easymap.ui.auth

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.enableEdgeToEdge
import com.example.easymap.MainActivity
import com.example.easymap.databinding.ActivityLoginBinding
import com.example.easymap.util.applySystemBarsPadding
import com.google.firebase.auth.FirebaseAuth

/** Ecrã de autenticação (também é o ecrã inicial da app). */
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (auth.currentUser != null) {
            goToMain()
            return
        }
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarsPadding()

        binding.btnLogin.setOnClickListener { login() }
        binding.btnGoRegister.setOnClickListener { startActivity(Intent(this, RegisterActivity::class.java)) }
        binding.tvForgot.setOnClickListener { startActivity(Intent(this, ResetPasswordActivity::class.java)) }
    }

    private fun login() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString()

        binding.tilEmail.error = null
        binding.tilPassword.error = null
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmail.error = "Email inválido"
            return
        }
        if (password.isEmpty()) {
            binding.tilPassword.error = "Introduz a palavra-passe"
            return
        }

        binding.btnLogin.isEnabled = false
        auth.signInWithEmailAndPassword(email, password).addOnCompleteListener { task ->
            binding.btnLogin.isEnabled = true
            if (task.isSuccessful) goToMain()
            else Toast.makeText(this, AuthErrors.message(task.exception), Toast.LENGTH_LONG).show()
        }
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
