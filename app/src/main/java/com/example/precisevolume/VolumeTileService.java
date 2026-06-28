package com.example.precisevolume;

import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public class VolumeTileService extends TileService {
    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTile();
    }

    @Override
    public void onClick() {
        super.onClick();
        if (VolumeFineTuneService.isEnabled(this)) {
            VolumeFineTuneService.stop(this);
            updateTile(false);
        } else {
            VolumeFineTuneService.rememberCurrentSystemVolume(this);
            VolumeFineTuneService.start(this);
            updateTile(true);
        }
    }

    private void updateTile() {
        updateTile(VolumeFineTuneService.isEnabled(this));
    }

    private void updateTile(boolean enabled) {
        Tile tile = getQsTile();
        if (tile == null) return;
        tile.setState(enabled ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.setLabel("音量微调");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.setSubtitle(enabled
                    ? String.format("ON %.1f%%", VolumeFineTuneService.getTargetPercent(this))
                    : "OFF");
        }
        tile.updateTile();
    }

    public static void requestTileRefresh(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            TileService.requestListeningState(
                    context,
                    new ComponentName(context, VolumeTileService.class)
            );
        }
    }
}
