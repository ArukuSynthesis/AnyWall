package com.anywall.converter.data.engines

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Motor criptográfico 100% local para la Bóveda Universal AnyWall (`AES-256-GCM` `.anywall`)
 * y cálculo/verificación forense de hashes `SHA-256` y `MD5` (v0.3).
 *
 * Estructura binaria del contenedor `.anywall`:
 * - 4 bytes: Magic Header `"AWV1"`
 * - 16 bytes: Salt aleatorio (`SecureRandom`)
 * - 12 bytes: IV aleatorio para `AES/GCM/NoPadding`
 * - Payload cifrado y autenticado con GCM (tag de 128 bits):
 *   - `UTF-8 String` (2 bytes longitud + bytes): nombre original del archivo (`originalFileName`)
 *   - Bytes íntegros del archivo original
 */
object AesVaultCrypto {

    private val MAGIC_BYTES = "AWV1".toByteArray(Charsets.US_ASCII)
    private const val SALT_LENGTH = 16
    private const val IV_LENGTH = 12
    private const val GCM_TAG_BITS = 128
    private const val PBKDF2_ITERATIONS = 120_000
    private const val KEY_BITS = 256

    data class DecryptedVaultPayload(
        val originalFileName: String,
        val plainBytes: ByteArray
    )

    private fun deriveAesKey(password: String, salt: ByteArray): SecretKeySpec {
        require(password.isNotBlank()) { "La contraseña de la bóveda AES-256 no puede estar vacía." }
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    fun encryptBytesToVault(
        plainBytes: ByteArray,
        originalFileName: String,
        password: String
    ): ByteArray {
        val out = ByteArrayOutputStream(plainBytes.size + 128)
        encryptStreamToVault(
            input = ByteArrayInputStream(plainBytes),
            originalFileName = originalFileName,
            password = password,
            output = out
        )
        return out.toByteArray()
    }

    fun encryptStreamToVault(
        input: InputStream,
        originalFileName: String,
        password: String,
        output: OutputStream
    ) {
        val random = SecureRandom()
        val salt = ByteArray(SALT_LENGTH).also { random.nextBytes(it) }
        val iv = ByteArray(IV_LENGTH).also { random.nextBytes(it) }
        val secretKey = deriveAesKey(password, salt)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))

        output.write(MAGIC_BYTES)
        output.write(salt)
        output.write(iv)

        val nameBytes = originalFileName.toByteArray(Charsets.UTF_8)
        CipherOutputStream(output, cipher).use { cos ->
            val dos = DataOutputStream(cos)
            dos.writeShort(nameBytes.size.coerceAtMost(4096))
            dos.write(nameBytes, 0, nameBytes.size.coerceAtMost(4096))
            input.copyTo(dos, bufferSize = 64 * 1024)
            dos.flush()
        }
    }

    @Throws(IOException::class)
    fun decryptVaultBytes(
        vaultBytes: ByteArray,
        password: String
    ): DecryptedVaultPayload {
        val out = ByteArrayOutputStream()
        val originalName = decryptVaultStream(
            input = ByteArrayInputStream(vaultBytes),
            password = password,
            output = out
        )
        return DecryptedVaultPayload(
            originalFileName = originalName,
            plainBytes = out.toByteArray()
        )
    }

    @Throws(IOException::class)
    fun decryptVaultStream(
        input: InputStream,
        password: String,
        output: OutputStream
    ): String {
        val disHeader = DataInputStream(input)
        val magic = ByteArray(4)
        disHeader.readFully(magic)
        if (!magic.contentEquals(MAGIC_BYTES)) {
            throw IOException("El archivo no es una Bóveda AnyWall válida (cabecera AWV1 no encontrada).")
        }

        val salt = ByteArray(SALT_LENGTH)
        val iv = ByteArray(IV_LENGTH)
        disHeader.readFully(salt)
        disHeader.readFully(iv)

        val secretKey = deriveAesKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))

        try {
            CipherInputStream(disHeader, cipher).use { cis ->
                val plainDis = DataInputStream(cis)
                val nameLen = plainDis.readUnsignedShort()
                if (nameLen <= 0 || nameLen > 4096) {
                    throw IOException("Metadatos de nombre inválidos o contraseña incorrecta.")
                }
                val nameBytes = ByteArray(nameLen)
                plainDis.readFully(nameBytes)
                val originalName = String(nameBytes, Charsets.UTF_8)
                plainDis.copyTo(output, bufferSize = 64 * 1024)
                output.flush()
                return originalName
            }
        } catch (e: Throwable) {
            throw IOException("No se pudo descifrar la bóveda AES-256: contraseña incorrecta o archivo alterado.", e)
        }
    }

    fun computeSha256Hex(bytes: ByteArray): String = computeDigestHex(ByteArrayInputStream(bytes), "SHA-256")

    fun computeMd5Hex(bytes: ByteArray): String = computeDigestHex(ByteArrayInputStream(bytes), "MD5")

    fun computeSha256Hex(file: File): String = FileInputStream(file).use { computeDigestHex(it, "SHA-256") }

    fun computeMd5Hex(file: File): String = FileInputStream(file).use { computeDigestHex(it, "MD5") }

    fun computeDigestHex(input: InputStream, algorithm: String): String {
        val digest = MessageDigest.getInstance(algorithm)
        val buffer = ByteArray(64 * 1024)
        var read: Int
        while (input.read(buffer).also { read = it } != -1) {
            if (read > 0) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { b ->
            String.format(Locale.US, "%02x", b.toInt() and 0xFF)
        }
    }
}
