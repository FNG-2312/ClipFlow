package com.example.clipflow.helper

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.example.clipflow.R
import com.example.clipflow.viewmodel.MainViewModel

class DialogHelper(
    private val activity: Activity,
    private val viewModel: MainViewModel,
    private val mlKitManager: MLKitManager
) {

    fun showImageSourceDialog(onTakePicture: () -> Unit, onPickImage: () -> Unit) {
        AlertDialog.Builder(activity)
            .setTitle("Lấy ảnh từ đâu?")
            .setItems(arrayOf("📸 Chụp ảnh mới", "📁 Chọn file ảnh")) { _, which ->
                when (which) {
                    0 -> onTakePicture()
                    1 -> onPickImage()
                }
            }.show()
    }

    fun showOcrResultDialog(extractedText: String) {
        // Chỉ lấy 100 ký tự đầu tiên để hiển thị
        val displayPreview = if (extractedText.length > 100) extractedText.take(100) + "..." else extractedText
        AlertDialog.Builder(activity)
            .setTitle("Trích xuất thành công!")
            .setMessage("Bản gốc:\n$displayPreview\n\nBạn muốn làm gì tiếp theo?")
            .setPositiveButton("Gửi luôn lên PC") { _, _ ->
                val finalMsg = extractedText.replace("\n", " ")
                val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("ClipFlow OCR", finalMsg))
                viewModel.sendTextToPC(finalMsg)
                Toast.makeText(activity, "Đã gửi text lên PC!", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("Dịch văn bản") { _, _ -> showTranslationDialog(extractedText) }
            .setNegativeButton("Hủy bỏ", null)
            .show()
    }

    private fun showTranslationDialog(extractedText: String) {
        val languages = arrayOf("🇻🇳 Tiếng Việt", "🇬🇧 Tiếng Anh", "🇯🇵 Tiếng Nhật", "🇰🇷 Tiếng Hàn", "🇨🇳 Tiếng Trung")
        val langCodes = arrayOf("vi", "en", "ja", "ko", "zh-CN")

        AlertDialog.Builder(activity)
            .setTitle("Chọn ngôn ngữ để dịch sang:")
            .setItems(languages) { _, which ->
                val targetCode = langCodes[which]
                Toast.makeText(activity, "Đang dịch qua Google...", Toast.LENGTH_LONG).show()

                mlKitManager.translateTextAPI(
                    text = extractedText,
                    targetLangCode = targetCode,
                    onResult = { translatedText ->
                        val finalMsg = translatedText.replace("\n", " ")
                        val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("ClipFlow Translate", finalMsg))

                        Toast.makeText(activity, "Đã dịch và gửi lên PC!", Toast.LENGTH_SHORT).show()
                        viewModel.sendTextToPC(finalMsg)
                    },
                    onError = { error ->
                        Toast.makeText(activity, "Lỗi dịch thuật:\n$error", Toast.LENGTH_LONG).show()
                    }
                )
            }
            .show()
    }

    fun showHardOnboardingDialog(isFirstTime: Boolean) {
        val view = LayoutInflater.from(activity).inflate(R.layout.layout_hard_onboarding, null)
        val btnAction = view.findViewById<Button>(R.id.btnAction)
        val dialog = AlertDialog.Builder(activity).setView(view).setCancelable(!isFirstTime).create()

        view.findViewById<TextView>(R.id.tvLink).setOnClickListener {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse((it as TextView).text.toString())))
        }

        view.findViewById<Button>(R.id.btnCopyLink).setOnClickListener {
            val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Link", view.findViewById<TextView>(R.id.tvLink).text.toString()))
            Toast.makeText(activity, "Đã copy link!", Toast.LENGTH_SHORT).show()
        }
// Ép người dùng chờ 10s ở lần mở app đầu tiên trước khi đóng dialog.
        if (isFirstTime) {
            btnAction.isEnabled = false
            object : CountDownTimer(10000, 1000) {
                override fun onTick(millis: Long) { btnAction.text = "Chờ (${millis / 1000}s)" }
                override fun onFinish() {
                    btnAction.isEnabled = true
                    btnAction.text = "Tôi đã hiểu"
                    btnAction.setOnClickListener { dialog.dismiss() }
                }
            }.start()
        } else {
            btnAction.text = "Đóng"
            btnAction.setOnClickListener { dialog.dismiss() }
        }
        dialog.show()
    }
}