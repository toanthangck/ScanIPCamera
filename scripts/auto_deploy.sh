#!/usr/bin/env bash
# Auto-deploy ScanIPCamera APK to phone via ADB (WiFi or USB)
set -e

APK_PATH="/root/ScanIPCamera/app/build/outputs/apk/debug/app-debug.apk"
PKG_NAME="com.scanipcamera.app"
MAIN_ACT="com.scanipcamera.app.MainActivity"
TARGET_IP="${1:-192.168.1.37}"

echo "=== SCANIPCAMERA AUTO-DEPLOY ==="

if [ ! -f "$APK_PATH" ]; then
    echo "[-] APK not found at $APK_PATH. Building now..."
    ./gradlew assembleDebug
fi

# Check if any device is already connected via adb
CONNECTED_DEV=$(adb devices | grep -E "device$" | awk '{print $1}' | head -n 1)

if [ -z "$CONNECTED_DEV" ]; then
    echo "[*] No active adb device found. Checking WiFi adb at $TARGET_IP..."
    
    # Check open wireless debugging ports (30000 - 50000 and 5555)
    OPEN_PORT=$(python3 -c "
import socket
ip = '$TARGET_IP'
ports = [5555, 38147] + list(range(30000, 50000))
found = None
for p in ports:
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    s.settimeout(0.15)
    if s.connect_ex((ip, p)) == 0:
        found = p
        s.close()
        break
    s.close()
if found:
    print(found)
")
    
    if [ -n "$OPEN_PORT" ]; then
        echo "[+] Found open port $OPEN_PORT on $TARGET_IP. Connecting..."
        adb connect "$TARGET_IP:$OPEN_PORT" || true
    else
        echo "[-] Cannot find open wireless debugging port on $TARGET_IP."
    fi
    CONNECTED_DEV=$(adb devices | grep -E "device$" | awk '{print $1}' | head -n 1)
fi

if [ -n "$CONNECTED_DEV" ]; then
    echo "[+] Deploying to device: $CONNECTED_DEV"
    adb -s "$CONNECTED_DEV" install -r -d "$APK_PATH"
    echo "[+] Launching app..."
    adb -s "$CONNECTED_DEV" shell am start -n "$PKG_NAME/$MAIN_ACT"
    echo "[✔] Successfully deployed and launched on $CONNECTED_DEV!"
else
    echo "[-] Device not connected or unauthorized. Please verify pairing/connection."
    exit 1
fi
