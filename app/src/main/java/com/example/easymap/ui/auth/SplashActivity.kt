package com.example.easymap.ui.auth

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.example.easymap.MainActivity
import com.example.easymap.databinding.ActivitySplashBinding
import com.example.easymap.util.applySystemBarsPadding
import com.google.firebase.auth.FirebaseAuth

/**
 * Ecrã de entrada (Splash Screen): exibe o fundo branco com o logótipo da aplicação
 * antes de redirecionar para a página principal ou de autenticação.
 */
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarsPadding()

        Handler(Looper.getMainLooper()).postDelayed({
            if (isFinishing) return@postDelayed
            val target = if (FirebaseAuth.getInstance().currentUser != null) {
                Intent(this, MainActivity::class.java)
            } else {
                Intent(this, LoginActivity::class.java)
            }
            startActivity(target)
            finish()
        }, 1200L)
    }
}
