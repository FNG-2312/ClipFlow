package com.example.clipflow.helper

import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Base64
import androidx.core.app.NotificationCompat
import com.example.clipflow.repository.ClipFlowRepository
import java.io.File
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.Socket
import javax.crypto.spec.SecretKeySpec

class NetworkTransferManager(
    private val context: Context,
    private val repository: ClipFlowRepository,
    private val onProgressUpdate: (Boolean, Int, String) -> Unit
) {
    private fun getSecretKey(): SecretKeySpec? {
        val currentPin = repository.getPcPin()
        return if (currentPin.isNotEmpty()) CryptoHelper.generateKey(currentPin) else null
    }

    private fun showSystemNotification(title: String, message: String, notifId: Int) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val builder = NotificationCompat.Builder(context, "CLIPFLOW_ALERTS")
            .setSmallIcon(android.R.drawable.ic_menu_send)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
        manager.notify(notifId, builder.build())
    }
// Mở cổng udp 5002 để nhận thông báo Broadcast từ Pc gửi qua
    fun startDownloadListener() {
        Thread {
            var udpSocket: DatagramSocket? = null
            try {
                udpSocket = DatagramSocket(null)
                udpSocket.reuseAddress = true
                udpSocket.bind(InetSocketAddress(5002))
                udpSocket.broadcast = true

                val recvBuf = ByteArray(65535)

                while (true) {
                    try {
                        val packet = DatagramPacket(recvBuf, recvBuf.size)
                        udpSocket.receive(packet)

                        val rawMessage = String(packet.data, 0, packet.length, Charsets.UTF_8).replace("\u0000", "")

                        if (rawMessage.startsWith("CLIPFLOW_TXT|") || rawMessage.startsWith("CLIPFLOW_DOWNLOAD|")) {
                            val parts = rawMessage.split("|", limit = 3)
                            if (parts.size >= 3) {
                                var rawContent = parts[2]
                                val currentIp = repository.getPcIp()

                                if (rawMessage.startsWith("CLIPFLOW_TXT|")) {
                                    if (rawContent.startsWith("[ENCRYPTED]")) {
                                        val secretKey = getSecretKey()
                                        if (secretKey != null) {
                                            try {
                                                val b64 = rawContent.replace("[ENCRYPTED]", "")
                                                val decBytes = CryptoHelper.decrypt(Base64.decode(b64, Base64.NO_WRAP), secretKey)
                                                rawContent = String(decBytes, Charsets.UTF_8)
                                            } catch (e: Exception) {
                                                continue
                                            }
                                        } else {
                                            continue
                                        }
                                    }

                                    Handler(Looper.getMainLooper()).post {
                                        repository.addHistory(currentIp, "TEXT", rawContent)
                                        try {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("PC Text", rawContent))
                                            showSystemNotification("Đã copy Text từ PC", rawContent, 1003)
                                        } catch (e: Exception) {
                                            showSystemNotification("Đã nhận Text từ PC", rawContent, 1003)
                                        }
                                    }
                                } else {
                                    Handler(Looper.getMainLooper()).post {
                                        showSystemNotification("Đang nhận file từ PC...", "Vui lòng đợi", 1001)
                                        downloadFileViaTCP(rawContent)
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            } catch (e: Exception) {
            } finally {
                udpSocket?.close()
            }
        }.start()
    }

    private fun downloadFileViaTCP(fileName: String) {
        Thread {
            try {
                val ip = repository.getPcIp()
                if (ip.isEmpty()) throw Exception("Chưa kết nối PC")

                val socket = Socket()
                socket.connect(InetSocketAddress(ip, 5000), 5000)

                val outStream = socket.getOutputStream()
                outStream.write("DOWNLOAD|$fileName\n".toByteArray(Charsets.UTF_8))
                outStream.flush()

                val inputStream = socket.getInputStream()
                val secretKey = getSecretKey()

                val cleanFileName = fileName.substringAfterLast("\\").substringAfterLast("/")
                val isImage = cleanFileName.lowercase().let { it.endsWith(".jpg") || it.endsWith(".jpeg") || it.endsWith(".png") }
                val dir = if (isImage) Environment.DIRECTORY_PICTURES else Environment.DIRECTORY_DOWNLOADS
                val mimeType = if (isImage) "image/*" else "application/octet-stream"

                var savedFilePath: String? = null
                var finalUri: Uri? = null
                var bytesCopied = 0L

                val writeAction: (java.io.OutputStream) -> Unit = { outputStream ->
                    if (secretKey != null) {
                        val iv = ByteArray(16)
                        var readIv = 0
                        while (readIv < 16) {
                            val r = inputStream.read(iv, readIv, 16 - readIv)
                            if (r == -1) throw Exception("Dữ liệu hỏng")
                            readIv += r
                        }
// Dùng cipherinputstream và cipheroutputstream để tự mã hóa dữ liệu
                        val cipher = javax.crypto.Cipher.getInstance("AES/CTR/NoPadding")
                        val ivSpec = javax.crypto.spec.IvParameterSpec(iv)
                        cipher.init(javax.crypto.Cipher.DECRYPT_MODE, secretKey, ivSpec)

                        val cis = javax.crypto.CipherInputStream(inputStream, cipher)
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val r = try { cis.read(buffer) } catch(e: Exception) { throw Exception("Sai mã PIN") }
                            if (r == -1) break
                            outputStream.write(buffer, 0, r)
                            bytesCopied += r
                        }
                        cis.close()
                    } else {
                        bytesCopied = inputStream.copyTo(outputStream)
                    }
                }
// Giao tiếp với MediaStore để xin quyền tạo file ở public.
// - IS_PENDING = 1: Khóa ẩn file trong lúc đang tải luồng 64KB, ngăn user/app khác mở sớm gây lỗi Corrupt.
// - IS_PENDING = 0: Luồng tải xong 100%, gỡ cờ để Publish file ra ngoài hệ thống.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val resolver = context.contentResolver
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, cleanFileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                        put(MediaStore.MediaColumns.RELATIVE_PATH, "$dir/ClipFlow")
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }

                    val collection = if (isImage) MediaStore.Images.Media.EXTERNAL_CONTENT_URI else MediaStore.Downloads.EXTERNAL_CONTENT_URI
                    finalUri = resolver.insert(collection, contentValues)

                    if (finalUri != null) {
                        resolver.openOutputStream(finalUri)?.use(writeAction)

                        if (bytesCopied == 0L) {
                            resolver.delete(finalUri, null, null)
                            throw Exception("File rỗng hoặc sai mã PIN")
                        } else {
                            contentValues.clear()
                            contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                            resolver.update(finalUri, contentValues, null, null)
                        }
                    }
                } else {
                    val publicDir = Environment.getExternalStoragePublicDirectory(dir)
                    val clipFlowDir = File(publicDir, "ClipFlow")
                    if (!clipFlowDir.exists()) clipFlowDir.mkdirs()
                    val file = File(clipFlowDir, cleanFileName)
                    FileOutputStream(file).use(writeAction)

                    if (bytesCopied == 0L) {
                        file.delete()
                        throw Exception("File rỗng hoặc sai mã PIN")
                    } else {
                        savedFilePath = file.absolutePath
                    }
                }

                inputStream.close()
                socket.close()

                if (savedFilePath != null) {
                    android.media.MediaScannerConnection.scanFile(context, arrayOf(savedFilePath), arrayOf(mimeType), null)
                }

                var thumbPath: String? = null
                if (isImage) {
                    thumbPath = if (finalUri != null) {
                        saveThumbnailToCache(finalUri, cleanFileName)
                    } else if (savedFilePath != null) {
                        saveThumbnailToCache(Uri.fromFile(File(savedFilePath)), cleanFileName)
                    } else null
                }

                Handler(Looper.getMainLooper()).post {
                    repository.addHistory(ip, "NHẬN TỪ PC", cleanFileName, thumbPath)
                    showSystemNotification("Tải file hoàn tất", cleanFileName, 1002)
                }

            } catch (e: Exception) {
                e.printStackTrace()
                Handler(Looper.getMainLooper()).post {
                    showSystemNotification("Lỗi nhận file", e.message ?: "Lỗi không xác định", 1004)
                }
            }
        }.start()
    }

    private fun saveThumbnailToCache(uri: Uri, fileName: String): String? {
        return try {
            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.loadThumbnail(uri, android.util.Size(150, 150), null)
            } else {
                @Suppress("DEPRECATION")
                android.provider.MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            }
            val thumbFile = File(context.cacheDir, "thumb_${System.currentTimeMillis()}.jpg")
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, FileOutputStream(thumbFile))
            thumbFile.absolutePath
        } catch (e: Exception) { null }
    }
// Kỹ thuật Buffer Stream gửi lần lượt 64KB thay vì một cục lớn để chống tràn RAM
    // Bất cứ thao tác nào dính tới mạng phải chạy Thread ngầm rồi dùng Handler(Looper) để chạy trên MainThread
    // Chỉ có Main Thread mới có quyền thao tác với UI và các service hệ thống
    fun sendMultipleFilesSequentially(uris: List<Uri>) {
        Thread {
            val pcIpAddress = repository.getPcIp()
            if (pcIpAddress.isEmpty()) {
                Handler(Looper.getMainLooper()).post { showSystemNotification("Lỗi gửi file", "Chưa nhập IP PC", 1005) }
                return@Thread
            }

            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(pcIpAddress, 5000), 2000)
                socket.getOutputStream().write("PING|\n".toByteArray(Charsets.UTF_8))
                socket.close()
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post { showSystemNotification("Lỗi gửi file", "PC không phản hồi", 1005) }
                return@Thread
            }

            Handler(Looper.getMainLooper()).post { onProgressUpdate(true, 0, "Chuẩn bị gửi...") }
            val secretKey = getSecretKey()

            for ((index, uri) in uris.withIndex()) {
                try {
                    var fileName = "file.dat"
                    var fileSize: Long = 0
                    context.contentResolver.query(uri, null, null, null, null)?.use {
                        if (it.moveToFirst()) {
                            fileName = it.getString(it.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                            fileSize = it.getLong(it.getColumnIndexOrThrow(OpenableColumns.SIZE))
                        }
                    }
                    val shouldEncrypt = secretKey != null

                    Handler(Looper.getMainLooper()).post { onProgressUpdate(true, 0, "Đang gửi: $fileName") }

                    val socket = Socket(pcIpAddress, 5000)
                    val outStream = socket.getOutputStream()
                    // Sử dụng ký tự '\n' làm điểm ngắt mạch, nhờ đó PC biết chính xác lúc nào ngừng đọc tên lệnh để chuyển sang ghi file.
                    val fileHeader = if (shouldEncrypt) "FILE|[ENCRYPTED]$fileName\n" else "FILE|$fileName\n"
                    outStream.write(fileHeader.toByteArray(Charsets.UTF_8))

                    val inputStream = context.contentResolver.openInputStream(uri)

                    if (shouldEncrypt) {
                        val cipher = javax.crypto.Cipher.getInstance("AES/CTR/NoPadding")
                        val iv = ByteArray(16)
                        java.security.SecureRandom().nextBytes(iv)
                        val ivSpec = javax.crypto.spec.IvParameterSpec(iv)
                        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, secretKey!!, ivSpec)

                        outStream.write(iv)
                        val cos = javax.crypto.CipherOutputStream(outStream, cipher)

                        val buffer = ByteArray(64 * 1024)
                        var totalRead: Long = 0

                        while (true) {
                            val bytesRead = inputStream?.read(buffer) ?: -1
                            if (bytesRead == -1) break
                            cos.write(buffer, 0, bytesRead)
                            totalRead += bytesRead
                            if (fileSize > 0) {
                                val progress = ((totalRead.toDouble() / fileSize) * 100).toInt()
                                Handler(Looper.getMainLooper()).post { onProgressUpdate(true, progress, "Đang gửi: $fileName") }
                            }
                        }
                        cos.close()
                    } else {
                        val buffer = ByteArray(64 * 1024)
                        var totalRead: Long = 0

                        while (true) {
                            val bytesRead = inputStream?.read(buffer) ?: -1
                            if (bytesRead == -1) break
                            outStream.write(buffer, 0, bytesRead)
                            totalRead += bytesRead
                            if (fileSize > 0) {
                                val progress = ((totalRead.toDouble() / fileSize) * 100).toInt()
                                Handler(Looper.getMainLooper()).post { onProgressUpdate(true, progress, "Đang gửi: $fileName") }
                            }
                        }
                    }

                    outStream.flush(); outStream.close(); inputStream?.close(); socket.close()

                    val thumbPath = if (fileName.substringAfterLast('.', "").lowercase() in listOf("jpg", "png", "mp4")) {
                        saveThumbnailToCache(uri, fileName)
                    } else null

                    Handler(Looper.getMainLooper()).post { repository.addHistory(pcIpAddress, "FILE", fileName, thumbPath) }
                } catch (e: Exception) { }
            }
            Handler(Looper.getMainLooper()).post {
                onProgressUpdate(false, 0, "")
                showSystemNotification("Gửi file hoàn tất", "Đã gửi thành công", 1006)
            }
        }.start()
    }
}