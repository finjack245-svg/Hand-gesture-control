package com.finjk.handgesture;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Size;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private static final int CAMERA_REQUEST = 41;
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

    private PreviewView previewView;
    private TextView status;
    private HandLandmarker handLandmarker;
    private ExecutorService cameraExecutor;
    private ImageAnalysis imageAnalysis;
    private SharedPreferences preferences;
    private volatile long lastActionAt = 0;
    private volatile String lastGesture = "";

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        cameraExecutor = Executors.newSingleThreadExecutor();
        buildScreen();
        try {
            BaseOptions baseOptions = BaseOptions.builder().setModelAssetPath("hand_landmarker.task").build();
            HandLandmarker.HandLandmarkerOptions options = HandLandmarker.HandLandmarkerOptions.builder()
                    .setBaseOptions(baseOptions).setRunningMode(RunningMode.VIDEO).setNumHands(1)
                    .setMinHandDetectionConfidence(0.60f).setMinHandPresenceConfidence(0.60f)
                    .setMinTrackingConfidence(0.60f).build();
            handLandmarker = HandLandmarker.createFromOptions(this, options);
        } catch (Exception e) {
            showStatus("Hand model could not load: " + e.getMessage());
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_REQUEST);
        }
    }

    private void buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(16, 12, 16, 12);
        root.setBackgroundColor(0xFF101014);

        TextView title = new TextView(this);
        title.setText("HAND GESTURE CONTROL");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(21);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, 48));

        status = new TextView(this);
        status.setText("Preparing hand tracking...");
        status.setTextColor(0xFFB8E6FF);
        status.setTextSize(14);
        status.setGravity(Gravity.CENTER);
        root.addView(status, new LinearLayout.LayoutParams(-1, 58));

        previewView = new PreviewView(this);
        previewView.setScaleType(PreviewView.ScaleType.FILL_CENTER);
        root.addView(previewView, new LinearLayout.LayoutParams(-1, 0, 1));

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
            int saved = actionIndex(preferences.getString(key, defaultAction(key)));
            spinner.setSelection(saved);
            spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                    preferences.edit().putString(key, ACTIONS[position]).apply();
                }
                @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
            });
            root.addView(spinner, new LinearLayout.LayoutParams(-1, 46));
        }

        Button accessibility = new Button(this);
        accessibility.setText("Enable phone control (Accessibility)");
        accessibility.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        root.addView(accessibility, new LinearLayout.LayoutParams(-1, 48));

        Button pause = new Button(this);
        pause.setText("Pause / Resume hand control");
        pause.setOnClickListener(v -> {
            boolean enabled = preferences.getBoolean("enabled", true);
            preferences.edit().putBoolean("enabled", !enabled).apply();
            showStatus(!enabled ? "Gesture actions enabled." : "Gesture actions paused.");
        });
        root.addView(pause, new LinearLayout.LayoutParams(-1, 48));
        setContentView(root);
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

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == CAMERA_REQUEST && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) startCamera();
        else if (requestCode == CAMERA_REQUEST) showStatus("Camera permission is required for hand tracking.");
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());
                imageAnalysis = new ImageAnalysis.Builder().setTargetResolution(new Size(640, 480))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888).build();
                imageAnalysis.setAnalyzer(cameraExecutor, this::analyseFrame);
                provider.unbindAll();
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, preview, imageAnalysis);
                showStatus("Camera ready. Try an open palm, fist, peace sign or point.");
            } catch (Exception e) { showStatus("Camera could not start: " + e.getMessage()); }
        }, ContextCompat.getMainExecutor(this));
    }

    private void analyseFrame(ImageProxy image) {
        Bitmap source = null, upright = null;
        try {
            if (handLandmarker == null) return;
            source = image.toBitmap();
            int rotation = image.getImageInfo().getRotationDegrees();
            if (rotation == 0) upright = source;
            else {
                Matrix matrix = new Matrix(); matrix.postRotate(rotation);
                upright = Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
            }
            HandLandmarkerResult result = handLandmarker.detectForVideo(
                    new BitmapImageBuilder(upright).build(), System.nanoTime() / 1_000_000L);
            List<List<NormalizedLandmark>> hands = result.landmarks();
            if (hands.isEmpty()) {
                lastGesture = "";
                showStatus("No hand detected. Keep your hand inside the camera frame.");
            } else {
                String gesture = classify(hands.get(0));
                showStatus("Detected: " + gesture.replace('_', ' ') + "  |  Actions " +
                        (preferences.getBoolean("enabled", true) ? "ON" : "PAUSED"));
                if (!gesture.equals("UNKNOWN")) maybeRunAction(gesture);
            }
        } catch (Exception e) { showStatus("Tracking error: " + e.getMessage()); }
        finally {
            if (upright != null && upright != source) upright.recycle();
            if (source != null) source.recycle();
            image.close();
        }
    }

    private String classify(List<NormalizedLandmark> p) {
        boolean index = p.get(8).y() < p.get(6).y();
        boolean middle = p.get(12).y() < p.get(10).y();
        boolean ring = p.get(16).y() < p.get(14).y();
        boolean pinky = p.get(20).y() < p.get(18).y();
        int count = (index ? 1 : 0) + (middle ? 1 : 0) + (ring ? 1 : 0) + (pinky ? 1 : 0);
        if (count == 4) return "OPEN_PALM";
        if (count == 0) return "FIST";
        if (index && middle && !ring && !pinky) return "PEACE";
        if (index && !middle && !ring && !pinky) return "POINT";
        if (index && middle && ring && !pinky) return "THREE_FINGERS";
        return "UNKNOWN";
    }

    private void maybeRunAction(String gesture) {
        if (!preferences.getBoolean("enabled", true)) return;
        long now = System.currentTimeMillis();
        if (now - lastActionAt < 1400 || gesture.equals(lastGesture)) return;
        lastGesture = gesture;
        lastActionAt = now;
        String action = preferences.getString(gesture, defaultAction(gesture));
        if ("NONE".equals(action)) return;
        boolean ok = GestureActionController.perform(this, action);
        if (!ok) showStatus("Action not sent. Enable Accessibility control, or check the action's Android permission.");
    }

    private void showStatus(String message) {
        runOnUiThread(() -> { if (status != null) status.setText(message); });
    }

    @Override protected void onDestroy() {
        if (imageAnalysis != null) imageAnalysis.clearAnalyzer();
        if (cameraExecutor != null) cameraExecutor.shutdown();
        if (handLandmarker != null) handLandmarker.close();
        super.onDestroy();
    }
}
