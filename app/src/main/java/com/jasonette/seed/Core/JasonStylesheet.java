package com.jasonette.seed.Core;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;

public class JasonStylesheet extends JSONObject {


    public JasonStylesheet(){

    }

    public void merge(JSONObject other) {
        try {
            Iterator other_keys = other.keys();
            String tmp_key;
            while(other_keys.hasNext()) {
                tmp_key = (String) other_keys.next();
                this.put(tmp_key, other.get(tmp_key));
            }
        } catch(JSONException e) {

        }
    }

}
