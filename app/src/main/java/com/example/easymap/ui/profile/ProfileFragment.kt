package com.example.easymap.ui.profile

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.easymap.data.AppDatabase
import com.example.easymap.data.SyncWorker
import com.example.easymap.databinding.FragmentProfileBinding
import com.example.easymap.ui.auth.LoginActivity
import com.example.easymap.util.ImageUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Ecrã de Perfil com avatar de iniciais / fotografia personalizada,
 * campos de edição de nome, e-mail estático, estado de sincronização e logout.
 */
class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private val auth = FirebaseAuth.getInstance()
    private var photoUri: Uri? = null

    private val takePicture = registerForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        if (saved && _binding != null) showAvatar()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val user = auth.currentUser ?: return

        binding.etName.setText(user.displayName ?: "")
        binding.tvEmail.text = user.email ?: ""

        updateInitials(user.displayName ?: "", user.email ?: "")
        showAvatar()

        binding.btnPhoto.setOnClickListener {
            val file = profilePhotoFile()
            val uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", file)
            photoUri = uri
            takePicture.launch(uri)
        }

        binding.cardSync.setOnClickListener {
            SyncWorker.enqueue(requireContext())
            Toast.makeText(requireContext(), "Sincronização agendada.", Toast.LENGTH_SHORT).show()
            checkSyncStatus()
        }

        binding.btnSave.setOnClickListener { saveName() }
        binding.btnLogout.setOnClickListener { logout() }
    }

    override fun onResume() {
        super.onResume()
        checkSyncStatus()
    }

    private fun checkSyncStatus() {
        val userId = auth.currentUser?.uid ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val pending = withContext(Dispatchers.IO) {
                AppDatabase.get(requireContext()).countUnsynced(userId)
            }
            if (_binding != null) {
                if (pending == 0) {
                    binding.tvSyncStatus.text = "Tudo sincronizado"
                    binding.tvSyncStatus.setTextColor(Color.parseColor("#15803D"))
                } else {
                    binding.tvSyncStatus.text = "$pending pendente(s)"
                    binding.tvSyncStatus.setTextColor(Color.parseColor("#D97706"))
                }
            }
        }
    }

    private fun updateInitials(name: String, email: String) {
        val initials = when {
            name.isNotBlank() -> {
                val parts = name.trim().split("\\s+".toRegex())
                if (parts.size >= 2) {
                    "${parts[0].first().uppercaseChar()}${parts[1].first().uppercaseChar()}"
                } else {
                    parts[0].take(2).uppercase()
                }
            }
            email.isNotBlank() -> email.take(2).uppercase()
            else -> "EM"
        }
        binding.tvAvatarInitials.text = initials
    }

    private fun profilePhotoFile() = File(requireContext().filesDir, "profile_${auth.currentUser?.uid}.jpg")

    private fun showAvatar() {
        val file = profilePhotoFile()
        if (!file.exists()) {
            binding.ivAvatar.isVisible = false
            binding.containerAvatarInitials.isVisible = true
            return
        }
        val bitmap = ImageUtils.decodeSampled(file.absolutePath, 400)
        if (bitmap != null) {
            binding.ivAvatar.setImageBitmap(bitmap)
            binding.ivAvatar.isVisible = true
            binding.containerAvatarInitials.isVisible = false
        } else {
            binding.ivAvatar.isVisible = false
            binding.containerAvatarInitials.isVisible = true
        }
    }

    private fun saveName() {
        val name = binding.etName.text.toString().trim()
        if (name.isEmpty()) {
            binding.etName.error = "O nome não pode estar vazio"
            return
        }
        val user = auth.currentUser
        val request = UserProfileChangeRequest.Builder().setDisplayName(name).build()
        user?.updateProfile(request)?.addOnCompleteListener { task ->
            if (_binding == null) return@addOnCompleteListener
            if (task.isSuccessful) {
                updateInitials(name, user.email ?: "")
                Toast.makeText(requireContext(), "Perfil atualizado.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Não foi possível guardar.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun logout() {
        auth.signOut()
        startActivity(
            Intent(requireContext(), LoginActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
