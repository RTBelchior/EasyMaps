package com.example.easymap.ui.auth

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.example.easymap.MainActivity
import com.example.easymap.databinding.ActivityRegisterBinding
import com.example.easymap.util.applySystemBarsPadding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest

/** Registo de novos utilizadores (Firebase Authentication). */
class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarsPadding()

        binding.btnRegister.setOnClickListener { register() }
        binding.btnBack.setOnClickListener { finish() }
    }

    private fun register() {
        val name = binding.etName.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString()
        val confirm = binding.etConfirm.text.toString()

        listOf(binding.tilName, binding.tilEmail, binding.tilPassword, binding.tilConfirm).forEach { it.error = null }
        when {
            name.isEmpty() -> { binding.tilName.error = "Introduz o nome"; return }
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> { binding.tilEmail.error = "Email inválido"; return }
            password.length < 6 -> { binding.tilPassword.error = "Mínimo 6 caracteres"; return }
            password != confirm -> { binding.tilConfirm.error = "As palavras-passe não coincidem"; return }
        }

        binding.btnRegister.isEnabled = false
        auth.createUserWithEmailAndPassword(email, password).addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                binding.btnRegister.isEnabled = true
                Toast.makeText(this, AuthErrors.message(task.exception), Toast.LENGTH_LONG).show()
                return@addOnCompleteListener
            }
            val request = UserProfileChangeRequest.Builder().setDisplayName(name).build()
            task.result.user?.updateProfile(request)?.addOnCompleteListener { goToMain() } ?: goToMain()
        }
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }
}
