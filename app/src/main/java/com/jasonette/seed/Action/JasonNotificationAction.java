package com.jasonette.seed.Action;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import androidx.annotation.RequiresApi;
import com.jasonette.seed.Core.JasonViewActivity;
import com.jasonette.seed.Helper.JasonHelper;
import com.jasonette.seed.R;

import org.json.JSONException;
import org.json.JSONObject;

public class JasonNotificationAction {

    private static final int NOTIFICATION_ID = 10;

    public void register(final JSONObject action, JSONObject data, final JSONObject event, Context context) {
        // needs implementation
        JasonHelper.next("success", action, data, event, context);
    }

    public void icon_badge_set(final JSONObject action, JSONObject data, final JSONObject event, Context context) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            try {
                int count = action.getJSONObject("options").getInt("count");

                if (count > 0) {
                    Notification notification = null;

                    notification = new Notification.Builder(context, JasonViewActivity.CHANNEL_ID_LOW)
                            .setContentTitle("New Notifications")
                            .setContentText(String.format("You have %s unread notifications.", count))
                            .setSmallIcon(R.drawable.ic_launcher_foreground)
                            .setNumber(count)
                            .build();

                    notificationManager.notify(NOTIFICATION_ID, notification);
                } else {
                    notificationManager.cancel(NOTIFICATION_ID);
                }
            } catch (JSONException e) {
                notificationManager.cancel(NOTIFICATION_ID);
                e.printStackTrace();
            }
        }

        JasonHelper.next("success", action, data, event, context);
    }
}
