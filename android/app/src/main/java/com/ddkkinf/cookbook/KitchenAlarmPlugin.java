package com.ddkkinf.cookbook;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.ActivityCompat;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "KitchenAlarm")
public class KitchenAlarmPlugin extends Plugin implements KitchenAlarmEngine.AlarmEventListener {

    private boolean askedNotificationPermOnce = false;

    @Override
    public void load() {
        KitchenAlarmEngine.getInstance(getContext()).setListener(this);
    }

    @Override
    public void onAlarmFired(String label) {
        JSObject payload = new JSObject();
        payload.put("label", label != null ? label : "");
        notifyListeners("alarmFired", payload, true);
    }

    @Override
    public void onAlarmDismissedExternally() {
        JSObject payload = new JSObject();
        payload.put("dismissed", true);
        notifyListeners("alarmDismissedExternally", payload, true);
    }

    private void maybeRequestNotificationPermission() {
        if (askedNotificationPermOnce) return;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;
        Activity act = getActivity();
        if (act == null) return;
        if (act.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            askedNotificationPermOnce = true;
            try {
                ActivityCompat.requestPermissions(act, new String[] { Manifest.permission.POST_NOTIFICATIONS }, 4105);
            } catch (Throwable ignored) {}
        }
    }

    @PluginMethod
    public void syncSchedule(PluginCall call) {
        JSObject data = call.getData();
        long nextEndTimeMs = data != null ? data.optLong("nextEndTimeMs", 0L) : 0L;
        int runningCount = data != null ? data.optInt("runningCount", 0) : 0;
        boolean soundEnabled = data == null || data.optBoolean("soundEnabled", true);
        boolean vibrateEnabled = data == null || data.optBoolean("vibrateEnabled", true);
        String label = call.getString("label", "조리 타이머");

        if (runningCount > 0) {
            maybeRequestNotificationPermission();
        }

        KitchenAlarmEngine.getInstance(getContext()).syncSchedule(
            nextEndTimeMs,
            label,
            runningCount,
            soundEnabled,
            vibrateEnabled
        );
        call.resolve();
    }

    @PluginMethod
    public void startAlarm(PluginCall call) {
        JSObject data = call.getData();
        boolean soundEnabled = data == null || data.optBoolean("soundEnabled", true);
        boolean vibrateEnabled = data == null || data.optBoolean("vibrateEnabled", true);
        String label = call.getString("label", "조리 타이머");

        KitchenAlarmEngine.getInstance(getContext()).startAlarm(label, soundEnabled, vibrateEnabled);
        call.resolve();
    }

    @PluginMethod
    public void stopAlarm(PluginCall call) {
        KitchenAlarmEngine.getInstance(getContext()).stopAlarm(false);
        call.resolve();
    }

    @PluginMethod
    public void playTestChime(PluginCall call) {
        JSObject data = call.getData();
        boolean vibrateEnabled = data == null || data.optBoolean("vibrateEnabled", true);
        KitchenAlarmEngine.getInstance(getContext()).playTestChime(vibrateEnabled);
        call.resolve();
    }
}
