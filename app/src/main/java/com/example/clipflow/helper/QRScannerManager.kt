package com.example.clipflow.helper

import android.content.Context
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

// Sử dụng GmsBarcodeScanning để quét QR code trên Android (không cần xin quyền).
class QRScannerManager(private val context: Context) {
    fun startScan(onSuccess: (String) -> Unit, onFail: (String) -> Unit) {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .enableAutoZoom()
            .build()

        val scanner = GmsBarcodeScanning.getClient(context, options)

        scanner.startScan()
            .addOnSuccessListener { barcode ->
                val result = barcode.rawValue

                if (result.isNullOrEmpty()) {
                    onFail("QR rỗng.")
                    return@addOnSuccessListener
                }

                if (!result.startsWith("CLIPFLOW_IP:")) {
                    onFail("QR không hợp lệ.")
                    return@addOnSuccessListener
                }

                val serverIp = result.removePrefix("CLIPFLOW_IP:").trim()
                onSuccess(serverIp)
            }
            .addOnCanceledListener {
                onFail("Đã hủy quét QR.")
            }
            .addOnFailureListener { e ->
                e.printStackTrace()
                onFail("Lỗi quét QR: ${e.message}")
            }
    }
}