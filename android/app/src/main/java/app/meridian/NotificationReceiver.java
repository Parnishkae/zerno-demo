package app.meridian;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/**
 * Показывает локальное уведомление, когда срабатывает будильник AlarmManager.
 * Работает и когда приложение закрыто.
 */
public class NotificationReceiver extends BroadcastReceiver {

    static final String CHANNEL_ID = "meridian_reminders";

    static void ensureChannel(Context ctx) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm =
                    (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null && nm.getNotificationChannel(CHANNEL_ID) == null) {
                NotificationChannel ch = new NotificationChannel(
                        CHANNEL_ID, "Напоминания Meridian",
                        NotificationManager.IMPORTANCE_DEFAULT);
                ch.setDescription("Плановые платежи, бюджеты и советы");
                nm.createNotificationChannel(ch);
            }
        }
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        ensureChannel(ctx);
        int id = intent.getIntExtra("id", 1);
        String title = intent.getStringExtra("title");
        String text = intent.getStringExtra("text");
        if (title == null) title = "Meridian";
        if (text == null) text = "";

        Intent open = new Intent(ctx, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) piFlags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi = PendingIntent.getActivity(ctx, id, open, piFlags);

        Notification n;
        if (Build.VERSION.SDK_INT >= 26) {
            n = new Notification.Builder(ctx, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setStyle(new Notification.BigTextStyle().bigText(text))
                    .setAutoCancel(true)
                    .setContentIntent(pi)
                    .build();
        } else {
            n = new Notification.Builder(ctx)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setAutoCancel(true)
                    .setContentIntent(pi)
                    .build();
        }

        NotificationManager nm =
                (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(id, n);
    }
}
