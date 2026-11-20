# Hand Gesture Control

Dual-platform hand gesture controller: **Android phone control** plus a separate **Windows PC webcam controller**.

## Android — Samsung Galaxy A05

The existing Android app remains in `app/`. It uses the phone's front camera, MediaPipe hand tracking, a visible foreground camera notification, a movable floating indicator, Android Accessibility Service for phone-wide navigation, and editable gesture-to-action mappings.

Build the debug APK with the **Android APK Build** GitHub Actions workflow. On the phone, grant camera and overlay permissions, and enable the app's Accessibility Service only when you want system navigation actions.

## Windows 10/11 — webcam control

The new desktop app is in `desktop/`. It uses the PC webcam and local MediaPipe hand tracking. Camera frames are processed locally; this implementation does not upload video or require an API key.

### Build and run from source

1. Install **64-bit Python 3.11** and a working webcam.
2. Open Command Prompt in the repository folder.
3. Run:

```bat
py -3.11 -m venv desktop\.venv
desktop\.venv\Scripts\python -m pip install --upgrade pip
desktop\.venv\Scripts\python -m pip install -r desktop\requirements.txt
desktop\.venv\Scripts\python desktop\app.py
```

Allow desktop apps to access the camera in **Windows Settings → Privacy & security → Camera**.

### Windows gesture defaults

| Gesture | Default PC action |
|---|---|
| Open palm | Toggle gesture actions paused/active |
| Closed fist | Show desktop (Win + D) |
| Peace sign | Browser/app back (Alt + Left) |
| Pointing finger | Move mouse pointer |
| Three fingers | Open Windows snipping overlay (Win + Shift + S) |
| Thumb + index pinch | Left click |

### PC controls (keyboard and mouse)

These are the controls while the camera preview window is open:

| PC key / action | What it does |
|---|---|
| `P` | Pause or resume gesture actions |
| `Q` or `Esc` | Stop the controller and close the camera preview |
| Move the mouse to any screen corner | PyAutoGUI emergency fail-safe; stops automated mouse/keyboard actions |

Keep the preview window visible while using gesture control. The controller does not run invisibly in the background.

### Gesture-to-PC action reference

The default gestures are the six entries in the table above. You can assign any of these supported actions to a recognised gesture in the JSON mapping:

| Mapping action | PC result |
|---|---|
| `PAUSE_TOGGLE` | Pause/resume gesture actions |
| `MOVE_CURSOR` | Move the mouse pointer using the pointing finger |
| `LEFT_CLICK` / `RIGHT_CLICK` | Left/right mouse click |
| `SCROLL_UP` / `SCROLL_DOWN` | Scroll the active window |
| `WIN_D` | Show or hide the Windows desktop |
| `ALT_LEFT` | Go back (Alt + Left) |
| `SCREENSHOT` | Open the Windows snipping overlay (Win + Shift + S) |
| `VOLUME_UP` / `VOLUME_DOWN` | Change system volume |
| `MEDIA_PLAY_PAUSE` | Play/pause media |
| `LOCK_PC` | Lock Windows (Win + L) |
| `NONE` | Do nothing for that gesture |

Example: to make a peace sign scroll down instead of going back, change its entry in `desktop/gesture-map.json` to:

```json
{
  "PEACE": "SCROLL_DOWN"
}
```

Keep the other gesture entries in the file if you want their current mappings to remain unchanged. Only recognised gesture names are accepted: `OPEN_PALM`, `FIST`, `PEACE`, `POINT`, `THREE_FINGERS`, and `PINCH`.

The mappings are stored in `desktop/gesture-map.json`. Copy `desktop/gesture-map.example.json` to that name and change action values to customise them. Available actions: `PAUSE_TOGGLE`, `WIN_D`, `ALT_LEFT`, `MOVE_CURSOR`, `SCREENSHOT`, `LEFT_CLICK`, `RIGHT_CLICK`, `SCROLL_UP`, `SCROLL_DOWN`, `VOLUME_UP`, `VOLUME_DOWN`, `MEDIA_PLAY_PAUSE`, `LOCK_PC`, and `NONE`.

**Safety controls:** keep the preview window visible. Press **P** to pause/resume and **Q** or **Esc** to quit. Moving the mouse to a screen corner triggers PyAutoGUI's emergency fail-safe. The app does not type arbitrary text, open arbitrary programs, or run shell commands from gestures.

### Download a Windows build

The **Windows Desktop Build** workflow creates a Windows executable folder and uploads it as the `Hand-Gesture-Control-Windows` artifact. Download and extract the artifact ZIP, then run `HandGestureControl.exe`. Keep the accompanying files/folders together.

## Repository layout

- `app/` — Android application source
- `desktop/` — Windows webcam application, requirements, gesture map and tests
- `.github/workflows/android.yml` — Android APK build
- `.github/workflows/windows.yml` — Windows desktop build

Both platforms are built independently; Windows additions do not replace or remove the Android app.
