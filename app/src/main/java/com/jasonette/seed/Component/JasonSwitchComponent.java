package com.jasonette.seed.Component;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.Switch;

import com.jasonette.seed.Core.JasonViewActivity;
import com.jasonette.seed.Helper.JasonHelper;

import org.json.JSONObject;


public class JasonSwitchComponent {
    public static View build(View view, final JSONObject component, final JSONObject parent, final Context context) {
        if(view == null) {
            return new Switch(context);
        } else {
            try {
                view = JasonComponent.build(view, component, parent, context);
                final Switch aSwitch = ((Switch) view);

                Boolean checked = false;
                if(component.has("name")){
                    if(((JasonViewActivity) context).model.var.has(component.getString("name"))){
                        checked = ((JasonViewActivity) context).model.var.getBoolean(component.getString("name"));
                    } else {
                        if(component.has("value")){
                            checked = component.getBoolean("value");
                        }
                    }
                }
                final JSONObject style = JasonHelper.style(component, context);
                aSwitch.setChecked(checked);
                aSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        onChange(aSwitch, isChecked, style, context);
                    }
                });
                changeColor(aSwitch, checked, style);
                view.requestLayout();
                return view;
            } catch (Exception e){
                Log.d("Warning", e.getStackTrace()[0].getMethodName() + " : " + e.toString());
                return new View(context);
            }
        }
    }

    public static void onChange(Switch view, boolean isChecked, JSONObject style, Context root_context) {
        changeColor(view, isChecked, style);
        JSONObject component = (JSONObject)view.getTag();
        try {
            ((JasonViewActivity) root_context).model.var.put(component.getString("name"), view.isChecked());
            if (component.has("action")) {
                JSONObject action = component.getJSONObject("action");
                ((JasonViewActivity) root_context).call(action.toString(), new JSONObject().toString(), "{}", view.getContext());
            }
        } catch (Exception e) {
            Log.d("Warning", e.getStackTrace()[0].getMethodName() + " : " + e.toString());
        }
    }
    public static void changeColor(Switch s, boolean isChecked, JSONObject style) {
        try {
            int thumbColor;
            int trackColor;

            if(isChecked) {
                if (style.has("color")) {
                    thumbColor = JasonHelper.parse_color(style.getString("color"));
                    trackColor = thumbColor;
                } else {
                    thumbColor = JasonHelper.parse_color("#009186");
                    trackColor = thumbColor;
                }
                s.getThumbDrawable().setColorFilter(thumbColor, PorterDuff.Mode.MULTIPLY);
                s.getTrackDrawable().setColorFilter(trackColor, PorterDuff.Mode.MULTIPLY);
            } else {
                if (style.has("color:disabled")) {
                    thumbColor = JasonHelper.parse_color(style.getString("color:disabled"));
                    trackColor = thumbColor;
                } else {
                    thumbColor = JasonHelper.parse_color("#EFEFEF");
                    trackColor = Color.BLACK;
                }
                s.getThumbDrawable().setColorFilter(thumbColor, PorterDuff.Mode.MULTIPLY);
                s.getTrackDrawable().setColorFilter(trackColor, PorterDuff.Mode.SRC_ATOP);
            }
        } catch (Exception e) {
            Log.d("Warning", e.getStackTrace()[0].getMethodName() + " : " + e.toString());
        }
    }
}
