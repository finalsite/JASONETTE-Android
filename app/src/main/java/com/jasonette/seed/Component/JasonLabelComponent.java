package com.jasonette.seed.Component;

import android.content.Context;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;
import com.jasonette.seed.Helper.JasonHelper;
import org.json.JSONObject;

public class JasonLabelComponent {

    public static View build(View view, final JSONObject component, final JSONObject parent, final Context context) {

        try {
            if(view == null || component.getString("text").length() == 0){
                return new TextView(context);
            } else {
                ((TextView)view).setText(component.getString("text"));
                JasonComponent.build(view, component, parent, context);

                String type;
                JSONObject style = JasonHelper.style(component, context);
                type = component.getString("type");

                if (style.has("color")) {
                    int color = JasonHelper.parse_color(style.getString("color"));
                    ((TextView)view).setTextColor(color);
                }

                if (component.has("label")) {
                    view.setContentDescription(component.getString("label").concat(", ").concat(component.getString("text")));
                }

                JasonHelper.setTextViewFont(((TextView)view), style, context);

                int g = 0;
                if (style.has("align")) {
                    String align = style.getString("align");
                    if (align.equalsIgnoreCase("center")) {
                        g = g | Gravity.CENTER_HORIZONTAL;
                        ((TextView) view).setGravity(Gravity.CENTER_HORIZONTAL);
                    } else if (align.equalsIgnoreCase("right")) {
                        g = g | Gravity.RIGHT;
                        ((TextView) view).setGravity(Gravity.RIGHT);
                    } else if (align.equalsIgnoreCase("left")) {
                        g = g | Gravity.LEFT;
                    }

                    if (align.equalsIgnoreCase("top")) {
                        g = g | Gravity.TOP;
                    } else if (align.equalsIgnoreCase("bottom")) {
                        g = g | Gravity.BOTTOM;
                    } else {
                        g = g | Gravity.CENTER_VERTICAL;
                    }
                } else {
                    g = Gravity.CENTER_VERTICAL;
                }

                ((TextView)view).setGravity(g);

                if (component.has("line_limit")) {
                    ((TextView) view).setSingleLine(false);
                    ((TextView) view).setEllipsize(TextUtils.TruncateAt.END);
                    ((TextView) view).setMaxLines(component.getInt("line_limit"));
                }


                if (style.has("size")) {
                    ((TextView)view).setTextSize(Float.parseFloat(style.getString("size")));
                }

                ((TextView)view).setHorizontallyScrolling(false);

                JasonComponent.addListener(view, context);

                view.requestLayout();
                return view;
            }
        } catch (Exception e){
            Log.d("Warning", e.getStackTrace()[0].getMethodName() + " : " + e.toString());
            return new View(context);
        }
    }
}
