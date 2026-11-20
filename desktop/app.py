"""Windows webcam hand-gesture controller. Camera frames stay on the PC."""
from __future__ import annotations

import json
import os
import sys
import time
from pathlib import Path

import cv2
import mediapipe as mp
import pyautogui

ROOT = Path(__file__).resolve().parent
CONFIG_PATH = ROOT / "gesture-map.json"
DEFAULTS = {
    "OPEN_PALM": "PAUSE_TOGGLE",
    "FIST": "WIN_D",
    "PEACE": "ALT_LEFT",
    "POINT": "MOVE_CURSOR",
    "THREE_FINGERS": "SCREENSHOT",
    "PINCH": "LEFT_CLICK",
}
pyautogui.FAILSAFE = True
pyautogui.PAUSE = 0.03


def load_mapping():
    mapping = dict(DEFAULTS)
    try:
        if CONFIG_PATH.exists():
            data = json.loads(CONFIG_PATH.read_text(encoding="utf-8"))
            if isinstance(data, dict):
                mapping.update({str(k): str(v) for k, v in data.items() if k in DEFAULTS})
    except (OSError, ValueError):
        pass
    return mapping


def finger_up(landmarks, tip, pip):
    return landmarks[tip].y < landmarks[pip].y


def classify_hand(landmarks):
    index = finger_up(landmarks, 8, 6)
    middle = finger_up(landmarks, 12, 10)
    ring = finger_up(landmarks, 16, 14)
    pinky = finger_up(landmarks, 20, 18)
    count = sum((index, middle, ring, pinky))
    thumb_tip, index_tip = landmarks[4], landmarks[8]
    pinch = ((thumb_tip.x-index_tip.x)**2 + (thumb_tip.y-index_tip.y)**2) ** 0.5 < 0.055
    if pinch:
        return "PINCH"
    if count == 4:
        return "OPEN_PALM"
    if count == 0:
        return "FIST"
    if index and middle and not ring and not pinky:
        return "PEACE"
    if index and not middle and not ring and not pinky:
        return "POINT"
    if index and middle and ring and not pinky:
        return "THREE_FINGERS"
    return "UNKNOWN"


class DesktopActions:
    def __init__(self):
        self.enabled = True
        self.last_action = 0.0
        self.last_gesture = None
        self.cursor_x, self.cursor_y = pyautogui.position()

    def move_cursor(self, landmarks, frame_width, frame_height):
        point = landmarks[8]
        screen_w, screen_h = pyautogui.size()
        target_x = int((1.0 - point.x) * screen_w)
        target_y = int(point.y * screen_h)
        self.cursor_x = int(self.cursor_x * 0.72 + target_x * 0.28)
        self.cursor_y = int(self.cursor_y * 0.72 + target_y * 0.28)
        pyautogui.moveTo(self.cursor_x, self.cursor_y, duration=0)

    def run(self, gesture, action):
        now = time.monotonic()
        if action == "MOVE_CURSOR":
            return
        if now - self.last_action < 1.25:
            return
        self.last_gesture = gesture
        self.last_action = now
        if action == "PAUSE_TOGGLE":
            self.enabled = not self.enabled
        elif action == "WIN_D":
            pyautogui.hotkey("win", "d")
        elif action == "ALT_LEFT":
            pyautogui.hotkey("alt", "left")
        elif action == "SCREENSHOT":
            pyautogui.hotkey("win", "shift", "s")
        elif action == "LEFT_CLICK":
            pyautogui.click()
        elif action == "RIGHT_CLICK":
            pyautogui.click(button="right")
        elif action == "SCROLL_UP":
            pyautogui.scroll(4)
        elif action == "SCROLL_DOWN":
            pyautogui.scroll(-4)
        elif action == "VOLUME_UP":
            pyautogui.press("volumeup")
        elif action == "VOLUME_DOWN":
            pyautogui.press("volumedown")
        elif action == "MEDIA_PLAY_PAUSE":
            pyautogui.press("playpause")
        elif action == "LOCK_PC":
            pyautogui.hotkey("win", "l")
        elif action == "NONE":
            pass
        else:
            print(f"Unknown configured action: {action}")


def main():
    mapping = load_mapping()
    actions = DesktopActions()
    camera = cv2.VideoCapture(0, cv2.CAP_DSHOW)
    if not camera.isOpened():
        camera = cv2.VideoCapture(0)
    if not camera.isOpened():
        raise RuntimeError("No webcam found. Connect a webcam and check Windows Camera privacy permissions.")

    mp_hands = mp.solutions.hands
    mp_draw = mp.solutions.drawing_utils
    stable_gesture = "UNKNOWN"
    stable_frames = 0
    fired_for_pose = False
    print("Hand Gesture Control started. Q/Esc = quit; P = pause/resume; F8 = emergency pause.")
    try:
        with mp_hands.Hands(
            static_image_mode=False, max_num_hands=1,
            min_detection_confidence=0.65, min_tracking_confidence=0.60
        ) as hands:
            while True:
                ok, frame = camera.read()
                if not ok:
                    raise RuntimeError("Webcam stopped returning frames.")
                frame = cv2.flip(frame, 1)
                height, width = frame.shape[:2]
                rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
                result = hands.process(rgb)
                gesture = "NO HAND"
                if result.multi_hand_landmarks:
                    hand = result.multi_hand_landmarks[0]
                    gesture = classify_hand(hand.landmark)
                    mp_draw.draw_landmarks(frame, hand, mp_hands.HAND_CONNECTIONS)
                    if actions.enabled and gesture == "POINT":
                        actions.move_cursor(hand.landmark, width, height)
                    if gesture == stable_gesture:
                        stable_frames += 1
                    else:
                        stable_gesture, stable_frames, fired_for_pose = gesture, 1, False
                    configured_action = mapping.get(gesture, "NONE")
                    if stable_frames >= 7 and not fired_for_pose and (actions.enabled or configured_action == "PAUSE_TOGGLE"):
                        actions.run(gesture, configured_action)
                        fired_for_pose = True
                else:
                    stable_gesture, stable_frames, fired_for_pose = "UNKNOWN", 0, False

                state = "ACTIVE" if actions.enabled else "PAUSED"
                cv2.putText(frame, f"{gesture} | {state}", (12, 32),
                            cv2.FONT_HERSHEY_SIMPLEX, 0.75, (40, 230, 160), 2)
                cv2.putText(frame, "Q: quit   P: pause/resume   F8: emergency pause",
                            (12, height - 16), cv2.FONT_HERSHEY_SIMPLEX, 0.48, (245, 245, 245), 1)
                cv2.imshow("Hand Gesture Control - Windows", frame)
                key = cv2.waitKey(1) & 0xFF
                if key in (ord("q"), 27):
                    break
                if key == ord("p"):
                    actions.enabled = not actions.enabled
                    stable_gesture, stable_frames, fired_for_pose = "UNKNOWN", 0, False
                if key == 0x71:  # F8 (OpenCV backend key code)
                    actions.enabled = False
    except pyautogui.FailSafeException:
        print("Emergency stop: mouse moved to a screen corner. Controls paused.")
    finally:
        camera.release()
        cv2.destroyAllWindows()


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        print(f"Hand Gesture Control could not start: {exc}", file=sys.stderr)
        input("Press Enter to close...")
        raise
