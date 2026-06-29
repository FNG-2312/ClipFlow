package com.example.clipflow.ui.dialogs

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.util.Patterns
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.example.clipflow.model.SavedServer
import com.example.clipflow.viewmodel.MainViewModel

class AddServerDialog : DialogFragment() {
// activityViewModels() giúp tái sử dụng ViewModel của activity cha, loại bỏ Callback.
    private val viewModel: MainViewModel by activityViewModels()

    companion object {
        fun newInstance(scannedIp: String? = null, scannedPin: String? = null): AddServerDialog {
            val frag = AddServerDialog()
            val args = Bundle()
            args.putString("IP", scannedIp)
            args.putString("PIN", scannedPin)
            frag.arguments = args
            return frag
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val scannedIp = arguments?.getString("IP")
        val scannedPin = arguments?.getString("PIN")

        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 20)
        }

        val inputName = EditText(requireContext()).apply { hint = "Tên gợi nhớ (VD: PC Nhà)" }
        val inputIp = EditText(requireContext()).apply {
            hint = "Địa chỉ IP (trên pc_server)"
            setText(scannedIp ?: "")
            inputType = android.text.InputType.TYPE_CLASS_PHONE
        }
        val inputPin = EditText(requireContext()).apply {
            hint = "Mã PIN bảo mật"
            setText(scannedPin ?: "")
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        layout.addView(inputName)
        layout.addView(inputIp)
        layout.addView(inputPin)

        return AlertDialog.Builder(requireContext())
            .setTitle("Thêm Máy Tính Mới")
            .setView(layout)
            .setPositiveButton("Lưu") { _, _ ->
                val name = inputName.text.toString().trim()
                val ip = inputIp.text.toString().trim()
                val pin = inputPin.text.toString().trim()

                if (name.isNotEmpty() && ip.isNotEmpty() && pin.isNotEmpty()) {
                    // Chặn đứng IP sai định dạng trước khi đẩy vào Socket.
                    if (Patterns.IP_ADDRESS.matcher(ip).matches()) {
                        val servers = viewModel.repository.getSavedServers()
                        servers.add(SavedServer(name, ip, pin))
                        viewModel.repository.saveServers(servers)

                        viewModel.connectToServer(SavedServer(name, ip, pin))
                    } else {
                        Toast.makeText(requireContext(), "Sai định dạng IP", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(requireContext(), "Vui lòng nhập đủ thông tin và mã PIN!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Hủy", null)
            .create()
    }
}