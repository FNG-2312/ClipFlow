package com.example.clipflow

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Observer
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.canhub.cropper.CropImageView
import com.example.clipflow.databinding.ActivityMainBinding
import com.example.clipflow.helper.DialogHelper
import com.example.clipflow.helper.MLKitManager
import com.example.clipflow.helper.NetworkTransferManager
import com.example.clipflow.helper.QRScannerManager
import com.example.clipflow.ui.dialogs.AddServerDialog
import com.example.clipflow.ui.dialogs.HistoryDialog
import com.example.clipflow.ui.dialogs.ServerManagerDialog
import com.example.clipflow.viewmodel.MainViewModel
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    private lateinit var mlKitManager: MLKitManager
    private lateinit var networkManager: NetworkTransferManager
    private lateinit var qrScannerManager: QRScannerManager
    private lateinit var dialogHelper: DialogHelper

    private var latestTmpUri: Uri? = null
    private var backPressedTime: Long = 0

    private val requestNotificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        updateNotificationUI(isGranted)
        if (!isGranted) {
            Toast.makeText(this, "⚠️ Bạn đã tắt thông báo.", Toast.LENGTH_LONG).show()
        }
    }

    private val cropImageLauncher = registerForActivityResult(CropImageContract()) { result ->
        if (result.isSuccessful) result.uriContent?.let { scanTextFromUri(it) }
        else Toast.makeText(this, "Đã hủy cắt ảnh.", Toast.LENGTH_SHORT).show()
    }

    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { isSuccess ->
        if (isSuccess && latestTmpUri != null) launchImageCropper(latestTmpUri!!)
    }

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { launchImageCropper(it) }
    }

    private val pickMultipleFilesLauncher = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            networkManager.sendMultipleFilesSequentially(uris)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        val currentNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val isNightMode = currentNightMode == Configuration.UI_MODE_NIGHT_YES
// Đặt chế độ tối/sáng cho ứng dụng
        binding.switchTheme.isChecked = isNightMode
        binding.switchTheme.text = if (isNightMode) "Dark Mode \uD83C\uDF19" else "Light Mode \u2600\uFE0F"

        binding.switchTheme.setOnCheckedChangeListener { _, isChecked ->
            binding.switchTheme.text = if (isChecked) "Dark Mode \uD83C\uDF19" else "Light Mode \u2600\uFE0F"
            if (isChecked) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
        }

        mlKitManager = MLKitManager(this)
        qrScannerManager = QRScannerManager(this)
        dialogHelper = DialogHelper(this, viewModel, mlKitManager)
        networkManager = NetworkTransferManager(this, viewModel.repository) { isVisible, progress, text ->
            binding.layoutProgress.visibility = if (isVisible) View.VISIBLE else View.GONE
            binding.progressBarTransfer.progress = progress
            binding.tvTransferProgress.text = text
            if (!isVisible) window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        if (viewModel.repository.isFirstLaunch()) dialogHelper.showHardOnboardingDialog(true)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (backPressedTime + 2000 > System.currentTimeMillis()) {
                    finish()
                } else {
                    Toast.makeText(this@MainActivity, "Nhấn lần nữa để thoát", Toast.LENGTH_SHORT).show()
                }
                backPressedTime = System.currentTimeMillis()
            }
        })

        setupObservers()
        setupClickListeners()

        val serviceIntent = Intent(this, BackgroundListenerService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        handleQuickAction(intent)
    }

    override fun onResume() {
        super.onResume()
        updateNotificationUI(isNotificationGranted())
    }

    private fun isNotificationGranted(): Boolean {
        return NotificationManagerCompat.from(this).areNotificationsEnabled()
    }

    private fun updateNotificationUI(isGranted: Boolean) {
        if (isGranted) {
            binding.ivSpeaker.setImageResource(android.R.drawable.ic_lock_silent_mode_off)
            binding.tvNotifyStatus.text = "Thông báo: Đang bật"
            binding.tvNotifyStatus.setTextColor(Color.parseColor("#2ECC71"))

            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(100)
            }
        } else {
            binding.ivSpeaker.setImageResource(android.R.drawable.ic_lock_silent_mode)
            binding.tvNotifyStatus.text = "Thông báo: Đã tắt"
            binding.tvNotifyStatus.setTextColor(Color.parseColor("#E74C3C"))
        }
    }

    private fun openNotificationSettings() {
        val intent = Intent()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent.action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        } else {
            intent.action = "android.settings.APP_NOTIFICATION_SETTINGS"
            intent.putExtra("app_package", packageName)
            intent.putExtra("app_uid", applicationInfo.uid)
        }
        startActivity(intent)
        Toast.makeText(this, "Hãy chọn Bật/Tắt trong cài đặt hệ thống", Toast.LENGTH_SHORT).show()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleQuickAction(intent)
    }

    private fun setupClickListeners() {
        binding.ivHelp.setOnClickListener { dialogHelper.showHardOnboardingDialog(false) }
        binding.btnServerManager.setOnClickListener { ServerManagerDialog().show(supportFragmentManager, "ServerManager") }
        binding.cardHistory.setOnClickListener { HistoryDialog().show(supportFragmentManager, "History") }

        binding.btnToggleNotify.setOnClickListener {
            openNotificationSettings()
        }

        binding.btnRescan.setOnClickListener {
            viewModel.autoDiscoverPC()
            Toast.makeText(this, "🔄 Đang dò tìm lại...", Toast.LENGTH_SHORT).show()
        }

        binding.cardBubble.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                startActivityForResult(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")), 1234)
            } else {
                Toast.makeText(this, "Bong bóng đã bật! Copy chữ ➡️ Chạm bong bóng", Toast.LENGTH_LONG).show()
                startService(Intent(this, FloatingService::class.java))
                moveTaskToBack(true)
            }
        }

        binding.cardSendFile.setOnClickListener { checkServerAndRun { pickMultipleFilesLauncher.launch("*/*") } }

        binding.cardScanText.setOnClickListener {
            checkServerAndRun {
                dialogHelper.showImageSourceDialog(
                    onTakePicture = {
                        latestTmpUri = getTmpFileUri()
                        latestTmpUri?.let { takePictureLauncher.launch(it) }
                    },
                    onPickImage = {
                        pickImageLauncher.launch(arrayOf("image/*"))
                    }
                )
            }
        }
    }
// Lắng nghe sự thay đổi trạng thái từ LiveData của ViewModel.
    private fun setupObservers() {
        viewModel.connectionStatus.observe(this, Observer { state ->
            binding.tvConnectionStatus.text = state.text
            binding.tvConnectionStatus.setTextColor(Color.parseColor(state.colorHex))
        })
        viewModel.toastMessage.observe(this, Observer { msg ->
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        })
    }

    private fun checkServerAndRun(action: () -> Unit) {
        if (viewModel.repository.getPcIp().isEmpty()) {
            Toast.makeText(this, "Chưa kết nối PC.", Toast.LENGTH_SHORT).show()
        } else {
            action.invoke()
        }
    }

    private fun launchImageCropper(uri: Uri) {
        cropImageLauncher.launch(CropImageContractOptions(uri, CropImageOptions(guidelines = CropImageView.Guidelines.ON, allowRotation = true)))
    }

    private fun getTmpFileUri(): Uri {
        val tmpFile = File.createTempFile("tmp_image", ".png", cacheDir).apply { deleteOnExit() }
        return FileProvider.getUriForFile(applicationContext, "${packageName}.fileprovider", tmpFile)
    }

    private fun scanTextFromUri(uri: Uri) {
        Toast.makeText(this, "Đang trích xuất chữ...", Toast.LENGTH_SHORT).show()
        mlKitManager.scanTextFromUri(uri,
            onSuccess = { resultText -> dialogHelper.showOcrResultDialog(resultText) },
            onError = { Toast.makeText(this, "Lỗi OCR: ${it.message}", Toast.LENGTH_SHORT).show() }
        )
    }

    fun startQRScanner() {
        qrScannerManager.startScan(
            onSuccess = { scannedText ->
                binding.root.postDelayed({
                    try {
                        if (!isFinishing && !isDestroyed && !supportFragmentManager.isStateSaved) {
                            val text = scannedText.trim()
                            val ipRegex = Regex("([0-9]{1,3}\\.){3}[0-9]{1,3}")
                            val ipMatch = ipRegex.find(text)

                            if (ipMatch != null) {
                                val ipPart = ipMatch.value
                                val pinPart = if (text.contains("PIN:")) text.substringAfter("PIN:").trim() else ""
                                AddServerDialog.newInstance(ipPart, pinPart).show(supportFragmentManager, "AddServer")
                            } else {
                                Toast.makeText(this, "Lỗi QR! Mã đọc được: $text", Toast.LENGTH_LONG).show()
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }, 350)
            },
            onFail = { errorMsg ->
                Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun handleQuickAction(intent: Intent) {
        when (intent.getStringExtra("QUICK_ACTION")) {
            "FILE" -> pickMultipleFilesLauncher.launch("*/*")
            "OCR" -> dialogHelper.showImageSourceDialog(
                onTakePicture = {
                    latestTmpUri = getTmpFileUri()
                    latestTmpUri?.let { takePictureLauncher.launch(it) }
                },
                onPickImage = { pickImageLauncher.launch(arrayOf("image/*")) }
            )
        }
    }
}