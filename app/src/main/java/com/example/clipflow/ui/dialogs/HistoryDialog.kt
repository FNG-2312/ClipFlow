package com.example.clipflow.ui.dialogs

import android.app.AlertDialog
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.example.clipflow.R
import com.example.clipflow.model.HistoryItem
import com.example.clipflow.viewmodel.MainViewModel
import java.io.File

class HistoryDialog : DialogFragment() {

    private val viewModel: MainViewModel by activityViewModels()
    private val selectedItems = mutableSetOf<HistoryItem>()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val currentIp = viewModel.repository.getPcIp()
        val history = viewModel.repository.getHistory(currentIp)
        selectedItems.clear()

        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 20)
        }

        val headerLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, 20)
            }
        }

        val btnSelectAll = Button(requireContext()).apply {
            text = "✅ CHỌN HẾT"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMarginEnd(10)
            }
        }

        val btnDeleteSelected = Button(requireContext()).apply {
            text = "🗑️ XÓA (0)"
            setBackgroundColor(Color.parseColor("#E74C3C"))
            setTextColor(Color.WHITE)
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        headerLayout.addView(btnSelectAll)
        headerLayout.addView(btnDeleteSelected)
        layout.addView(headerLayout)

        val scrollView = ScrollView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        val listLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
        }
        scrollView.addView(listLayout)
// Gọp cnung vào một biến để mỗi khi người dùng check/uncheck thì cập nhật lại.
        val updateUIState = {
            btnSelectAll.text = if (selectedItems.size == history.size && history.isNotEmpty()) "❌ HỦY" else "✅ CHỌN HẾT"
            if (selectedItems.isNotEmpty()) {
                btnDeleteSelected.visibility = View.VISIBLE
                btnDeleteSelected.text = "🗑️ XÓA (${selectedItems.size})"
            } else {
                btnDeleteSelected.visibility = View.GONE
            }
        }

        if (history.isEmpty()) {
            listLayout.addView(TextView(requireContext()).apply {
                text = "📭\nLịch sử trống."
                gravity = Gravity.CENTER
                textSize = 16f
                setTextColor(Color.GRAY)
                setPadding(0, 80, 0, 80)
            })
            btnSelectAll.visibility = View.GONE
        } else {
            for (item in history) {
                val row = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(20, 25, 20, 25)
                    gravity = Gravity.CENTER_VERTICAL
                    background = GradientDrawable().apply {
                        setColor(Color.WHITE)
                        cornerRadius = 24f
                    }
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                        setMargins(0, 0, 0, 15)
                    }
                }

                val checkBox = CheckBox(requireContext()).apply {
                    setOnCheckedChangeListener { _, isChecked ->
                        if (isChecked) selectedItems.add(item) else selectedItems.remove(item)
                        updateUIState()
                    }
                }

                val contentLayout = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    setPadding(20, 0, 20, 0)
                }

                contentLayout.addView(TextView(requireContext()).apply {
                    text = "[${item.type}] - ${item.time}"
                    textSize = 11f
                    setTextColor(Color.GRAY)
                })

                contentLayout.addView(TextView(requireContext()).apply {
                    text = item.content
                    textSize = 14f
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
                    setTextColor(Color.BLACK)
                })
// Dùng FrameLayout làm slot trống, yùy thuộc vào dữ liệu mà chèn linh hoạt ImageView hoặc Button vào row.
                val actionView = FrameLayout(requireContext())
                val isFileTransfer = item.type.contains("FILE") || item.type.contains("NHẬN TỪ PC")
                val contentLower = item.content.lowercase()
                val isImage = contentLower.let { it.endsWith(".jpg") || it.endsWith(".jpeg") || it.endsWith(".png") || it.endsWith(".webp") }
                val isVideo = contentLower.let { it.endsWith(".mp4") || it.endsWith(".mkv") || it.endsWith(".mov") }

                if (isFileTransfer) {
                    val imgPreview = ImageView(requireContext()).apply {
                        layoutParams = FrameLayout.LayoutParams(110, 110)
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        try {
                            val thumbFile = if (item.uriString != null) File(item.uriString!!) else null
                            if ((isImage || isVideo) && thumbFile != null && thumbFile.exists()) {
                                setImageURI(android.net.Uri.fromFile(thumbFile))
                            } else {
                                setImageResource(if (isVideo) android.R.drawable.ic_media_play else android.R.drawable.ic_menu_save)
                            }
                        } catch (e: Exception) {
                            setImageResource(android.R.drawable.ic_menu_report_image)
                        }
                    }
                    actionView.addView(imgPreview)
                } else {
                    val btnCopy = ImageButton(requireContext()).apply {
                        setImageResource(R.drawable.ic_copy)
                        setBackgroundColor(Color.TRANSPARENT)
                        setPadding(10, 10, 10, 10)
                        setOnClickListener {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("ClipFlow", item.content))
                            Toast.makeText(context, "Đã sao chép nội dung!", Toast.LENGTH_SHORT).show()
                        }
                    }
                    actionView.addView(btnCopy)
                }

                row.addView(checkBox)
                row.addView(contentLayout)
                row.addView(actionView)
                listLayout.addView(row)
            }
        }

        btnSelectAll.setOnClickListener {
            val shouldSelectAll = selectedItems.size < history.size
            selectedItems.clear()
            if (shouldSelectAll) selectedItems.addAll(history)

            for (i in 0 until listLayout.childCount) {
                val row = listLayout.getChildAt(i) as? LinearLayout
                val cb = row?.getChildAt(0) as? CheckBox
                cb?.isChecked = shouldSelectAll
            }
            updateUIState()
        }

        btnDeleteSelected.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Xác nhận xóa")
                .setMessage("Bạn có chắc muốn xóa ${selectedItems.size} mục này khỏi lịch sử?")
                .setPositiveButton("Xóa") { _, _ ->
                    val currentHistory = viewModel.repository.getHistory(currentIp)
                    currentHistory.removeAll { h -> selectedItems.any { it.time == h.time && it.content == h.content } }
                    val historyKey = if (currentIp.isNotEmpty()) "HISTORY_$currentIp" else "HISTORY_GENERAL"
                    val sharedPref = requireContext().getSharedPreferences("ClipFlowPrefs", Context.MODE_PRIVATE)
                    sharedPref.edit().putString(historyKey, com.google.gson.Gson().toJson(currentHistory)).apply()
                    Toast.makeText(requireContext(), "Lịch sử đã được cập nhật!", Toast.LENGTH_SHORT).show()
                    dismiss()
                }
                .setNegativeButton("Hủy", null)
                .show()
        }

        layout.addView(scrollView)

        return AlertDialog.Builder(requireContext())
            .setTitle(if (currentIp.isNotEmpty()) "Lịch sử: $currentIp" else "Lịch sử chung")
            .setView(layout)
            .setPositiveButton("Đóng", null)
            .create()
    }
}