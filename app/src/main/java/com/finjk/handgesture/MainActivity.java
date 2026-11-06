package com.finjk.handgesture;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {
    private static final int CAMERA_REQUEST = 41;
    private static final int OVERLAY_REQUEST = 42;
    private static final String PREFS = "gesture_mappings";
    private static final String[] ACTION_LABELS = {
            "Do nothing", "Go Back", "Go Home", "Recent Apps", "Notifications",
            "Quick Settings", "Take Screenshot", "Lock Screen", "Volume Up",
            "Volume Down", "Play / Pause Media", "Flashlight On / Off", "Open Settings"
    };
    private static final String[] ACTIONS = {
            "NONE", "BACK", "HOME", "RECENTS", "NOTIFICATIONS", "QUICK_SETTINGS",
            "SCREENSHOT", "LOCK_SCREEN", "VOLUME_UP", "VOLUME_DOWN",
            "MEDIA_PLAY_PAUSE", "FLASHLIGHT", "OPEN_SETTINGS"
    };
    private static final String[] GESTURES = {
            "OPEN_PALM", "FIST", "PEACE", "POINT", "THREE_FINGERS"
    };
    private static final String[] GESTURE_LABELS = {
            "Open palm", "Closed fist", "Peace sign", "Pointing finger", "Three fingers"
    };

    private SharedPreferences preferences;
    private TextView status;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        buildScreen();
    }

    @Override protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    private void buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(18, 14, 18, 14);
        root.setBackgroundColor(0xFF101014);

        TextView title = new TextView(this);
        title.setText("HAND GESTURE CONTROL");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(21);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, 52));

        status = new TextView(this);
        status.setTextColor(0xFFB8E6FF);
        status.setTextSize(14);
        status.setGravity(Gravity.CENTER);
        root.addView(status, new LinearLayout.LayoutParams(-1, 54));

        Button start = new Button(this);
        start.setText("START FLOATING HAND CONTROL");
        start.setOnClickListener(v -> requestStart());
        root.addView(start, new LinearLayout.LayoutParams(-1, 52));

        Button stop = new Button(this);
        stop.setText("STOP HAND CONTROL");
        stop.setOnClickListener(v -> stopControl());
        root.addView(stop, new LinearLayout.LayoutParams(-1, 48));

        Button accessibility = new Button(this);
        accessibility.setText("ENABLE PHONE CONTROL (ACCESSIBILITY)");
        accessibility.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        root.addView(accessibility, new LinearLayout.LayoutParams(-1, 48));

        Button overlay = new Button(this);
        overlay.setText("ALLOW FLOATING INDICATOR");
        overlay.setOnClickListener(v -> requestOverlayPermission());
        root.addView(overlay, new LinearLayout.LayoutParams(-1, 48));

        TextView heading = new TextView(this);
        heading.setText("CUSTOM GESTURE ACTIONS");
        heading.setTextColor(0xFFFFFFFF);
        heading.setTextSize(16);
        heading.setPadding(0, 12, 0, 4);
        root.addView(heading);

        for (int i = 0; i < GESTURES.length; i++) {
            final String key = GESTURES[i];
            TextView label = new TextView(this);
            label.setText(GESTURE_LABELS[i]);
            label.setTextColor(0xFFDADAE2);
            root.addView(label);
            Spinner spinner = new Spinner(this);
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                    android.R.layout.simple_spinner_item, ACTION_LABELS);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinner.setAdapter(adapter);
            spinner.setSelection(actionIndex(preferences.getString(key, defaultAction(key))));
            spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                    preferences.edit().putString(key, ACTIONS[position]).apply();
                }
                @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
            });
            root.addView(spinner, new LinearLayout.LayoutParams(-1, 46));
        }

        TextView note = new TextView(this);
        note.setText("Keep the service running from the Start button. Android will show a camera-use notification while hand tracking is active. Tap the floating hand to pause or resume actions; drag it to move it.");
        note.setTextColor(0xFFAAAAAA);
        note.setTextSize(12);
        note.setPadding(2, 12, 2, 8);
        root.addView(note);

        setContentView(root);
    }

    private void requestStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_REQUEST);
            return;
        }
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            requestOverlayPermission();
            return;
        }
        startControlService();
    }

    private void requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, OVERLAY_REQUEST);
        } else {
            refreshStatus();
        }
    }

    private void startControlService() {
        Intent intent = new Intent(this, GestureCameraService.class);
        intent.setAction(GestureCameraService.ACTION_START);
        ContextCompat.startForegroundService(this, intent);
        status.setText("Starting background camera and floating indicator...");
    }

    private void stopControl() {
        stopService(new Intent(this, GestureCameraService.class));
        status.setText("Hand control stopped.");
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == CAMERA_REQUEST && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            requestStart();
        } else if (requestCode == CAMERA_REQUEST) {
            status.setText("Camera permission is required for hand tracking.");
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == OVERLAY_REQUEST) {
            if (Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this)) startControlService();
            else status.setText("Floating indicator permission was not granted.");
        }
    }

    private void refreshStatus() {
        if (status == null) return;
        boolean overlayAllowed = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this);
        status.setText("Background mode: start when ready. Floating overlay permission: " +
                (overlayAllowed ? "allowed" : "needed") + ". Accessibility must be enabled separately for phone navigation.");
    }

    private int actionIndex(String action) {
        for (int i = 0; i < ACTIONS.length; i++) if (ACTIONS[i].equals(action)) return i;
        return 0;
    }

    private String defaultAction(String gesture) {
        switch (gesture) {
            case "OPEN_PALM": return "NOTIFICATIONS";
            case "FIST": return "HOME";
            case "PEACE": return "BACK";
            case "POINT": return "RECENTS";
            case "THREE_FINGERS": return "SCREENSHOT";
            default: return "NONE";
        }
    }
}
