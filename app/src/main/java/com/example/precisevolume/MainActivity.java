package com.example.precisevolume;

import android.app.Activity;
import android.media.AudioManager;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public class MainActivity extends Activity {
    private static final int PRECISION_STEPS = 1000;

    private AudioManager audioManager;
    private SeekBar preciseSlider;
    private TextView targetText;
    private TextView actualText;
    private TextView limitText;
    private RadioGroup streamGroup;
    private int selectedStream = AudioManager.STREAM_MUSIC;
    private boolean syncingFromSystem;

    private final StreamOption[] streams = new StreamOption[] {
            new StreamOption("媒体", AudioManager.STREAM_MUSIC),
            new StreamOption("铃声", AudioManager.STREAM_RING),
            new StreamOption("通知", AudioManager.STREAM_NOTIFICATION),
            new StreamOption("闹钟", AudioManager.STREAM_ALARM),
            new StreamOption("通话", AudioManager.STREAM_VOICE_CALL),
            new StreamOption("系统", AudioManager.STREAM_SYSTEM)
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        setVolumeControlStream(selectedStream);
        buildUi();
        syncSliderFromSystem();
    }

    private void buildUi() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(color(android.R.color.background_light));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(24));
        scrollView.addView(root, matchWrap());

        TextView title = text("精细音量", 28, true);
        root.addView(title, matchWrap());

        TextView subtitle = text("用百分比精细选择目标值，再应用到安卓允许的最近系统挡位。", 15, false);
        subtitle.setTextColor(color(R.color.text_secondary));
        subtitle.setPadding(0, dp(8), 0, dp(18));
        root.addView(subtitle, matchWrap());

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundResource(R.drawable.panel_bg);
        root.addView(panel, matchWrap());

        TextView streamLabel = text("音频通道", 16, true);
        panel.addView(streamLabel, matchWrap());

        streamGroup = new RadioGroup(this);
        streamGroup.setOrientation(RadioGroup.VERTICAL);
        streamGroup.setPadding(0, dp(8), 0, dp(14));
        for (int i = 0; i < streams.length; i++) {
            RadioButton button = new RadioButton(this);
            button.setId(i + 100);
            button.setText(streams[i].label);
            button.setTextSize(16);
            button.setTextColor(color(R.color.text_primary));
            button.setPadding(0, dp(4), 0, dp(4));
            streamGroup.addView(button, matchWrap());
        }
        streamGroup.check(100);
        streamGroup.setOnCheckedChangeListener((group, checkedId) -> {
            int index = checkedId - 100;
            if (index >= 0 && index < streams.length) {
                selectedStream = streams[index].stream;
                setVolumeControlStream(selectedStream);
                syncSliderFromSystem();
            }
        });
        panel.addView(streamGroup, matchWrap());

        targetText = text("", 22, true);
        panel.addView(targetText, matchWrap());

        actualText = text("", 15, false);
        actualText.setTextColor(color(R.color.text_secondary));
        actualText.setPadding(0, dp(6), 0, dp(12));
        panel.addView(actualText, matchWrap());

        preciseSlider = new SeekBar(this);
        preciseSlider.setMax(PRECISION_STEPS);
        preciseSlider.setPadding(0, dp(8), 0, dp(8));
        preciseSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!syncingFromSystem) updateLabels(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        panel.addView(preciseSlider, matchWrap());

        LinearLayout fineControls = new LinearLayout(this);
        fineControls.setOrientation(LinearLayout.HORIZONTAL);
        fineControls.setGravity(Gravity.CENTER);
        fineControls.setPadding(0, dp(10), 0, dp(12));
        fineControls.addView(stepButton("-0.5%", -5), weightedButton());
        fineControls.addView(stepButton("-0.1%", -1), weightedButton());
        fineControls.addView(stepButton("+0.1%", 1), weightedButton());
        fineControls.addView(stepButton("+0.5%", 5), weightedButton());
        panel.addView(fineControls, matchWrap());

        Button applyButton = new Button(this);
        applyButton.setText("应用到系统音量");
        applyButton.setTextColor(color(android.R.color.white));
        applyButton.setTextSize(16);
        applyButton.setAllCaps(false);
        applyButton.setBackgroundResource(R.drawable.button_primary);
        applyButton.setPadding(0, dp(10), 0, dp(10));
        applyButton.setOnClickListener(v -> applyVolume());
        panel.addView(applyButton, matchWrap());

        Button refreshButton = new Button(this);
        refreshButton.setText("读取当前系统音量");
        refreshButton.setTextSize(15);
        refreshButton.setAllCaps(false);
        refreshButton.setBackgroundResource(R.drawable.button_secondary);
        refreshButton.setPadding(0, dp(10), 0, dp(10));
        LinearLayout.LayoutParams refreshParams = matchWrap();
        refreshParams.setMargins(0, dp(10), 0, 0);
        refreshButton.setOnClickListener(v -> syncSliderFromSystem());
        panel.addView(refreshButton, refreshParams);

        limitText = text("说明：安卓公开接口只能设置整数挡位。本 app 可以精确输入目标百分比，但全局系统音量会落到最接近的挡位。", 14, false);
        limitText.setTextColor(color(R.color.text_secondary));
        limitText.setPadding(0, dp(18), 0, 0);
        root.addView(limitText, matchWrap());

        setContentView(scrollView);
    }

    private Button stepButton(String label, int delta) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(13);
        button.setAllCaps(false);
        button.setBackgroundResource(R.drawable.button_secondary);
        button.setOnClickListener(v -> {
            int next = Math.max(0, Math.min(PRECISION_STEPS, preciseSlider.getProgress() + delta));
            preciseSlider.setProgress(next);
            updateLabels(next);
        });
        return button;
    }

    private void syncSliderFromSystem() {
        syncingFromSystem = true;
        int max = Math.max(1, audioManager.getStreamMaxVolume(selectedStream));
        int current = audioManager.getStreamVolume(selectedStream);
        int progress = Math.round((current * PRECISION_STEPS) / (float) max);
        preciseSlider.setProgress(progress);
        syncingFromSystem = false;
        updateLabels(progress);
    }

    private void applyVolume() {
        int max = Math.max(1, audioManager.getStreamMaxVolume(selectedStream));
        int targetIndex = progressToSystemIndex(preciseSlider.getProgress(), max);
        try {
            audioManager.setStreamVolume(selectedStream, targetIndex, AudioManager.FLAG_SHOW_UI);
        } catch (SecurityException exception) {
            Toast.makeText(this, "系统当前不允许修改这个通道，可能受勿扰模式限制", Toast.LENGTH_LONG).show();
            return;
        }
        updateLabels(preciseSlider.getProgress());
        Toast.makeText(this, "已应用到第 " + targetIndex + " / " + max + " 挡", Toast.LENGTH_SHORT).show();
    }

    private void updateLabels(int progress) {
        int max = Math.max(1, audioManager.getStreamMaxVolume(selectedStream));
        int targetIndex = progressToSystemIndex(progress, max);
        float targetPercent = progress / 10f;
        float actualPercent = targetIndex * 100f / max;

        targetText.setText(String.format(Locale.getDefault(), "目标：%.1f%%", targetPercent));
        actualText.setText(String.format(
                Locale.getDefault(),
                "将应用为系统第 %d / %d 挡，实际约 %.1f%%",
                targetIndex,
                max,
                actualPercent
        ));
    }

    private int progressToSystemIndex(int progress, int max) {
        return Math.max(0, Math.min(max, Math.round(progress * max / (float) PRECISION_STEPS)));
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

    private static class StreamOption {
        final String label;
        final int stream;

        StreamOption(String label, int stream) {
            this.label = label;
            this.stream = stream;
        }
    }
}
