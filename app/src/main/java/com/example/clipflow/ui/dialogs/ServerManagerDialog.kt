package com.example.clipflow.ui.dialogs

import android.app.AlertDialog
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.example.clipflow.MainActivity
import com.example.clipflow.model.SavedServer
import com.example.clipflow.viewmodel.MainViewModel

class ServerManagerDialog : DialogFragment() {

    private val viewModel: MainViewModel by activityViewModels()
    private val selectedServers = mutableSetOf<SavedServer>()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val servers = viewModel.repository.getSavedServers()
        selectedServers.clear()

        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 20)
            setBackgroundColor(Color.parseColor("#F5F7FA"))
        }

        val btnFrame = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            setPadding(0, 0, 0, 20)
        }

        val btnScanQR = Button(requireContext()).apply {
            text = "📷 QUÉT MÃ QR"
            setBackgroundColor(Color.parseColor("#9B59B6"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, 15)
            }
            setOnClickListener {
                dismiss()
                // Ủy quyền việc khởi chạy Camera/QR Scanner cho Activity xử lý để đảm bảo an toàn context.
                (requireActivity() as MainActivity).startQRScanner()
            }
        }

        val btnManualIP = Button(requireContext()).apply {
            text = "✍️ THÊM PC THỦ CÔNG"
            setBackgroundColor(Color.parseColor("#34495E"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, 15)
            }
            setOnClickListener {
                dismiss()
                AddServerDialog.newInstance().show(parentFragmentManager, "AddServer")
            }
        }

        val btnToggleList = Button(requireContext()).apply {
            text = "🖥️ CÁC MẠNG ĐÃ LƯU (${servers.size})"
            setBackgroundColor(Color.parseColor("#BDC3C7"))
            setTextColor(Color.parseColor("#2C3E50"))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        val btnDeleteSelected = Button(requireContext()).apply {
            text = "🗑️ XÓA ĐÃ CHỌN (0)"
            setBackgroundColor(Color.parseColor("#E74C3C"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 15, 0, 0)
            }
            visibility = View.GONE
        }

        btnFrame.addView(btnScanQR)
        btnFrame.addView(btnManualIP)
        btnFrame.addView(btnToggleList)
        btnFrame.addView(btnDeleteSelected)

        val scrollView = ScrollView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            visibility = View.GONE
        }

        val listLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 20, 0, 0)
        }
        scrollView.addView(listLayout)

        btnToggleList.setOnClickListener {
            scrollView.visibility = if (scrollView.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        val updateDeleteButton = {
            if (selectedServers.isNotEmpty()) {
                btnDeleteSelected.visibility = View.VISIBLE
                btnDeleteSelected.text = "🗑️ XÓA ĐÃ CHỌN (${selectedServers.size})"
            } else {
                btnDeleteSelected.visibility = View.GONE
            }
        }

        if (servers.isEmpty()) {
            listLayout.addView(TextView(requireContext()).apply {
                text = "Chưa có máy tính nào được lưu."
                textSize = 15f
                setTextColor(Color.parseColor("#7F8C8D"))
                gravity = Gravity.CENTER
                setPadding(0, 20, 0, 20)
            })
        } else {
            for ((index, server) in servers.withIndex()) {
                val row = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(20, 30, 20, 30)
                    gravity = Gravity.CENTER_VERTICAL
                    val shape = GradientDrawable()
                    shape.shape = GradientDrawable.RECTANGLE
                    shape.setColor(Color.WHITE)
                    shape.cornerRadius = 24f
                    background = shape
                    val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    params.setMargins(0, 0, 0, 20)
                    layoutParams = params
                }

                val checkBox = CheckBox(requireContext()).apply {
                    setOnCheckedChangeListener { _, isChecked ->
                        if (isChecked) {
                            selectedServers.add(server)
                        } else {
                            selectedServers.remove(server)
                        }
                        updateDeleteButton()
                    }
                }

                val infoLayout = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    setPadding(10, 0, 0, 0)
                    setOnClickListener {
                        viewModel.connectToServer(server)
                        dismiss()
                    }
                }

                infoLayout.addView(TextView(requireContext()).apply {
                    text = "🖥️ ${server.name}"
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    textSize = 16f
                    setTextColor(Color.parseColor("#2C3E50"))
                })

                infoLayout.addView(TextView(requireContext()).apply {
                    text = server.ip
                    setTextColor(Color.parseColor("#95A5A6"))
                    textSize = 14f
                    setPadding(0, 5, 0, 0)
                })

                val btnEdit = ImageButton(requireContext()).apply {
                    setImageResource(android.R.drawable.ic_menu_edit)
                    setBackgroundColor(Color.TRANSPARENT)
                    setPadding(15, 15, 15, 15)
                    setColorFilter(Color.parseColor("#2980B9"))
                    setOnClickListener {
                        dismiss()
                        EditServerDialog.newInstance(index, server).show(parentFragmentManager, "EditServer")
                    }
                }

                row.addView(checkBox)
                row.addView(infoLayout)
                row.addView(btnEdit)
                listLayout.addView(row)
            }
        }

        btnDeleteSelected.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Xác nhận xóa")
                .setMessage("Bạn có muốn xóa ${selectedServers.size} máy tính này?")
                .setPositiveButton("Có") { _, _ ->
                    val currentServers = viewModel.repository.getSavedServers()

                    currentServers.removeAll { s -> selectedServers.any { it.ip == s.ip } }
                    viewModel.repository.saveServers(currentServers)

                    val currentIp = viewModel.repository.getPcIp()
                    // Kiểm tra nếu PC bị xóa trùng với PC đang kết nối hiện tại -> Xóa luôn State kết nối
                    if (selectedServers.any { it.ip == currentIp }) {
                        try {
                            viewModel.repository.clearPcConnection()
                        } catch (e: Exception) {
                            viewModel.repository.savePcConnection("")
                        }
                    }

                    Toast.makeText(requireContext(), "Đã xóa thành công!", Toast.LENGTH_SHORT).show()
                    dismiss()
                }
                .setNegativeButton("Không", null)
                .show()
        }

        layout.addView(btnFrame)
        layout.addView(scrollView)

        return AlertDialog.Builder(requireContext())
            .setTitle("Bảng điều khiển PC")
            .setView(layout)
            .setPositiveButton("Đóng", null)
            .create()
    }
}