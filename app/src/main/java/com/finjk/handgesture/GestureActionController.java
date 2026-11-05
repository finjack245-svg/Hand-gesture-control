package com.finjk.handgesture;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.KeyEvent;
import android.hardware.camera2.CameraManager;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

/** Executes only the actions selected by the user in the gesture mapping screen. */
public final class GestureActionController {
    private static boolean torchOn = false;
    private GestureActionController() {}

    public static boolean perform(Context context, String action) {
        try {
            switch (action) {
                case "BACK":
                    return GestureAccessibilityService.back();
                case "HOME":
                    return GestureAccessibilityService.home();
                case "RECENTS":
                    return GestureAccessibilityService.recents();
                case "NOTIFICATIONS":
                    return GestureAccessibilityService.notifications();
                case "QUICK_SETTINGS":
                    return GestureAccessibilityService.quickSettings();
                case "SCREENSHOT":
                    return GestureAccessibilityService.screenshot();
                case "LOCK_SCREEN":
                    return GestureAccessibilityService.lockScreen();
                case "VOLUME_UP":
                    ((AudioManager) context.getSystemService(Context.AUDIO_SERVICE))
                            .adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0);
                    return true;
                case "VOLUME_DOWN":
                    ((AudioManager) context.getSystemService(Context.AUDIO_SERVICE))
                            .adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, 0);
                    return true;
                case "MEDIA_PLAY_PAUSE":
                    AudioManager audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
                    audio.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE));
                    audio.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE));
                    return true;
                case "FLASHLIGHT":
                    CameraManager camera = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
                    String[] ids = camera.getCameraIdList();
                    if (ids.length == 0) return false;
                    torchOn = !torchOn;
                    camera.setTorchMode(ids[0], torchOn);
                    return true;
                case "OPEN_SETTINGS":
                    Intent settings = new Intent(Settings.ACTION_SETTINGS);
                    settings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(settings);
                    return true;
                case "NONE":
                default:
                    return false;
            }
        } catch (Exception error) {
            Toast.makeText(context, "Action unavailable: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            return false;
        }
    }
}
