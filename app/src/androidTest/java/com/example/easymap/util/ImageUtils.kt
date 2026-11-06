package com.example.easymap.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File

/** Utilitários para fotografias (leitura reduzida, rotação EXIF e conversão para Base64). */
object ImageUtils {

    /** Lê a imagem já reduzida (evita estourar a memória) e corrige a rotação da câmara. */
    fun decodeSampled(path: String, maxSize: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (bounds.outWidth / sample > maxSize || bounds.outHeight / sample > maxSize) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = BitmapFactory.decodeFile(path, options) ?: return null
        return rotateIfRequired(path, bitmap)
    }

    private fun rotateIfRequired(path: String, bitmap: Bitmap): Bitmap {
        val orientation = try {
            ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } catch (e: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> return bitmap
        }
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /** Miniatura JPEG em Base64 (≈20-40 KB) para enviar dentro do documento do Firestore. */
    fun toBase64Thumbnail(path: String): String? {
        val bitmap = decodeSampled(path, 480) ?: return null
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 60, output)
        return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
    }

    fun saveFromBase64(base64: String, target: File): Boolean = try {
        target.parentFile?.mkdirs()
        target.writeBytes(Base64.decode(base64, Base64.DEFAULT))
        true
    } catch (e: Exception) {
        false
    }
}
