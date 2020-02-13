package com.jasonette.seed.Service.push;
import android.util.Log;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.jasonette.seed.Core.JasonViewActivity;
import com.jasonette.seed.Launcher.Launcher;

import org.json.JSONObject;

public class JasonPushRegisterService extends FirebaseMessagingService {
    @Override
    public void onNewToken(String token) {
        super.onNewToken(token);

        if (token != null) {
            try {
                JSONObject payload = new JSONObject();
                payload.put("token", token);
                JSONObject response = new JSONObject();
                response.put("$jason", payload);
                ((JasonViewActivity) Launcher.getCurrentContext()).simple_trigger("$push.onregister", response, Launcher.getCurrentContext());
            } catch (Exception e) {
                Log.d("Warning", e.getStackTrace()[0].getMethodName() + " : " + e.toString());
            }
        }
    }
}
