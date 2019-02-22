package com.jasonette.seed.Core;

import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import android.util.Base64;
import android.util.Log;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import com.aurelhubert.ahbottomnavigation.AHBottomNavigation;
import com.aurelhubert.ahbottomnavigation.AHBottomNavigationItem;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.animation.GlideAnimation;
import com.bumptech.glide.request.target.SimpleTarget;
import com.jasonette.seed.Component.JasonComponentFactory;
import com.jasonette.seed.Component.JasonImageComponent;
import com.jasonette.seed.Helper.JasonHelper;
import com.jasonette.seed.Service.vision.JasonVisionService;
import com.jasonette.seed.Lib.JasonToolbar;
import com.jasonette.seed.Lib.MaterialBadgeTextView;
import com.jasonette.seed.R;
import com.jasonette.seed.Section.ItemAdapter;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.bumptech.glide.Glide.with;
import static java.lang.Integer.parseInt;

public class JasonViewActivity extends AppCompatActivity implements ActivityCompat.OnRequestPermissionsResultCallback, FragmentManager.OnBackStackChangedListener{
    private JasonToolbar toolbar;
    private RecyclerView listView;
    public String url;
    public JasonModel model;
    public JSONObject preload;
    public Integer depth;

    public boolean loaded;
    private boolean resumed;

    private int header_height;
    private ImageView logoView;
    private SparseArray<AHBottomNavigationItem> bottomNavigationItems;
    public HashMap<String, Object> modules;
    public JasonVisionService cameraManager;
    private AHBottomNavigation bottomNavigation;
    private LinearLayout footerInput;
    private View footer_input_textfield;
    private SearchView searchView;
    private ArrayList<JSONObject> event_queue;

    public View focusView = null;

    public JSONObject agents = new JSONObject();
    private boolean isexecuting = false;

    /*************************************************************
     *
     * JASON ACTIVITY LIFECYCLE MANAGEMENT
     *
     ************************************************************/

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        loaded = false;
        event_queue = new ArrayList<>();

        // Initialize Parser instance
        JasonParser.getInstance(this);

        // Setup Layouts
        setContentView(R.layout.jason_view_activity_layout);

        // 1. Create root layout (Relative Layout)
        getSupportFragmentManager().addOnBackStackChangedListener(this);
        dispatchFragment(getIntent(), false);

        // 3. Create body.header
        if (toolbar == null) {
            toolbar = new JasonToolbar(this);
            setSupportActionBar(toolbar);
            getSupportActionBar().setTitle("");
        } else {
            setSupportActionBar(toolbar);
        }
        ((LinearLayout) findViewById(R.id.jason_header_container)).addView(toolbar);
        depth = getIntent().getIntExtra("depth", 0);
    }

    @Override
    public void onBackStackChanged() {
        shouldDisplayHomeUp();
    }

    public void shouldDisplayHomeUp() {
        //Enable Up button only if there are entries in the back stack
        boolean canGoBack = getSupportFragmentManager().getBackStackEntryCount() > 1;
        getSupportActionBar().setDisplayHomeAsUpEnabled(canGoBack);
    }

    @Override
    public void onBackPressed() {
        FragmentManager fragmentManager = getSupportFragmentManager();
        if (fragmentManager.getBackStackEntryCount() == 1) {
            finish();
        } else {
            super.onBackPressed();
            // Because the view activity still has a fragment we should call onResume on it
            currentFragment().onResume();
        }
    }

    public FrameLayout getFragmentContainer(){
        return findViewById(R.id.jason_fragment_container);
    }

    public HashMap<String, Object> createModules() {
        modules = new HashMap<String, Object>();
        return modules;
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    public boolean onSupportNavigateUp() {
        FragmentManager fragmentManager = getSupportFragmentManager();
        // Call immediate pop so that we can grab the proper currentFragment below for calling onResume
        fragmentManager.popBackStackImmediate();
        // Because the view activity will still have a fragment we should call onResume on it
        currentFragment().onResume();
        return true;
    }

    public JasonFragment currentFragment() {
        return (JasonFragment) getSupportFragmentManager().findFragmentById(R.id.jason_fragment_container);
    }

    public JasonModel createModel(String url, Intent intent) {
        model = new JasonModel(url, intent, this);
        return model;
    }

    public JasonModel setModel(JasonModel m) {
        model = m;
        return model;
    }

    // This gets executed automatically when an external intent returns with result
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        // We want to have the currentFragment handle the intent_to_resolve logic for it's onResume function.
        currentFragment().onActivityResult(requestCode, resultCode, intent);
    }

    /*************************************************************
     *
     * JASON ACTION DISPATCH
     *
     ************************************************************/

    // How action calls work:
    // 1. First need to resolve the action in case the root level is an array => This means it's an if statment and needs to be parsed once before going forward.
    //      if array => "call" method parses the action once and then calls final_call
    //      else     => "call" immediately calls final_call
    // 2. Then need to parse the "option" part of the action, so that the options will have been filled in. (But NOT success and error, since they need to be parsed AFTER the current action is over)
    //
    // 3. Only then, we actually make the invocation.

    //public void call(final Object action, final JSONObject data, final Context context) {
    public void call(final String action_json, final String data_json, final String event_json, final Context context) {
        try {
            Object action = JasonHelper.objectify(action_json);
            final JSONObject data = (JSONObject)JasonHelper.objectify(data_json);

            JSONObject ev;
            try {
                ev = (JSONObject) JasonHelper.objectify(event_json);
            } catch (Exception e){
                ev = new JSONObject();
            }
            final JSONObject event = ev;

            model.set("state", (JSONObject)data);

            if (action instanceof JSONArray) {
                // resolve
                JasonParser.getInstance(this).setParserListener(new JasonParser.JasonParserListener() {
                    @Override
                    public void onFinished(JSONObject reduced_action) {
                        final_call(reduced_action, data, event, context);
                    }
                });

                JasonParser.getInstance(this).parse("json", model.state, action, context);
            } else {
                final_call((JSONObject)action, data, event, context);
            }
        } catch (Exception e) {
            Log.d("Warning", e.getStackTrace()[0].getMethodName() + " : " + e.toString());
        }
    };
    private void final_call(final JSONObject action, final JSONObject data, final JSONObject event, final Context context) {

        try {
            if (action.toString().equalsIgnoreCase("{}")) {
                // no action to execute
                unlock(new JSONObject(), new JSONObject(), new JSONObject(), context);
                return;
            }

            // Handle trigger first
            if (action.has("trigger")) {
                trigger(action, data, event, context);
            } else if (action.has("options")) {
                // if action has options, we need to parse out the options first
                Object options = action.get("options");
                JasonParser.getInstance(this).setParserListener(new JasonParser.JasonParserListener() {
                    @Override
                    public void onFinished(JSONObject parsed_options) {
                        try {
                            JSONObject action_with_parsed_options = new JSONObject(action.toString());
                            action_with_parsed_options.put("options", parsed_options);
                            exec(action_with_parsed_options, model.state, event, context);
                        } catch (Exception e) {
                            Log.d("Warning", e.getStackTrace()[0].getMethodName() + " : " + e.toString());
                        }
                    }
                });
                JasonParser.getInstance(this).parse("json", model.state, options, context);
            } else if (action.length() > 0) {
                // otherwise we can just call immediately
                exec(action, model.state, event, context);
            }
        } catch (Exception e) {
            Log.d("Warning", e.getStackTrace()[0].getMethodName() + " : " + e.toString());
        }
    }

    private void trigger(final JSONObject action, final JSONObject data, final JSONObject event, final Context context) {

        /****************************************************************************************

         This method is a syntactic sugar for calling a $lambda action.
         The syntax is as follows:

         {
         "trigger": "twitter.get",
         "options": {
         "endpoint": "timeline"
         },
         "success": {
         "type": "$render"
         },
         "error": {
         "type": "$util.toast",
         "options": {
         "text": "Uh oh. Something went wrong"
         }
         }
         }

         Above is a syntactic sugar for the below "$lambda" type action call:

         $lambda action is a special purpose action that triggers another action by name and waits until it returns.
         This way we can define a huge size action somewhere and simply call them as a subroutine and wait for its return value.
         When the subroutine (the action that was triggered by name) returns via `"type": "$return.success"` action,
         the $lambda action picks off where it left off and starts executing its "success" action with the value returned from the subroutine.

         Notice that:
         1. we get rid of the "trigger" field and turn it into a regular action of `"type": "$lambda"`.
         2. the "trigger" value (`"twitter.get"`) gets mapped to "options.name"
         3. the "options" value (`{"endpoint": "timeline"}`) gets mapped to "options.options"


         {
         "type": "$lambda",
         "options": {
         "name": "twitter.get",
         "options": {
         "endpoint": "timeline"
         }
         },
         "success": {
         "type": "$render"
         },
         "error": {
         "type": "$util.toast",
         "options": {
         "text": "Uh oh. Something went wrong"
         }
         }
         }

         The success / error actions get executed AFTER the triggered action has finished and returns with a return value.

         ****************************************************************************************/

        try {
            // construct options
            if (action.has("options")) {
                Object options = action.get("options");
                JasonParser.getInstance(this).setParserListener(new JasonParser.JasonParserListener() {
                    @Override
                    public void onFinished(JSONObject parsed_options) {
                        try {
                            invoke_lambda(action, data, parsed_options, context);
                        } catch (Exception e) {
                            Log.d("Warning", e.getStackTrace()[0].getMethodName() + " : " + e.toString());
                        }
                    }
                });
                JasonParser.getInstance(this).parse("json", model.state, options, context);
            } else {
                invoke_lambda(action, data, null, context);
            }
        } catch (Exception e){
            Log.d("Warning", e.getStackTrace()[0].getMethodName() + " : " + e.toString());
        }

    }

    private void invoke_lambda(final JSONObject action, final JSONObject data, final JSONObject options, final Context context) {
        try {
            // construct lambda
            JSONObject lambda = new JSONObject();
            lambda.put("type", "$lambda");

            JSONObject args = new JSONObject();
            args.put("name", action.getString("trigger"));
            if (options!=null) {
                args.put("options", options);
            }
            lambda.put("options", args);

            if (action.has("success")) {
                lambda.put("success", action.get("success"));
            }
            if (action.has("error")) {
                lambda.put("error", action.get("error"));
            }

            call(lambda.toString(), data.toString(), "{}", context);
        } catch (Exception e){
            Log.d("Warning", e.getStackTrace()[0].getMethodName() + " : " + e.toString());
        }
    }

    public void simple_trigger(final String event_name, JSONObject data, Context context){
        try{
            if ((isexecuting || !resumed) && event_queue.size() > 0) {
                JSONObject event_store = new JSONObject();
                event_store.put("event_name", event_name);
                event_store.put("data", data);
                event_queue.add(event_store);
                return;
            } else {
                isexecuting = true;
            }

            JSONObject head = model.jason.getJSONObject("$jason").getJSONObject("head");
            JSONObject events = head.getJSONObject("actions");
            // Look up an action by event_name
            if (events.has(event_name)) {
                Object action = events.get(event_name);
                call(action.toString(), data.toString(), "{}", context);
            } else {
                unlock(new JSONObject(), new JSONObject(), new JSONObject(), JasonViewActivity.this);
            }
        } catch (Exception e) {
            Log.d("Warning", e.getStackTrace()[0].getMethodName() + " : " + e.toString());
        }
    }

    private void exec(final JSONObject action, final JSONObject data, final JSONObject event, final Context context){
        try {
            String type = action.getString("type");
            if (type.startsWith("$") || type.startsWith("@")){

                String[] tokens = type.split("\\.");
                String className;
                String fileName;
                String methodName;

                if(tokens.length == 1){
                    // Core
                    methodName = type.substring(1);
                    Method method = JasonViewActivity.class.getMethod(methodName, JSONObject.class, JSONObject.class, JSONObject.class, Context.class);
                    method.invoke(this, action, model.state, event, context);
                } else {

                    className = type.substring(1, type.lastIndexOf('.'));

                    // Resolve classname by looking up the json files
                    String resolved_classname = null;
                    String jr = null;
                    try {
                        InputStream is = getAssets().open("file/$" + className + ".json");
                        int size = is.available();
                        byte[] buffer = new byte[size];
                        is.read(buffer);
                        is.close();
                        jr = new String(buffer, "UTF-8");
                        JSONObject jrjson = new JSONObject(jr);
                        if(jrjson.has("classname")){
                            resolved_classname = jrjson.getString("classname");
                        }
                    } catch (Exception e) {
                        Log.d("Warning", e.getStackTrace()[0].getMethodName() + " : " + e.toString());
                    }


                    if(resolved_classname != null) {
                        fileName = "com.jasonette.seed.Action." + resolved_classname;
                    } else {
                        fileName = "com.jasonette.seed.Action.Jason" + className.toUpperCase().charAt(0) + className.substring(1) + "Action";
                    }

                    methodName = type.substring(type.lastIndexOf('.') + 1);

                    // Look up the module registry to see if there's an instance already
                    // 1. If there is, use that
                    // 2. If there isn't:
                    //      A. Instantiate one
                    //      B. Add it to the registry
                    Object module;
                    if (modules.containsKey(fileName)) {
                        module = modules.get(fileName);
                    } else {
                        Class<?> classObject = Class.forName(fileName);
                        Constructor<?> constructor = classObject.getConstructor();
                        module = constructor.newInstance();
                        modules.put(fileName, module);
                    }

                    Method method = module.getClass().getMethod(methodName, JSONObject.class, JSONObject.class, JSONObject.class, Context.class);
                    model.action = action;
                    method.invoke(module, action, model.state, event, context);
                }
            }
        } catch (Exception e){
            // Action doesn't exist yet
            Log.d("Warning", e.getStackTrace()[0].getMethodName() + " : " + e.toString());
            try {
                JSONObject alert_action = new JSONObject();
                alert_action.put("type", "$util.banner");

                JSONObject options = new JSONObject();
                options.put("title", "Not implemented");
                String type = action.getString("type");
                options.put("description", action.getString("type") + " is not implemented yet.");

                alert_action.put("options", options);

                call(alert_action.toString(), new JSONObject().toString(), "{}", JasonViewActivity.this);
            } catch (Exception e2){
                Log.d("Warning", e2.getStackTrace()[0].getMethodName() + " : " + e2.toString());
            }
        }
    }

    /*************************************************************
     *
     * JASON CORE ACTION API
     *
     ************************************************************/

    /**
     * Renders a template using data
     * @param {String} template_name - the name of the template to render
     * @param {JSONObject} data - the data object to render
     */

    public void lambda(final JSONObject action, JSONObject data, JSONObject event, Context context) {
        /*

        # Similar to `trigger` keyword, but with a few differences:
        1. Trigger was just for one-off triggering and finish. Lambda waits until the subroutine returns and continues where it left off.
        2. `trigger` was a keyword, but lambda itself is just another type of action. `{"type": "$lambda"}`
        3. Lambda can pass arguments via `options`

        # How it works
        1. Triggers another action by name
        2. Waits for the subroutine to return via `$return.success` or `$return.error`
        3. When the subroutine calls `$return.success`, continue executing from `success` action, using the return value from the subroutine
        4. When the subroutine calls `$return.error`, continue executing from `error` action, using the return value from the subroutine

        # Example 1: Basic lambda (Same as trigger)
        {
            "type": "$lambda",
            "options": {
                "name": "fetch"
            }
        }

        # Example 2: Basic lambda with success/error handlers
        {
            "type": "$lambda",
            "options": {
                "name": "fetch"
            }
            "success": {
                "type": "$render"
            },
            "error": {
                "type": "$util.toast",
                "options": {
                    "text": "Error"
                }
            }
        }

        # Example 3: Passing arguments
        {
            "type": "$lambda",
            "options": {
                "name": "fetch",
                "options": {
                    "url": "https://www.jasonbase.com/things/73g"
                }
            },
            "success": {
                "type": "$render"
            },
            "error": {
                "type": "$util.toast",
                "options": {
                    "text": "Error"
                }
            }
        }

        # Example 4: Using the previous action's return value

        {
            "type": "$network.request",
            "options": {
                "url": "https://www.jasonbase.com/things/73g"
            },
            "success": {
                "type": "$lambda",
                "options": {
                    "name": "draw"
                },
                "success": {
                    "type": "$render"
                },
                "error": {
                    "type": "$util.toast",
                    "options": {
                        "text": "Error"
                    }
                }
            }
        }

        # Example 5: Using the previous action's return value as well as custom options

        {
            "type": "$network.request",
            "options": {
                "url": "https://www.jasonbase.com/things/73g"
            },
            "success": {
                "type": "$lambda",
                "options": {
                    "name": "draw",
                    "options": {
                        "p1": "another param",
                        "p2": "yet another param"
                    }
                },
                "success": {
                    "type": "$render"
                },
                "error": {
                    "type": "$util.toast",
                    "options": {
                        "text": "Error"
                    }
                }
            }
        }

        # Example 6: Using the previous action's return value as well as custom options

        {
            "type": "$network.request",
            "options": {
                "url": "https://www.jasonbase.com/things/73g"
            },
            "success": {
                "type": "$lambda",
                "options": [{
                    "{{#if $jason}}": {
                        "name": "draw",
                        "options": {
                            "p1": "another param",
                            "p2": "yet another param"
                        }
                    }
                }, {
                    "{{#else}}": {
                        "name": "err",
                        "options": {
                            "text": "No content to render"
                        }
                    }
                }],
                "success": {
                    "type": "$render"
                },
                "error": {
                    "type": "$util.toast",
                    "options": {
                        "text": "Error"
                    }
                }
            }
        }

         */

        try{
            if(action.has("options")){
                JSONObject options = action.getJSONObject("options");
                // 1. Resolve the action by looking up from $jason.head.actions
                String event_name = options.getString("name");
                JSONObject head = model.jason.getJSONObject("$jason").getJSONObject("head");
                JSONObject events = head.getJSONObject("actions");
                final Object lambda = events.get(event_name);

                final String caller = action.toString();

                // 2. If `options` exists, use that as the data to pass to the next action
                if(options.has("options")){
                    Object new_options = options.get("options");

                    // take the options and parse it with current model.state
                    JasonParser.getInstance(this).setParserListener(new JasonParser.JasonParserListener() {
                        @Override
                        public void onFinished(JSONObject parsed_options) {
                            try {
                                JSONObject wrapped = new JSONObject();
                                wrapped.put("$jason", parsed_options);
                                call(lambda.toString(), wrapped.toString(), caller, JasonViewActivity.this);
                            } catch (Exception e){
                                JasonHelper.next("error", action, new JSONObject(), new JSONObject(), JasonViewActivity.this);
                            }
                        }
                    });
                    JasonParser.getInstance(this).parse("json", model.state, new_options, context);

                }

                // 3. If `options` doesn't exist, forward the data from the previous action
                else {
                    call(lambda.toString(), data.toString(), caller, JasonViewActivity.this);
                }
            }
        } catch (Exception e){
            Log.d("Warning", e.getStackTrace()[0].getMethodName() + " : " + e.toString());
            JasonHelper.next("error", action, new JSONObject(), new JSONObject(), JasonViewActivity.this);
        }
    }

    public void require(final JSONObject action, JSONObject data, final JSONObject event, final Context context){
        /*
         {
            "type": "$require",
            "options": {
                "items": ["https://...", "https://...", ....],
                "item": "https://...."
            }
         }

         Crawl all the items in the array and assign it to the key
         */

        try {
            if (action.has("options")) {
                JSONObject options = action.getJSONObject("options");

                ArrayList<String> urlSet = new ArrayList<>();
                Iterator<?> keys = options.keys();
                while (keys.hasNext()) {
                    String key = (String) keys.next();
                    Object val = options.get(key);

                    // must be either array or string
                    if(val instanceof JSONArray){
                        for (int i = 0; i < ((JSONArray)val).length(); i++) {
                            if(!urlSet.contains(((JSONArray) val).getString(i))){
                                urlSet.add(((JSONArray) val).getString(i));
                            }
                        }
                    } else if(val instanceof String){
                        if(!urlSet.contains(val)){
                            urlSet.add(((String)val));
                        }
                    }
                }
                if (urlSet.size()>0) {
                    JSONObject refs = new JSONObject();

                    CountDownLatch latch = new CountDownLatch(urlSet.size());
                    ExecutorService taskExecutor = Executors.newFixedThreadPool(urlSet.size());
                    for (String key : urlSet) {
                        taskExecutor.submit(new JasonRequire(key, latch, refs, model.client, this));
                    }
                    try {
                        latch.await();
                    } catch (Exception e) {
                        Log.d("Warning", e.getStackTrace()[0].getMethodName() + " : " + e.toString());
                    }

                    JSONObject res = new JSONObject();

                    Iterator<?> ks = options.keys();
                    while (ks.hasNext()) {
                        String key = (String) ks.next();
                        Object val = options.get(key);
                        if (val instanceof JSONArray) {
                            JSONArray ret = new JSONArray();
                            for (int i = 0; i < ((JSONArray)val).length(); i++) {
                                String url = ((JSONArray) val).getString(i);
                                ret.put(refs.get(url));
                            }
                            res.put(key, ret);
                        } else if (val instanceof String) {
                            res.put(key, refs.get((String)val));
                        }
                    }
                    JasonHelper.next("success", action, res, event, context);
                }
            } else {
                JasonHelper.next("error", action, new JSONObject(), event, context);
            }
        } catch (Exception e){
            Log.d("Warning", e.getStackTrace()[0].getMethodName() + " : " + e.toString());
            JasonHelper.next("error", action, new JSONObject(), event, context);
        }

        // get all urls
    }

    public void set(final JSONObject action, JSONObject data, JSONObject event, Context context) {
        try{
            if(action.has("options")){
                JSONObject options = action.getJSONObject("options");
                model.var = JasonHelper.merge(model.var, options);
            }
            JasonHelper.next("success", action, new JSONObject(), event, context);

        } catch (Exception e){
            Log.d("Warning", e.getStackTrace()[0].getMethodName() + " : " + e.toString());
        }
    }

    public void handleErrorCallback() {
        if (model.onError != null) {
            exec(model.onError, model.state, null, this);
        }
    }

    public void hideProgressBar() {
        runOnUiThread(new Runnable() {

            @Override
            public void run() {
                findViewById(R.id.jason_loading_fragment_container).setVisibility(View.GONE);
            }
        });
    }

    public void showProgressBar() {
        runOnUiThread(new Runnable() {

            @Override
            public void run() {
                findViewById(R.id.jason_loading_fragment_container).setVisibility(View.VISIBLE);
            }
        });
    }

    public void href(final JSONObject action, JSONObject data, JSONObject event, Context context) {
        try {
            if (action.has("options")) {
                JSONObject action_options = action.getJSONObject("options");
                isexecuting = false;
                resumed = false;
                String url = action.getJSONObject("options").getString("url");
                String transition = "push";
                if(action_options.has("transition")){
                    transition = action_options.getString("transition");
                }
                // "view": "web"
                if (action_options.has("view")) {
                    String view_type = action_options.getString("view");
                    if (view_type.equalsIgnoreCase("web") || view_type.equalsIgnoreCase("app")) {
                        try {
                            Intent intent = new Intent(Intent.ACTION_VIEW);
                            intent.setData(Uri.parse(url));
                            startActivity(intent);
                        } catch (Exception e) {
                            Intent intent = new Intent(url);
                            startActivity(intent);
                        }
                        return;
                    }
                }

                // "view": "jason" (default)
                // Set params for the next view (use either 'options' or 'params')
                String params = null;
                if (action_options.has("options")){
                    params = action_options.getJSONObject("options").toString();
                } else if (action_options.has("params")) {
                    params = action_options.getJSONObject("params").toString();
                }

                // Reset SharedPreferences so it doesn't overwrite the model onResume
                SharedPreferences pref = getSharedPreferences("model", 0);
                SharedPreferences.Editor editor = pref.edit();
                editor.remove(url);
                editor.commit();

                if (transition.equalsIgnoreCase("switchtab")) {
                    if (action_options.has("preload")) {
                        preload = action_options.getJSONObject("preload");
                    }
                    Intent intent = new Intent(this, JasonViewActivity.class);
                    intent.putExtra("transition", transition);
                    intent.putExtra("depth", depth);

                    if (action.has("error")) {
                        intent.putExtra("onError", action.getJSONObject("error").toString());
                    }

                    if (params!=null) {
                        intent.putExtra("params", params);
                        onSwitchTab(url, params, intent);
                    } else {
                        params = "{}";
                        onSwitchTab(url, params, intent);
                    }
                } else if(transition.equalsIgnoreCase("replace")){
                    // remove all touch listeners before replacing
                    // Use case : Tab bar
                    removeListViewOnItemTouchListeners();

                    Intent intent = new Intent(this, JasonViewActivity.class);
                    intent.putExtra("transition", transition);
                    intent.putExtra("url", url);
                    intent.putExtra("depth", depth);

                    if (action.has("error")) {
                        intent.putExtra("onError", action.getJSONObject("error").toString());
                    }

                    if (action_options.has("preload")) {
                        intent.putExtra("preload", action_options.getJSONObject("preload").toString());
                    }

                    if (params!=null) {
                        intent.putExtra("params", params);
                    }
                    dispatchFragment(intent, true);
                } else {
                    Intent intent = new Intent(this, JasonViewActivity.class);
                    intent.putExtra("transition", transition);
                    intent.putExtra("url", url);
                    intent.putExtra("depth", depth+1);

                    if (action.has("error")) {
                        intent.putExtra("onError", action.getJSONObject("error").toString());
                    }

                    if (action_options.has("preload")) {
                        intent.putExtra("preload", action_options.getJSONObject("preload").toString());
                    }

                    if (params != null) {
                        intent.putExtra("params", params);
                    }

                    // Start an Intent with a callback option:
                    // 1. call dispatchIntent
                    // 2. the intent will return with JasonCallback.href
                    JSONObject callback = new JSONObject();
                    callback.put("class", "JasonCallback");
                    callback.put("method", "href");
                    if (transition.equalsIgnoreCase("modal")) {
                        JasonHelper.dispatchIntent(action, data, event, context, intent, callback);
                    } else {
                        JasonHelper.dispatchIntent(action, data, event, context, null, callback);
                        dispatchFragment(intent, false);
                    }
                }
            }
        } catch (Exception e) {
            Log.d("Warning", e.getStackTrace()[0].getMethodName() + " : " + e.toString());
        }
    }

    private void dispatchFragment(Intent intent, boolean switchTab) {
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        JasonFragment fragment = new JasonFragment();
        Bundle bundle = new Bundle();
        bundle.putParcelable("intent", intent);
        fragment.setArguments(bundle);

        if (switchTab) {
            fragmentManager.popBackStack();
            fragmentTransaction.replace(R.id.jason_fragment_container, fragment);
        } else {
            fragmentTransaction.add(R.id.jason_fragment_container, fragment);
        }
        fragmentTransaction.addToBackStack(null);
        fragmentTransaction.commit();
    }

    private void onSwitchTab(String newUrl, String newParams, Intent intent) {
        FragmentManager fragmentManager = getSupportFragmentManager();
        while (fragmentManager.getBackStackEntryCount() > 1) {
            fragmentManager.popBackStackImmediate();
        }
        currentFragment().onSwitchTab(newUrl, newParams, intent);
    }

    public void back ( final JSONObject action, JSONObject data, JSONObject event, Context context){
        finish();
    }

    public void close ( final JSONObject action, JSONObject data, JSONObject event, Context context){
        finish();
    }

    public void ok ( final JSONObject action, JSONObject data, JSONObject event, Context context){
        try {
            Intent intent = new Intent();
            if (action.has("options")) {
                intent.putExtra("return", action.get("options").toString());
            }
            setResult(RESULT_OK, intent);
            finish();
        } catch (Exception e) {
            Log.d("Warning", e.getStackTrace()[0].getMethodName() + " : " + e.toString());
        }
    }

    public void unlock ( final JSONObject action, JSONObject data, JSONObject event, Context context){
        if (event_queue.size() > 0) {
            JSONObject next_action = event_queue.get(0);
            event_queue.remove(0);
            try {
                isexecuting = false;
                simple_trigger(next_action.getString("event_name"), next_action.getJSONObject("data"), context);
            } catch (Exception e) {
                Log.d("Warning", e.getStackTrace()[0].getMethodName() + " : " + e.toString());
            }
        } else {
            isexecuting = false;
        }
    }

    public void flush ( final JSONObject action, JSONObject data, JSONObject event, Context context){
        // there's no default caching on Android. So don't do anything for now
        try {
            JasonHelper.next("success", action, new JSONObject(), event, context);
        } catch (Exception e) {
            Log.d("Warning", e.getStackTrace()[0].getMethodName() + " : " + e.toString());
        }
    }

    public void snapshot ( final JSONObject action, JSONObject data, final JSONObject event, final Context context){
        View v1 = getWindow().getDecorView().getRootView();
        v1.setDrawingCacheEnabled(true);
        final Bitmap bitmap = Bitmap.createBitmap(v1.getDrawingCache());
        v1.setDrawingCacheEnabled(false);
        new Thread(new Runnable() {
            @Override
            public void run() {
                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream);
                byte[] byteArray = stream.toByteArray();
                String encoded = Base64.encodeToString(byteArray, Base64.NO_WRAP);

                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append("data:image/jpeg;base64,");
                stringBuilder.append(encoded);
                String data_uri = stringBuilder.toString();

                try {
                    JSONObject ret = new JSONObject();
                    ret.put("data", encoded);
                    ret.put("data_uri", data_uri);
                    ret.put("content_type", "image/png");
                    JasonHelper.next("success", action, ret, event, context);
                } catch (Exception e) {
                    Log.d("Warning", e.getStackTrace()[0].getMethodName() + " : " + e.toString());
                }

            }
        }).start();
    }

    public void scroll (final JSONObject action, JSONObject data, final JSONObject event, final Context context) {
        try {
            if (action.has("options")) {
                JSONObject options = action.getJSONObject("options");
                if (options.has("position")) {
                    String position = options.getString("position");
                    if (position.equalsIgnoreCase("top")) {
                        listView.smoothScrollToPosition(0);
                    } else if (position.equalsIgnoreCase("bottom")) {
                        listView.smoothScrollToPosition(listView.getAdapter().getItemCount() - 1);
                    } else {
                        RecyclerView.SmoothScroller smoothScroller = new LinearSmoothScroller(context) {
                            @Override protected int getVerticalSnapPreference() {
                                return LinearSmoothScroller.SNAP_TO_START;
                            }
                        };
                        smoothScroller.setTargetPosition(((ItemAdapter)listView.getAdapter()).getHeaderAt(parseInt(position)));
                        listView.getLayoutManager().startSmoothScroll(smoothScroller);
                    }
                }
            }
        } catch (Exception e) {
            Log.d("Warning", e.getStackTrace()[0].getMethodName() + " : " + e.toString());
        }
        JasonHelper.next("success", action, new JSONObject(), event, context);
    }

    public void build(JSONObject jason) {
        currentFragment().build(jason);
    }

    public void setup_header(JSONObject header){
        if (header != null && header.has("style")) {
            try {
                JSONObject style = header.getJSONObject("style");

                if (style.has("background")) {
                    String backgroundColor = style.getString("background");
                    toolbar.setBackgroundColor(JasonHelper.parse_color(backgroundColor));
                }

                if (style.has("gradient_background")) {
                    JSONArray backgroundGradient = style.getJSONArray("gradient_background");

                    GradientDrawable gd = new GradientDrawable(
                            GradientDrawable.Orientation.BL_TR,
                            new int[]{
                                    JasonHelper.parse_color(((JSONArray) backgroundGradient).getString(0)),
                                    JasonHelper.parse_color(((JSONArray) backgroundGradient).getString(1)),
                            });
                    toolbar.setBackground(gd);
                }

                if (style.has("color")) {
                    int color = JasonHelper.parse_color(style.getString("color"));
                    toolbar.setTitleTextColor(color);
                    toolbar.setSubtitleTextColor(color);

                    final Drawable upArrow = ContextCompat.getDrawable(this, R.drawable.abc_ic_ab_back_material);
                    upArrow.setColorFilter(color, PorterDuff.Mode.SRC_ATOP);
                    getSupportActionBar().setHomeAsUpIndicator(upArrow);
                }

            } catch (Exception e){
              Log.d("Warning", e.getStackTrace()[0].getMethodName() + " : " + e.toString());
            }

            toolbar.setVisibility(View.VISIBLE);
        } else {
            toolbar.setVisibility(View.GONE);
        }
    }

    public void setup_footer(JSONObject footer){
        try {
            if (footer.has("tabs")) {
                setup_tabs(footer.getJSONObject("tabs"));
                findViewById(R.id.jason_bottom_navigation).setVisibility(View.VISIBLE);
            }
            else if (footer.has("input")) {
                setup_input(footer.getJSONObject("input"));
                findViewById(R.id.jason_bottom_navigation).setVisibility(View.VISIBLE);
            }
        } catch (Exception e) {
            Log.d("Warning", e.getStackTrace()[0].getMethodName() + " : " + e.toString());
        }
    }

    private void setup_input(JSONObject input) {
        // Set up a horizontal linearlayout
        // which sticks to the bottom
        AHBottomNavigation footerLayout = findViewById(R.id.jason_bottom_navigation);

        footerLayout.removeView(footerInput);
        int height = (int) JasonHelper.pixels(JasonViewActivity.this, "60", "vertical");
        int spacing = (int) JasonHelper.pixels(JasonViewActivity.this, "5", "vertical");
        int outer_padding = (int) JasonHelper.pixels(JasonViewActivity.this, "10", "vertical");
        footerInput = new LinearLayout(this);
        footerInput.setOrientation(LinearLayout.HORIZONTAL);
        footerInput.setGravity(Gravity.CENTER_VERTICAL);
        footerInput.setPadding(outer_padding,0,outer_padding,0);
        footerLayout.addView(footerInput);
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, height);

        params.bottomMargin = 0;
        params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        footerInput.setLayoutParams(params);

        try {
            if(input.has("style") && input.getJSONObject("style").has("background")){
                int color = JasonHelper.parse_color(input.getJSONObject("style").getString("background"));
                footerInput.setBackgroundColor(color);
            }
            if (input.has("left")) {
                JSONObject json = input.getJSONObject("left");
                JSONObject style;
                if (json.has("style")) {
                    style = json.getJSONObject("style");
                }  else {
                    style = new JSONObject();
                }
                style.put("height", "25");
                if (json.has("image")) {
                    json.put("url", json.getString("image"));
                }
                json.put("type", "button");
                json.put("style", style);
                View leftButton = JasonComponentFactory.build(null, json, null, JasonViewActivity.this);
                leftButton.setPadding(spacing,0,spacing,0);
                JasonComponentFactory.build(leftButton, input.getJSONObject("left"), null, JasonViewActivity.this);
                footerInput.addView(leftButton);
            }

            JSONObject textfield;
            if (input.has("textfield")) {
                textfield = input.getJSONObject("textfield");
            } else {
                textfield = input;
            }
            textfield.put("type", "textfield");
            // First build only creates the stub.
            footer_input_textfield= JasonComponentFactory.build(null, textfield, null, JasonViewActivity.this);
            int padding = (int) JasonHelper.pixels(JasonViewActivity.this, "10", "vertical");
            // Build twice because the first build only builds the stub.
            JasonComponentFactory.build(footer_input_textfield, textfield, null, JasonViewActivity.this);
            footer_input_textfield.setPadding(padding, padding, padding, padding);
            footerInput.addView(footer_input_textfield);
            LinearLayout.LayoutParams layout_params = (LinearLayout.LayoutParams)footer_input_textfield.getLayoutParams();
            layout_params.height = LinearLayout.LayoutParams.MATCH_PARENT;
            layout_params.weight = 1;
            layout_params.width = 0;
            layout_params.leftMargin = spacing;
            layout_params.rightMargin = spacing;
            layout_params.topMargin = spacing;
            layout_params.bottomMargin = spacing;

            if (input.has("right")) {
                JSONObject json = input.getJSONObject("right");
                JSONObject style;
                if (json.has("style")) {
                    style = json.getJSONObject("style");
                }  else {
                    style = new JSONObject();
                }
                if (!json.has("image") && !json.has("text")) {
                    json.put("text", "Send");
                }
                if (json.has("image")) {
                    json.put("url", json.getString("image"));
                }
                style.put("height", "25");

                json.put("type", "button");
                json.put("style", style);
                View rightButton = JasonComponentFactory.build(null, json, null, JasonViewActivity.this);
                JasonComponentFactory.build(rightButton, input.getJSONObject("right"), null, JasonViewActivity.this);
                rightButton.setPadding(spacing,0,spacing,0);
                footerInput.addView(rightButton);
            }

            footerInput.requestLayout();

            listView.setClipToPadding(false);
            listView.setPadding(0,0,0,height);
        } catch (Exception e){
            Log.d("Warning", e.getStackTrace()[0].getMethodName() + " : " + e.toString());
        }
    }

    private void setup_tabs(JSONObject tabs){
        try {
            final JSONArray items = tabs.getJSONArray("items");
            JSONObject style;
            if (bottomNavigation == null) {
                bottomNavigation = this.findViewById(R.id.jason_bottom_navigation);
                bottomNavigation.setDefaultBackgroundColor(Color.parseColor("#FEFEFE"));

            }

            if (tabs.has("style")) {
                style = tabs.getJSONObject("style");
                if (style.has("color")) {
                    int color = JasonHelper.parse_color(style.getString("color"));
                    bottomNavigation.setAccentColor(color);
                }
                if (style.has("color:disabled")) {
                    int disabled_color = JasonHelper.parse_color(style.getString("color:disabled"));
                    bottomNavigation.setInactiveColor(disabled_color);
                }
                if (style.has("background")) {
                    int background = JasonHelper.parse_color(style.getString("background"));
                    bottomNavigation.setDefaultBackgroundColor(background);
                    bottomNavigation.setBackgroundColor(background);
                }
            }
            boolean sameNavigationItems = true;
            if (bottomNavigation.getItemsCount() == items.length()) {
                for (int i = 0; i < items.length(); i++) {
                    if (((JSONObject) items.get(i)).getString("text") != bottomNavigation.getItem(i).getTitle(this)) {
                        sameNavigationItems = false;
                    }
                }
            } else {
                sameNavigationItems = false;
            }

            if (sameNavigationItems) {
                // if the same number as the previous state, try to fill in the items instead of re-instantiating them all

                for (int i = 0; i < items.length(); i++) {
                    final JSONObject item = items.getJSONObject(i);
                    if (item.has("image")) {

                        String temptext = "";
                        try {
                            if (item.has("text")) {
                                temptext = item.getString("text");
                                bottomNavigation.setTitleState(AHBottomNavigation.TitleState.ALWAYS_SHOW);
                            }
                        } catch (Exception e) {
                            Log.d("Warning", e.getStackTrace()[0].getMethodName() + " : " + e.toString());
                        }
                        final String text = temptext;

                        final int index = i;
                        JSONObject c = new JSONObject();
                        c.put("url", item.getString("image"));
                        Glide
                                .with(this)
                                .load(JasonImageComponent.resolve_url(c, JasonViewActivity.this))
                                .asBitmap()
                                .into(new SimpleTarget<Bitmap>(100, 100) {
                                    @Override
                                    public void onResourceReady(Bitmap resource, GlideAnimation glideAnimation) {
                                        AHBottomNavigationItem tab_item = bottomNavigation.getItem(index);
                                        bottomNavigationItems.put(Integer.valueOf(index), tab_item);
                                        Drawable drawable = new BitmapDrawable(getResources(), resource);
                                        tab_item.setDrawable(drawable);
                                        tab_item.setTitle(text);
                                    }
                                    @Override
                                    public void onLoadFailed(Exception e, Drawable errorDrawable) {
                                        AHBottomNavigationItem tab_item = bottomNavigation.getItem(index);
                                        bottomNavigationItems.put(Integer.valueOf(index), tab_item);
                                        tab_item.setTitle(text);
                                    }
                                });

                    } else if (item.has("text")) {
                        String text = item.getString("text");
                        try {
                            bottomNavigation.setTitleState(AHBottomNavigation.TitleState.ALWAYS_SHOW);
                        } catch (Exception e) {
                            Log.d("Warning", e.getStackTrace()[0].getMethodName() + " : " + e.toString());
                        }
                        AHBottomNavigationItem tab_item = bottomNavigation.getItem(i);
                        bottomNavigationItems.put(Integer.valueOf(i), tab_item);
                        ColorDrawable d = new ColorDrawable(Color.TRANSPARENT);
                        tab_item.setDrawable(d);
                        tab_item.setTitle(text);
                    }
                }
            } else {
                // The navigation didn't match so we want to remove items before continuing to draw the new ones
                bottomNavigation.removeAllItems();
                bottomNavigationItems = new SparseArray<AHBottomNavigationItem>(5);

                for (int i = 0; i < items.length(); i++) {
                    final JSONObject item = items.getJSONObject(i);
                    final int index = i;
                    String text = "";
                    ColorDrawable blank_image = new ColorDrawable(Color.TRANSPARENT);
                    final AHBottomNavigationItem tab_item = new AHBottomNavigationItem(text, blank_image);
                    if (item.has("text")) {
                        try {
                            if (item.has("text")) {
                                text = item.getString("text");
                                bottomNavigation.setTitleState(AHBottomNavigation.TitleState.ALWAYS_SHOW);
                            }
                        } catch (Exception e) {
                            Log.d("Warning", e.getStackTrace()[0].getMethodName() + " : " + e.toString());
                        }

                        tab_item.setTitle(text);
                    }
                    if (item.has("image")) {
                        JSONObject c = new JSONObject();
                        c.put("url", item.getString("image"));
                        with(this)
                                .load(JasonImageComponent.resolve_url(c, JasonViewActivity.this))
                                .asBitmap()
                                .into(new SimpleTarget<Bitmap>(100, 100) {
                                    @Override
                                    public void onResourceReady(Bitmap resource, GlideAnimation glideAnimation) {
                                        Drawable drawable = new BitmapDrawable(getResources(), resource);
                                        tab_item.setDrawable(drawable);
                                        // Tell the bottomNavigation to update the visuals if they're different
                                        bottomNavigation.refresh();
                                    }
                                });

                    }
                    bottomNavigationItems.put(Integer.valueOf(index), tab_item);
                    if (bottomNavigationItems.size() >= items.length()) {
                        for (int j = 0; j < bottomNavigationItems.size(); j++) {
                            bottomNavigation.addItem(bottomNavigationItems.get(j));
                        }
                    }
                }
            }

            for (int i = 0; i < items.length(); i++) {
                final JSONObject item = items.getJSONObject(i);
                if (item.has("badge")) {
                    bottomNavigation.setNotification(item.get("badge").toString(), i);
                }
            }

            bottomNavigation.setOnTabSelectedListener(new AHBottomNavigation.OnTabSelectedListener() {
                @Override
                public boolean onTabSelected(int position, boolean wasSelected) {
                    try {
                        JSONObject item = items.getJSONObject(position);
                        if (item.has("href")) {
                            JSONObject action = new JSONObject();
                            JSONObject href = item.getJSONObject("href");
                            if (!href.has("transition")) {
                                href.put("transition", "switchtab");
                            }
                            action.put("options", href);
                            href(action, new JSONObject(), new JSONObject(), JasonViewActivity.this);
                        } else if (item.has("action")) {
                            call(item.get("action").toString(), "{}", "{}", JasonViewActivity.this);
                            return false;
                        } else if (item.has("url")) {
                            String url = item.getString("url");
                            JSONObject action = new JSONObject();
                            JSONObject options = new JSONObject();
                            options.put("url", url);
                            options.put("transition", "switchtab");
                            if (item.has("preload")) {
                                options.put("preload", item.getJSONObject("preload"));
                            }
                            action.put("options", options);
                            href(action, new JSONObject(), new JSONObject(), JasonViewActivity.this);
                        }
                    } catch (Exception e) {
                        Log.d("Warning", e.getStackTrace()[0].getMethodName() + " : " + e.toString());
                    }
                    return true;
                }
            });
        } catch (Exception e) {
            Log.d("Warning", e.getStackTrace()[0].getMethodName() + " : " + e.toString());
        }
    }

    // Menu
    public boolean onPrepareOptionsMenu(Menu menu) {
        try {
            menu = toolbar.getMenu();
            // Clear out old menu item if present
            try {
                menu.removeItem(0);
            } catch (Exception e) { }

            if (model.rendered == null) {
                return super.onPrepareOptionsMenu(menu);
            }

            if (!model.rendered.has("header")) {
                setup_title(new JSONObject());
            }
            // Breaks by design short circuiting the code when a header isn't present on the view
            JSONObject header = model.rendered.getJSONObject("header");

            header_height = toolbar.getHeight();

            setup_title(header);

            if (header.has("search")) {
                SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
                final JSONObject search = header.getJSONObject("search");
                if (searchView == null) {
                    searchView = new SearchView(this);
                    searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));

                    // put the search icon on the right hand side of the toolbar
                    searchView.setLayoutParams(new Toolbar.LayoutParams(Gravity.RIGHT));

                    toolbar.addView(searchView);
                } else {
                    searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
                }

                // styling

                // color
                int c;
                if (search.has("style") && search.getJSONObject("style").has("color")) {
                    c = JasonHelper.parse_color(search.getJSONObject("style").getString("color"));
                } else if (header.has("style") && header.getJSONObject("style").has("color")) {
                    c = JasonHelper.parse_color(header.getJSONObject("style").getString("color"));
                } else {
                    c = -1;
                }
                if (c > 0) {
                    ImageView searchButton = (ImageView) searchView.findViewById(androidx.appcompat.R.id.search_button);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        searchButton.setImageTintList(ColorStateList.valueOf(JasonHelper.parse_color(header.getJSONObject("style").getString("color"))));
                    }
                }

                // background
                if (search.has("style") && search.getJSONObject("style").has("background")) {
                    int bc = JasonHelper.parse_color(search.getJSONObject("style").getString("background"));
                    searchView.setBackgroundColor(bc);
                }

                // placeholder
                if (search.has("placeholder")) {
                    searchView.setQueryHint(search.getString("placeholder"));
                }

                searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener(){
                    @Override
                    public boolean onQueryTextSubmit(String s) {
                        // name
                        if (search.has("name")) {
                            try {
                                JSONObject kv = new JSONObject();
                                kv.put(search.getString("name"), s);
                                model.var = JasonHelper.merge(model.var, kv);
                                if (search.has("action")) {
                                    call(search.getJSONObject("action").toString(), new JSONObject().toString(), "{}", JasonViewActivity.this);
                                }
                            } catch (Exception e){
                                Log.d("Warning", e.getStackTrace()[0].getMethodName() + " : " + e.toString());
                            }
                        }
                        return false;
                    }

                    @Override
                    public boolean onQueryTextChange(String s) {
                        if (search.has("action")) {
                            return false;
                        } else if (listView != null) {
                            ItemAdapter adapter = (ItemAdapter)listView.getAdapter();
                            adapter.filter(s);
                        }
                        return true;
                    }
                });
            }


            if (header.has("menu")) {
                JSONObject json = header.getJSONObject("menu");

                final MenuItem item = menu.add("Menu");
                item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);

                // We're going to create a button.
                json.put("type", "button");

                // if it's an image button, both url and image should work
                if (json.has("image")) {
                    json.put("url", json.getString("image"));
                }

                // let's override the style so the menu button size has a sane dimension
                JSONObject style;
                if (json.has("style")) {
                    style = json.getJSONObject("style");
                } else {
                    style = new JSONObject();
                }

                // For image, limit the height so it doesn't look too big
                if (json.has("url")) {
                    style.put("height", JasonHelper.pixels(this, "8", "vertical"));
                }

                json.put("style", style);

                // Now creating the menuButton and itemview
                FrameLayout itemView;
                View menuButton;
                if (item.getActionView() == null) {
                    // Create itemView if it doesn't exist yet
                    itemView = new FrameLayout(this);
                    menuButton = JasonComponentFactory.build(null, json, null, JasonViewActivity.this);
                    JasonComponentFactory.build(menuButton, json, null, JasonViewActivity.this);
                    itemView.addView(menuButton);
                    item.setActionView(itemView);
                } else {
                    // Reuse the itemView if it already exists
                    itemView = (FrameLayout) item.getActionView();
                    menuButton = itemView.getChildAt(0);
                    JasonComponentFactory.build(menuButton, json, null, JasonViewActivity.this);
                }

                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
                menuButton.setLayoutParams(lp);

                // Set padding for the image menu button
                if (json.has("image")) {
                    int padding = (int) JasonHelper.pixels(this, "15", "vertical");
                    itemView.setPadding(padding, padding, padding, padding);
                }

                if (json.has("badge")) {
                    String badge_text = "";
                    JSONObject badge = json.getJSONObject("badge");
                    if(badge.has("text")) {
                        badge_text = badge.get("text").toString();
                    }
                    JSONObject badge_style;
                    if (badge.has("style")) {
                        badge_style = badge.getJSONObject("style");
                    } else {
                        badge_style = new JSONObject();
                    }

                    int color = JasonHelper.parse_color("#ffffff");
                    int background = JasonHelper.parse_color("#ff0000");

                    if(badge_style.has("color")) color = JasonHelper.parse_color(badge_style.getString("color"));
                    if(badge_style.has("background")) background = JasonHelper.parse_color(badge_style.getString("background"));

                    MaterialBadgeTextView v = new MaterialBadgeTextView(this);
                    v.setBackgroundColor(background);
                    v.setTextColor(color);
                    v.setText(badge_text);

                    FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
                    //layoutParams.gravity = Gravity.RIGHT | Gravity.TOP;
                    int left = (int)JasonHelper.pixels(this, String.valueOf(30), "horizontal");
                    int top = (int)JasonHelper.pixels(this, String.valueOf(-3), "vertical");
                    if (badge_style.has("left")) {
                        left = (int)JasonHelper.pixels(this, badge_style.getString("left"), "horizontal");
                    }
                    if (badge_style.has("top")) {
                        top = (int)JasonHelper.pixels(this, String.valueOf(parseInt(badge_style.getString("top"))), "vertical");
                    }
                    layoutParams.setMargins(left,top,0,0);
                    itemView.addView(v);
                    v.setLayoutParams(layoutParams);
                    itemView.setClipChildren(false);
                    itemView.setClipToPadding(false);
                }

                item.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem menuItem) {
                        try {
                            JSONObject header = model.rendered.getJSONObject("header");
                            if (header.has("menu")) {
                                if (header.getJSONObject("menu").has("action")) {
                                    call(header.getJSONObject("menu").getJSONObject("action").toString(), new JSONObject().toString(), "{}", JasonViewActivity.this);
                                } else if (header.getJSONObject("menu").has("href")) {
                                    JSONObject action = new JSONObject().put("type", "$href").put("options", header.getJSONObject("menu").getJSONObject("href"));
                                    call(action.toString(), new JSONObject().toString(), "{}", JasonViewActivity.this);
                                }
                            }
                        } catch (Exception e) {
                            Log.d("Warning", e.getStackTrace()[0].getMethodName() + " : " + e.toString());
                        }
                        return true;
                    }
                });
            }
        }catch(Exception e){
            Log.d("Warning", e.getStackTrace()[0].getMethodName() + " : " + e.toString());
        }
        return super.onPrepareOptionsMenu(menu);
    }

    void setup_title(JSONObject header) {
        try {
            // Default title values
            toolbar.setTitle("");
            toolbar.setTitleSize(20);

            // Global font
            if (header.has("style")) {
                toolbar.setTitleFont(header.getJSONObject("style"));
            }

            if (header.has("title")) {
                Object title = header.get("title");

                // set align:center by default
                toolbar.setAlignment(Gravity.CENTER);

                if (title instanceof JSONObject) {
                    JSONObject t = ((JSONObject) title);
                    String type = t.getString("type");
                    JSONObject style = null;

                    if (t.has("style")) {
                        style = t.getJSONObject("style");
                    }

                    if (style != null) {
                        // title alignment
                        String align;
                        toolbar.setAlignment(-1);
                        try {
                            align = style.getString("align");

                            if (align.equals("center")) {
                                toolbar.setAlignment(Gravity.CENTER);
                            }
                            else if (align.equals("left")) {
                                toolbar.setAlignment(Gravity.LEFT);
                            }
                        } catch (JSONException e) { }

                        // offsets
                        int leftOffset = 0;
                        int topOffset = 0;

                        try {
                            leftOffset = (int)JasonHelper.pixels(JasonViewActivity.this, style.getString("left"), "horizontal");
                        } catch (JSONException e) { }

                        try {
                            topOffset = (int)JasonHelper.pixels(JasonViewActivity.this, style.getString("top"), "vertical");
                        } catch (JSONException e) { }

                        toolbar.setLeftOffset(leftOffset);
                        toolbar.setTopOffset(topOffset);
                    }

                    // image options
                    if (type.equalsIgnoreCase("image")) {
                        String url = t.getString("url");
                        JSONObject c = new JSONObject();
                        c.put("url", url);
                        int height = header_height;
                        int width = Toolbar.LayoutParams.WRAP_CONTENT;
                        if (style != null) {
                            if (style.has("height")) {
                                try {
                                    height = (int) JasonHelper.pixels(this, style.getString("height"), "vertical");
                                } catch (Exception e) {
                                    Log.d("Warning", e.getStackTrace()[0].getMethodName() + " : " + e.toString());
                                }
                            }
                            if (style.has("width")) {
                                try {
                                    width = (int) JasonHelper.pixels(this, style.getString("width"), "horizontal");
                                } catch (Exception e) {
                                    Log.d("Warning", e.getStackTrace()[0].getMethodName() + " : " + e.toString());
                                }
                            }
                        }

                        toolbar.setImageHeight(height);
                        toolbar.setImageWidth(width);
                        toolbar.setImage(c);
                    }
                    // label options
                    else if (type.equalsIgnoreCase("label")) {
                        String text = ((JSONObject) title).getString("text");

                        if (style != null) {
                            // size
                            try {
                                toolbar.setTitleSize(Float.parseFloat(style.getString("size")));
                            } catch (JSONException e) {}

                            // font
                            toolbar.setTitleFont(style);
                        }

                        toolbar.setTitle(text);

                        if (logoView != null) {
                            toolbar.removeView(logoView);
                            logoView = null;
                        }
                    } else if (logoView != null) {
                        toolbar.removeView(logoView);
                        logoView = null;
                    }
                } else {
                    String simple_title = header.get("title").toString();
                    toolbar.setTitle(simple_title);
                    if(logoView != null){
                        toolbar.removeView(logoView);
                        logoView = null;
                    }
                }
            } else if (logoView != null) {
                toolbar.removeView(logoView);
                logoView = null;
            }
        } catch (Exception e) {
            Log.d("Warning", e.getStackTrace()[0].getMethodName() + " : " + e.toString());
        }
    }

    /******************
     * Event listeners
     ******************/

    /**
     * Enables components, or anyone with access to this activity, to listen for item touch events
     * on listView. If the same listener is passed more than once, only the first listener is added.
     * @param listener
     */
    public void addListViewOnItemTouchListener(RecyclerView.OnItemTouchListener listener) {
        currentFragment().addListViewOnItemTouchListener(listener);
    }

    /**
     * Removes all item touch listeners attached to this activity
     * Called when the activity
     */
    public void removeListViewOnItemTouchListeners() {
        currentFragment().removeListViewOnItemTouchListeners();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            cameraManager.startVision(JasonViewActivity.this);
        } else {
            Log.d("Warning", "Waiting for permission approval");
        }
    }
}
