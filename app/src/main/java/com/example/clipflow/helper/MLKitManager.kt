package com.example.clipflow.helper

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.net.ssl.HttpsURLConnection

// Google ML Kit Vision API để trích xuất chữ từ ảnh
class MLKitManager(private val context: Context) {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    fun scanTextFromUri(uri: Uri, onSuccess: (String) -> Unit, onError: (Exception) -> Unit) {
        try {
            val image = InputImage.fromFilePath(context, uri)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    if (visionText.text.isBlank()) onError(Exception("Không tìm thấy chữ nào trong ảnh!"))
                    else onSuccess(visionText.text)
                }
                .addOnFailureListener { e -> onError(e) }
        } catch (e: Exception) {
            onError(e)
        }
    }
// Dùng client=gtx để truy cập API không chính thức của google dịch (rủi ro Rate-limit và khả năng sập)
    fun translateTextAPI(
        text: String,
        targetLangCode: String,
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        Thread {
            try {
                val encodedText = URLEncoder.encode(text, "UTF-8")
                val urlStr = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=$targetLangCode&dt=t&q=$encodedText"

                val connection = URL(urlStr).openConnection() as HttpsURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("User-Agent", "Mozilla/5.0")
                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                val responseCode = connection.responseCode
                if (responseCode != 200) throw Exception("HTTP Error: $responseCode")

                val response = connection.inputStream.bufferedReader().use { it.readText() }
                android.util.Log.d("CLIPFLOW_TRANSLATE_RAW", response)
// Parse mảng JSON lồng nhau và dùng Handler ném kết quả về Main Thread để update UI.
                val jsonArray = JSONArray(response)
                val segments = jsonArray.getJSONArray(0)
                val translatedText = StringBuilder()

                for (i in 0 until segments.length()) {
                    translatedText.append(segments.getJSONArray(i).getString(0))
                }

                Handler(Looper.getMainLooper()).post {
                    if (translatedText.isNotBlank()) {
                        onResult(translatedText.toString().trim())
                    } else {
                        onError("Bản dịch rỗng")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Handler(Looper.getMainLooper()).post {
                    onError(e.message ?: "Lỗi dịch")
                }
            }
        }.start()
    }
}