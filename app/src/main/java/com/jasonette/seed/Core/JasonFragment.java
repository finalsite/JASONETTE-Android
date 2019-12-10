package com.jasonette.seed.Core;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;

import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.transition.Transition;
import com.bumptech.glide.request.target.SimpleTarget;
import com.jasonette.seed.BuildConfig;
import com.jasonette.seed.Component.JasonComponentFactory;
import com.jasonette.seed.Component.JasonImageComponent;
import com.jasonette.seed.Helper.JasonHelper;
import com.jasonette.seed.Launcher.Launcher;
import com.jasonette.seed.Service.agent.JasonAgentService;
import com.jasonette.seed.Service.vision.JasonVisionService;
import com.jasonette.seed.R;

import com.jasonette.seed.Section.ItemAdapter;
import com.yqritc.recyclerviewflexibledivider.HorizontalDividerItemDecoration;

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

import androidx.fragment.app.Fragment;

import static com.bumptech.glide.Glide.with;
import static java.lang.Integer.parseInt;

public class JasonFragment extends Fragment {
    private RecyclerView listView;
    public String url;
    public JasonModel model;
    public JSONObject preload;
    private ProgressBar loading;
    public Integer depth;

    private ArrayList<RecyclerView.OnItemTouchListener> listViewOnItemTouchListeners;

    private boolean firstResume = true;
    public boolean loaded;
    private boolean fetched;
    private boolean resumed;

    private ArrayList<JSONObject> section_items;
    public HashMap<String, Object> modules;
    private SwipeRefreshLayout swipeLayout;
    public LinearLayout sectionLayout;
    public RelativeLayout rootLayout;
    private ItemAdapter adapter;
    public View backgroundCurrentView;
    public WebView backgroundWebview;
    public ImageView backgroundImageView;
    private SurfaceView backgroundCameraView;
    public JasonVisionService cameraManager;
    private HorizontalDividerItemDecoration divider;
    private String previous_background;
    private JSONObject launch_action;
    private ArrayList<JSONObject> event_queue;
    ArrayList<View> layer_items;

    public View focusView = null;
    private Intent intent;
    private int requestCode;
    private Context context;

    Parcelable listState;
    JSONObject intent_to_resolve;
    public JSONObject agents = new JSONObject();
    private boolean isexecuting = false;

    /*************************************************************
     *
     * JASON ACTIVITY LIFECYCLE MANAGEMENT
     *
     ************************************************************/

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        context = getContext();
        // Initialize Parser instance
        JasonParser.getInstance(context);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        loaded = false;
        event_queue = new ArrayList<>();

        listViewOnItemTouchListeners = new ArrayList<>();

        layer_items = new ArrayList<>();
        // Setup Layouts
        intent = getArguments().getParcelable("intent");
        requestCode = getArguments().getInt("return");

        Launcher.setCurrentContext(context);

        // 1. Create root layout (Relative Layout)
        RelativeLayout.LayoutParams rlp = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.MATCH_PARENT);

        if(rootLayout == null) {
            // Create the root layout
            rootLayout = new RelativeLayout(context);
            // If we don't set it to clickable then clicks will bubble up to all fragments in the stack until it finds a button
            // meaning that we can click on white space where a covered fragment has a button and it will then act on that button...
            rootLayout.setClickable(true);
            rootLayout.setLayoutParams(rlp);
            rootLayout.setFitsSystemWindows(true);
            rootLayout.setBackgroundColor(JasonHelper.parse_color("rgb(255,255,255)"));
        }

        // 2. Add Swipe layout
        if(swipeLayout == null) {
            swipeLayout = new SwipeRefreshLayout(context);
            swipeLayout.setLayoutParams(rlp);
            rootLayout.addView(swipeLayout);
        }

        // 4. Create body.sections

        // 4.1. RecyclerView
        listView = new RecyclerView(context);
        listView.setItemViewCacheSize(20);
        listView.setHasFixedSize(true);

        // Create adapter passing in the sample user data
        adapter = new ItemAdapter(context, context, new ArrayList<JSONObject>());
        // Attach the adapter to the recyclerview to populate items
        listView.setAdapter(adapter);
        // Set layout manager to position the items
        listView.setLayoutManager(new LinearLayoutManager(context));

        // 4.2. LinearLayout
        if(sectionLayout == null){
            // Create LinearLayout
            sectionLayout = new LinearLayout(context);
            sectionLayout.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            sectionLayout.setLayoutParams(p);

            // Add RecyclerView to LinearLayout
            if(listView!=null) sectionLayout.addView(listView);

            // Add LinearLayout to Swipe Layout
            swipeLayout.addView(sectionLayout);
        }

        // 5. Start Loading
        RelativeLayout.LayoutParams loadingLayoutParams = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        loadingLayoutParams.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE);
        loading = new ProgressBar(context);
        loading.setLayoutParams(loadingLayoutParams);
        loading.setVisibility(View.INVISIBLE);
        rootLayout.addView(loading);

        firstResume = true;

        modules = ((JasonViewActivity) context).createModules();

        // Launch Action Payload Handling.
        // We will store this and queue it up at onLoad() after the first action call chain has finished
        // And then execute it on "unlock" of that call chain
        launch_action = null;

        if (intent.hasExtra("href")) {
            try {
                JSONObject href = new JSONObject(intent.getStringExtra("href"));
                launch_action = new JSONObject();
                launch_action.put("type", "$href");
                launch_action.put("options", href);
            } catch (Exception e) {
                Log.d("Warning", e.getStackTrace()[0].getMethodName() + " : " + e.toString());
            }
        } else if (intent.hasExtra("action")) {
            try {
                launch_action = new JSONObject(intent.getStringExtra("action"));
            } catch (Exception e) { }
        }

        if(intent.hasExtra("url")){
            url = intent.getStringExtra("url");
        } else {
            url = getString(R.string.url);
        }
        depth = intent.getIntExtra("depth", 0);
        preload = null;
        if (intent.hasExtra("preload")) {
            try {
                preload = new JSONObject(intent.getStringExtra("preload"));
            } catch (Exception e) {
                Log.d("Warning", e.getStackTrace()[0].getMethodName() + " : " + e.toString());
            }
        } else {
            if (intent.hasCategory(Intent.CATEGORY_LAUNCHER)) {
                // first time launch
                String launch_url = getString(R.string.launch);
                if (launch_url != null && launch_url.length() > 0) {
                    // if preload is specified, use that url
                    preload = (JSONObject)JasonHelper.read_json(launch_url, context);
                }
            }
        }

        // Create model
        model = ((JasonViewActivity) context).createModel(url, intent);

        Uri uri = intent.getData();
        if (uri != null && uri.getHost().contains("oauth")) {
            loaded = true; // in case of oauth process we need to set loaded to true since we know it's already been loaded.
            return rootLayout;
        }

        if (savedInstanceState != null) {
            // Restore model and url
            // Then rebuild the view
            try {
                url = savedInstanceState.getString("url");
                model.url = url;
                if(savedInstanceState.getString("jason")!=null) model.jason = new JSONObject(savedInstanceState.getString("jason"));
                if(savedInstanceState.getString("rendered")!=null) model.rendered = new JSONObject(savedInstanceState.getString("rendered"));
                if(savedInstanceState.getString("state")!=null) model.state = new JSONObject(savedInstanceState.getString("state"));
                if(savedInstanceState.getString("var")!=null) model.var = new JSONObject(savedInstanceState.getString("var"));
                if(savedInstanceState.getString("cache")!=null) model.cache = new JSONObject(savedInstanceState.getString("cache"));
                if(savedInstanceState.getString("params")!=null) model.params = new JSONObject(savedInstanceState.getString("params"));
                if(savedInstanceState.getString("session")!=null) model.session = new JSONObject(savedInstanceState.getString("session"));

                listState = savedInstanceState.getParcelable("listState");
                setup_body(model.rendered);
            } catch (Exception e){
                Log.d("Warning", e.getStackTrace()[0].getMethodName() + " : " + e.toString());
            }
        } else {
            onRefresh();
        }
        return rootLayout;
    }

    private void setup_agents() {
        try {
            JSONObject head = model.jason.getJSONObject("$jason").getJSONObject("head");
            if (head.has("agents")) {
                final JSONObject agents = head.getJSONObject("agents");
                Iterator<String> iterator = agents.keys();
                while (iterator.hasNext()) {
                    final String key = iterator.next();
                    ((JasonViewActivity) context).runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                JasonAgentService agentService = (JasonAgentService) ((Launcher) context.getApplicationContext()).services.get("JasonAgentService");
                                WebView agent = agentService.setup((JasonViewActivity) context, agents.getJSONObject(key), key);
                            } catch (JSONException e) { }
                        }
                    });
                }
            }
        } catch (Exception e) {
            Log.d("Warning", e.getStackTrace()[0].getMethodName() + " : " + e.toString());
        }
    }

    private void clear_agents() {
        try {
            JSONObject head = model.jason.getJSONObject("$jason").getJSONObject("head");
            final JSONObject agents = head.getJSONObject("agents");
            Iterator<String> iterator = agents.keys();
            while (iterator.hasNext()) {
                final String key = iterator.next();
                ((JasonViewActivity) context).runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            JasonAgentService agentService = (JasonAgentService) ((Launcher) context.getApplicationContext()).services.get("JasonAgentService");
                            JSONObject clearAction = new JSONObject();
                            JSONObject options = new JSONObject();
                            options.put("id", key);
                            clearAction.put("options", options);
                            agentService.clear(clearAction, context);
                        } catch (JSONException e) {
                        }
                    }
                });
            }
        } catch (Exception e) { }
    }

    public void onRefresh() {
        // offline: true logic
        // 1. check if the url + params signature exists
        // 2. if it does, use that to construct the model and setup_body
        // 3. Go on to fetching (it will be re-rendered if fetch is successful)

        // reset "offline mode"
        model.offline = false;

        loaded = false;

        // Reset local variables when reloading
        model.var = new JSONObject();

        SharedPreferences pref = context.getSharedPreferences("offline", 0);
        String signature = model.url + model.params.toString();
        if(pref.contains(signature)){
            String offline = pref.getString(signature, null);
            try {
                JSONObject offline_cache = new JSONObject(offline);
                model.jason = offline_cache.getJSONObject("jason");
                // For some reason some of the offline_caches were missing the "rendered" key, so we're falling back to the body in those instances
                if (offline_cache.has("rendered")) {
                    model.rendered = offline_cache.getJSONObject("rendered");
                }
                else {
                    model.rendered = model.jason.getJSONObject("$jason").getJSONObject("body");
                }
                model.offline = true; // we confirm that this model is offline so it shouldn't trigger error.json when network fails
                setup_body(model.rendered);
            } catch (Exception e) {
                Log.d("Warning", e.getStackTrace()[0].getMethodName() + " : " + e.toString());
            }
        } else if (preload != null) {
            setup_body(preload);
            preload = null;
        }

        // Fetch
        model.fetch();
    }

    public void onSwitchTab(String newUrl, String newParams, Intent intent) {
        // if tab transition, restore from stored tab using this.build()
        try {
            // remove all touch listeners before replacing
            // Use case : Tab bar
            removeListViewOnItemTouchListeners();
            // Set loaded to false since we're opening a new tab
            loaded = false;
            // Store the current model
            ((Launcher) context.getApplicationContext()).setTabModel(model.url+model.params, model);

            // clear agents
            clear_agents();

            // Retrieve the new view's model
            JasonModel m = ((Launcher) context.getApplicationContext()).getTabModel(newUrl + newParams);

            url = newUrl;

            if (m == null) {
                // refresh
                removeListViewOnItemTouchListeners();
                model = ((JasonViewActivity) context).createModel(newUrl, intent);
                onRefresh();
            } else {
                // build
                model = ((JasonViewActivity) context).setModel(m);
                setup_agents();
                setup_body(m.rendered);
            }
        } catch (Exception e) {
            Log.d("Warning", e.getStackTrace()[0].getMethodName() + " : " + e.toString());
        }
    }

    @Override
    public void onPause() {

        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                rootLayout.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            }
        });

        // Unregister since the activity is paused.
        LocalBroadcastManager.getInstance(context).unregisterReceiver(onSuccess);
        LocalBroadcastManager.getInstance(context).unregisterReceiver(onError);
        LocalBroadcastManager.getInstance(context).unregisterReceiver(onCall);

        // Clear agents
        clear_agents();

        // Store model to shared preference
        SharedPreferences pref = context.getSharedPreferences("model", 0);
        SharedPreferences.Editor editor = pref.edit();

        JSONObject temp_model = new JSONObject();
        try {
            if (model.url != null) temp_model.put("url", model.url);
            if (model.jason != null) temp_model.put("jason", model.jason);
            if (model.rendered != null) temp_model.put("rendered", model.rendered);
            if (model.state != null) temp_model.put("state", model.state);
            if (model.var != null) temp_model.put("var", model.var);
            if (model.cache != null) temp_model.put("cache", model.cache);
            if (model.params != null) temp_model.put("params", model.params);
            if (model.session != null) temp_model.put("session", model.session);
            if (model.action != null) temp_model.put("action", model.action);
            temp_model.put("depth", depth);
            if (model.url != null){
                editor.putString(model.url, temp_model.toString());
                editor.commit();
            }
        } catch (Exception e) {
            Log.d("Warning", e.getStackTrace()[0].getMethodName() + " : " + e.toString());
        }

        super.onPause();
    }

    @Override
    public void onResume() {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                rootLayout.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            }
        });

        // Register to receive messages.
        // We are registering an observer (mMessageReceiver) to receive Intents
        // with actions named "custom-event-name".
        Launcher.setCurrentContext(context);

        LocalBroadcastManager.getInstance(context).registerReceiver(onSuccess, new IntentFilter("success"));
        LocalBroadcastManager.getInstance(context).registerReceiver(onError, new IntentFilter("error"));
        LocalBroadcastManager.getInstance(context).registerReceiver(onCall, new IntentFilter("call"));

        resumed = true;

        SharedPreferences pref = context.getSharedPreferences("model", 0);
        if(model.url != null && pref.contains(model.url)) {
            String str = pref.getString(model.url, null);
            try {
                JSONObject temp_model = new JSONObject(str);
                if (temp_model.has("url")) model.url = temp_model.getString("url");
                if (temp_model.has("jason")) model.jason = temp_model.getJSONObject("jason");
                if (temp_model.has("rendered")) model.rendered = temp_model.getJSONObject("rendered");
                if (temp_model.has("state")) model.state = temp_model.getJSONObject("state");
                if (temp_model.has("var")) model.var = temp_model.getJSONObject("var");
                if (temp_model.has("cache")) model.cache = temp_model.getJSONObject("cache");
                if (temp_model.getInt("depth") == depth && temp_model.has("params")) model.params = temp_model.getJSONObject("params");
                if (temp_model.has("session")) model.session = temp_model.getJSONObject("session");
                if (temp_model.has("action")) model.action = temp_model.getJSONObject("action");

                // Delete shared preference after resuming
                SharedPreferences.Editor editor = pref.edit();
                editor.remove(model.url);
                editor.commit();
            } catch (Exception e) {
                Log.d("Warning", e.getStackTrace()[0].getMethodName() + " : " + e.toString());
            }
        }


        if (!firstResume) {
            JasonViewActivity currentView = (JasonViewActivity) context;
            // update the view to be using the model for this fragment
            currentView.setModel(model);

            onShow();
            // tell the view to setup it's header/options menu
            if (model.rendered != null && model.rendered.has("header")) {
                currentView.onPrepareOptionsMenu(null);
            } else {
                currentView.setup_header(null);
            }
        }
        firstResume = false;

        Uri uri = intent.getData();
        if(uri != null && uri.getHost().contains("oauth")) {
            try {
                intent_to_resolve = new JSONObject();
                intent_to_resolve.put("type", "success");
                intent_to_resolve.put("name", "oauth");
                intent_to_resolve.put("intent", intent);
            } catch (JSONException e) {
                Log.d("Warning", e.getStackTrace()[0].getMethodName() + " : " + e.toString());
            }
        }

        // Intent Handler
        // This part is for handling return values from external Intents triggered
        // We set "intent_to_resolve" from onActivityResult() below, and then process it here.
        // It's because onCall/onSuccess/onError callbacks are not yet attached when onActivityResult() is called.
        // Need to wait till this point.
        try {
            if(intent_to_resolve != null && intent_to_resolve.has("type")) {
                ((Launcher) context.getApplicationContext()).trigger(intent_to_resolve, (JasonViewActivity) context);
                intent_to_resolve = null;
            }
        } catch (Exception e) {
            Log.d("Warning", e.getStackTrace()[0].getMethodName() + " : " + e.toString());
        }

        super.onResume();

        if (listState != null) {
            listView.getLayoutManager().onRestoreInstanceState(listState);
        }
    }

    // This gets executed automatically when an external intent returns with result
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        try {
            // We can't process the intent here because
            // we need to wait until onResume gets triggered (which comes after this callback)
            // onResume reattaches all the onCall/onSuccess/onError callbacks to the current Activity
            // so we need to wait until that happens.
            // Therefore here we only set the "intent_to_resolve", and the actual processing is
            // carried out inside onResume()

            intent_to_resolve = new JSONObject();
            if(resultCode == JasonViewActivity.RESULT_OK) {
                intent_to_resolve.put("type", "success");
                intent_to_resolve.put("name", requestCode);
                intent_to_resolve.put("intent", intent);
            } else {
                intent_to_resolve.put("type", "error");
                intent_to_resolve.put("name", requestCode);
            }
        } catch (Exception e) {
            Log.d("Warning", e.getStackTrace()[0].getMethodName() + " : " + e.toString());
        }
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        if(model.url!=null) savedInstanceState.putString("url", model.url.toString());
        if(model.jason!=null) savedInstanceState.putString("jason", model.jason.toString());
        if(model.rendered!=null) savedInstanceState.putString("rendered", model.rendered.toString());
        if(model.state!=null) savedInstanceState.putString("state", model.state.toString());
        if(model.var!=null) savedInstanceState.putString("var", model.var.toString());
        if(model.cache!=null) savedInstanceState.putString("cache", model.cache.toString());
        if(model.params!=null) savedInstanceState.putString("params", model.params.toString());
        if(model.session!=null) savedInstanceState.putString("session", model.session.toString());
        if(model.action!=null) savedInstanceState.putString("action", model.action.toString());

        // Store RecyclerView state
        listState = listView.getLayoutManager().onSaveInstanceState();
        savedInstanceState.putParcelable("listState", listState);

        super.onSaveInstanceState(savedInstanceState);
    }

    /*************************************************************

     ## Event Handlers Rule ver2.

     1. When there's only $show handler
     - $show: Handles both initial load and subsequent show events

     2. When there's only $load handler
     - $load: Handles Only the initial load event

     3. When there are both $show and $load handlers
     - $load : handle initial load only
     - $show : handle subsequent show events only

     ## Summary

     $load:
     - triggered when view loads for the first time.
     $show:
     - triggered at load time + subsequent show events (IF $load handler doesn't exist)
     - NOT triggered at load time BUT ONLY at subsequent show events (IF $load handler exists)

     *************************************************************/

    public void onShow(){
        loaded = true;
        try {
            JSONObject head = model.jason.getJSONObject("$jason").getJSONObject("head");
            JSONObject events = head.getJSONObject("actions");
            simple_trigger("$show", new JSONObject(), context);
        } catch (Exception e){
            Log.d("Warning", e.getStackTrace()[0].getMethodName() + " : " + e.toString());
        }
    }

    void onLoad(){
        loaded = true;
        simple_trigger("$load", new JSONObject(), context);
        try {
            JSONObject head = model.jason.getJSONObject("$jason").getJSONObject("head");
            JSONObject events = head.getJSONObject("actions");
            if(events == null || !events.has("$load")) {
                onShow();
            }
            if (launch_action != null) {
                JSONObject copy = new JSONObject(launch_action.toString());
                launch_action = null;
                if (head.has("actions")) {
                    model.jason.getJSONObject("$jason").getJSONObject("head").getJSONObject("actions").put("$launch", copy);
                } else {
                    JSONObject actions = new JSONObject();
                    actions.put("$launch", copy);
                    model.jason.getJSONObject("$jason").getJSONObject("head").put("actions", actions);
                }
                simple_trigger("$launch", new JSONObject(), context);
            }
        } catch (Exception e){
            Log.d("Warning", e.getStackTrace()[0].getMethodName() + " : " + e.toString());
        }
    }

    void onForeground(){
        // Not implemented yet
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

            model.set("state", data);

            if (action instanceof JSONArray) {
                // resolve
                JasonParser.JasonParserListener listener = new JasonParser.JasonParserListener() {
                    @Override
                    public void onFinished(JSONObject reduced_action) {
                        final_call(reduced_action, data, event, getContext());
                    }
                };

                JasonParser.getInstance(context).parse("json", model.state, action, listener, context);

            } else {
                final_call((JSONObject)action, data, event, context);
            }
        } catch (Exception e) {
            Log.d("Warning", e.getStackTrace()[0].getMethodName() + " : " + e.toString());
        }
    }

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
                JasonParser.JasonParserListener listener = new JasonParser.JasonParserListener() {
                    @Override
                    public void onFinished(JSONObject parsed_options) {
                        try {
                            JSONObject action_with_parsed_options = new JSONObject(action.toString());
                            action_with_parsed_options.put("options", parsed_options);
                            exec(action_with_parsed_options, model.state, event, getContext());
                        } catch (Exception e) {
                            Log.d("Warning", e.getStackTrace()[0].getMethodName() + " : " + e.toString());
                        }
                    }
                };
                JasonParser.getInstance(context).parse("json", model.state, options, listener, context);
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
            if(action.has("options")) {
                Object options = action.get("options");
                JasonParser.JasonParserListener listener = new JasonParser.JasonParserListener() {
                    @Override
                    public void onFinished(JSONObject parsed_options) {
                        try {
                            invoke_lambda(action, data, parsed_options, getContext());
                        } catch (Exception e) {
                            Log.d("Warning", e.getStackTrace()[0].getMethodName() + " : " + e.toString());
                        }
                    }
                };
                JasonParser.getInstance(context).parse("json", model.state, options, listener, context);
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
                unlock(new JSONObject(), new JSONObject(), new JSONObject(), context);
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
                    Method method = JasonFragment.class.getMethod(methodName, JSONObject.class, JSONObject.class, JSONObject.class, Context.class);
                    method.invoke(this, action, model.state, event, context);
                } else {

                    className = type.substring(1, type.lastIndexOf('.'));


                    // Resolve classname by looking up the json files
                    String resolved_classname = null;
                    String jr = null;
                    try {
                        InputStream is = context.getAssets().open("file/$" + className + ".json");
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
            /**
                TODO: Figure out why this callback loop suddenly happens on events page with DebugLauncher as context instead
                of a jason view activity
                The point of this block appears to have been to pop a message to the developer to say the action was not
                implemented yet, the way this callback is getting hit however is also failing for an unknown reason
                This is then causing an infinite loop of trying to call the util banner with a DebugLauncher context
                when it should have a JasonViewActivity context
            **/
            /**

                try {
                    JSONObject alert_action = new JSONObject();
                    alert_action.put("type", "$util.banner");

                    JSONObject options = new JSONObject();
                    options.put("title", "Not implemented");
                    String type = action.getString("type");
                    options.put("description", action.getString("type") + " is not implemented yet.");

                    alert_action.put("options", options);


                    call(alert_action.toString(), new JSONObject().toString(), "{}", context);
                } catch (Exception e2){
                    Log.d("Warning", e2.getStackTrace()[0].getMethodName() + " : " + e2.toString());
                }
            **/
        }
    }

    // Our handler for received Intents. This will be called whenever an Intent
    // with an action named "custom-event-name" is broadcasted.
    public BroadcastReceiver onSuccess = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                String action_string = intent.getStringExtra("action");
                String data_string = intent.getStringExtra("data");
                String event_string = intent.getStringExtra("event");

                // Wrap return value with $jason
                JSONObject data = addToObject("$jason", data_string);

                // call next
                call(action_string, data.toString(), event_string, getContext());
            } catch (Exception e){
                Log.d("Warning", e.getStackTrace()[0].getMethodName() + " : " + e.toString());
            }
        }
    };

    public BroadcastReceiver onError = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                String action_string = intent.getStringExtra("action");
                String data_string = intent.getStringExtra("data");
                String event_string = intent.getStringExtra("event");

                // Wrap return value with $jason
                JSONObject data = addToObject("$jason", data_string);

                // call next
                call(action_string, data.toString(), event_string, getContext());
            } catch (Exception e){
                Log.d("Warning", e.getStackTrace()[0].getMethodName() + " : " + e.toString());
            }
        }
    };

    public BroadcastReceiver onCall = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                String action_string = intent.getStringExtra("action");
                String event_string = intent.getStringExtra("event");
                String data_string = intent.getStringExtra("data");
                if(data_string == null){
                    data_string = new JSONObject().toString();
                }

                // Wrap return value with $jason
                JSONObject data = addToObject("$jason", data_string);

                // call next
                call(action_string, data.toString(), event_string, getContext());
            } catch (Exception e){
                Log.d("Warning", e.getStackTrace()[0].getMethodName() + " : " + e.toString());
            }
        }
    };

    private JSONObject addToObject(String prop, String json_data) {
        JSONObject data = new JSONObject();
        try {
            // Detect if the result is JSONObject, JSONArray, or String
            if(json_data.trim().startsWith("[")) {
                // JSONArray
                data = new JSONObject().put("$jason", new JSONArray(json_data));
            } else if(json_data.trim().startsWith("{")){
                // JSONObject
                data = new JSONObject().put("$jason", new JSONObject(json_data));
            } else {
                // String
                data = new JSONObject().put("$jason", json_data);
            }
        } catch (Exception e){
            Log.d("Warning", e.getStackTrace()[0].getMethodName() + " : " + e.toString());
        }
        return data;
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

    public void lambda(final JSONObject action, JSONObject data, JSONObject event, Context context){
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
                    JasonParser.JasonParserListener listener = new JasonParser.JasonParserListener() {
                        @Override
                        public void onFinished(JSONObject parsed_options) {
                            try {
                                JSONObject wrapped = new JSONObject();
                                wrapped.put("$jason", parsed_options);
                                call(lambda.toString(), wrapped.toString(), caller, getContext());
                            } catch (Exception e){
                                JasonHelper.next("error", action, new JSONObject(), new JSONObject(), getContext());
                            }
                        }
                    };
                    JasonParser.getInstance(context).parse("json", model.state, new_options, listener, context);

                }
                // 3. If `options` doesn't exist, forward the data from the previous action
                else {
                    call(lambda.toString(), data.toString(), caller, context);
                }
            }
        } catch (Exception e){
            Log.d("Warning", e.getStackTrace()[0].getMethodName() + " : " + e.toString());
            JasonHelper.next("error", action, new JSONObject(), new JSONObject(), context);
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
                            if (!urlSet.contains(((JSONArray) val).getString(i))) {
                                urlSet.add(((JSONArray) val).getString(i));
                            }
                        }
                    } else if (val instanceof String && !urlSet.contains(val)) {
                        urlSet.add(((String) val));
                    }
                }
                if(urlSet.size()>0) {
                    JSONObject refs = new JSONObject();

                    CountDownLatch latch = new CountDownLatch(urlSet.size());
                    ExecutorService taskExecutor = Executors.newFixedThreadPool(urlSet.size());
                    for (String key : urlSet) {
                        taskExecutor.submit(new JasonRequire(key, latch, refs, model.client, context));
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
                        if(val instanceof JSONArray){
                            JSONArray ret = new JSONArray();
                            for (int i = 0; i < ((JSONArray)val).length(); i++) {
                                String url = ((JSONArray) val).getString(i);
                                ret.put(refs.get(url));
                            }
                            res.put(key, ret);
                        } else if(val instanceof String){
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
    }

    public void render(final JSONObject action, final JSONObject data, final JSONObject event, final Context context){
        try{
            String template_name = "body";
            String type = "json";

            if (action.has("options")) {
                JSONObject options = action.getJSONObject("options");
                if (options.has("template")) {
                    template_name = options.getString("template");
                }
                // parse the template with JSON
                if (options.has("data")) {
                    data.put("$jason", options.get("data"));
                }

                if (options.has("type")) {
                    type = options.getString("type");
                }
            }

            JSONObject head = model.jason.getJSONObject("$jason").getJSONObject("head");

            try {
                // If the request returned "head_styles" we want to update the model to be able to use them for rendering.
                JSONObject template_styles = data.getJSONObject("$get").getJSONObject("head_styles");
                ((JasonViewActivity) context).stylesheet.merge(template_styles);
            } catch (JSONException e) {}

            JSONObject templates = head.getJSONObject("templates");

            JSONObject template = templates.getJSONObject(template_name);
            JasonParser.JasonParserListener listener = new JasonParser.JasonParserListener() {
                @Override
                public void onFinished(JSONObject body) {
                    setup_body(body);
                    JasonHelper.next("success", action, data, event, getContext());
                }
            };

            JasonParser.getInstance(context).parse(type, data, template, listener, context);

        } catch (Exception e){
            Log.d("Warning", e.getStackTrace()[0].getMethodName() + " : " + e.toString());
            JasonHelper.next("error", action, new JSONObject(), event, context);
        }
    }

    public void href(final JSONObject action, JSONObject data, JSONObject event, Context passed_context) {
        ((JasonViewActivity) context).href(action, data, event, context);
    }

    public void set(final JSONObject action, JSONObject data, JSONObject event, Context context){
        try{
            if (action.has("options")) {
                JSONObject options = action.getJSONObject("options");
                model.var = JasonHelper.merge(model.var, options);
            }
            JasonHelper.next("success", action, new JSONObject(), event, context);

        } catch (Exception e){
            Log.d("Warning", e.getStackTrace()[0].getMethodName() + " : " + e.toString());
        }
    }

    public void back ( final JSONObject action, JSONObject data, JSONObject event, Context context) {
        ((JasonViewActivity) context).back(action, data, event, context);
    }

    public void close ( final JSONObject action, JSONObject data, JSONObject event, Context context) {
        ((JasonViewActivity) context).close(action, data, event, context);
    }

    public void ok ( final JSONObject action, JSONObject data, JSONObject event, Context context){
        try {
            back(action, data, event, context);

            // if we don't have a request code then there probably wasn't something expecting a return
            if (requestCode == 0) return;

            Intent intent = new Intent();
            if (action.has("options")) {
                intent.putExtra("return", action.get("options").toString());
            }
            JSONObject resolve_intent = new JSONObject();
            resolve_intent.put("type", "success");
            resolve_intent.put("name", requestCode);
            resolve_intent.put("intent", intent);
            ((Launcher)context.getApplicationContext()).trigger(resolve_intent, (JasonViewActivity)context);
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
            ((JasonViewActivity) context).runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        loading.setVisibility(View.GONE);
                        if (swipeLayout != null) {
                            swipeLayout.setRefreshing(false);
                        }

                    } catch (Exception e) {
                        Log.d("Warning", e.getStackTrace()[0].getMethodName() + " : " + e.toString());
                    }
                }
            });
        }
    }
    
    public void appReload ( final JSONObject action, JSONObject data, JSONObject event, Context context){
        // clear offline caches and tab models
        ((Launcher) context.getApplicationContext()).clearTabModels();
        context.getSharedPreferences("offline", 0).edit().clear().commit();
        startActivity(new Intent(context, SplashActivity.class));
    }

    public void reload ( final JSONObject action, JSONObject data, JSONObject event, Context context){
        if(model != null){
            onRefresh();
            try {
                JasonHelper.next("success", action, new JSONObject(), event, context);
            } catch (Exception e) {
                Log.d("Warning", e.getStackTrace()[0].getMethodName() + " : " + e.toString());
            }
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
        View v1 = ((JasonViewActivity) context).getWindow().getDecorView().getRootView();
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
                    if(position.equalsIgnoreCase("top")) {
                        listView.smoothScrollToPosition(0);
                    } else if(position.equalsIgnoreCase("bottom")) {
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
    /*************************************************************
     *
     * JASON VIEW
     *
     ************************************************************/

    public void build(JSONObject jason){
        // set fetched to true since build() is only called after network.request succeeds
        fetched = true;
        if(jason != null) {
            try {

                try {
                    JasonViewActivity activity = (JasonViewActivity) getActivity();
                    JSONObject head = jason.getJSONObject("$jason").getJSONObject("head");
                    if (head.has("styles")) {
                        activity.stylesheet.merge(head.getJSONObject("styles"));
                    }
                } catch (Exception e) { }
                if (jason.getJSONObject("$jason").has("body")) {
                    final JSONObject body;
                    body = jason.getJSONObject("$jason").getJSONObject("body");
                    // If the returned body matches the rendered body skip build phase.
                    if (model.rendered == null || !model.rendered.toString().equalsIgnoreCase(body.toString())) {
                        model.set("state", new JSONObject());
                        setup_body(body);
                    }
                }

                if (jason.getJSONObject("$jason").has("head")) {
                    final JSONObject head = jason.getJSONObject("$jason").getJSONObject("head");

                    if (head.has("agents")) {
                        final JSONObject agents = head.getJSONObject("agents");
                        Iterator<String> iterator = agents.keys();
                        while (iterator.hasNext()) {
                            final String key = iterator.next();
                            ((JasonViewActivity) context).runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        JasonAgentService agentService = (JasonAgentService)((Launcher) context.getApplicationContext()).services.get("JasonAgentService");
                                        WebView agent = agentService.setup(((JasonViewActivity) context), agents.getJSONObject(key), key);
                                        rootLayout.addView(agent);
                                    } catch (JSONException e) {
                                    }
                                }
                            });
                        }
                    }

                    if (head.has("templates") && head.getJSONObject("templates").has("body")) {
                        model.set("state", new JSONObject());
                        render(new JSONObject(), model.state, new JSONObject(), context);

                        // return here so onLoad() below will NOT be triggered.
                        // onLoad() will be triggered after render has finished
                        return;
                    }
                }
                /**
                    I'm not sure what case this is handling but this is triggering load events to
                    happen before the view has rendered, there is another call to this elsewhere after
                    the view has rendered that seems to always still be called if this one isn't.
                    It was noted that this onLoad was breaking initial scroll. (APP-25)
                    but it seems to once again be required with the fragment changes (APP-42)
                **/
                if (!loaded) {
                    onLoad();
                }

            } catch (JSONException e) {
                Log.d("Warning", e.getStackTrace()[0].getMethodName() + " : " + e.toString());
            }
        }
    }

    public void setup_body(final JSONObject body) {

        // Store to offline cache in case head.offline == true
        try {
            model.rendered = body;
            ((JasonViewActivity) context).invalidateOptionsMenu();
            if(model.jason != null && model.jason.has("$jason") && model.jason.getJSONObject("$jason").has("head") && model.jason.getJSONObject("$jason").getJSONObject("head").has("offline")){
                SharedPreferences pref = context.getSharedPreferences("offline", 0);
                SharedPreferences.Editor editor = pref.edit();

                String signature = model.url + model.params.toString();
                JSONObject offline_cache = new JSONObject();
                offline_cache.put("jason", model.jason);
                offline_cache.put("rendered", model.rendered);

                editor.putString(signature, offline_cache.toString());
                editor.commit();

            }
        } catch (Exception e){
            Log.d("Warning", e.getStackTrace()[0].getMethodName() + " : " + e.toString());
        }

        ((JasonViewActivity) context).runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    // First need to remove all handlers because they will be reattached after render
                    removeListViewOnItemTouchListeners();
                    if(swipeLayout !=null) {
                        swipeLayout.setRefreshing(false);
                    }
                    sectionLayout.setBackgroundColor(JasonHelper.parse_color("rgb(255,255,255)"));
                    ((JasonViewActivity) context).getWindow().getDecorView().setBackgroundColor(JasonHelper.parse_color("rgb(255,255,255)"));

                    Object bg = null;
                    if (body.has("style") && body.getJSONObject("style").has("background")) {
                        bg = body.getJSONObject("style").get("background");
                    } else if (body.has("background")) {
                        bg = body.get("background");
                    }


                    if (body.has("gradient_background")) {
                        JSONArray backgroundGradient = body.getJSONArray("gradient_background");

                        bg = new GradientDrawable(
                                GradientDrawable.Orientation.BL_TR,
                                new int[]{
                                        JasonHelper.parse_color(((JSONArray) backgroundGradient).getString(0)),
                                        JasonHelper.parse_color(((JSONArray) backgroundGradient).getString(1)),
                                });

                    }

                    // Background Logic
                    if (bg != null) {
                        // sectionLayout must be transparent to see the background
                        sectionLayout.setBackgroundColor(JasonHelper.parse_color("rgba(0,0,0,0)"));

                        // we remove the current view from the root layout
                        Boolean needs_redraw = false;
                        if (backgroundCurrentView != null) {
                            String current_background = bg.toString();
                            if(previous_background == null) {
                                needs_redraw = true;
                                rootLayout.removeView(backgroundCurrentView);
                                backgroundCurrentView = null;
                            } else if(current_background.equalsIgnoreCase(previous_background)) {
                                needs_redraw = false;
                            } else {
                                needs_redraw = true;
                                rootLayout.removeView(backgroundCurrentView);
                                backgroundCurrentView = null;
                            }
                            previous_background = current_background;
                        } else {
                            needs_redraw = true;
                            rootLayout.removeView(backgroundCurrentView);
                            backgroundCurrentView = null;
                        }

                        if(needs_redraw) {
                            if (bg instanceof String) {
                                String background = (String)bg;
                                JSONObject c = new JSONObject();
                                c.put("url", background);
                                if (background.matches("(file|http[s]?):\\/\\/.*")) {
                                    if (backgroundImageView == null) {
                                        backgroundImageView = new ImageView(context);
                                    }

                                    backgroundCurrentView = backgroundImageView;

                                    DiskCacheStrategy cacheStrategy = DiskCacheStrategy.AUTOMATIC;
                                    with(context)
                                            .load(JasonImageComponent.resolve_url(c, context))
                                            .diskCacheStrategy(cacheStrategy)
                                            .centerCrop()
                                            .into(backgroundImageView);
                                } else if (background.matches("data:image.*")) {
                                    String base64;
                                    if (background.startsWith("data:image/jpeg")) {
                                        base64 = background.substring("data:image/jpeg;base64,".length());
                                    } else if (background.startsWith("data:image/png")) {
                                        base64 = background.substring("data:image/png;base64,".length());
                                    } else if (background.startsWith("data:image/gif")) {
                                        base64 = background.substring("data:image/gif;base64,".length());
                                    } else {
                                        base64 = "";    // exception
                                    }
                                    byte[] bs = Base64.decode(base64, Base64.NO_WRAP);

                                    with(context).load(bs).into(new SimpleTarget<Drawable>() {
                                        @Override
                                        public void onResourceReady(Drawable resource, Transition<? super Drawable> glideAnimation) {
                                            sectionLayout.setBackground(resource);
                                        }
                                    });
                                } else if (background.equalsIgnoreCase("camera")) {
                                    int side = JasonVisionService.FRONT;
                                    if (cameraManager == null) {
                                        cameraManager = new JasonVisionService(((JasonViewActivity) context));
                                        backgroundCameraView = cameraManager.getView();
                                    }
                                    cameraManager.setSide(side);
                                    backgroundCurrentView = backgroundCameraView;
                                } else {
                                    sectionLayout.setBackgroundColor(JasonHelper.parse_color(background));
                                    ((JasonViewActivity) context).getWindow().getDecorView().setBackgroundColor(JasonHelper.parse_color(background));
                                }
                            } else if (bg instanceof GradientDrawable) {
                                sectionLayout.setBackground((Drawable) bg);
                            } else {
                                JSONObject background = (JSONObject)bg;
                                String type = background.getString("type");
                                if (type.equalsIgnoreCase("html")) {
                                    // on Android the tabs work differently from iOS
                                    // => All tabs share a single activity.
                                    // therefore, unlike ios where each viewcontroller owns a web container through "$webcontainer" id,
                                    // on android we need to distinguish between multiple web containers through URL
                                    background.put("id", "$webcontainer@" + model.url);
                                    JasonAgentService agentService = (JasonAgentService)((Launcher) context.getApplicationContext()).services.get("JasonAgentService");
                                    backgroundWebview = agentService.setup((JasonViewActivity) context, background, "$webcontainer@" + model.url);
                                    backgroundWebview.getSettings().setUserAgentString(backgroundWebview.getSettings().getUserAgentString().replace("; wv", "") + " Finalsite-App/" + BuildConfig.VERSION_NAME);
                                    backgroundWebview.setVisibility(View.VISIBLE);
                                    // not interactive by default;
                                    Boolean responds_to_webview = false;

                                    /**

                                     if has an 'action' attribute
                                     - if the action is "type": "$default"
                                     => Allow touch. The visit will be handled in the agent handler
                                     - if the action is everything else
                                     => Allow touch. The visit will be handled in the agent handler
                                     if it doesn't have an 'action' attribute
                                     => Don't allow touch.

                                     **/
                                    if (background.has("action")) {
                                        responds_to_webview = true;
                                    }
                                    if (responds_to_webview) {
                                        // webview receives click
                                        backgroundWebview.setOnTouchListener(null);
                                    } else {
                                        // webview shouldn't receive click
                                        backgroundWebview.setOnTouchListener(new View.OnTouchListener() {
                                            @Override
                                            public boolean onTouch(View v, MotionEvent event) {
                                                return true;
                                            }
                                        });
                                    }
                                    backgroundCurrentView = backgroundWebview;
                                } else if (type.equalsIgnoreCase("camera")) {
                                    int side = JasonVisionService.FRONT;
                                    if (background.has("options")) {
                                        JSONObject options = background.getJSONObject("options");
                                        if (options.has("device") && options.getString("device").equals("back")) {
                                            side = JasonVisionService.BACK;
                                        }
                                    }

                                    if (cameraManager == null) {
                                        cameraManager = new JasonVisionService((JasonViewActivity) context);
                                        backgroundCameraView = cameraManager.getView();
                                    }
                                    cameraManager.setSide(side);
                                    backgroundCurrentView = backgroundCameraView;
                                }
                            }

                            if (backgroundCurrentView != null) {
                                RelativeLayout.LayoutParams rlp = new RelativeLayout.LayoutParams(
                                        RelativeLayout.LayoutParams.MATCH_PARENT,
                                        RelativeLayout.LayoutParams.MATCH_PARENT);

                                // Update Layout after the rootLayout has finished rendering in order to change the background dimension
                                rootLayout.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {

                                    @Override
                                    public void onGlobalLayout() {
                                        rootLayout.getViewTreeObserver().removeOnGlobalLayoutListener(this);

                                        // header
                                        int toolbarHeight = 0;

                                        RelativeLayout.LayoutParams newrlp = new RelativeLayout.LayoutParams(
                                                RelativeLayout.LayoutParams.MATCH_PARENT,
                                                RelativeLayout.LayoutParams.MATCH_PARENT);
                                        newrlp.setMargins(0, toolbarHeight, 0, 0);
                                        backgroundCurrentView.setLayoutParams(newrlp);
                                    }
                                });
                                if (backgroundCurrentView.getParent() != null) {
                                    ((RelativeLayout) backgroundCurrentView.getParent()).removeView(backgroundCurrentView);
                                }
                                rootLayout.addView(backgroundCurrentView, 0, rlp);
                            }
                        }
                    }

                    rootLayout.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                        @Override
                        public void onGlobalLayout() {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
                                rootLayout.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                            } else {
                                rootLayout.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                            }
                            if (focusView != null) {
                                focusView.requestFocus();
                                InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
                                imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, InputMethodManager.HIDE_IMPLICIT_ONLY);
                            }
                        }
                    });

                    // Set header
                    if (body.has("header")) {
                        ((JasonViewActivity) context).setup_header(body.getJSONObject("header"));
                    } else {
                        ((JasonViewActivity) context).setup_header(null);
                    }
                    // Set sections
                    if (body.has("sections")) {
                        setup_sections(body.getJSONArray("sections"));
                        String border = "#eaeaea"; // Default color

                        if (body.has("style") && body.getJSONObject("style").has("border")) {
                            border = body.getJSONObject("style").getString("border");
                        }

                        if (divider != null) {
                            listView.removeItemDecoration(divider);
                            divider = null;
                        }

                        if (!border.equalsIgnoreCase("none")) {
                            int color = JasonHelper.parse_color(border);
                            listView.removeItemDecoration(divider);
                            divider = new HorizontalDividerItemDecoration.Builder(context)
                                    .color(color)
                                    .showLastDivider()
                                    .positionInsideItem(true)
                                    .build();
                            listView.addItemDecoration(divider);
                        }
                    } else {
                        setup_sections(null);
                    }

                    swipeLayout.setEnabled(false);
                    if (model.jason != null && model.jason.has("$jason") && model.jason.getJSONObject("$jason").has("head")) {
                        final JSONObject head = model.jason.getJSONObject("$jason").getJSONObject("head");
                        if (head.has("actions") && head.getJSONObject("actions").has("$pull")) {
                            // Setup refresh listener which triggers new data loading
                            swipeLayout.setEnabled(true);
                            swipeLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
                                @Override
                                public void onRefresh() {
                                    try {
                                        JSONObject action = head.getJSONObject("actions").getJSONObject("$pull");
                                        call(action.toString(), new JSONObject().toString(), "{}", context);
                                    } catch (Exception e) {
                                        Log.d("Warning", e.getStackTrace()[0].getMethodName() + " : " + e.toString());
                                    }
                                }
                            });
                        }
                    }

                    if (body.has("style")) {
                        JSONObject style = body.getJSONObject("style");
                        if (style.has("align") && style.getString("align").equalsIgnoreCase("bottom")) {
                            ((LinearLayoutManager) listView.getLayoutManager()).setStackFromEnd(true);
                            listView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                                @Override
                                public void onLayoutChange(View view, int i, int i1, int i2, int i3, int i4, int i5, int i6, int i7) {
                                    if (i3 < i7) {
                                        listView.postDelayed(new Runnable() {
                                            @Override
                                            public void run() {
                                                if (listView.getAdapter().getItemCount() > 0) {
                                                    listView.smoothScrollToPosition( listView.getAdapter().getItemCount() - 1);
                                                }
                                            }
                                        }, 100);
                                    }
                                }
                            });
                        }
                    }

                    // Set footer
                    if (body.has("footer")) {
                        ((JasonViewActivity) context).setup_footer(body.getJSONObject("footer"));
                    }

                    // Set layers
                    if (body.has("layers")){
                        setup_layers(body.getJSONArray("layers"));
                    } else {
                        setup_layers(null);
                    }
                    rootLayout.requestLayout();

                    // if the first time being loaded and if the content has finished fetching (not via remote: true)
                    if (!loaded && fetched) {
                        // trigger onLoad.
                        // onLoad shouldn't be triggered when just drawing the offline cached view initially
                        onLoad();
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void setup_sections(JSONArray sections){
        section_items = new ArrayList<JSONObject>();
        if (sections != null) {
            try {
                for (int i = 0; i < sections.length(); i++) {
                    JSONObject section = sections.getJSONObject(i);

                    // Determine if it's a horizontal section or vertical section
                    // if it's vertical, simply keep adding to the section as individual items
                    // if it's horizontal, start a nested recyclerview
                    if (section.has("type") && section.getString("type").equals("horizontal")) {
                        // horizontal type
                        // TEMPORARY: Add header as an item
                        if (section.has("header")) {
                            JSONObject header = section.getJSONObject("header");
                            header.put("isHeader", true);
                            section_items.add(header);
                        }
                        if (section.has("items")) {
                            // Let's add the entire section as an item, under:
                            // "horizontal_section": [items]
                            JSONObject horizontal_section = new JSONObject();
                            horizontal_section.put("horizontal_section", section);
                            section_items.add(horizontal_section);
                        }
                    } else {
                        // vertical type (default)
                        if (section.has("header")) {
                            JSONObject header = section.getJSONObject("header");
                            header.put("isHeader", true);
                            section_items.add(header);
                        }
                        if (section.has("items")) {
                            JSONArray items = section.getJSONArray("items");
                            for (int j = 0; j < items.length(); j++) {
                                JSONObject item = items.getJSONObject(j);
                                section_items.add(item);
                            }
                        }
                    }
                }
            } catch (JSONException e) {
                Log.d("Warning", e.getStackTrace()[0].getMethodName() + " : " + e.toString());
            }
        }

        if (adapter == null || adapter.getItems().size() == 0) {
            // Create adapter passing in the sample user data
            adapter = new ItemAdapter(context, context, section_items);
            // Attach the adapter to the recyclerview to populate items
            listView.setAdapter(adapter);
            // Set layout manager to position the items
            listView.setLayoutManager(new LinearLayoutManager(context));
        } else {
            //ArrayList<JSONObject> old_section_items = adapter.getItems();
            adapter.updateItems(section_items);
            adapter.notifyDataSetChanged();
        }
    }

    private void setup_layers(JSONArray layers){
        try{
            if (layer_items != null) {
                for (int j = 0; j < layer_items.size(); j++) {
                    View layerView = layer_items.get(j);
                    rootLayout.removeView(layerView);
                }
                layer_items = new ArrayList<View>();
            }
            if (layers != null) {
                for(int i = 0; i < layers.length(); i++){
                    JSONObject layer = layers.getJSONObject(i);
                    if(layer.has("type")){
                        View view = JasonComponentFactory.build(null, layer, null, context);
                        JasonComponentFactory.build(view, layer, null, context);
                        stylize_layer(view, layer);
                        rootLayout.addView(view);
                        layer_items.add(view);
                    }
                }
            }
        } catch (Exception e) {
            Log.d("Warning", e.getStackTrace()[0].getMethodName() + " : " + e.toString());
        }
    }

    private void stylize_layer(View view, JSONObject component){
        try{
            JSONObject style = JasonHelper.style(component, context);
            RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) view.getLayoutParams();

            if (style.has("top")) {
                int top = (int) JasonHelper.pixels(context, style.getString("top"), "vertical");
                params.topMargin = top;
                params.addRule(RelativeLayout.ALIGN_PARENT_TOP);
            }
            if (style.has("left")) {
                int left = (int) JasonHelper.pixels(context, style.getString("left"), "horizontal");
                params.leftMargin = left;
                params.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
            }
            if (style.has("right")) {
                int right = (int) JasonHelper.pixels(context, style.getString("right"), "horizontal");
                params.rightMargin = right;
                params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
            }
            if (style.has("bottom")) {
                int bottom = (int) JasonHelper.pixels(context, style.getString("bottom"), "vertical");
                params.bottomMargin = bottom;
                params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
            }
            view.setLayoutParams(params);
        } catch (Exception e){
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
        if (!listViewOnItemTouchListeners.contains(listener)) {
            listViewOnItemTouchListeners.add(listener);
            listView.addOnItemTouchListener(listener);
        }
    }

    /**
     * Removes all item touch listeners attached to this activity
     * Called when the activity
     */
    public void removeListViewOnItemTouchListeners() {
        for (RecyclerView.OnItemTouchListener listener: listViewOnItemTouchListeners) {
            listView.removeOnItemTouchListener(listener);
            listViewOnItemTouchListeners.remove(listener);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            cameraManager.startVision((JasonViewActivity) context);
        } else {
            Log.d("Warning", "Waiting for permission approval");
        }
    }
}
