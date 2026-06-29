package com.example.clipflow

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.widget.Toast
import com.example.clipflow.helper.CryptoHelper
import com.example.clipflow.repository.ClipFlowRepository
import java.net.Socket

// Bật activity lên nhanh, lấy text, gửi qua mạng rồi tự đóng lại để tránh bị block bởi hệ thống
class InvisibleActivity : Activity() {

    private lateinit var repository: ClipFlowRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = ClipFlowRepository(this)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)

        if (hasFocus) {
            try {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = clipboard.primaryClip

                if (clip != null && clip.itemCount > 0) {
                    val copiedText = clip.getItemAt(0).coerceToText(this).toString()

                    if (copiedText.isNotEmpty()) {
                        sendDataToPC(copiedText)
                    } else {
                        Toast.makeText(this, "Khay nhớ tạm không có dữ liệu.", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                } else {
                    Toast.makeText(this, "Khay nhớ tạm trống.", Toast.LENGTH_SHORT).show()
                    finish()
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Lỗi truy xuất: ${e.message}", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun sendDataToPC(message: String) {
        Thread {
            try {
                val pcIpAddress = repository.getPcIp()
                val pin = repository.getPcPin()

                if (pcIpAddress.isEmpty()) {
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(applicationContext, "Chưa thiết lập IP PC.", Toast.LENGTH_LONG).show()
                        finish()
                    }
                    return@Thread
                }

                val socket = Socket(pcIpAddress, 5000)
                val outStream = socket.getOutputStream()

                val payloadString = if (pin.isNotEmpty()) {
                    val secretKey = CryptoHelper.generateKey(pin)
                    val encryptedBytes = CryptoHelper.encrypt(message.toByteArray(Charsets.UTF_8), secretKey)
                    val base64Text = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
                    "TXT|[ENCRYPTED]$base64Text\n"
                } else {
                    "TXT|$message\n"
                }

                val payload = payloadString.toByteArray(Charsets.UTF_8)

                outStream.write(payload)
                outStream.flush()
                outStream.close()
                socket.close()

                val displayContent = if (message.length > 30) message.take(30) + "..." else message

                repository.addHistory(pcIpAddress, "TEXT (Bong bóng)", message)

                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(applicationContext, "Đã gửi: $displayContent", Toast.LENGTH_SHORT).show()
                    finish()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(applicationContext, "Lỗi kết nối đến PC.", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }.start()
    }
}