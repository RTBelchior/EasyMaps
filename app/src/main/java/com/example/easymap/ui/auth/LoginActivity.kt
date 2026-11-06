package com.example.easymap.ui.auth

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Patterns
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.HtmlCompat
import com.example.easymap.MainActivity
import com.example.easymap.databinding.ActivityLoginBinding
import com.example.easymap.util.applySystemBarsPadding
import com.google.firebase.auth.FirebaseAuth

/** Ecrã de autenticação com layout limpo e moderno. */
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

        val registerText = HtmlCompat.fromHtml(
            "Ainda não tens conta? <font color=\"#2563EB\"><b>Criar conta</b></font>",
            HtmlCompat.FROM_HTML_MODE_LEGACY
        )
        binding.btnGoRegister.text = registerText

        binding.btnLogin.setOnClickListener { login() }
        binding.btnGoRegister.setOnClickListener { startActivity(Intent(this, RegisterActivity::class.java)) }
        binding.tvForgot.setOnClickListener { startActivity(Intent(this, ResetPasswordActivity::class.java)) }
    }

    private fun login() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString()

        binding.cardEmail.strokeColor = Color.parseColor("#CBD5E1")
        binding.cardPassword.strokeColor = Color.parseColor("#CBD5E1")

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.cardEmail.strokeColor = Color.parseColor("#EF4444")
            Toast.makeText(this, "Email inválido", Toast.LENGTH_SHORT).show()
            return
        }
        if (password.isEmpty()) {
            binding.cardPassword.strokeColor = Color.parseColor("#EF4444")
            Toast.makeText(this, "Introduz a palavra-passe", Toast.LENGTH_SHORT).show()
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
