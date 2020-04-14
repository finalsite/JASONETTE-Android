package com.jasonette.seed.Action;

import android.content.Context;
import com.jasonette.seed.Helper.JasonHelper;
import org.json.JSONObject;

public class JasonNotificationAction {
    private static final String CHANNEL_ID = "default";

    public void register(final JSONObject action, JSONObject data, final JSONObject event, Context context) {
        // needs implementation
        JasonHelper.next("success", action, data, event, context);
    }

    public void icon_badge_set(final JSONObject action, JSONObject data, final JSONObject event, Context context) {
        // needs implementation
        JasonHelper.next("success", action, data, event, context);
    }
}
