package com.ddkkinf.cookbook;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class KitchenAlarmReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;
        String action = intent.getAction();
        if (KitchenAlarmEngine.ACTION_FIRE_ALARM.equals(action)) {
            KitchenAlarmEngine.getInstance(context).onScheduledTimerExpired();
        } else if (KitchenAlarmEngine.ACTION_DISMISS_ALARM.equals(action)) {
            KitchenAlarmEngine.getInstance(context).stopAlarm(true);
        }
    }
}
