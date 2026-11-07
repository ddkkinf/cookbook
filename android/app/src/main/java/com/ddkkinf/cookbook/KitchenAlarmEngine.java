package com.ddkkinf.cookbook;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

public class KitchenAlarmEngine {

    public interface AlarmEventListener {
        void onAlarmFired(String label);
        void onAlarmDismissedExternally();
    }

    public static final String ACTION_FIRE_ALARM = "com.ddkkinf.cookbook.ACTION_FIRE_KITCHEN_ALARM";
    public static final String ACTION_DISMISS_ALARM = "com.ddkkinf.cookbook.ACTION_DISMISS_KITCHEN_ALARM";
    private static final String CHANNEL_ID = "kitchen_timer_alarm_v1";
    private static final int NOTIFICATION_ID = 4102;
    private static final int SAMPLE_RATE = 44100;
    private static final int MAX_ALARM_PULSES = 20;
    private static final long PULSE_INTERVAL_MS = 1350L;

    private static KitchenAlarmEngine instance;

    public static synchronized KitchenAlarmEngine getInstance(Context context) {
        if (instance == null) {
            instance = new KitchenAlarmEngine(context.getApplicationContext());
        }
        return instance;
    }

    private final Context appContext;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AudioManager audioManager;
    private final AlarmManager alarmManager;
    private final NotificationManager notificationManager;
    private final PowerManager powerManager;
    private final byte[] chimePcmBytes;
    private final AudioAttributes alarmAudioAttributes;

    private PowerManager.WakeLock wakeLock;
    private AudioFocusRequest audioFocusRequest;
    private AudioTrack activeTrack;
    private AlarmEventListener listener;

    private boolean isRinging = false;
    private int pulseCount = 0;
    private int savedAlarmVolume = -1;

    private long scheduledEndTimeMs = 0L;
    private String scheduledLabel = "조리 타이머";
    private boolean soundEnabled = true;
    private boolean vibrateEnabled = true;
    private String activeRingingLabel = "조리 타이머";

    private final Runnable countdownRunnable = new Runnable() {
        @Override
        public void run() {
            onScheduledTimerExpired();
        }
    };

    private final Runnable pulseRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isRinging) return;
            if (pulseCount >= MAX_ALARM_PULSES) {
                stopAlarm(false);
                return;
            }
            pulseCount++;
            playSinglePulse(soundEnabled, vibrateEnabled);
            mainHandler.postDelayed(this, PULSE_INTERVAL_MS);
        }
    };

    private KitchenAlarmEngine(Context context) {
        this.appContext = context;
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        this.alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        this.notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        this.powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        this.alarmAudioAttributes = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build();
        this.chimePcmBytes = synthesizeKitchenChimePcm();
        ensureNotificationChannel();
    }

    public void setListener(AlarmEventListener listener) {
        this.listener = listener;
    }

    private byte[] synthesizeKitchenChimePcm() {
        final double durationSec = 0.72;
        final int totalSamples = (int) (SAMPLE_RATE * durationSec);
        final byte[] pcm = new byte[totalSamples * 2];

        // Crisp 4-beep kitchen alarm pattern (A5 - A5 - E6 - E6)
        final double[][] notes = new double[][] {
            { 880.0,   0.00, 0.11 },
            { 880.0,   0.15, 0.26 },
            { 1318.51, 0.31, 0.43 },
            { 1318.51, 0.47, 0.68 }
        };

        for (int i = 0; i < totalSamples; i++) {
            double t = (double) i / SAMPLE_RATE;
            double sample = 0.0;

            for (double[] note : notes) {
                double freq = note[0];
                double start = note[1];
                double end = note[2];
                if (t >= start && t <= end) {
                    double localT = t - start;
                    double noteLen = end - start;
                    double env = 1.0;
                    if (localT < 0.008) {
                        env = localT / 0.008;
                    } else if (noteLen - localT < 0.020) {
                        env = Math.max(0.0, (noteLen - localT) / 0.020);
                    }
                    double fundamental = Math.sin(2.0 * Math.PI * freq * localT);
                    double overtone = Math.sin(2.0 * Math.PI * (freq * 2.0) * localT);
                    sample = (0.76 * fundamental + 0.20 * overtone) * env;
                    break;
                }
            }

            int val = (int) Math.round(Math.max(-1.0, Math.min(1.0, sample)) * 30000.0);
            pcm[i * 2] = (byte) (val & 0xFF);
            pcm[i * 2 + 1] = (byte) ((val >> 8) & 0xFF);
        }
        return pcm;
    }

    private void ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && notificationManager != null) {
            NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID,
                "조리 타이머 알람",
                NotificationManager.IMPORTANCE_HIGH
            );
            ch.setDescription("요리 타이머 종료 시 알람 및 끄기 버튼을 표시합니다.");
            ch.setSound(null, null); // Sound is played directly via USAGE_ALARM AudioTrack
            ch.enableVibration(false);
            notificationManager.createNotificationChannel(ch);
        }
    }

    private void acquireTimedWakeLock(long timeoutMs) {
        if (powerManager == null) return;
        try {
            if (wakeLock == null) {
                wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "cookbook:KitchenTimerWakeLock"
                );
                wakeLock.setReferenceCounted(false);
            }
            wakeLock.acquire(Math.max(5000L, timeoutMs));
        } catch (Throwable ignored) {}
    }

    private void releaseWakeLockIfIdle() {
        if (isRinging) return;
        if (scheduledEndTimeMs > System.currentTimeMillis()) return;
        try {
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
            }
        } catch (Throwable ignored) {}
    }

    private void requestAlarmAudioFocus() {
        if (audioManager == null) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (audioFocusRequest == null) {
                    audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                        .setAudioAttributes(alarmAudioAttributes)
                        .setAcceptsDelayedFocusGain(false)
                        .setOnAudioFocusChangeListener(focusChange -> {})
                        .build();
                }
                audioManager.requestAudioFocus(audioFocusRequest);
            } else {
                //noinspection deprecation
                audioManager.requestAudioFocus(
                    null,
                    AudioManager.STREAM_ALARM,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                );
            }
        } catch (Throwable ignored) {}
    }

    private void abandonAlarmAudioFocus() {
        if (audioManager == null) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (audioFocusRequest != null) {
                    audioManager.abandonAudioFocusRequest(audioFocusRequest);
                }
            } else {
                //noinspection deprecation
                audioManager.abandonAudioFocus(null);
            }
        } catch (Throwable ignored) {}
    }

    private void ensureAudibleAlarmStreamVolume() {
        if (audioManager == null) return;
        try {
            int maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM);
            int curVol = audioManager.getStreamVolume(AudioManager.STREAM_ALARM);
            int minTargetVol = Math.max(1, (int) Math.ceil(maxVol * 0.70));
            if (curVol < minTargetVol) {
                if (savedAlarmVolume < 0) {
                    savedAlarmVolume = curVol;
                }
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, minTargetVol, 0);
            }
        } catch (Throwable ignored) {}
    }

    private void restoreAlarmStreamVolume() {
        if (audioManager == null || savedAlarmVolume < 0) return;
        try {
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, savedAlarmVolume, 0);
        } catch (Throwable ignored) {
        } finally {
            savedAlarmVolume = -1;
        }
    }

    private void releaseActiveTrack() {
        if (activeTrack != null) {
            try {
                if (activeTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                    activeTrack.stop();
                }
            } catch (Throwable ignored) {}
            try {
                activeTrack.release();
            } catch (Throwable ignored) {}
            activeTrack = null;
        }
    }

    private Vibrator getSystemVibrator() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager vm = (VibratorManager) appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            if (vm != null) return vm.getDefaultVibrator();
        }
        //noinspection deprecation
        return (Vibrator) appContext.getSystemService(Context.VIBRATOR_SERVICE);
    }

    private void triggerAlarmVibration() {
        try {
            Vibrator vib = getSystemVibrator();
            if (vib == null || !vib.hasVibrator()) return;
            long[] pattern = new long[] { 0, 280, 120, 280, 120, 420 };
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                VibrationEffect effect = VibrationEffect.createWaveform(pattern, -1);
                VibrationAttributes attrs = VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM);
                vib.vibrate(effect, attrs);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                VibrationEffect effect = VibrationEffect.createWaveform(pattern, -1);
                //noinspection deprecation
                vib.vibrate(effect, alarmAudioAttributes);
            } else {
                //noinspection deprecation
                vib.vibrate(pattern, -1);
            }
        } catch (Throwable ignored) {}
    }

    private void cancelVibration() {
        try {
            Vibrator vib = getSystemVibrator();
            if (vib != null) vib.cancel();
        } catch (Throwable ignored) {}
    }

    private void playSinglePulse(boolean playSound, boolean vibrate) {
        releaseActiveTrack();
        if (playSound) {
            try {
                AudioFormat format = new AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build();

                activeTrack = new AudioTrack.Builder()
                    .setAudioAttributes(alarmAudioAttributes)
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(chimePcmBytes.length)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build();

                activeTrack.write(chimePcmBytes, 0, chimePcmBytes.length);
                activeTrack.play();
            } catch (Throwable ignored) {}
        }
        if (vibrate) {
            triggerAlarmVibration();
        }
    }

    private PendingIntent buildBroadcastPendingIntent(String action, int reqCode, int flags) {
        Intent intent = new Intent(appContext, KitchenAlarmReceiver.class);
        intent.setAction(action);
        int mutability = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0;
        return PendingIntent.getBroadcast(appContext, reqCode, intent, flags | mutability);
    }

    private void cancelSystemAlarmManager() {
        if (alarmManager == null) return;
        try {
            PendingIntent pi = buildBroadcastPendingIntent(ACTION_FIRE_ALARM, 9001, PendingIntent.FLAG_NO_CREATE);
            if (pi != null) {
                alarmManager.cancel(pi);
                pi.cancel();
            }
        } catch (Throwable ignored) {}
    }

    private void scheduleSystemAlarmManager(long triggerAtMillis) {
        if (alarmManager == null) return;
        try {
            PendingIntent pi = buildBroadcastPendingIntent(ACTION_FIRE_ALARM, 9001, PendingIntent.FLAG_UPDATE_CURRENT);
            boolean canExact = true;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                canExact = alarmManager.canScheduleExactAlarms();
            }
            if (canExact) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi);
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi);
            }
        } catch (Throwable ignored) {}
    }

    private void postAlarmNotification(String label) {
        if (notificationManager == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (appContext.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                return;
            }
        }
        try {
            int imm = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0;
            Intent openIntent = new Intent(appContext, MainActivity.class);
            openIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent openPi = PendingIntent.getActivity(
                appContext,
                9002,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | imm
            );

            PendingIntent dismissPi = buildBroadcastPendingIntent(
                ACTION_DISMISS_ALARM,
                9003,
                PendingIntent.FLAG_UPDATE_CURRENT
            );

            Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(appContext, CHANNEL_ID)
                : new Notification.Builder(appContext);

            Notification.Action stopAction = new Notification.Action.Builder(
                android.R.drawable.ic_lock_silent_mode_off,
                "알람 끄기",
                dismissPi
            ).build();

            builder
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("🔔 조리 시간 완료!")
                .setContentText((label != null && !label.isEmpty()) ? label : "설정한 요리 타이머가 완료되었습니다.")
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setOngoing(true)
                .setContentIntent(openPi)
                .setDeleteIntent(dismissPi)
                .addAction(stopAction);

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                //noinspection deprecation
                builder.setPriority(Notification.PRIORITY_MAX);
            }

            notificationManager.notify(NOTIFICATION_ID, builder.build());
        } catch (Throwable ignored) {}
    }

    private void cancelAlarmNotification() {
        if (notificationManager == null) return;
        try {
            notificationManager.cancel(NOTIFICATION_ID);
        } catch (Throwable ignored) {}
    }

    public void syncSchedule(
        final long nextEndTimeMs,
        final String label,
        final int runningCount,
        final boolean soundOn,
        final boolean vibrateOn
    ) {
        mainHandler.post(() -> {
            this.soundEnabled = soundOn;
            this.vibrateEnabled = vibrateOn;
            mainHandler.removeCallbacks(countdownRunnable);
            cancelSystemAlarmManager();

            long now = System.currentTimeMillis();
            if (runningCount > 0 && nextEndTimeMs > 0L) {
                this.scheduledEndTimeMs = nextEndTimeMs;
                this.scheduledLabel = (label != null && !label.isEmpty()) ? label : "조리 타이머";
                long delayMs = Math.max(0L, nextEndTimeMs - now);
                acquireTimedWakeLock(delayMs + 40_000L);
                mainHandler.postDelayed(countdownRunnable, delayMs);
                scheduleSystemAlarmManager(Math.max(now + 250L, nextEndTimeMs));
            } else {
                this.scheduledEndTimeMs = 0L;
                releaseWakeLockIfIdle();
            }
        });
    }

    public void onScheduledTimerExpired() {
        mainHandler.post(() -> {
            if (scheduledEndTimeMs <= 0L) return;
            long now = System.currentTimeMillis();
            if (scheduledEndTimeMs - now > 400L) {
                // Fired slightly early; wait remaining ms
                mainHandler.removeCallbacks(countdownRunnable);
                mainHandler.postDelayed(countdownRunnable, scheduledEndTimeMs - now);
                return;
            }
            String firedLabel = this.scheduledLabel;
            this.scheduledEndTimeMs = 0L;
            cancelSystemAlarmManager();
            startAlarmInternal(firedLabel, this.soundEnabled, this.vibrateEnabled, true);
            if (listener != null) {
                listener.onAlarmFired(firedLabel);
            }
        });
    }

    public void startAlarm(final String label, final boolean soundOn, final boolean vibrateOn) {
        mainHandler.post(() -> startAlarmInternal(label, soundOn, vibrateOn, true));
    }

    private void startAlarmInternal(String label, boolean soundOn, boolean vibrateOn, boolean showNotif) {
        this.soundEnabled = soundOn;
        this.vibrateEnabled = vibrateOn;
        this.activeRingingLabel = (label != null && !label.isEmpty()) ? label : "조리 타이머";

        acquireTimedWakeLock(38_000L);

        if (!soundOn && !vibrateOn) {
            if (showNotif) postAlarmNotification(activeRingingLabel);
            return;
        }

        if (soundOn) {
            requestAlarmAudioFocus();
            ensureAudibleAlarmStreamVolume();
        }

        if (showNotif) {
            postAlarmNotification(activeRingingLabel);
        }

        if (isRinging) {
            // Already ringing — reset pulse budget so newly finished timer gets full alert duration
            pulseCount = 0;
            return;
        }

        isRinging = true;
        pulseCount = 0;
        mainHandler.removeCallbacks(pulseRunnable);
        pulseRunnable.run();
    }

    public void stopAlarm(final boolean notifyExternal) {
        mainHandler.post(() -> {
            isRinging = false;
            pulseCount = 0;
            mainHandler.removeCallbacks(pulseRunnable);
            releaseActiveTrack();
            cancelVibration();
            abandonAlarmAudioFocus();
            restoreAlarmStreamVolume();
            cancelAlarmNotification();
            releaseWakeLockIfIdle();
            if (notifyExternal && listener != null) {
                listener.onAlarmDismissedExternally();
            }
        });
    }

    public void playTestChime(final boolean vibrateOn) {
        mainHandler.post(() -> {
            if (isRinging) return;
            requestAlarmAudioFocus();
            ensureAudibleAlarmStreamVolume();
            playSinglePulse(true, vibrateOn);
            mainHandler.postDelayed(() -> {
                if (!isRinging) {
                    releaseActiveTrack();
                    abandonAlarmAudioFocus();
                    restoreAlarmStreamVolume();
                }
            }, 1100L);
        });
    }
}
