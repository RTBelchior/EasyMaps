package com.example.easymap

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.easymap.data.SyncWorker
import com.example.easymap.databinding.ActivityMainBinding
import com.example.easymap.ui.auth.LoginActivity
import com.example.easymap.ui.history.HistoryFragment
import com.example.easymap.ui.map.MapFragment
import com.example.easymap.ui.profile.ProfileFragment
import com.example.easymap.util.applySystemBarsPadding
import com.google.firebase.auth.FirebaseAuth

/** Ecrã principal: navegação inferior entre Mapa, Histórico e Perfil. */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (FirebaseAuth.getInstance().currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarsPadding(top = true, bottom = false)

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_map -> show(MapFragment())
                R.id.nav_history -> show(HistoryFragment())
                R.id.nav_profile -> show(ProfileFragment())
            }
            true
        }
        if (savedInstanceState == null) binding.bottomNav.selectedItemId = R.id.nav_map

        SyncWorker.enqueue(this) // tenta sincronizar dados pendentes ao abrir a app
    }

    private fun show(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
}
