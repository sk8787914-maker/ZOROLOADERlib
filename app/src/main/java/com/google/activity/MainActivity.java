package com.google.activity;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import com.google.R;
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.core.ZoroLogUploader;
import top.niunaijun.blackbox.entity.pm.InstallResult;
import com.google.libhelper.FileCopyTask;
import com.google.utils.FLog;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

// Lottie Animation Import
import com.airbnb.lottie.LottieAnimationView;
import com.airbnb.lottie.LottieDrawable;

public class MainActivity extends Activity {
    
    public static native String TimeExpired();

    private static final String BGMI_PACKAGE = "com.pubg.imobile";
    private static final int USER_ID = 0;
    private static final String PREFS_NAME = "zoro_upload";
    private static final String TOKEN_KEY = "gh_token";
    private static final String LOADER_REPO = "ZOROLOADERlib";
    private static final String AAR_REPO = "BLACK-OPEN-fix-to-ai";

    private static final long SYNC_INTERVAL_MS = 60_000L;

    private BlackBoxCore blackBoxCore;
    private Button starthack, stophack, uploadLogs;
    private FileCopyTask fileCopyTask;
    private LottieAnimationView backgroundAnimation; // Lottie Animation View

    // Keep all periodic work on one main-thread handler and reuse parsed objects.
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean syncInProgress = new AtomicBoolean(false);
    private final SimpleDateFormat countdownFormat =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
    private final Runnable autoSyncRunnable = new Runnable() {
        @Override
        public void run() {
            syncAllLogs();
            mainHandler.postDelayed(this, SYNC_INTERVAL_MS);
        }
    };

    private TextView tvD, tvH, tvM, tvS;

    @SuppressLint("NewApi")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // --- LOTTIE ANIMATION BACKGROUND ---
        backgroundAnimation = findViewById(R.id.backgroundAnimation);
        if (backgroundAnimation != null) {
            // Set animation speed (0.8 = 80% of original speed)
            backgroundAnimation.setSpeed(0.8f);
            
            // Infinite loop
            backgroundAnimation.setRepeatCount(LottieDrawable.INFINITE);
            
            // Optional: Adjust transparency if needed
            // backgroundAnimation.setAlpha(0.9f);
            
            // Optional: Start animation (already autoplay in XML)
            backgroundAnimation.playAnimation();
            
            // Optional: Animation listener for events
            /*
            backgroundAnimation.addAnimatorUpdateListener(animation -> {
                float progress = (float) animation.getAnimatedValue();
                // Do something with animation progress
            });
            */
        }

        // --- Your Original Logic Below ---
        blackBoxCore = BlackBoxCore.get();
        blackBoxCore.doCreate();
        
        fileCopyTask = new FileCopyTask(this);

        starthack = findViewById(R.id.starthack);
        stophack = findViewById(R.id.stophack);
        uploadLogs = findViewById(R.id.upload_logs);

        countDownStart();

        if (starthack != null) {
            starthack.setOnClickListener(view -> handleStart());
        }
        
        if (stophack != null) {
            stophack.setOnClickListener(view -> handleStop());
        }

        if (uploadLogs != null) {
            uploadLogs.setOnClickListener(view -> syncAllLogs());
            // Long press only: change the saved credential if it ever stops working.
            uploadLogs.setOnLongClickListener(view -> {
                showTokenSetup();
                return true;
            });
        }

        FLog.info("MainActivity ready");
        ensureCredential();
    }

    /** Asked only once when no credential is stored on this device. */
    private void ensureCredential() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String token = prefs.getString(TOKEN_KEY, "");
        if (token == null || token.trim().isEmpty()) {
            FLog.info("No device credential found - showing one-time setup");
            showTokenSetup();
            return;
        }
        startAutoSync();
    }

    private void showTokenSetup() {
        final SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setText(prefs.getString(TOKEN_KEY, ""));
        input.setHint("GitHub token (repo scope)");

        new AlertDialog.Builder(this)
                .setTitle("One-time log setup")
                .setMessage("Enter a GitHub token once. It is saved only on this device, never in the APK. After this, logs sync automatically without asking again.")
                .setView(input)
                .setCancelable(false)
                .setPositiveButton("Save and sync", (dialog, which) -> {
                    String token = input.getText().toString().trim();
                    prefs.edit().putString(TOKEN_KEY, token).apply();
                    FLog.info("Device credential saved");
                    startAutoSync();
                })
                .show();
    }

    private void startAutoSync() {
        // One recurring job on the main handler; no overlapping upload storms.
        mainHandler.removeCallbacks(autoSyncRunnable);
        syncAllLogs();
        mainHandler.postDelayed(autoSyncRunnable, SYNC_INTERVAL_MS);
    }

    /** Uses the credential already saved on this device only. */
    private void syncAllLogs() {
        if (!syncInProgress.compareAndSet(false, true)) {
            FLog.info("Log sync skipped: previous sync still running");
            return;
        }
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String token = prefs.getString(TOKEN_KEY, "");
        if (token == null || token.trim().isEmpty()) {
            FLog.warning("Log sync skipped: no device credential stored");
            syncInProgress.set(false);
            return;
        }
        token = token.trim();

        final int[] pending = new int[]{7};
        final StringBuilder summary = new StringBuilder();
        FLog.info("Auto-syncing APK, native, AAR, login, crash, detection and tombstone logs to GitHub");

        try {
            sendLog(token, LOADER_REPO, "loader_log.txt", "logs/loader_log.txt", "APK", pending, summary);
            sendLog(token, LOADER_REPO, "zoro_native.txt", "logs/zoro_native.txt", "Native", pending, summary);
            sendLog(token, AAR_REPO, "zoro.txt", "logs/zoro.txt", "AAR", pending, summary);
            sendLog(token, AAR_REPO, "zoro_login.txt", "logs/zoro_login.txt", "Login", pending, summary);
            sendLog(token, AAR_REPO, "zoro_crash.txt", "logs/zoro_crash.txt", "Crash", pending, summary);
            sendLog(token, AAR_REPO, "zoro_detect.txt", "logs/zoro_detect.txt", "Detect", pending, summary);
            sendLog(token, AAR_REPO, "zoro_tombstone.txt", "logs/zoro_tombstone.txt", "Tombstone", pending, summary);
        } catch (Throwable t) {
            FLog.error("Log sync start failed: " + t);
            syncInProgress.set(false);
        }
    }

    private void sendLog(final String token, final String repo, final String localFile,
                         final String remotePath, final String label, final int[] pending,
                         final StringBuilder summary) {
        ZoroLogUploader.upload(this, token, "sk8787914-maker", repo, "main", localFile,
                remotePath, new ZoroLogUploader.Callback() {
                    @Override
                    public void onResult(boolean success, String message) {
                        FLog.info(label + " log sync: " + success + " - " + message);
                        summary.append(label).append(": ").append(message).append('\n');
                        syncFinished(pending, summary);
                    }
                });
    }

    private void syncFinished(int[] pending, StringBuilder summary) {
        if (pending[0] <= 0) {
            return;
        }
        if (--pending[0] == 0) {
            syncInProgress.set(false);
            Toast.makeText(this, "Logs synced to GitHub", Toast.LENGTH_SHORT).show();
        }
    }

    private void countDownStart() {
        // Cache views and formatter once. findViewById + new SimpleDateFormat
        // every second was pure main-thread overhead.
        tvD = findViewById(R.id.tv_d);
        tvH = findViewById(R.id.tv_h);
        tvM = findViewById(R.id.tv_m);
        tvS = findViewById(R.id.tv_s);

        final Handler handler = mainHandler;
        final Runnable runnable = new Runnable() {
            @Override
            public void run() {
                try {
                    String expiryStr = TimeExpired();
                    if (expiryStr != null && !expiryStr.isEmpty()) {
                        Date expiryDate = countdownFormat.parse(expiryStr);
                        long distance = expiryDate.getTime() - System.currentTimeMillis();

                        if (distance > 0) {
                            final long days = distance / (24 * 60 * 60 * 1000);
                            final long hours = (distance / (60 * 60 * 1000)) % 24;
                            final long minutes = (distance / (60 * 1000)) % 60;
                            final long seconds = (distance / 1000) % 60;

                            runOnUiThread(() -> {
                                if (tvD != null) tvD.setText(String.format(Locale.getDefault(), "%02d", days));
                                if (tvH != null) tvH.setText(String.format(Locale.getDefault(), "%02d", hours));
                                if (tvM != null) tvM.setText(String.format(Locale.getDefault(), "%02d", minutes));
                                if (tvS != null) tvS.setText(String.format(Locale.getDefault(), "%02d", seconds));
                            });
                        }
                    }
                } catch (Exception e) {
                    FLog.error("countdown update failed: " + e);
                } finally {
                    handler.postDelayed(this, 1000);
                }
            }
        };
        handler.postDelayed(runnable, 0);
    }

    private void handleStart() {
        FLog.info("STEP start button pressed");
        if (blackBoxCore.isInstalled(BGMI_PACKAGE, USER_ID)) {
            FLog.info("STEP game already installed in container");
            copyObbFilesAndLaunch();
        } else {
            installGame();
        }
    }

    private void installGame() {
        FLog.info("STEP install game in container start");
        Toast.makeText(this, "Installing In Container...", Toast.LENGTH_SHORT).show();
        // Container install is disk-heavy; never run it on the main thread.
        new Thread(() -> {
            final InstallResult[] holder = new InstallResult[1];
            try {
                holder[0] = blackBoxCore.installPackageAsUser(BGMI_PACKAGE, USER_ID);
            } catch (Throwable t) {
                FLog.error("STEP install game threw: " + t);
            }
            runOnUiThread(() -> {
                InstallResult res = holder[0];
                if (res != null && res.success) {
                    FLog.info("STEP install game success");
                    copyObbFilesAndLaunch();
                } else {
                    String msg = res == null ? "install error" : res.msg;
                    FLog.error("STEP install game failed: " + msg);
                    Toast.makeText(this, "Installation Failed: " + msg, Toast.LENGTH_SHORT).show();
                }
            });
        }, "zoro-install").start();
    }

    private void copyObbFilesAndLaunch() {
        FLog.info("STEP OBB copy start");
        fileCopyTask.copyObbFolderAsync(BGMI_PACKAGE, success -> {
            FLog.info("STEP OBB copy result: " + success);
            FLog.info("STEP launching game process");
            blackBoxCore.launchApk(BGMI_PACKAGE, USER_ID);
            // Sync after the known death window, on the main looper (callback may be background).
            mainHandler.postDelayed(this::syncAllLogs, 180000);
        });
    }

    private void handleStop() {
        FLog.info("STEP uninstall game start");
        blackBoxCore.uninstallPackageAsUser(BGMI_PACKAGE, USER_ID);
        FLog.info("STEP uninstall game done");
        Toast.makeText(this, "Game Uninstalled From Container", Toast.LENGTH_SHORT).show();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // Resume animation when activity resumes
        if (backgroundAnimation != null && !backgroundAnimation.isAnimating()) {
            backgroundAnimation.resumeAnimation();
        }
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        // Pause animation when activity pauses (optional)
        if (backgroundAnimation != null && backgroundAnimation.isAnimating()) {
            backgroundAnimation.pauseAnimation();
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up animation resources
        if (backgroundAnimation != null) {
            backgroundAnimation.cancelAnimation();
            backgroundAnimation.clearAnimation();
        }
    }
}