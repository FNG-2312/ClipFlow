# ClipFlow - Hệ thống Truyền tải P2P LAN

## Ứng dụng Android và PC Server hỗ trợ chuyển file và khay nhớ tạm (Clipboard) siêu tốc, ngoại tuyến 100% qua mạng nội bộ

Dự án này được xây dựng nhằm giải quyết bài toán truyền tải tệp tin và văn bản khi không có cáp kết nối hoặc mất Internet. Bằng cách tận dụng giao thức UDP/TCP tầng thấp và thuật toán mã hóa, ClipFlow đảm bảo dữ liệu được luân chuyển an toàn, nhanh chóng trực tiếp giữa hai thiết bị mà không cần đi qua bất kỳ máy chủ đám mây nào.

##  Hình ảnh hoạt động 
<div align="center">

  <h3> Giao diện PC Server</h3>
  <img src="https://github.com/user-attachments/assets/b838ec91-017b-4bac-a1f5-555b1bd39ee1" width="413" alt="PC Server Demo" />

  <br/><br/>

  <h3> Giao diện Android Client</h3>
  <img src="https://github.com/user-attachments/assets/7fd07a3c-e2a8-49b9-9af9-9fa1fc327827" width="300" alt="Mobile Client Demo" />

  <div align="center">
  <h3>Truyền file Điện thoại -> PC</h3>
  <video src="https://github.com/user-attachments/assets/f40e26f7-8cb3-445d-9b2e-2ce0ed5da78e" width="600" controls></video>

  <br/><br/>

  <h3>Truyền file PC -> Điện thoại</h3>
  <video src="https://github.com/user-attachments/assets/370a00f2-42aa-4be2-ab2a-52aa59e2310f" width="600" controls></video>

  <br/><br/>

  <h3>Đồng bộ Khay nhớ tạm</h3>
  <video src="https://github.com/user-attachments/assets/ca5f17e8-4591-4c15-a4f5-de0e0da2c58e" width="600" controls></video>
</div>
</div>

##  Hướng dẫn cài đặt 
Nếu bạn chỉ muốn tải về dùng ngay:
1. Vào mục **Releases** ở góc phải trang này.
2. Tải file `ClipFlow.apk` và cài đặt lên điện thoại Android.
3. Tải file `pc_server.exe` về máy tính Windows và mở lên (không cần cài đặt).
4. Cho 2 thiết bị dùng chung mạng Wi-Fi (hoặc điện thoại phát Hotspot cho PC), dùng điện thoại quét mã QR trên màn hình PC là xong.

##  Hướng dẫn Build Code 
Dành cho anh em muốn tải mã nguồn về vọc vạch:

**Bước 1: Tải mã nguồn về máy**
* Dùng Git: Mở Terminal và gõ lệnh `git clone https://github.com/FNG-2312/ClipFlow.git`
* Vào Releases để tải Source code

**Bước 2: Chạy Android Client**
1. Mở thư mục gốc của dự án bằng Android Studio.
2. Đợi hệ thống sync Gradle hoàn tất và chạy (Run) trực tiếp trên thiết bị hoặc máy ảo (Yêu cầu Android 10+).

**Bước 3: Chạy PC Server**
1. Đảm bảo máy tính đã cài đặt Python 3.10+.
2. Mở Terminal / CMD tại thư mục mã nguồn vừa tải về.
3. Cài đặt các thư viện bắt buộc bằng lệnh:
   `pip install qrcode pyperclip pystray Pillow pycryptodome`
4. Khởi động server bằng lệnh:
   `python pc_server.py`

## Đóng góp 
Đây là dự án thực hành môn học nên cấu trúc mã nguồn vẫn còn nhiều thiếu sót. Mình rất hoan nghênh anh em dev góp ý!
* Nếu phát hiện lỗi, vui lòng mở **Issue**.
* Ưu tiên các Pull Request hỗ trợ tối ưu hóa luồng Socket và làm gọn giao diện UI.

## Lỗi đã biết 
* Chỉ mới hỗ trợ Android và Windows.
* Tính năng Dịch thuật hiện tại chỉ dừng ở mức trích xuất chữ OCR (Google ML Kit), API dịch đang được tạm ẩn.

## Ủng hộ tác giả
Nếu tool này giúp anh em copy/paste nhanh hơn, tiết kiệm được vài giây cuộc đời, hãy mời dev một ly cà phê nhé!
<div align="center">
  <img src="https://github.com/user-attachments/assets/09187e2e-cd9e-4858-978d-2e2004c41e82" width="250" alt="Buy me a coffee" />
</div>
