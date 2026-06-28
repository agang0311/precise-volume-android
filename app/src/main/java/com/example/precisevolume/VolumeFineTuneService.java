package com.example.precisevolume;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.audiofx.DynamicsProcessing;
import android.media.audiofx.Equalizer;
import android.os.Build;
import android.os.IBinder;

import java.util.Locale;

public class VolumeFineTuneService extends Service {
    public static final String ACTION_START = "com.example.precisevolume.START";
    public static final String ACTION_STOP = "com.example.precisevolume.STOP";
    public static final String ACTION_APPLY = "com.example.precisevolume.APPLY";
    public static final String PREFS = "precise_volume";
    public static final String KEY_ENABLED = "enabled";
    public static final String KEY_TARGET_PERCENT = "target_percent";
    public static final String KEY_LAST_STATUS = "last_status";

    private static final String CHANNEL_ID = "volume_fine_tune";
    private static final int NOTIFICATION_ID = 1001;

    private AudioManager audioManager;
    private DynamicsProcessing dynamicsProcessing;
    private Equalizer equalizer;

    public static void start(Context context) {
        Intent intent = new Intent(context, VolumeFineTuneService.class).setAction(ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    public static void stop(Context context) {
        context.startService(new Intent(context, VolumeFineTuneService.class).setAction(ACTION_STOP));
    }

    public static boolean isEnabled(Context context) {
        return prefs(context).getBoolean(KEY_ENABLED, false);
    }

    public static float getTargetPercent(Context context) {
        return prefs(context).getFloat(KEY_TARGET_PERCENT, 5.0f);
    }

    public static String getLastStatus(Context context) {
        return prefs(context).getString(KEY_LAST_STATUS, "未启动");
    }

    public static void setTargetPercent(Context context, float percent) {
        prefs(context).edit().putFloat(KEY_TARGET_PERCENT, percent).apply();
        if (isEnabled(context)) {
            start(context);
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        ensureNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            setEnabled(false);
            stopForeground(true);
            stopSelf();
            VolumeTileService.requestTileRefresh(this);
            return START_NOT_STICKY;
        }

        setEnabled(true);
        startForeground(NOTIFICATION_ID, buildNotification("正在启动"));
        applyFineTune();
        VolumeTileService.requestTileRefresh(this);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        releaseEffects();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void applyFineTune() {
        float targetPercent = Math.max(0.0f, Math.min(100.0f, getTargetPercent(this)));
        int max = Math.max(1, audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        int targetIndex = targetPercent <= 0.0f ? 0 : Math.max(1, (int) Math.ceil(targetPercent * max / 100.0f));
        targetIndex = Math.min(targetIndex, max);

        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetIndex, 0);

        float coarsePercent = targetIndex * 100.0f / max;
        float gain = targetIndex == 0 ? 0.0f : targetPercent / coarsePercent;
        gain = Math.max(0.0f, Math.min(1.0f, gain));
        float gainDb = gain <= 0.0f ? -80.0f : (float) (20.0 * Math.log10(gain));

        String effect = applyGlobalGain(gainDb);
        String status = String.format(
                Locale.getDefault(),
                "目标 %.1f%%，系统第 %d/%d 挡，额外 %.2f 倍（%.1f dB），%s",
                targetPercent,
                targetIndex,
                max,
                gain,
                gainDb,
                effect
        );
        prefs(this).edit().putString(KEY_LAST_STATUS, status).apply();
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        manager.notify(NOTIFICATION_ID, buildNotification(status));
    }

    private String applyGlobalGain(float gainDb) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                releaseEqualizer();
                if (dynamicsProcessing == null) {
                    dynamicsProcessing = new DynamicsProcessing(0, 0, null);
                    dynamicsProcessing.setEnabled(true);
                }
                dynamicsProcessing.setInputGainAllChannelsTo(gainDb);
                return "DynamicsProcessing 已启用";
            } catch (RuntimeException exception) {
                releaseDynamicsProcessing();
            }
        }

        try {
            releaseDynamicsProcessing();
            if (equalizer == null) {
                equalizer = new Equalizer(0, 0);
                equalizer.setEnabled(true);
            }
            short[] range = equalizer.getBandLevelRange();
            short millibels = (short) Math.max(range[0], Math.min(range[1], Math.round(gainDb * 100.0f)));
            short bandCount = equalizer.getNumberOfBands();
            for (short band = 0; band < bandCount; band++) {
                equalizer.setBandLevel(band, millibels);
            }
            return "Equalizer 已启用";
        } catch (RuntimeException exception) {
            releaseEffects();
            return "全局音效不被此设备支持";
        }
    }

    private Notification buildNotification(String status) {
        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent openPendingIntent = PendingIntent.getActivity(
                this,
                1,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent stopIntent = new Intent(this, VolumeFineTuneService.class).setAction(ACTION_STOP);
        PendingIntent stopPendingIntent = PendingIntent.getService(
                this,
                2,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        return builder
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("音量微调运行中")
                .setContentText(status)
                .setContentIntent(openPendingIntent)
                .setOngoing(true)
                .setShowWhen(false)
                .setOnlyAlertOnce(true)
                .setPriority(Notification.PRIORITY_MIN)
                .addAction(R.drawable.ic_launcher, "停止", stopPendingIntent)
                .build();
    }

    private void ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "音量微调常驻服务",
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("用于维持系统级音效增益，通知可设为静音。");
        channel.setShowBadge(false);
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        manager.createNotificationChannel(channel);
    }

    private void setEnabled(boolean enabled) {
        prefs(this).edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    private void releaseEffects() {
        releaseDynamicsProcessing();
        releaseEqualizer();
    }

    private void releaseDynamicsProcessing() {
        if (dynamicsProcessing == null) return;
        dynamicsProcessing.release();
        dynamicsProcessing = null;
    }

    private void releaseEqualizer() {
        if (equalizer == null) return;
        equalizer.release();
        equalizer = null;
    }
}
