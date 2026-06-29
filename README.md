# ClipFlow - P2P LAN Transfer System

## Android App and PC Server supporting ultra-fast, 100% offline file and clipboard transfer over a local network

This project was built to solve the problem of transferring files and text when there is no connecting cable or internet access. By leveraging low-level UDP/TCP protocols and encryption algorithms, ClipFlow ensures your data is transferred securely and quickly directly between two devices without going through any cloud servers.

## Demo
<div align="center">
  <h3> PC Server Interface</h3>
  <img src="https://github.com/user-attachments/assets/b838ec91-017b-4bac-a1f5-555b1bd39ee1" width="413" alt="PC Server Demo" />

  <br/><br/>

  <h3> Android Client Interface</h3>
  <img src="https://github.com/user-attachments/assets/7fd07a3c-e2a8-49b9-9af9-9fa1fc327827" width="300" alt="Mobile Client Demo" />
</div>

## Installation Guide
If you just want to download and use it right away:
1. Go to the **Releases** section on the right side of this page.
2. Download the `ClipFlow.apk` file and install it on your Android phone.
3. Download the `pc_server.exe` file to your Windows PC and open it (no installation required).
4. Connect both devices to the same Wi-Fi network (or use your phone's Hotspot for the PC), scan the QR code on the PC screen with your phone, and you're good to go.

## Build Instructions
For developers who want to download and explore the source code:

**Step 1: Download the source code**
* Using Git: Open your Terminal and run the command: `git clone https://github.com/FNG-2312/ClipFlow.git`

**Step 2: Run the Android Client**
1. Open the project's root folder with Android Studio.
2. Wait for the Gradle sync to complete and Run it directly on a physical device or emulator (Android 10+ required).

**Step 3: Run the PC Server**
1. Ensure Python 3.10+ is installed on your computer.
2. Open Terminal / CMD in the downloaded source code folder.
3. Install the required libraries using the command:
   `pip install qrcode pyperclip pystray Pillow pycryptodome`
4. Start the server using the command:
   `python pc_server.py`

## Contributing
As this is a practical course project, the source code structure still has some shortcomings. I highly welcome feedback and contributions from fellow devs!
* If you find a bug, please open an **Issue**.
* Pull Requests that optimize the Socket flow and clean up the UI are highly prioritized.

## Known Issues
* Currently only supports Android and Windows.
* The translation feature is currently limited to OCR text extraction (Google ML Kit); the translation API is temporarily hidden.

## Support the Author
If this tool helps you copy/paste faster and saves a few seconds of your life, consider buying me a coffee!
<div align="center">
  <img src="https://github.com/user-attachments/assets/09187e2e-cd9e-4858-978d-2e2004c41e82" width="250" alt="Buy me a coffee" />
</div>
