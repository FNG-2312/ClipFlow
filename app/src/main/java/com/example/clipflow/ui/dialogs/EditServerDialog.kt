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

class EditServerDialog : DialogFragment() {

    private val viewModel: MainViewModel by activityViewModels()

    companion object {
        fun newInstance(index: Int, server: SavedServer): EditServerDialog {
            val frag = EditServerDialog()
            val args = Bundle()
            args.putInt("INDEX", index)
            args.putString("NAME", server.name)
            args.putString("IP", server.ip)
            args.putString("PIN", server.pin)
            frag.arguments = args
            return frag
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val index = arguments?.getInt("INDEX") ?: 0
        val currentName = arguments?.getString("NAME") ?: ""
        val currentIp = arguments?.getString("IP") ?: ""
        val currentPin = arguments?.getString("PIN") ?: ""

        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 20)
        }

        val inputName = EditText(requireContext()).apply {
            hint = "Tên gợi nhớ"
            setText(currentName)
        }
        val inputIp = EditText(requireContext()).apply {
            hint = "Địa chỉ IP (trên pc_server)"
            setText(currentIp)
            inputType = android.text.InputType.TYPE_CLASS_PHONE
        }
        val inputPin = EditText(requireContext()).apply {
            hint = "Mã PIN bảo mật"
            setText(currentPin)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        layout.addView(inputName)
        layout.addView(inputIp)
        layout.addView(inputPin)

        return AlertDialog.Builder(requireContext())
            .setTitle("Sửa thông tin PC")
            .setView(layout)
            .setPositiveButton("Lưu") { _, _ ->
                val newName = inputName.text.toString().trim()
                val newIp = inputIp.text.toString().trim()
                val newPin = inputPin.text.toString().trim()

                if (newName.isNotEmpty() && newIp.isNotEmpty() && newPin.isNotEmpty()) {
                    if (Patterns.IP_ADDRESS.matcher(newIp).matches()) {
                        val servers = viewModel.repository.getSavedServers()
                        servers[index] = SavedServer(newName, newIp, newPin)
                        viewModel.repository.saveServers(servers)
                        if (viewModel.repository.getPcIp() == currentIp) {
                            viewModel.connectToServer(SavedServer(newName, newIp, newPin))
                        } else {
                            Toast.makeText(requireContext(), "Đã cập nhật!", Toast.LENGTH_SHORT).show()
                        }
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