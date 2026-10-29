package com.example.easymap.ui.auth

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException

/** Converte erros do Firebase Auth em mensagens simples em português. */
object AuthErrors {
    fun message(error: Exception?): String = when (error) {
        is FirebaseAuthInvalidUserException -> "Não existe nenhuma conta com este email."
        is FirebaseAuthInvalidCredentialsException -> "Email ou palavra-passe incorretos."
        is FirebaseAuthUserCollisionException -> "Já existe uma conta com este email."
        is FirebaseNetworkException -> "Sem ligação à Internet."
        else -> "Ocorreu um erro. Tenta novamente."
    }
}
