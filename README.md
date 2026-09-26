# TriKiTok v2.0

**TriKiTok** let's you use your triki to scroll TikTok and other social media. It also let's you use it to control media volume.

---

## What's New in v2.0

* **Total UI Redesign (Material 3):** Sleek dashboard layout with high-elevation cards, gorgeous typography, and quick start shortcuts.
* **Top-Right Settings Gear (⚙️):** All advanced configuration, permissions, and language/theme pickers are neatly organized behind the top-right settings gear icon.
* **8 Color Themes:** Choose from 8 vibrant color palettes via a clean dialog picker:
  - TikTok Pink 🌸
  - Ocean Blue 🌊
  - Sunset Orange 🌅
  - Emerald Green 🌿
  - Cyberpunk Purple ⚡
  - Neon Cyan 💎
  - Ruby Red 🥊
  - Midnight Gold ✨
* **Multilingual Support (PL / EN):** Switch languages instantly via popup dialogs with 100% full translation coverage.
* **Customizable Gesture Actions:** Fully configure what each gesture does (Rotate Clockwise, Rotate Counter-Clockwise, Shake):
  - Scroll Down / Next Video ⬇️
  - Scroll Up / Previous Video ⬆️
  - Single Tap (Play / Pause) 👆
  - Double Tap (Like ❤️) 👆👆
  - Swipe Left ⬅️ / Swipe Right ➡️
  - Volume Up 🔊 / Volume Down 🔉
  - None (Disabled)
* **Subtle Haptic Feedback Tick:** Ultra-delicate system haptic feedback (`EFFECT_TICK`) on successful gesture execution.
* **Wake Lock (Keep Screen Awake):** Optional toggle to keep the screen on while connected for hands-free viewing.
* **Custom Bluetooth Device Name:** Configurable target device name.
* **Persistent Accessibility Service:** Configured with `stopWithTask="false"` to prevent the accessibility service from being killed when closing the app.

---

## Setup & First Run

1. **Grant Bluetooth Permissions:** Tap **Bluetooth Permissions** in the Home screen quick start card or Settings.
2. **Enable Accessibility Service:** Tap **Enable Accessibility Service**, find **TriKiTok** in your Android accessibility settings, and turn it on.
3. **Connect:** Wake up your Triki and tap **Connect**.
4. **Open TikTok** and enjoy hands-free scrolling, volume control, and media management!

---

## License & Credits

This project is licensed under the MIT License.
Part of the BLE communication logic is based on [TrikiControl](https://github.com/8ad3rror/TrikiControl) by 8ad3rror / egortar-pi.
