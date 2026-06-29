package com.example.clipflow.viewmodel

import android.app.Application
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.clipflow.helper.CryptoHelper
import com.example.clipflow.model.SavedServer
import com.example.clipflow.repository.ClipFlowRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.Socket

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val repository = ClipFlowRepository(application)

    private val _connectionStatus = MutableLiveData<ConnectionState>()
    val connectionStatus: LiveData<ConnectionState> = _connectionStatus

    private val _toastMessage = MutableLiveData<String>()
    val toastMessage: LiveData<String> = _toastMessage

    private val _transferProgress = MutableLiveData<TransferProgress>()
    val transferProgress: LiveData<TransferProgress> = _transferProgress

    init {
        autoDiscoverPC()
    }
// Tạo gói tin ClipFlow_Discover để tìm kiếm máy chủ PC, PC nào tìm thấy thì rep CLIPFLOW_SERVER_HERE để kết nối (bên pc_server)
    fun autoDiscoverPC() {
        _connectionStatus.postValue(ConnectionState("🔴 Đang dò tìm máy chủ PC...", "#E67E22"))
// NetworkInterface: Quét động các Card mạng thực tế của các máy android đời mới
// 192.168.43.255: Broadcast hotspot của các máy Android đời cũ
// 255.255.255.255: Global Broadcast để phủ sóng toàn bộ mạng LAN vật lý hiện tại.
        viewModelScope.launch(Dispatchers.IO) {
            var udpSocket: DatagramSocket? = null
            try {
                udpSocket = DatagramSocket()
                udpSocket.broadcast = true
                udpSocket.soTimeout = 4000
                val sendData = "CLIPFLOW_DISCOVER".toByteArray()

                val interfaces = NetworkInterface.getNetworkInterfaces()
                while (interfaces.hasMoreElements()) {
                    val networkInterface = interfaces.nextElement()
                    if (networkInterface.isLoopback || !networkInterface.isUp) continue
                    for (interfaceAddress in networkInterface.interfaceAddresses) {
                        interfaceAddress.broadcast?.let {
                            udpSocket.send(DatagramPacket(sendData, sendData.size, it, 5001))
                        }
                    }
                }
                try { udpSocket.send(DatagramPacket(sendData, sendData.size, InetAddress.getByName("192.168.43.255"), 5001)) } catch (e: Exception) {}
                try { udpSocket.send(DatagramPacket(sendData, sendData.size, InetAddress.getByName("255.255.255.255"), 5001)) } catch (e: Exception) {}

                val recvBuf = ByteArray(1024)
                val recvPacket = DatagramPacket(recvBuf, recvBuf.size)
                udpSocket.receive(recvPacket)

                val reply = String(recvPacket.data, 0, recvPacket.length)
                if (reply == "CLIPFLOW_SERVER_HERE") {
                    val serverIp = recvPacket.address.hostAddress
                    val matchedServer = repository.getSavedServers().find { it.ip == serverIp }

                    if (matchedServer != null) {
                        connectToServer(matchedServer, true)
                    } else {
                        _connectionStatus.postValue(ConnectionState("🟡 Thấy máy mới: $serverIp", "#F1C40F"))
                        _toastMessage.postValue("Thấy PC: $serverIp. Nhấn vào QR để kết nối.")
                    }
                }
            } catch (e: Exception) {
                _connectionStatus.postValue(ConnectionState("⭕ Chưa tìm thấy máy chủ PC. Hãy mở lại app.", "#E74C3C"))
            } finally {
                udpSocket?.close()
            }
        }
    }

    fun connectToServer(server: SavedServer, isAutoDiscover: Boolean = false) {
        if (!isAutoDiscover) {
            _connectionStatus.postValue(ConnectionState("⏳ Đang kết nối PC...", "#95A5A6"))
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val socket = Socket(server.ip, 5000)
                socket.soTimeout = 2000
                val outStream = socket.getOutputStream()
                val inStream = socket.getInputStream()

                outStream.write("PING|${server.pin}\n".toByteArray(Charsets.UTF_8))
                outStream.flush()

                val buffer = ByteArray(1024)
                val bytesRead = inStream.read(buffer)
                val reply = if (bytesRead != -1) String(buffer, 0, bytesRead) else ""
                socket.close()

                if (reply == "OK") {
                    repository.savePcConnection(server.ip, server.pin)
                    _connectionStatus.postValue(ConnectionState("🟢 Đã kết nối: ${server.name}", "#2ECC71"))
                    if (!isAutoDiscover) _toastMessage.postValue("Kết nối thành công!")
                } else if (reply == "ERR_PIN") {
                    _connectionStatus.postValue(ConnectionState("🟡 Đổi mã PIN, hãy Quét lại QR!", "#F1C40F"))
                    if (!isAutoDiscover) _toastMessage.postValue("Mã PIN không khớp. Hãy quét lại QR!")
                } else {
                    throw Exception("Sai phản hồi")
                }
            } catch (e: Exception) {
                if (!isAutoDiscover) {
                    _connectionStatus.postValue(ConnectionState("🔴 Mất kết nối: ${server.name}", "#E74C3C"))
                    _toastMessage.postValue("Không thể kết nối đến PC.")
                }
            }
        }
    }

    fun sendTextToPC(text: String) {
        val ip = repository.getPcIp()

        if (ip.isEmpty()) {
            _toastMessage.postValue("Vui lòng kết nối với PC trước!")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val socket = Socket(ip, 5000)
                val outStream = socket.getOutputStream()
                val pin = repository.getPcPin()

                if (pin.isNotEmpty()) {
                    val secretKey = CryptoHelper.generateKey(pin)
                    val encryptedBytes = CryptoHelper.encrypt(text.toByteArray(Charsets.UTF_8), secretKey)
                    val base64Text = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
                    outStream.write("TXT|[ENCRYPTED]$base64Text\n".toByteArray(Charsets.UTF_8))
                } else {
                    outStream.write("TXT|$text\n".toByteArray(Charsets.UTF_8))
                }

                outStream.flush()
                socket.close()
                repository.addHistory(ip, "TEXT", text)
                _toastMessage.postValue("Đã gửi Text thành công!")
            } catch (e: Exception) {
                _toastMessage.postValue("Gửi thất bại. Kiểm tra lại Mạng.")
            }
        }
    }

    data class ConnectionState(val text: String, val colorHex: String)
    data class TransferProgress(val isVisible: Boolean, val progress: Int, val text: String)
}