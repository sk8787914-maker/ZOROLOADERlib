package com.zoro.loader.activity;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import com.zoro.loader.R;
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.core.ZoroLogUploader;
import top.niunaijun.blackbox.entity.pm.InstallResult;
import com.zoro.loader.libhelper.FileCopyTask;
import com.zoro.loader.utils.FLog;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

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

    private BlackBoxCore blackBoxCore;
    private Button starthack, stophack, uploadLogs;
    private FileCopyTask fileCopyTask;
    private LottieAnimationView backgroundAnimation; // Lottie Animation View

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
        }

        FLog.info("MainActivity ready");
        // Automatic sync: logs land on GitHub without asking for a token again.
        new Handler().postDelayed(this::syncAllLogs, 5000);
    }

    /** No token prompt. Uses the credential already saved on this device only. */
    private void syncAllLogs() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String token = prefs.getString(TOKEN_KEY, "");
        if (token == null || token.trim().isEmpty()) {
            FLog.warning("Log sync skipped: no device credential stored");
            return;
        }
        token = token.trim();

        final int[] pending = new int[]{4};
        final StringBuilder summary = new StringBuilder();
        FLog.info("Auto-syncing APK, native and AAR logs to GitHub");

        sendLog(token, LOADER_REPO, "loader_log.txt", "logs/loader_log.txt", "APK", pending, summary);
        sendLog(token, LOADER_REPO, "zoro_native.txt", "logs/zoro_native.txt", "Native", pending, summary);
        sendLog(token, AAR_REPO, "zoro.txt", "logs/zoro.txt", "AAR", pending, summary);
        sendLog(token, AAR_REPO, "zoro_crash.txt", "logs/zoro_crash.txt", "Crash", pending, summary);
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
        pending[0]--;
        if (pending[0] == 0) {
            Toast.makeText(this, "Logs synced to GitHub", Toast.LENGTH_SHORT).show();
        }
    }

    private void countDownStart() {
        final Handler handler = new Handler();
        final Runnable runnable = new Runnable() {
            @Override
            public void run() {
                try {
                    String expiryStr = TimeExpired();
                    if (expiryStr != null && !expiryStr.isEmpty()) {
                        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                        Date expiryDate = dateFormat.parse(expiryStr);
                        long distance = expiryDate.getTime() - System.currentTimeMillis();

                        if (distance > 0) {
                            long days = distance / (24 * 60 * 60 * 1000);
                            long hours = (distance / (60 * 60 * 1000)) % 24;
                            long minutes = (distance / (60 * 1000)) % 60;
                            long seconds = (distance / 1000) % 60;

                            runOnUiThread(() -> {
                                TextView tvD = findViewById(R.id.tv_d);
                                TextView tvH = findViewById(R.id.tv_h);
                                TextView tvM = findViewById(R.id.tv_m);
                                TextView tvS = findViewById(R.id.tv_s);
                                
                                if (tvD != null) tvD.setText(String.format(Locale.getDefault(), "%02d", days));
                                if (tvH != null) tvH.setText(String.format(Locale.getDefault(), "%02d", hours));
                                if (tvM != null) tvM.setText(String.format(Locale.getDefault(), "%02d", minutes));
                                if (tvS != null) tvS.setText(String.format(Locale.getDefault(), "%02d", seconds));
                            });
                        }
                    }
                    handler.postDelayed(this, 1000);
                } catch (Exception e) { 
                    e.printStackTrace(); 
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
        InstallResult res = blackBoxCore.installPackageAsUser(BGMI_PACKAGE, USER_ID);
        if (res.success) {
            FLog.info("STEP install game success");
            copyObbFilesAndLaunch();
        } else {
            FLog.error("STEP install game failed: " + res.msg);
            Toast.makeText(this, "Installation Failed: " + res.msg, Toast.LENGTH_SHORT).show();
        }
    }

    private void copyObbFilesAndLaunch() {
        FLog.info("STEP OBB copy start");
        fileCopyTask.copyObbFolderAsync(BGMI_PACKAGE, success -> {
            FLog.info("STEP OBB copy result: " + success);
            FLog.info("STEP launching game process");
            blackBoxCore.launchApk(BGMI_PACKAGE, USER_ID);
            // The game dies roughly 2m43s after launch; sync automatically after it.
            new Handler().postDelayed(this::syncAllLogs, 180000);
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