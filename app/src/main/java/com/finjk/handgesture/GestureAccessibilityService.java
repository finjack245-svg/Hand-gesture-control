package com.finjk.handgesture;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;

/** User-enabled bridge for system navigation actions and screen gestures. */
public class GestureAccessibilityService extends AccessibilityService {
    private static GestureAccessibilityService instance;

    @Override public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    @Override public void onInterrupt() { }
    @Override public void onDestroy() {
        if (instance == this) instance = null;
        super.onDestroy();
    }

    public static boolean tap(float x, float y) {
        if (instance == null) return false;
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, 80)).build();
        return instance.dispatchGesture(gesture, null, new Handler(Looper.getMainLooper()));
    }
    public static boolean swipe(float x1, float y1, float x2, float y2) {
        if (instance == null) return false;
        Path path = new Path();
        path.moveTo(x1, y1);
        path.lineTo(x2, y2);
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, 350)).build();
        return instance.dispatchGesture(gesture, null, new Handler(Looper.getMainLooper()));
    }
    public static boolean back() { return action(GLOBAL_ACTION_BACK); }
    public static boolean home() { return action(GLOBAL_ACTION_HOME); }
    public static boolean recents() { return action(GLOBAL_ACTION_RECENTS); }
    public static boolean notifications() { return action(GLOBAL_ACTION_NOTIFICATIONS); }
    public static boolean quickSettings() { return action(GLOBAL_ACTION_QUICK_SETTINGS); }
    public static boolean screenshot() {
        return Build.VERSION.SDK_INT >= 28 && action(GLOBAL_ACTION_TAKE_SCREENSHOT);
    }
    public static boolean lockScreen() {
        return Build.VERSION.SDK_INT >= 28 && action(GLOBAL_ACTION_LOCK_SCREEN);
    }
    private static boolean action(int action) {
        return instance != null && instance.performGlobalAction(action);
    }
}
