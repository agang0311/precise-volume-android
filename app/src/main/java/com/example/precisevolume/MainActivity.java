package com.example.precisevolume;

import android.app.Activity;
import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public class MainActivity extends Activity {
    private static final int GAIN_STEPS = 1000;
    private static final int SAMPLE_RATE = 44100;
    private static final double TONE_HZ = 440.0;
    private static final double REFERENCE_AMPLITUDE = 0.35;

    private AudioManager audioManager;
    private SeekBar gainSlider;
    private SeekBar systemSlider;
    private TextView gainText;
    private TextView relationText;
    private TextView systemText;
    private TextView playStateText;
    private TextView serviceStatusText;
    private Button playButton;
    private Switch serviceSwitch;
    private boolean updatingServiceSwitch;

    private volatile boolean playing;
    private volatile double preciseGain = 0.05;
    private Thread audioThread;
    private AudioTrack audioTrack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        buildUi();
        syncSystemSlider();
        updateGainLabels();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateServiceLabels();
    }

    @Override
    protected void onDestroy() {
        stopTone();
        super.onDestroy();
    }

    private void buildUi() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(color(R.color.bg));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(24));
        scrollView.addView(root, matchWrap());

        TextView title = text("精确音量", 28, true);
        root.addView(title, matchWrap());

        TextView subtitle = text("安卓全局音量只能按系统挡位变化；真正连续的细分音量必须在播放端做增益。", 15, false);
        subtitle.setTextColor(color(R.color.text_secondary));
        subtitle.setPadding(0, dp(8), 0, dp(18));
        root.addView(subtitle, matchWrap());

        LinearLayout gainPanel = panel();
        root.addView(gainPanel, matchWrap());

        TextView gainTitle = text("本 app 精确增益", 17, true);
        gainPanel.addView(gainTitle, matchWrap());

        gainText = text("", 26, true);
        gainText.setPadding(0, dp(12), 0, dp(4));
        gainPanel.addView(gainText, matchWrap());

        relationText = text("", 15, false);
        relationText.setTextColor(color(R.color.text_secondary));
        relationText.setPadding(0, 0, 0, dp(12));
        gainPanel.addView(relationText, matchWrap());

        gainSlider = new SeekBar(this);
        gainSlider.setMax(GAIN_STEPS);
        gainSlider.setProgress(50);
        gainSlider.setPadding(0, dp(8), 0, dp(8));
        gainSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                preciseGain = progress / (double) GAIN_STEPS;
                VolumeFineTuneService.setTargetPercent(MainActivity.this, (float) preciseGain * 100.0f);
                updateGainLabels();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        gainPanel.addView(gainSlider, matchWrap());

        LinearLayout presets = row();
        presets.addView(presetButton("2.5%", 25), weightedButton());
        presets.addView(presetButton("5%", 50), weightedButton());
        presets.addView(stepButton("-0.1%", -1), weightedButton());
        presets.addView(stepButton("+0.1%", 1), weightedButton());
        gainPanel.addView(presets, matchWrap());

        LinearLayout switchRow = row();
        TextView switchLabel = text("全局微调", 16, true);
        switchRow.addView(switchLabel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        serviceSwitch = new Switch(this);
        serviceSwitch.setText("OFF");
        serviceSwitch.setTextSize(16);
        serviceSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!updatingServiceSwitch) setFineTuneEnabled(isChecked);
        });
        switchRow.addView(serviceSwitch, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        gainPanel.addView(switchRow, matchWrap());

        serviceStatusText = text("", 14, false);
        serviceStatusText.setTextColor(color(R.color.text_secondary));
        serviceStatusText.setPadding(0, dp(12), 0, dp(12));
        gainPanel.addView(serviceStatusText, matchWrap());

        playButton = primaryButton("播放测试音");
        playButton.setOnClickListener(v -> {
            if (playing) stopTone();
            else startTone();
        });
        gainPanel.addView(playButton, matchWrap());

        playStateText = text("播放 440Hz 测试音后，切换 2.5% 和 5% 可验证二者是严格 1:2。", 14, false);
        playStateText.setTextColor(color(R.color.text_secondary));
        playStateText.setPadding(0, dp(12), 0, 0);
        gainPanel.addView(playStateText, matchWrap());

        LinearLayout systemPanel = panel();
        LinearLayout.LayoutParams systemParams = matchWrap();
        systemParams.setMargins(0, dp(16), 0, 0);
        root.addView(systemPanel, systemParams);

        TextView systemTitle = text("系统媒体音量粗调", 17, true);
        systemPanel.addView(systemTitle, matchWrap());

        systemText = text("", 15, false);
        systemText.setTextColor(color(R.color.text_secondary));
        systemText.setPadding(0, dp(10), 0, dp(12));
        systemPanel.addView(systemText, matchWrap());

        systemSlider = new SeekBar(this);
        systemSlider.setPadding(0, dp(8), 0, dp(8));
        systemSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) applySystemVolume(progress);
                updateSystemLabel(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        systemPanel.addView(systemSlider, matchWrap());

        Button refreshButton = secondaryButton("读取当前系统媒体音量");
        refreshButton.setOnClickListener(v -> syncSystemSlider());
        systemPanel.addView(refreshButton, matchWrap());

        TextView note = text("使用方式：先在系统下拉快捷设置里添加“音量微调”Tile，之后可像 v2rayNG 一样从下拉菜单开关。开启后会保留一个低干扰常驻通知，这是安卓前台服务的系统要求。若状态显示“不被此设备支持”，说明这台设备不允许普通 app 挂全局音效链。", 14, false);
        note.setTextColor(color(R.color.text_secondary));
        note.setPadding(0, dp(18), 0, 0);
        root.addView(note, matchWrap());

        setContentView(scrollView);
    }

    private void setFineTuneEnabled(boolean enabled) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] { Manifest.permission.POST_NOTIFICATIONS }, 10);
        }

        if (enabled) {
            VolumeFineTuneService.setTargetPercent(this, (float) preciseGain * 100.0f);
            VolumeFineTuneService.rememberCurrentSystemVolume(this);
            VolumeFineTuneService.start(this);
        } else {
            VolumeFineTuneService.stop(this);
        }
        updateServiceLabels();
    }

    private void startTone() {
        if (playing) return;
        playing = true;
        playButton.setText("停止测试音");
        playStateText.setText("正在播放。当前精确增益会实时作用到本 app 输出的音频样本。");
        audioThread = new Thread(this::runTone, "precise-volume-tone");
        audioThread.start();
    }

    private void stopTone() {
        playing = false;
        if (audioThread != null) {
            try {
                audioThread.join(500);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            audioThread = null;
        }
        releaseAudioTrack();
        if (playButton != null) playButton.setText("播放测试音");
        if (playStateText != null) {
            playStateText.setText("播放 440Hz 测试音后，切换 2.5% 和 5% 可验证二者是严格 1:2。");
        }
    }

    private void runTone() {
        int minBuffer = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
        );
        int bufferFrames = Math.max(1024, minBuffer / 2);
        short[] buffer = new short[bufferFrames];
        double phase = 0.0;
        double phaseStep = 2.0 * Math.PI * TONE_HZ / SAMPLE_RATE;

        AudioTrack track = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build())
                .setBufferSizeInBytes(bufferFrames * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build();

        audioTrack = track;
        track.play();
        while (playing) {
            double gain = preciseGain;
            for (int i = 0; i < buffer.length; i++) {
                double sample = Math.sin(phase) * REFERENCE_AMPLITUDE * gain;
                buffer[i] = (short) Math.round(sample * Short.MAX_VALUE);
                phase += phaseStep;
                if (phase >= 2.0 * Math.PI) phase -= 2.0 * Math.PI;
            }
            track.write(buffer, 0, buffer.length);
        }
        releaseAudioTrack();
    }

    private void releaseAudioTrack() {
        AudioTrack track = audioTrack;
        audioTrack = null;
        if (track == null) return;
        try {
            track.pause();
            track.flush();
        } catch (IllegalStateException ignored) {
        }
        track.release();
    }

    private void updateGainLabels() {
        double percent = preciseGain * 100.0;
        gainText.setText(String.format(Locale.getDefault(), "精确增益：%.1f%%", percent));
        relationText.setText(String.format(
                Locale.getDefault(),
                "相对 5%%：%.2f 倍。开启全局微调后，会把系统音量设到不低于目标的最近挡位，再额外衰减。",
                preciseGain / 0.05
        ));
        updateServiceLabels();
    }

    private void updateServiceLabels() {
        if (serviceSwitch == null || serviceStatusText == null) return;
        boolean enabled = VolumeFineTuneService.isEnabled(this);
        updatingServiceSwitch = true;
        serviceSwitch.setChecked(enabled);
        serviceSwitch.setText(enabled ? "ON" : "OFF");
        updatingServiceSwitch = false;
        serviceStatusText.setText(enabled
                ? VolumeFineTuneService.getLastStatus(this)
                : "全局微调未开启。当前滑杆只影响本 app 测试音。");
    }

    private void syncSystemSlider() {
        int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        systemSlider.setMax(max);
        systemSlider.setProgress(current);
        updateSystemLabel(current);
    }

    private void applySystemVolume(int index) {
        try {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, index, AudioManager.FLAG_SHOW_UI);
        } catch (SecurityException exception) {
            Toast.makeText(this, "系统当前不允许修改媒体音量", Toast.LENGTH_LONG).show();
        }
    }

    private void updateSystemLabel(int index) {
        int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        systemText.setText(String.format(
                Locale.getDefault(),
                "当前系统媒体音量：第 %d / %d 挡。它仍然只能按整数挡位生效。",
                index,
                max
        ));
    }

    private Button presetButton(String label, int progress) {
        Button button = secondaryButton(label);
        button.setOnClickListener(v -> gainSlider.setProgress(progress));
        return button;
    }

    private Button stepButton(String label, int delta) {
        Button button = secondaryButton(label);
        button.setOnClickListener(v -> {
            int next = Math.max(0, Math.min(GAIN_STEPS, gainSlider.getProgress() + delta));
            gainSlider.setProgress(next);
        });
        return button;
    }

    private LinearLayout panel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundResource(R.drawable.panel_bg);
        return panel;
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setPadding(0, dp(10), 0, dp(12));
        return row;
    }

    private Button primaryButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(color(android.R.color.white));
        button.setTextSize(16);
        button.setAllCaps(false);
        button.setBackgroundResource(R.drawable.button_primary);
        button.setPadding(0, dp(10), 0, dp(10));
        return button;
    }

    private Button secondaryButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setBackgroundResource(R.drawable.button_secondary);
        return button;
    }

    private TextView text(String value, int sp, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color(R.color.text_primary));
        if (bold) view.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return view;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    private LinearLayout.LayoutParams weightedButton() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(44), 1);
        params.setMargins(dp(3), 0, dp(3), 0);
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private int color(int id) {
        return getResources().getColor(id, getTheme());
    }
}
