package com.example.easymap.ui.auth

import android.graphics.Color
import android.os.Bundle
import android.util.Patterns
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.example.easymap.databinding.ActivityResetPasswordBinding
import com.example.easymap.util.applySystemBarsPadding
import com.google.firebase.auth.FirebaseAuth

/** Recuperação de palavra-passe: o Firebase envia um email com o link de redefinição. */
class ResetPasswordActivity : AppCompatActivity() {

    private lateinit var binding: ActivityResetPasswordBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityResetPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarsPadding()

        binding.btnBack.setOnClickListener { finish() }
        binding.btnSend.setOnClickListener { sendEmail() }
    }

    private fun sendEmail() {
        val email = binding.etEmail.text.toString().trim()
        binding.cardEmail.strokeColor = Color.parseColor("#CBD5E1")

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.cardEmail.strokeColor = Color.parseColor("#EF4444")
            Toast.makeText(this, "Email inválido", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnSend.isEnabled = false
        FirebaseAuth.getInstance().sendPasswordResetEmail(email).addOnCompleteListener { task ->
            binding.btnSend.isEnabled = true
            if (task.isSuccessful) {
                Toast.makeText(this, "Email enviado. Verifica a caixa de correio.", Toast.LENGTH_LONG).show()
                finish()
            } else {
                Toast.makeText(this, AuthErrors.message(task.exception), Toast.LENGTH_LONG).show()
            }
        }
    }
}
