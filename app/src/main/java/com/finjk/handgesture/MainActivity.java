package com.finjk.handgesture;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Size;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
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

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private static final int CAMERA_REQUEST = 41;
    private PreviewView previewView;
    private TextView status;
    private HandLandmarker handLandmarker;
    private ExecutorService cameraExecutor;
    private ImageAnalysis imageAnalysis;
    private String lastStatus = "";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        cameraExecutor = Executors.newSingleThreadExecutor();
        buildScreen();
        try {
            BaseOptions baseOptions = BaseOptions.builder()
                    .setModelAssetPath("hand_landmarker.task")
                    .build();
            HandLandmarker.HandLandmarkerOptions options =
                    HandLandmarker.HandLandmarkerOptions.builder()
                            .setBaseOptions(baseOptions)
                            .setRunningMode(RunningMode.VIDEO)
                            .setNumHands(2)
                            .setMinHandDetectionConfidence(0.55f)
                            .setMinHandPresenceConfidence(0.55f)
                            .setMinTrackingConfidence(0.55f)
                            .build();
            handLandmarker = HandLandmarker.createFromOptions(this, options);
        } catch (Exception e) {
            showStatus("Hand model could not load: " + e.getMessage());
            return;
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, CAMERA_REQUEST);
        }
    }

    private void buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(20, 20, 20, 20);
        root.setBackgroundColor(0xFF101014);

        TextView title = new TextView(this);
        title.setText("HAND TRACKING");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(23);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, 58));

        status = new TextView(this);
        status.setText("Starting MediaPipe hand tracker...");
        status.setTextColor(0xFFB8E6FF);
        status.setTextSize(15);
        status.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(status, new LinearLayout.LayoutParams(-1, 64));

        previewView = new PreviewView(this);
        previewView.setScaleType(PreviewView.ScaleType.FILL_CENTER);
        root.addView(previewView, new LinearLayout.LayoutParams(-1, 0, 1));

        Button accessibility = new Button(this);
        accessibility.setText("Enable phone control");
        accessibility.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        root.addView(accessibility, new LinearLayout.LayoutParams(-1, 54));

        Button testBack = new Button(this);
        testBack.setText("Test BACK action");
        testBack.setOnClickListener(v -> {
            if (!GestureAccessibilityService.back()) {
                showStatus("Enable Hand Gesture Control in Accessibility settings first.");
            } else {
                showStatus("BACK action sent.");
            }
        });
        root.addView(testBack, new LinearLayout.LayoutParams(-1, 54));
        setContentView(root);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == CAMERA_REQUEST && results.length > 0
                && results[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else if (requestCode == CAMERA_REQUEST) {
            showStatus("Camera permission is required for hand tracking.");
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                imageAnalysis = new ImageAnalysis.Builder()
                        .setTargetResolution(new Size(640, 480))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                        .build();
                imageAnalysis.setAnalyzer(cameraExecutor, this::analyseFrame);

                provider.unbindAll();
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA,
                        preview, imageAnalysis);
                showStatus("Front camera ready. Show one or both hands.");
            } catch (Exception e) {
                showStatus("Camera could not start: " + e.getMessage());
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void analyseFrame(ImageProxy image) {
        Bitmap source = null;
        Bitmap upright = null;
        try {
            if (handLandmarker == null) return;
            source = image.toBitmap();
            int rotation = image.getImageInfo().getRotationDegrees();
            if (rotation == 0) {
                upright = source;
            } else {
                Matrix matrix = new Matrix();
                matrix.postRotate(rotation);
                upright = Bitmap.createBitmap(source, 0, 0,
                        source.getWidth(), source.getHeight(), matrix, true);
            }

            long timestamp = System.nanoTime() / 1_000_000L;
            HandLandmarkerResult result = handLandmarker.detectForVideo(
                    new BitmapImageBuilder(upright).build(), timestamp);
            List<List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>>
                    hands = result.landmarks();

            if (hands.isEmpty()) {
                showStatus("No hand detected. Hold your hand clearly in front of the camera.");
            } else {
                StringBuilder message = new StringBuilder();
                message.append(hands.size()).append(hands.size() == 1
                        ? " HAND DETECTED" : " HANDS DETECTED");
                for (int i = 0; i < hands.size(); i++) {
                    int extended = countExtendedFingers(hands.get(i));
                    message.append("\nHand ").append(i + 1).append(": ")
                            .append(extended >= 3 ? "OPEN HAND" : "CLOSED / OTHER")
                            .append(" · 21 landmarks tracked");
                }
                showStatus(message.toString());
            }
        } catch (Exception e) {
            showStatus("Tracking error: " + e.getMessage());
        } finally {
            if (upright != null && upright != source) upright.recycle();
            if (source != null) source.recycle();
            image.close();
        }
    }

    private int countExtendedFingers(
            List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark> points) {
        int count = 0;
        int[][] pairs = {{8, 6}, {12, 10}, {16, 14}, {20, 18}};
        for (int[] pair : pairs) {
            if (points.get(pair[0]).y() < points.get(pair[1]).y()) count++;
        }
        return count;
    }

    private void showStatus(String message) {
        runOnUiThread(() -> {
            if (status != null && !message.equals(lastStatus)) {
                lastStatus = message;
                status.setText(message);
            }
        });
    }

    @Override
    protected void onDestroy() {
        if (imageAnalysis != null) imageAnalysis.clearAnalyzer();
        if (cameraExecutor != null) cameraExecutor.shutdown();
        if (handLandmarker != null) handLandmarker.close();
        super.onDestroy();
    }
}