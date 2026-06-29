import socket
import threading
import qrcode
import pyperclip
import os
import sys
import json
import re
import time
import tkinter as tk
from tkinter import filedialog
import pystray
from PIL import Image, ImageDraw
import shutil
import base64
import hashlib
import random
import string
from Crypto.Cipher import AES
from Crypto.Util import Counter

CONFIG_FILE = "clipflow_config.json"
app_window = None;
lbl_path = None;
tray_icon = None


def get_base_dir(): return os.path.dirname(sys.executable) if getattr(sys, 'frozen', False) else os.path.dirname(
    os.path.abspath(__file__))


def get_config_path(): return os.path.join(get_base_dir(), CONFIG_FILE)

# Load các cài đặt vào file clipflow_config.json
def load_config():
    if os.path.exists(get_config_path()):
        with open(get_config_path(), 'r', encoding='utf-8') as f: return json.load(f)
    return {}

# Save các cài đặt vào file clipflow_config.json
def save_config(config):
    with open(get_config_path(), 'w', encoding='utf-8') as f: json.dump(config, f)

# Mở kết nối mạng đến google (8.8.8.8) để ép hệ điều hành đưa địa chỉ IPv4
def get_local_ip():
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM);
        s.connect(("8.8.8.8", 80));
        ip = s.getsockname()[0];
        s.close();
        return ip
    except:
        return "127.0.0.1"

# Tính toán dải IP để phát broadcast
def get_broadcast_ip(ip):
    parts = ip.split('.')
    if len(parts) == 4: return f"{parts[0]}.{parts[1]}.{parts[2]}.255"
    return "255.255.255.255"

# Random các số, chữ và ký tự đặc biệt tạo mã pin 8 ký tự
def generate_random_pin(length=8):
    chars = string.ascii_lowercase + string.ascii_uppercase + string.digits + "!@#$%*"
    return ''.join(random.choice(chars) for _ in range(length))

# Đưa mã pin qua hàm băm sha256
def get_aes_key(pin_code):
    return hashlib.sha256(pin_code.encode('utf-8')).digest()

# Nhận văn bản, tạo nonce random 12 ký tự, dùng AES-GCM khóa dữ liệu, return nonce + dữ liệu mã hóa + tag
def encrypt_data(data_bytes, pin_code):
    key = get_aes_key(pin_code)
    nonce = os.urandom(12)
    cipher = AES.new(key, AES.MODE_GCM, nonce=nonce)
    ciphertext, tag = cipher.encrypt_and_digest(data_bytes)
    return nonce + ciphertext + tag

# Cắt nonce và tag, dùng khóa để decrypt và xác thực
def decrypt_data(encrypted_bytes, pin_code):
    key = get_aes_key(pin_code)
    nonce = encrypted_bytes[:12]
    tag = encrypted_bytes[-16:]
    ciphertext = encrypted_bytes[12:-16]
    cipher = AES.new(key, AES.MODE_GCM, nonce=nonce)
    return cipher.decrypt_and_verify(ciphertext, tag)


def create_tray_image():
    img = Image.new('RGB', (64, 64), color=(41, 128, 185));
    d = ImageDraw.Draw(img);
    d.rectangle([16, 16, 48, 48], fill=(255, 255, 255));
    return img


def tray_action_quit(icon, item): icon.stop(); os._exit(0)


def restore_window(icon, item): icon.stop(); app_window.after(0, app_window.deiconify)

# Gọi app_window.withdraw() ẩn cửa sổ và tạo biểu tượng dưới khay hệ thống bằng thư viện pystray
def on_closing():
    app_window.withdraw()
    global tray_icon
    menu = pystray.Menu(pystray.MenuItem("Bảng điều khiển", restore_window),
                        pystray.MenuItem("Thoát", tray_action_quit))
    tray_icon = pystray.Icon("ClipFlow", create_tray_image(), "ClipFlow Server", menu)
    threading.Thread(target=tray_icon.run, daemon=True).start()


def show_qr_code():
    cfg = load_config()
    current_pin = cfg.get("pin_code", "")
    qr_data = f"CLIPFLOW_IP:{get_local_ip()}|PIN:{current_pin}"
    qr = qrcode.QRCode(version=1, box_size=10, border=4);
    qr.add_data(qr_data);
    qr.make(fit=True)
    qr.make_image(fill='black', back_color='white').show()


def show_toast(title, message):
    if app_window is None: return

    def create_toast():
        t = tk.Toplevel(app_window);
        t.overrideredirect(True);
        t.attributes('-topmost', True);
        t.configure(bg='#2C3E50')
        t.geometry(f"300x80+{t.winfo_screenwidth() - 320}+{t.winfo_screenheight() - 150}")
        tk.Label(t, text=title, fg='#F1C40F', bg='#2C3E50', font=("Arial", 11, "bold")).pack(pady=(10, 2))
        tk.Label(t, text=message if len(message) <= 35 else message[:32] + "...", fg='white', bg='#2C3E50').pack()
        t.after(3000, t.destroy)

    app_window.after(0, create_toast)

# Chuyển clipboard PC xuống điện thoại
# Bắt chữ từ pyperclip.paste(), mã hóa AES rồi gửi qua broadcast 5002 báo cho điện thoại
def manual_send_clipboard():
    text = pyperclip.paste()
    if not text: return show_toast("ClipFlow", "Clipboard trống!")

    pin = load_config().get("pin_code", "")
    if pin:
        enc_bytes = encrypt_data(text.encode('utf-8'), pin)
        b64_text = base64.b64encode(enc_bytes).decode('utf-8')
        send_text = f"[ENCRYPTED]{b64_text}"
    else:
        send_text = text

    msg = f"CLIPFLOW_TXT|{int(time.time() * 1000)}|{send_text}".encode('utf-8')
    u = socket.socket(socket.AF_INET, socket.SOCK_DGRAM);
    u.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
    b_ip = get_broadcast_ip(get_local_ip())
    try:
        u.sendto(msg, (b_ip, 5002));
        u.sendto(msg, ('255.255.255.255', 5002))
    except:
        pass
    show_toast("ClipFlow", "Đã gửi Text!")


def get_category_folder(base_folder, filename):
    ext = filename.split('.')[-1].lower() if '.' in filename else ''
    if ext in ['jpg', 'jpeg', 'png', 'gif', 'bmp', 'webp']:
        sub = 'ClipFlow_Images'
    elif ext in ['mp4', 'avi', 'mkv', 'mov', 'webm']:
        sub = 'ClipFlow_Videos'
    elif ext in ['mp3', 'wav', 'ogg', 'flac', 'm4a']:
        sub = 'ClipFlow_Music'
    else:
        sub = 'ClipFlow_Files'

    if not base_folder.endswith("ClipFlow_Received"): base_folder = os.path.join(base_folder, "ClipFlow_Received")
    cat_folder = os.path.join(base_folder, sub)
    if not os.path.exists(cat_folder): os.makedirs(cat_folder)
    return cat_folder


def handle_client(c, a):
    try:
        c.settimeout(15.0); # Thời gian timeout chống PC server bị kẹt
        header_data = b""
        while b"\n" not in header_data:
            chunk = c.recv(1)
            if not chunk: return
            header_data += chunk

        raw_msg = header_data.decode('utf-8').strip()
# Xác thực, so sánh mã PIN điện thoại và mã PIN của PC
        if raw_msg.startswith("PING|"):
            parts = raw_msg.split('|', 1)
            client_pin = parts[1] if len(parts) > 1 else ""
            pin = load_config().get("pin_code", "")
            if pin and client_pin != pin:
                c.sendall(b"ERR_PIN")
            else:
                c.sendall(b"OK")
# Xử lý văn bản, cắt lấy nội dung, gọi hàm decrypt_data() r dùng hàm pyperclip.copy(content) để copy nội dung vào khay nhớ tạm của windows
        elif raw_msg.startswith("TXT|"):
            parts = raw_msg.split('|', 1)
            if len(parts) > 1:
                content = parts[1]
                if not content.startswith("[ENCRYPTED]"):
                    show_toast("Bảo mật", f"Chặn IP {a[0]}: Không mã hóa!")
                    return

                pin = load_config().get("pin_code", "")
                enc_bytes = base64.b64decode(content.replace("[ENCRYPTED]", ""))
                try:
                    content = decrypt_data(enc_bytes, pin).decode('utf-8')
                except:
                    show_toast("Bảo mật", f"Chặn IP {a[0]}: Sai mã PIN!")
                    return

                pyperclip.copy(content)
                show_toast("ClipFlow: Đã nhận Text", content)
                c.sendall(b"OK")
# Xử lý điện thoại gửi File, hàm get)category_folder để phân loại tệp dựa vào đuôi file,
        elif raw_msg.startswith("FILE|"):
            parts = raw_msg.split('|', 1)
            filename = parts[1]

            if not filename.startswith("[ENCRYPTED]"):
                show_toast("Bảo mật", f"Chặn IP {a[0]}: File không mã hóa!")
                return

            filename = filename.replace("[ENCRYPTED]", "")
            base_folder = load_config().get("save_folder", get_base_dir())
            safe_name = re.sub(r'[<>:"/\\|?*]', '_', filename)
            cat_folder = get_category_folder(base_folder, safe_name)
            save_path = os.path.join(cat_folder, safe_name)

            pin = load_config().get("pin_code", "")
            key = get_aes_key(pin)

            iv = b""
            while len(iv) < 16:
                chunk = c.recv(16 - len(iv))
                if not chunk: return
                iv += chunk

            ctr = Counter.new(128, initial_value=int.from_bytes(iv, 'big'))
            cipher = AES.new(key, AES.MODE_CTR, counter=ctr)

            try:
                with open(save_path, "wb") as f:
                    while True:
                        chunk = c.recv(64 * 1024)
                        if not chunk: break
                        f.write(cipher.decrypt(chunk))
                show_toast("ClipFlow", f"Đã nhận -> {os.path.basename(cat_folder)}")
            except Exception:
                if os.path.exists(save_path): os.remove(save_path)
                show_toast("Bảo mật", f"Chặn IP {a[0]}: Lỗi giải mã file!")
# Xử lý điện thoại xin lấy File, tìm đường dẫn của File, đọc từng khối 64KB, mã hóa AES rồi c.sendall() cho điện thoại
        elif raw_msg.startswith("DOWNLOAD|"):
            parts = raw_msg.split('|', 1)
            if len(parts) > 1:
                filepath = os.path.join(get_base_dir(), "ClipFlow_Share", parts[1])
                if os.path.exists(filepath):
                    show_toast("ClipFlow", f"Đang gửi -> {parts[1]}...")
                    pin = load_config().get("pin_code", "")
                    if pin:
                        key = get_aes_key(pin)
                        iv = os.urandom(16)
                        ctr = Counter.new(128, initial_value=int.from_bytes(iv, 'big'))
                        cipher = AES.new(key, AES.MODE_CTR, counter=ctr)
                        c.sendall(iv)
                        with open(filepath, "rb") as f:
                            # Vòng lặp hứng luồng dữ liệu 64KB, giải mã bằng AES
                            while True:
                                chunk = f.read(64 * 1024)
                                if not chunk: break
                                c.sendall(cipher.encrypt(chunk))
                    else:
                        with open(filepath, "rb") as f:
                            while True:
                                chunk = f.read(64 * 1024)
                                if not chunk: break
                                c.sendall(chunk)
                    show_toast("ClipFlow", "Gửi hoàn tất!")
    except:
        pass
    finally:
        c.close()

# Hàm listen TCP cổng 5000, điện thoại gọi (t.accpet) thì tạo luồng mới và giao cho hàm handle_clinent xử lý
def tcp_server():
    t = socket.socket(socket.AF_INET, socket.SOCK_STREAM);
    t.bind(("0.0.0.0", 5000));
    t.listen(5)
    while True:
        c, a = t.accept()
        threading.Thread(target=handle_client, args=(c, a), daemon=True).start()

# Hàm listen UDP cổng 5001, nếu điện thoại gởi tới CLIPFLOW_DISCOVER thì send ngược lại CLIPFLOW_SERVER_HERE
def udp_discover_listener():
    u = socket.socket(socket.AF_INET, socket.SOCK_DGRAM);
    u.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
    u.bind(("", 5001))
    while True:
        try:
            d, a = u.recvfrom(1024)
        except:
            continue
        if d.decode() == "CLIPFLOW_DISCOVER": u.sendto(b"CLIPFLOW_SERVER_HERE", a)

# Bấm gửi file -> mở hộp thoại chọn tệp filedialog -> copy file đó vào thư mục ClipFlow_Share -> gửi broadcast udp 5002 để điện thoại gọi TCP kéo file về
def send_file_to_phone_logic():
    files = filedialog.askopenfilenames()
    if not files: return
    sf = os.path.join(get_base_dir(), "ClipFlow_Share")
    if not os.path.exists(sf): os.makedirs(sf)
    ip = get_local_ip()
    b_ip = get_broadcast_ip(ip)

    for f in files:
        name = os.path.basename(f);
        dp = os.path.join(sf, name);
        shutil.copy2(f, dp)
        msg = f"CLIPFLOW_DOWNLOAD|{int(time.time() * 1000)}|{name}".encode('utf-8')
        u = socket.socket(socket.AF_INET, socket.SOCK_DGRAM);
        u.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
        try:
            u.sendto(msg, (b_ip, 5002));
            u.sendto(msg, ('255.255.255.255', 5002))
        except:
            pass
        # Đúng 3 phút sau khi gửi, một luồng ngầm sẽ tự chạy lệnh os.remove(p) để xóa sạch file tạm để dọn ổ cứng
        threading.Timer(180.0, lambda p=dp: os.remove(p) if os.path.exists(p) else None).start()


def choose_folder():
    f = filedialog.askdirectory()
    if f:
        cfg = load_config();
        cfg["save_folder"] = f;
        save_config(cfg);
        lbl_path.config(text=f)

# Dùng thư viện tkinter vẽ bảng điều khiển
def init_ui():
    global app_window, lbl_path
    app_window = tk.Tk();
    app_window.title("ClipFlow Server");
    app_window.geometry("420x350")
    app_window.protocol("WM_DELETE_WINDOW", on_closing)

    cfg = load_config()
    if "pin_code" not in cfg or not cfg["pin_code"]:
        cfg["pin_code"] = generate_random_pin()
        save_config(cfg)

    tk.Label(app_window, text="CLIPFLOW SERVER", font=("Arial", 14, "bold")).pack(pady=10)
    tk.Label(app_window, text=f"IP: {get_local_ip()}", fg="green", font=("Arial", 11, "bold")).pack(pady=5)

    pf = tk.Frame(app_window);
    pf.pack(pady=5)
    tk.Label(pf, text="Mã PIN:", font=("Arial", 10, "bold")).pack(side="left")

    pin_var = tk.StringVar(value=cfg["pin_code"])
    tk.Entry(pf, textvariable=pin_var, font=("Consolas", 12, "bold"), fg="red", state="readonly", width=12,
             justify="center").pack(side="left", padx=10)

    def refresh_pin():
        new_pin = generate_random_pin()
        pin_var.set(new_pin)
        c = load_config();
        c["pin_code"] = new_pin;
        save_config(c)
        show_toast("Bảo mật", "Đã tạo mã PIN mới!")

    tk.Button(pf, text="Tạo mới", bg="#8E44AD", fg="white", font=("Arial", 8, "bold"), command=refresh_pin).pack(
        side="left")

    ff = tk.Frame(app_window);
    ff.pack(pady=5, padx=20, fill="x")
    tk.Label(ff, text="Lưu file tại:", font=("Arial", 9, "bold")).pack(anchor="w")
    lbl_path = tk.Label(ff, text=cfg.get("save_folder", os.path.join(get_base_dir(), "ClipFlow_Received")), fg="blue",
                        wraplength=350);
    lbl_path.pack(anchor="w")

    bf = tk.Frame(app_window);
    bf.pack(pady=10)
    tk.Button(bf, text="Đổi thư mục", command=choose_folder, width=12).pack(side="left", padx=5)
    tk.Button(bf, text="Hiện QR Code", command=show_qr_code, width=12).pack(side="left", padx=5)

    tk.Button(app_window, text="GỬI CLIPBOARD SANG ĐT", bg="#E67E22", fg="white", font=("Arial", 10, "bold"),
              command=manual_send_clipboard).pack(pady=5)
    tk.Button(app_window, text="GỬI FILE SANG ĐIỆN THOẠI", bg="#3498DB", fg="white", font=("Arial", 10, "bold"),
              command=lambda: threading.Thread(target=send_file_to_phone_logic).start()).pack()

    tk.Label(app_window, text="Nhấn X để chạy ngầm dưới khay hệ thống", font=("Arial", 8, "italic"), fg="gray").pack(
        side="bottom", pady=5)
    app_window.mainloop()

# daemon=true để khi main thread chết thì các thread khác cũng chết theo -> chống lỗi port in use
if __name__ == "__main__":
    threading.Thread(target=udp_discover_listener, daemon=True).start()
    threading.Thread(target=tcp_server, daemon=True).start()
    init_ui()