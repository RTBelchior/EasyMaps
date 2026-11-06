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

        val loginText = HtmlCompat.fromHtml(
            "Já tens conta? <font color=\"#2563EB\"><b>Entrar</b></font>",
            HtmlCompat.FROM_HTML_MODE_LEGACY
        )
        binding.btnBack.text = loginText

        binding.btnRegister.setOnClickListener { register() }
        binding.btnBack.setOnClickListener { finish() }
    }

    private fun register() {
        val name = binding.etName.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString()
        val confirm = binding.etConfirm.text.toString()

        val cards = listOf(binding.cardName, binding.cardEmail, binding.cardPassword, binding.cardConfirm)
        cards.forEach { it.strokeColor = Color.parseColor("#CBD5E1") }

        when {
            name.isEmpty() -> {
                binding.cardName.strokeColor = Color.parseColor("#EF4444")
                Toast.makeText(this, "Introduz o nome", Toast.LENGTH_SHORT).show()
                return
            }
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                binding.cardEmail.strokeColor = Color.parseColor("#EF4444")
                Toast.makeText(this, "Email inválido", Toast.LENGTH_SHORT).show()
                return
            }
            password.length < 6 -> {
                binding.cardPassword.strokeColor = Color.parseColor("#EF4444")
                Toast.makeText(this, "Palavra-passe requer no mínimo 6 caracteres", Toast.LENGTH_SHORT).show()
                return
            }
            password != confirm -> {
                binding.cardConfirm.strokeColor = Color.parseColor("#EF4444")
                Toast.makeText(this, "As palavras-passe não coincidem", Toast.LENGTH_SHORT).show()
                return
            }
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
