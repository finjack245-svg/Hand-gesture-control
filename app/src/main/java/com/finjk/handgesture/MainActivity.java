package com.finjk.handgesture;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.view.Gravity;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.ExecutionException;

public class MainActivity extends AppCompatActivity {
    private PreviewView previewView;
    private TextView status;
    private static final int CAMERA_REQUEST = 41;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 24, 24, 24);
        root.setBackgroundColor(0xFF101014);

        TextView title = new TextView(this);
        title.setText("HAND GESTURE CONTROL");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(22);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, 64));

        status = new TextView(this);
        status.setText("Camera preview setup. Hand recognition is the next stage.");
        status.setTextColor(0xFFCCCCCC);
        root.addView(status, new LinearLayout.LayoutParams(-1, 56));

        previewView = new PreviewView(this);
        root.addView(previewView, new LinearLayout.LayoutParams(-1, 0, 1));

        Button accessibility = new Button(this);
        accessibility.setText("Enable accessibility control");
        accessibility.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        root.addView(accessibility, new LinearLayout.LayoutParams(-1, 56));

        Button testBack = new Button(this);
        testBack.setText("Test BACK action");
        testBack.setOnClickListener(v -> {
            if (!GestureAccessibilityService.back()) status.setText("Enable Hand Gesture Control in Accessibility settings first.");
        });
        root.addView(testBack, new LinearLayout.LayoutParams(-1, 56));

        setContentView(root);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_REQUEST);
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == CAMERA_REQUEST && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) startCamera();
        else status.setText("Camera permission is required for hand tracking.");
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());
                provider.unbindAll();
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, preview);
                status.setText("Front camera active. Hand tracking is not installed yet.");
            } catch (ExecutionException | InterruptedException e) {
                status.setText("Could not start camera: " + e.getMessage());
            }
        }, ContextCompat.getMainExecutor(this));
    }
}
