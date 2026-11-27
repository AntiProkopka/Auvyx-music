"""Exercise the installed APK on the disposable CI emulator and capture its screen."""
from pathlib import Path
import re
import subprocess
import time
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
            nodes = list(tree().iter("node"))
            # Some freshly booted Pixel images show a launcher ANR over our activity.
            # Only dismiss a dialog naming the launcher. AUVYX errors must fail the test.
            if any("Pixel Launcher isn't responding" in (n.get("text") or "") for n in nodes):
                close = next(n for n in nodes if n.get("text") == "Close app")
                touch_node(close)
                continue
            for node in nodes:
                if label.strip() in ((node.get("text") or "").strip(), (node.get("content-desc") or "").strip()):
                    return node
        except (subprocess.CalledProcessError, ET.ParseError):
            pass
        time.sleep(.4)
    raise RuntimeError(f"Could not find {label!r} on screen")


def touch_node(node):
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
    adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))


def tap(label):
    print("Tap", label, flush=True)
    touch_node(find(label))


def screenshot(name):
    time.sleep(.5)
    data = adb("exec-out", "screencap", "-p", binary=True)
    assert data.startswith(b"\x89PNG\r\n\x1a\n")
    (OUT / f"{name}.png").write_bytes(data)


def main():
    # The fixture is imported with Android's document picker, exercising the same
    # permissions and Media3 path as a user picking a local song.
    for attempt in range(8):
        try:
            adb("shell", "mkdir", "-p", "/sdcard/Download")
            adb("push", ".github/fixtures/Glass-audio.mp3", "/sdcard/Download/Glass-audio.mp3")
            break
        except subprocess.CalledProcessError:
            if attempt == 7:
                raise
            time.sleep(2)
    adb("shell", "am", "broadcast", "-a", "android.intent.action.MEDIA_SCANNER_SCAN_FILE",
        "-d", "file:///sdcard/Download/Glass-audio.mp3")
    adb("shell", "pm", "grant", PACKAGE, "android.permission.POST_NOTIFICATIONS")
    adb("shell", "am", "start", "-W", "-n", f"{PACKAGE}/.MainActivity")
    tap("Пропустить")
    find("welcome back")
    screenshot("01-home")
    tap("Поиск")
    find("Последние запросы")
    screenshot("02-search")
    tap("Настройки")
    find("auvyx Classic")
    screenshot("03-appearance")
    tap("Material 3 Expressive")
    tap("Главная")
    find("auvyx  /  MUSIC")
    screenshot("04-material")
    tap("Настройки")
    tap("auvyx Classic")
    tap("Медиатека")
    find("Понравившиеся песни")
    screenshot("05-library")
    tap("Файлы")
    try:
        tap("Glass-audio.mp3")
    except RuntimeError:
        tap("Show roots")
        tap("Downloads")
        tap("Glass-audio.mp3")
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
    tap("Studio Test")
    find("Подписаться")
    tap("Подписаться")
    find("Отписаться")
    screenshot("08-artist")
    adb("shell", "input", "keyevent", "4")
    tap(" · Glass Sessions")
    find("Воспроизвести альбом")
    screenshot("09-album")
    adb("shell", "input", "keyevent", "4")
    tap("Эквалайзер и Speed Lab")
    find("Эквалайзер")
    screenshot("10-equalizer")
    (OUT / "gfxinfo.txt").write_text(adb("shell", "dumpsys", "gfxinfo", PACKAGE))
    (OUT / "meminfo.txt").write_text(adb("shell", "dumpsys", "meminfo", PACKAGE))


if __name__ == "__main__":
    try:
        main()
    except Exception:
        screenshot("error")
        (OUT / "error-ui.xml").write_bytes(ET.tostring(tree(), encoding="utf-8"))
        (OUT / "logcat.txt").write_text(adb("logcat", "-d", "-t", "800"))
        raise
