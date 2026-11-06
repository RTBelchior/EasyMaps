package com.example.easymap.util

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/** Aplica como padding o espaço das barras do sistema (modo edge-to-edge). */
fun View.applySystemBarsPadding(top: Boolean = true, bottom: Boolean = true) {
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val bars = insets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        )
        view.setPadding(bars.left, if (top) bars.top else 0, bars.right, if (bottom) bars.bottom else 0)
        insets
    }
}
