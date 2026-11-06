package com.finjk.handgesture;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleService;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker;
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;

import android.Manifest;
import android.content.pm.PackageManager;
import android.util.Size;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Camera foreground service. It is started by the visible activity after the user grants camera and overlay access. */
public class GestureCameraService extends LifecycleService {
    public static final String ACTION_START = "com.finjk.handgesture.START";
    public static final String ACTION_STOP = "com.finjk.handgesture.STOP";
    private static final String CHANNEL_ID = "hand_gesture_camera";
    private static final int NOTIFICATION_ID = 701;
    private static final String PREFS = "gesture_mappings";

    private WindowManager windowManager;
    private TextView bubble;
    private WindowManager.LayoutParams bubbleParams;
    private SharedPreferences preferences;
    private ExecutorService cameraExecutor;
    private HandLandmarker handLandmarker;
    private ImageAnalysis imageAnalysis;
    private volatile long lastActionAt;
    private volatile String lastGesture = "";
    private volatile boolean movedBubble;

    @Override public void onCreate() {
        super.onCreate();
        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        cameraExecutor = Executors.newSingleThreadExecutor();
        createNotificationChannel();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return Service.START_NOT_STICKY;
        }
        if (androidx.core.content.ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            stopSelf();
            return Service.START_NOT_STICKY;
        }
        Notification notification = buildNotification();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
        showFloatingIndicator();
        startHandTracking();
        return Service.START_STICKY;
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pending = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Hand Gesture Control is active")
                .setContentText("Front camera hand tracking is running. Tap the floating hand to pause actions.")
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setContentIntent(pending)
                .setOngoing(true)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    "Hand gesture camera", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Visible notification while hand gesture camera control is active");
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(channel);
        }
    }

    private void showFloatingIndicator() {
        if (bubble != null) return;
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        bubble = new TextView(this);
        bubble.setText("✋");
        bubble.setTextSize(23);
        bubble.setGravity(Gravity.CENTER);
        bubble.setTextColor(0xFFFFFFFF);
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.OVAL);
        shape.setColor(0xDD176B87);
        shape.setStroke(2, 0xFFFFFFFF);
        bubble.setBackground(shape);

        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        bubbleParams = new WindowManager.LayoutParams(58, 58, type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        bubbleParams.gravity = Gravity.TOP | Gravity.START;
        bubbleParams.x = 24;
        bubbleParams.y = 180;

        bubble.setOnTouchListener((view, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    movedBubble = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (Math.abs(event.getRawX() - bubbleParams.x) > 8 ||
                            Math.abs(event.getRawY() - bubbleParams.y) > 8) movedBubble = true;
                    bubbleParams.x = (int) event.getRawX() - bubble.getWidth() / 2;
                    bubbleParams.y = (int) event.getRawY() - bubble.getHeight() / 2;
                    try { windowManager.updateViewLayout(bubble, bubbleParams); } catch (Exception ignored) { }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (!movedBubble) toggleActions();
                    return true;
                default:
                    return false;
            }
        });
        try { windowManager.addView(bubble, bubbleParams); }
        catch (Exception error) { bubble = null; stopSelf(); }
    }

    private void toggleActions() {
        boolean enabled = !preferences.getBoolean("enabled", true);
        preferences.edit().putBoolean("enabled", enabled).apply();
        if (bubble != null) {
            bubble.setText(enabled ? "✋" : "Ⅱ");
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(enabled ? 0xDD176B87 : 0xDD777777);
            bg.setStroke(2, 0xFFFFFFFF);
            bubble.setBackground(bg);
        }
    }

    private void startHandTracking() {
        try {
            BaseOptions base = BaseOptions.builder().setModelAssetPath("hand_landmarker.task").build();
            HandLandmarker.HandLandmarkerOptions options = HandLandmarker.HandLandmarkerOptions.builder()
                    .setBaseOptions(base).setRunningMode(RunningMode.VIDEO).setNumHands(1)
                    .setMinHandDetectionConfidence(0.60f).setMinHandPresenceConfidence(0.60f)
                    .setMinTrackingConfidence(0.60f).build();
            handLandmarker = HandLandmarker.createFromOptions(this, options);
        } catch (Exception error) {
            stopSelf();
            return;
        }

        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();
                imageAnalysis = new ImageAnalysis.Builder().setTargetResolution(new Size(640, 480))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888).build();
                imageAnalysis.setAnalyzer(cameraExecutor, this::analyseFrame);
                provider.unbindAll();
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, imageAnalysis);
            } catch (Exception error) {
                stopSelf();
            }
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
                Matrix matrix = new Matrix();
                matrix.postRotate(rotation);
                upright = Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
            }
            HandLandmarkerResult result = handLandmarker.detectForVideo(
                    new BitmapImageBuilder(upright).build(), System.nanoTime() / 1_000_000L);
            List<List<NormalizedLandmark>> hands = result.landmarks();
            if (hands.isEmpty()) {
                lastGesture = "";
                return;
            }
            String gesture = classify(hands.get(0));
            if (!"UNKNOWN".equals(gesture)) maybeRunAction(gesture);
        } catch (Exception ignored) {
        } finally {
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
        String fallback;
        switch (gesture) {
            case "OPEN_PALM": fallback = "NOTIFICATIONS"; break;
            case "FIST": fallback = "HOME"; break;
            case "PEACE": fallback = "BACK"; break;
            case "POINT": fallback = "RECENTS"; break;
            case "THREE_FINGERS": fallback = "SCREENSHOT"; break;
            default: fallback = "NONE";
        }
        String action = preferences.getString(gesture, fallback);
        if (!"NONE".equals(action)) GestureActionController.perform(this, action);
    }

    @Override public void onDestroy() {
        if (imageAnalysis != null) imageAnalysis.clearAnalyzer();
        try {
            ProcessCameraProvider.getInstance(this).get().unbindAll();
        } catch (Exception ignored) { }
        if (handLandmarker != null) handLandmarker.close();
        if (cameraExecutor != null) cameraExecutor.shutdown();
        if (bubble != null && windowManager != null) {
            try { windowManager.removeView(bubble); } catch (Exception ignored) { }
            bubble = null;
        }
        super.onDestroy();
    }

    @Nullable @Override public IBinder onBind(Intent intent) {
        super.onBind(intent);
        return null;
    }
}
