"""Exercise the installed APK on the disposable CI emulator and capture its screen."""
import math
import os
from pathlib import Path
import re
import struct
import subprocess
import time
import wave
import xml.etree.ElementTree as ET

PACKAGE = "com.nexus.music"
OUT = Path("glass-screens")
OUT.mkdir(exist_ok=True)


def adb(*args, binary=False):
    return subprocess.check_output(["adb", *args], text=not binary, timeout=30)


def tree():
    adb("shell", "uiautomator", "dump", "/sdcard/window.xml")
    return ET.fromstring(adb("exec-out", "cat", "/sdcard/window.xml", binary=True))


def find(label, timeout=30):
    end = time.monotonic() + timeout
    while time.monotonic() < end:
        try:
            for node in tree().iter("node"):
                if label in (node.get("text"), node.get("content-desc")):
                    return node
        except (subprocess.CalledProcessError, ET.ParseError):
            pass
        time.sleep(.4)
    raise RuntimeError(f"Could not find {label!r} on screen")


def tap(label):
    node = find(label)
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
    adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))


def screenshot(name):
    time.sleep(.5)
    data = adb("exec-out", "screencap", "-p", binary=True)
    assert data.startswith(b"\x89PNG\r\n\x1a\n")
    (OUT / f"{name}.png").write_bytes(data)


def main():
    # The fixture is imported with Android's document picker, exercising the same
    # permissions and Media3 path as a user picking a local song.
    with wave.open("/tmp/Glass-audio.wav", "wb") as sample:
        sample.setparams((1, 2, 16000, 0, "NONE", "NONE"))
        second = b"".join(struct.pack("<h", int(420 * math.sin(i * 2 * math.pi * 220 / 16000))) for i in range(16000))
        for _ in range(180):
            sample.writeframes(second)
    adb("push", "/tmp/Glass-audio.wav", "/sdcard/Download/Glass-audio.wav")
    adb("shell", "am", "broadcast", "-a", "android.intent.action.MEDIA_SCANNER_SCAN_FILE",
        "-d", "file:///sdcard/Download/Glass-audio.wav")
    adb("shell", "pm", "grant", PACKAGE, "android.permission.POST_NOTIFICATIONS")
    adb("shell", "am", "start", "-W", "-n", f"{PACKAGE}/.MainActivity")
    tap("Пропустить")
    find("welcome back")
    screenshot("01-home")
    tap("Поиск")
    find("Последние запросы")
    screenshot("02-search")
    tap("Настройки")
    find("AUVYX Glass UI")
    screenshot("03-appearance")
    tap("Material 3 Expressive")
    tap("Главная")
    find("auvyx  /  MUSIC")
    screenshot("04-material")
    tap("Настройки")
    tap("AUVYX Glass UI")
    tap("Медиатека")
    find("Понравившиеся песни")
    screenshot("05-library")
    tap("Файлы")
    try:
        tap("Glass-audio.wav")
    except RuntimeError:
        tap("Show roots")
        tap("Downloads")
        tap("Glass-audio.wav")
    tap("Треки")
    tap("Glass-audio")
    tap("Пауза")
    tap("Обложка")
    find("сейчас играет")
    screenshot("06-player")
    tap("Очередь")
    find("Убрать из очереди")
    screenshot("07-queue")
    tap("Закрыть очередь")
    tap("Эквалайзер и Speed Lab")
    find("Эквалайзер")
    screenshot("08-equalizer")
    (OUT / "gfxinfo.txt").write_text(adb("shell", "dumpsys", "gfxinfo", PACKAGE))
    (OUT / "meminfo.txt").write_text(adb("shell", "dumpsys", "meminfo", PACKAGE))


if __name__ == "__main__":
    try:
        main()
    except Exception:
        screenshot("error")
        (OUT / "logcat.txt").write_text(adb("logcat", "-d", "-t", "800"))
        raise
