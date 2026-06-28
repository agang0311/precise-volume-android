package com.example.precisevolume;

import android.app.Activity;
import android.os.Bundle;

public class TileToggleActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (VolumeFineTuneService.isEnabled(this)) {
            VolumeFineTuneService.stop(this);
        } else {
            VolumeFineTuneService.rememberCurrentSystemVolume(this);
            VolumeFineTuneService.start(this);
        }
        finish();
        overridePendingTransition(0, 0);
    }
}
