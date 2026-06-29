package com.example.clipflow.helper

import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
// Dùng object để tạo một lần bản sao thôi
object CryptoHelper {
    private const val ALGORITHM = "AES"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val TAG_LENGTH_BIT = 128
    private const val IV_LENGTH_BYTE = 12
// Sinh khóa bằng SHA-256 và đưa mã PIN vào hàm MessageDigest để băm thành mảng đúng 32 bytes
    fun generateKey(pinCode: String): SecretKeySpec {
        val digest = MessageDigest.getInstance("SHA-256")
        val keyBytes = digest.digest(pinCode.toByteArray(Charsets.UTF_8))
        return SecretKeySpec(keyBytes, ALGORITHM)
    }
// Chuẩn hóa GCM No Padding - không cần đệm thêm byte vào cuối chuỗi
    fun encrypt(data: ByteArray, secretKey: SecretKeySpec): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val iv = ByteArray(IV_LENGTH_BYTE)
        SecureRandom().nextBytes(iv)
        val parameterSpec = GCMParameterSpec(TAG_LENGTH_BIT, iv)

        cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec)
        val cipherText = cipher.doFinal(data)
// ByteBuffer bỏ iv vào trước rồi bỏ cipherText vào sau, allocate sẵn kích thước để tối ưu hoa bộ nhớ
        val byteBuffer = ByteBuffer.allocate(iv.size + cipherText.size)
        byteBuffer.put(iv)
        byteBuffer.put(cipherText)
        return byteBuffer.array()
    }
    // Giải mã dữ liệu đã được mã hóa
    // iv (Initialization Vector) là một mảng ngẫu nhiên được sử dụng trong quá trình mã hóa
    fun decrypt(encryptedData: ByteArray, secretKey: SecretKeySpec): ByteArray {
        val byteBuffer = ByteBuffer.wrap(encryptedData)
        val iv = ByteArray(IV_LENGTH_BYTE)
        byteBuffer.get(iv)
        val cipherText = ByteArray(byteBuffer.remaining())
        byteBuffer.get(cipherText)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        val parameterSpec = GCMParameterSpec(TAG_LENGTH_BIT, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec)
// Thư vện javax.crypto đã gộp chung ciphertext và tag vào một mảng duy nhất
        return cipher.doFinal(cipherText)
    }
}