package com.jasonette.seed.Action;

import android.content.Context;

import com.jasonette.seed.Core.JasonViewActivity;
import com.jasonette.seed.Helper.JasonHelper;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class JasonTimedAction {
    public void refresh(final JSONObject action, final JSONObject data, final JSONObject event, final Context context) {
        try {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault());
            TimeZone timeZone = TimeZone.getTimeZone(action.getJSONObject("options").getString("time_zone"));

            // Sets the time zone to be the same for the new date and the date we're parsing from our server
            format.setTimeZone(timeZone);

            // Get current time with same timezone
            TimeZone defaultTimezone = timeZone.getDefault();
            TimeZone.setDefault(timeZone);
            Date now = new Date();
            TimeZone.setDefault(defaultTimezone);

            Date loadTime = format.parse(action.getJSONObject("options").getString("load_time"));
            // convert the frequency that we give in minutes to milliseconds since getTime returns milliseconds long
            Long frequency = Long.parseLong(action.getJSONObject("options").getString("frequency")) * 60000;

            // If now - the loadTime of the page is > frequency converted to milliseconds then reload
            if (now.getTime() - loadTime.getTime() > frequency) {
                ((JasonViewActivity) context).currentFragment().reload(action, data, event, context);
            } else {
                JasonHelper.next("success", action, data, event, context);
            }
        } catch (ParseException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }

    }
}
