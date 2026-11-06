package com.example.easymap.ui.profile

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
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

/** Perfil: consultar/editar nome, fotografia de perfil (câmara), estado da sincronização e terminar sessão. */
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
        binding.tvEmail.text = "Email: ${user.email}"
        showAvatar()

        binding.btnPhoto.setOnClickListener {
            val file = profilePhotoFile()
            val uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", file)
            photoUri = uri
            takePicture.launch(uri)
        }
        binding.btnSave.setOnClickListener { saveName() }
        binding.btnSync.setOnClickListener {
            SyncWorker.enqueue(requireContext())
            Toast.makeText(requireContext(), "Sincronização agendada.", Toast.LENGTH_SHORT).show()
        }
        binding.btnLogout.setOnClickListener { logout() }
    }

    override fun onResume() {
        super.onResume()
        val userId = auth.currentUser?.uid ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val pending = withContext(Dispatchers.IO) { AppDatabase.get(requireContext()).countUnsynced(userId) }
            if (_binding != null) {
                binding.tvSync.text = if (pending == 0) "Sincronização: tudo atualizado"
                else "Sincronização: $pending registo(s) por enviar"
            }
        }
    }

    private fun profilePhotoFile() = File(requireContext().filesDir, "profile_${auth.currentUser?.uid}.jpg")

    private fun showAvatar() {
        val file = profilePhotoFile()
        if (!file.exists()) return
        val bitmap = ImageUtils.decodeSampled(file.absolutePath, 400) ?: return
        binding.ivAvatar.setPadding(0, 0, 0, 0)
        binding.ivAvatar.imageTintList = null
        binding.ivAvatar.setImageBitmap(bitmap)
    }

    private fun saveName() {
        val name = binding.etName.text.toString().trim()
        if (name.isEmpty()) {
            binding.etName.error = "O nome não pode estar vazio"
            return
        }
        val request = UserProfileChangeRequest.Builder().setDisplayName(name).build()
        auth.currentUser?.updateProfile(request)?.addOnCompleteListener { task ->
            if (_binding == null) return@addOnCompleteListener
            val message = if (task.isSuccessful) "Perfil atualizado." else "Não foi possível guardar (sem ligação?)."
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
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
