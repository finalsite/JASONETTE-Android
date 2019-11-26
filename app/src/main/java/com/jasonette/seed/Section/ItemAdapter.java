package com.jasonette.seed.Section;

import android.content.Context;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.transition.Transition;
import com.bumptech.glide.request.target.SimpleTarget;
import com.jasonette.seed.Component.JasonComponentFactory;
import com.jasonette.seed.Component.JasonImageComponent;
import com.jasonette.seed.Helper.JasonHelper;
import com.jasonette.seed.Core.JasonViewActivity;
import com.jasonette.seed.Lib.PageIndicatorDecoration;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;


/********************************************************
 *
 * Here's the hierarchy:
 *
 *  - ViewHolder
 *      - ContentView
 *          - Layout
 *              - Component
 *
 ********************************************************/



public class ItemAdapter extends RecyclerView.Adapter <ItemAdapter.ViewHolder>{
    public static final int DATA = 0;

    Context context;
    Context root_context;
    ArrayList<JSONObject> items;
    ArrayList<JSONObject> cloned_items;
    Map<String, Integer> signature_to_type = new HashMap<String,Integer>();
    Map<Integer, String> type_to_signature = new HashMap<Integer, String>();
    ViewHolderFactory factory = new ViewHolderFactory();
    Boolean isHorizontalScroll = false;
    ImageView backgroundImageView;

    /********************************************************
     *
     * Root level RecyclerView/ViewHolder logic
     *
     ********************************************************/

    public class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener{
        ArrayList<View> subviews;
        String type;
        public ViewHolder(View itemView){
            super(itemView);
            this.subviews = new ArrayList<View>();
            itemView.setOnClickListener(this);
            this.type = "item";
        }
        public View getView(){
            return this.itemView;
        }
        @Override
        public void onClick(View view) {
            int position = getAdapterPosition();
            JSONObject item = (JSONObject)view.getTag();
            try {
                if (item.has("action")) {
                    JSONObject action = item.getJSONObject("action");
                    ((JasonViewActivity)root_context).call(action.toString(), new JSONObject().toString(), "{}", view.getContext());
                } else if (item.has("href")){
                    JSONObject href = item.getJSONObject("href");
                    JSONObject action = new JSONObject().put("type", "$href").put("options", href);
                    ((JasonViewActivity)root_context).call(action.toString(), new JSONObject().toString(), "{}", view.getContext());
                }
            } catch (Exception e){ }
        }
    }

    public ItemAdapter(Context root_context, Context context, ArrayList<JSONObject> items) {
        this.items = items;
        this.cloned_items = new ArrayList<JSONObject>();
        this.cloned_items.addAll(items);
        this.context = context;
        this.root_context = root_context;
    }

    public void updateItems(ArrayList<JSONObject> items) {
        this.items = items;
        this.cloned_items = new ArrayList<JSONObject>();
        this.cloned_items.addAll(items);
    }
    public ArrayList<JSONObject> getItems() {
        return this.items;
    }

    public void filter(String text) {
        this.items.clear();
        if(text.isEmpty()){
            this.items.addAll(this.cloned_items);
        } else{
            text = text.toLowerCase();
            for(JSONObject item: this.cloned_items){
                if(item.toString().toLowerCase().contains(text)){
                    this.items.add(item);
                }
            }
        }
        notifyDataSetChanged();
    }

    @Override
    // For determining the view type.
    // 1. Generate a signature using the JSON markup and assign it to signature_to_type.
    // 2. If the signature already exists, return the type.
    public int getItemViewType(int position) {

        JSONObject item = this.items.get(position);

        // if the key starts with "horizontal_section",
        // we deal with it in a special manner.
        // Assuming that all items for a horizontal section will have the same prototype,
        // we can generate the signature from just one of its items.

        String stringified_item;
        if(item.has("horizontal_section")){
            try {
                JSONObject horizontal_section = item.getJSONObject("horizontal_section");
                JSONArray horizontal_section_items = horizontal_section.getJSONArray("items");

                // assuming that the section would contain at least one item,
                // we will take the first item from the section and generate the signature
                JSONObject first_item = horizontal_section_items.getJSONObject(0);
                stringified_item = "[" + first_item.toString() + "]";
            } catch (Exception e) {
                stringified_item = item.toString();
            }
        } else {
            stringified_item = item.toString();
        }

        // Simplistic way of transforming an item JSON into a generic string, by replacing out all non-structural values
        // - replace out text and url
        String regex = "\"(url|text)\"[ ]*:[ ]*\"([^\"]+)\"";
        String signature = stringified_item.replaceAll(regex, "\"jason\":\"jason\"");
        // - replace out 'title' and 'description'
        regex = "\"(title|description)\"[ ]*:[ ]*\"([^\"]+)\"";
        signature = signature.replaceAll(regex, "\"jason\":\"jason\"");

        if(signature_to_type.containsKey(signature)){
            // if the signature exists, get the type using the signature
            return signature_to_type.get(signature);
        } else {
            // If it's a new signature, set the mapping between jason and type, both ways

            // Increment the index (new type) first.
            int index = signature_to_type.size();

            // 1. jason => type: assign that index as the type for the signature
            signature_to_type.put(signature, index);

            // 2. type => jason: assign the stringified item so it can be used later
            //  Need to use the original instance instead of the stubbed out "signature" since some components requre url or text attributes to instantiate (create)
            type_to_signature.put(index, stringified_item);
            //type_to_signature.put(index, signature);

            // Return the new index;
            return index;
        }
    }

    @Override
    public ItemAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {

        String signatureString = type_to_signature.get(new Integer(viewType));
        ItemAdapter.ViewHolder viewHolder;

        if (signatureString.startsWith("[")) {
            // Horizontal Section => Build a ViewHolder with a horizontally scrolling RecyclerView

            // 1. Create RecyclerView
            RecyclerView horizontalListView = new RecyclerView(parent.getContext());
            horizontalListView.setLayoutManager(new LinearLayoutManager(horizontalListView.getContext(), LinearLayoutManager.HORIZONTAL, false));
            horizontalListView.setNestedScrollingEnabled(false);

            // 2. Create Adapter
            ItemAdapter horizontal_adapter = new ItemAdapter(context, horizontalListView.getContext(), new ArrayList<JSONObject>());
            horizontal_adapter.isHorizontalScroll = true;

            // 3. Connect RecyclerView with Adapter
            horizontalListView.setAdapter(horizontal_adapter);

            // 4. Instantiate a new ViewHolder with the RecyclerView
            viewHolder = new ViewHolder(horizontalListView);

        } else {
            // Vertcial Section => Regular ViewHolder

            JSONObject json;
            try {
                json = new JSONObject(signatureString);
            } catch (JSONException e) {
                json = new JSONObject();
            }
            viewHolder = factory.build(null, json);
        }

        return viewHolder;
    }

    @Override
    public void onBindViewHolder(ItemAdapter.ViewHolder viewHolder, int position) {
        JSONObject json = this.items.get(position);
        if(position == this.items.size() - Math.min(this.items.size()/5, 20)) {
            ((JasonViewActivity)root_context).simple_trigger("$scroll.end", new JSONObject(), this.context);
        }

        if(json.has("horizontal_section")) {
            // Horizontal Section
            // In this case, the viewHolder is a Recyclerview.

            // We fetch the recyclerview from the viewholder (the viewholder's itemView is the recyclerview)
            ItemAdapter horizontalListAdapter = ((ItemAdapter) ((RecyclerView)viewHolder.itemView).getAdapter());

            // Transform JasonArray into ArrayList
            try {
                JSONObject horizontal_section = (JSONObject)json.getJSONObject("horizontal_section");
                if (horizontal_section.has("style")) {
                    JSONObject style = horizontal_section.getJSONObject("style");
                    if (style.has("snap")) {
                        PagerSnapHelper snapHelper = new PagerSnapHelper();
                        snapHelper.attachToRecyclerView((RecyclerView) viewHolder.itemView);
                        ((RecyclerView) viewHolder.itemView).addItemDecoration(new PageIndicatorDecoration());
                    }
                }

                horizontalListAdapter.items = JasonHelper.toArrayList(horizontal_section.getJSONArray("items"));
            } catch (Exception e) { }

            // Update viewholder
            horizontalListAdapter.notifyDataSetChanged();
            viewHolder.itemView.invalidate();
        } else {
            // Vertical section
            // Build ViewHolder via ViewHolderFactory
            factory.build(viewHolder, json);
        }
    }

    @Override
    public int getItemCount(){
        return this.items.size();
    }

    // returns position of n-th header in the main items list
    // jasonette currently fudges headers in as a main item which makes distinguishing sections difficult
    // using headers is the only consistent way of counting sections
    public int getHeaderAt(int position) {
        int header_count = 0;
        for(int i=0; i < this.items.size() - 1; i++) {
            JSONObject json = this.items.get(i);
            if(json.has("isHeader")) {
                header_count++;
            }
            if (header_count > position) {
                return i;
            }
        }
        return 0;
    }

    /********************************************************
     *
     * ViewHolderFactory => Creates ViewHolders
     *
     ********************************************************/

    public class ViewHolderFactory {

        // "subviews" =>
        //
        //      store the DOM tree under viewHolder, so that it can be accessed easily inside onBindViewHolder, for example:
        //      viewHolder.subviews = [Image1, TextView1, Image2, TextView2, TextView3, TextView4];
        //      for(int i = 0 ; i < viewHolder.subviews.size() ; i++){
        //          View el = viewHolder.subviews.get(i);
        //          if(el instancof Button){
        //              ..
        //          } ..
        //      }

        private ArrayList<View> subviews;
        private Boolean exists;
        private int index;

        private JSONObject chevron = new JSONObject() {{
            try {
                put("type", "image");
                put("url", "file://chevron-right.png");
                put("style", new JSONObject("{\"height\": 20, \"width\": 20}"));
            } catch (JSONException e) { }
        }};

        public ItemAdapter.ViewHolder build(ViewHolder prototype, JSONObject json) {
            LinearLayout layout;

            if (prototype != null) {
                // Fill
                this.exists = true;
            } else {
                this.exists = false;
            }

            if (this.exists) {
                // Fill

                // Get the subviews
                this.subviews = prototype.subviews;
                this.index = 0;

                // Build content view with the existing prototype layout
                layout = (LinearLayout) prototype.getView();
                buildContentView(layout, json);

                layout.setTag(json);

                // return the existing prototype layout
                return prototype;
            } else {
                // Create

                // Initialize subviews
                this.subviews = new ArrayList<View>();

                // Build content view with a new layout
                layout = buildContentView(new LinearLayout(context), json);

                // Create a new viewholder with the new layout
                ItemAdapter.ViewHolder viewHolder = new ItemAdapter.ViewHolder(layout);

                // Assign subviews
                viewHolder.subviews = this.subviews;

                return viewHolder;
            }
        }

        // ContentView is the top level view of a cell.
        // It's always a layout.
        // If the JSON supplies a component, ContentView creates a layout wrapper around it
        private LinearLayout buildContentView(LinearLayout layout, JSONObject json) {
            try {
                if (json.has("type")) {
                    String type = json.getString("type");
                    if (type.equalsIgnoreCase("vertical") || type.equalsIgnoreCase("horizontal")) {
                        layout = buildLayout(layout, json, null, 0);
                        layout.setClickable(true);
                    } else {
                        // 1. Create components array
                        JSONArray components = new JSONArray();

                        // 2. Create a vertical layout and set its components
                        JSONObject wrapper = new JSONObject();
                        wrapper.put("type", "vertical");

                        // When wrapping, we set the padding on the wrapper to 0, since it will be taken care of on the component
                        JSONObject style = new JSONObject();
                        style.put("padding", type.equalsIgnoreCase("html") ? 1 : 0);
                        wrapper.put("style", style);

                        // Instead, we set the component's padding to 10
                        JSONObject componentStyle;
                        if(json.has("style")) {
                            componentStyle = json.getJSONObject("style");
                            if(!componentStyle.has("padding")){
                                componentStyle.put("padding", "10");
                            }
                        } else {
                            componentStyle = new JSONObject();
                            componentStyle.put("padding", "10");
                        }
                        json.put("style", componentStyle);

                        // Setup components array
                        components.put(json);
                        wrapper.put("components", components);

                        // Setup href and actions
                        if (json.has("href")) {
                            wrapper.put("href", json.getJSONObject("href"));
                        }
                        if (json.has("action")) {
                            wrapper.put("action", json.getJSONObject("action"));
                        }

                        // 3. Start running the layout logic
                        buildLayout(layout, wrapper, null, 0);

                        // In case we're at the root level
                        // and the child has a width, we need to set the wrapper's width to wrap its child. (for horizontal scrolling sections)
                        View componentView = layout.getChildAt(0);
                        ViewGroup.LayoutParams componentLayoutParams = (ViewGroup.LayoutParams)componentView.getLayoutParams();
                        if(componentLayoutParams.width > 0){
                            ViewGroup.LayoutParams layoutParams = (ViewGroup.LayoutParams)layout.getLayoutParams();
                            layoutParams.width = ViewGroup.LayoutParams.WRAP_CONTENT;
                        }
                    }
                } else {
                    layout = new LinearLayout(context);
                }
            } catch (JSONException e) {
                layout = new LinearLayout(context);
            }

            return layout;
        }

        class BackgroundImage extends SimpleTarget<Drawable> {
            LinearLayout layout;
            int corner_radius;
            public BackgroundImage(LinearLayout layout, int corner_radius) {
                this.layout = layout;
                this.corner_radius = corner_radius;
            }
            @Override
            public void onResourceReady(Drawable resource, Transition<? super Drawable> glideAnimation) {
                this.layout.setBackground(resource);

                LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) this.layout.getLayoutParams();
                Bitmap backgroundBitmap = drawableToBitmap(resource);

                // if we have a height and width center crop the background image
                // NOTE: this.layout.getWidth()/getHeight() are not reliable here
                if(params.height > 0 && params.width > 0) {
                    backgroundBitmap = scaleCenterCrop(backgroundBitmap, params.width, params.height);
                }

                if (this.corner_radius > 0) {
                    RoundedBitmapDrawable bitmapDrawable = RoundedBitmapDrawableFactory.create(context.getResources(), backgroundBitmap);
                    bitmapDrawable.setCornerRadius(corner_radius);
                    this.layout.setBackground(bitmapDrawable);
                } else {
                    this.layout.setBackground(new BitmapDrawable(context.getResources(), backgroundBitmap));
                }
            }

            private Bitmap drawableToBitmap (Drawable drawable) {
                Bitmap bitmap = null;

                if (drawable instanceof BitmapDrawable) {
                    BitmapDrawable bitmapDrawable = (BitmapDrawable) drawable;
                    if(bitmapDrawable.getBitmap() != null) {
                        return bitmapDrawable.getBitmap();
                    }
                }

                if(drawable.getIntrinsicWidth() <= 0 || drawable.getIntrinsicHeight() <= 0) {
                    bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888); // Single color bitmap will be created of 1x1 pixel
                } else {
                    bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
                }

                Canvas canvas = new Canvas(bitmap);
                drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
                drawable.draw(canvas);
                return bitmap;
            }

            public Bitmap scaleCenterCrop(Bitmap source, int newWidth, int newHeight) {
                int sourceWidth = source.getWidth();
                int sourceHeight = source.getHeight();

                // Compute the scaling factors to fit the new height and width, respectively.
                // To cover the final image, the final scaling will be the bigger
                // of these two.
                float xScale = (float) newWidth / sourceWidth;
                float yScale = (float) newHeight / sourceHeight;
                float scale = Math.max(xScale, yScale);

                // Now get the size of the source bitmap when scaled
                float scaledWidth = scale * sourceWidth;
                float scaledHeight = scale * sourceHeight;

                // Let's find out the upper left coordinates if the scaled bitmap
                // should be centered in the new size give by the parameters
                float left = (newWidth - scaledWidth) / 2;
                float top = (newHeight - scaledHeight) / 2;

                // The target rectangle for the new, scaled version of the source bitmap will now
                // be
                RectF targetRect = new RectF(left, top, left + scaledWidth, top + scaledHeight);

                // Finally, we create a new bitmap of the specified size and draw our new,
                // scaled bitmap onto it.
                Bitmap dest = Bitmap.createBitmap(newWidth, newHeight, source.getConfig());
                Canvas canvas = new Canvas(dest);
                canvas.drawBitmap(source, null, targetRect, null);

                return dest;
            }
        }

        public LinearLayout buildLayout(final LinearLayout layout, JSONObject item, JSONObject parent, int level) {
            if (exists) {
                try {
                    JSONArray components = item.getJSONArray("components");
                    for (int i = 0; i < components.length(); i++) {
                        JSONObject component = components.getJSONObject(i);
                        if (component.getString("type").equalsIgnoreCase("vertical") || component.getString("type").equalsIgnoreCase("horizontal")) {
                            LinearLayout childLayout = (LinearLayout)layout.getChildAt(i);
                            buildLayout(childLayout, component, item, ++level);
                            if (i > 0) {
                                add_spacing(childLayout, item, item.getString("type"));
                            }

                            attach_layout_actions(childLayout, component);

                        } else {
                            View child_component = buildComponent(component, item);
                            if (i > 0) {
                                add_spacing(child_component, item, item.getString("type"));
                            }
                        }
                    }
                    // If we reach this conditional then we want to add the chevron image because it's a navigation section
                    if (item.getString("type").equalsIgnoreCase("horizontal") && item.has("href")) {
                        buildComponent(chevron, item);
                    }
                } catch (JSONException e) {

                }
                return new LinearLayout(context);
            } else {
                try {
                    // Layout styling
                    String type = item.getString("type");
                    JSONObject style = JasonHelper.style(item, root_context);
                    layout.setBackgroundColor(JasonHelper.parse_color("rgba(0,0,0,0)"));

                    JSONArray components;
                    if (type.equalsIgnoreCase("vertical") || type.equalsIgnoreCase("horizontal")) {
                        components = item.getJSONArray("components");
                    } else {
                        components = new JSONArray();
                    }

                    LinearLayout.LayoutParams layoutParams;
                    if (type.equalsIgnoreCase("vertical")) {
                        // vertical layout
                        layout.setOrientation(LinearLayout.VERTICAL);
                        components = item.getJSONArray("components");
                    } else if (type.equalsIgnoreCase("horizontal")) {
                        // horizontal layout
                        layout.setOrientation(LinearLayout.HORIZONTAL);
                        components = item.getJSONArray("components");
                        // If we reach this conditional then we want to add the chevron image because it's a navigation section
                        if (item.has("href")) {
                            components.put(chevron);
                        }
                    }

                    // set width and height
                    layoutParams = JasonLayout.autolayout(isHorizontalScroll, parent, item, root_context);

                    layout.setLayoutParams(layoutParams);

                    // Padding
                    // If root level, set the default padding to 10
                    String default_padding;
                    if (level == 0) {
                        default_padding = "10";
                    } else {
                        default_padding = "0";
                    }
                    int padding_left = (int) JasonHelper.pixels(root_context, default_padding, type);
                    int padding_right = (int) JasonHelper.pixels(root_context, default_padding, type);
                    int padding_top = (int) JasonHelper.pixels(root_context, default_padding, type);
                    int padding_bottom = (int) JasonHelper.pixels(root_context, default_padding, type);
                    if (style.has("padding")) {
                        padding_left = (int) JasonHelper.pixels(root_context, style.getString("padding"), type);
                        padding_right = padding_left;
                        padding_top = padding_left;
                        padding_bottom = padding_left;
                    }
                    if (style.has("padding_left")) {
                        padding_left = (int) JasonHelper.pixels(root_context, style.getString("padding_left"), type);
                    }
                    if (style.has("padding_right")) {
                        padding_right = (int) JasonHelper.pixels(context, style.getString("padding_right"), type);
                    }
                    if (style.has("padding_top")) {
                        padding_top = (int) JasonHelper.pixels(root_context, style.getString("padding_top"), type);
                    }
                    if (style.has("padding_bottom")) {
                        padding_bottom = (int) JasonHelper.pixels(root_context, style.getString("padding_bottom"), type);
                    }
                    layout.setPadding(padding_left, padding_top, padding_right, padding_bottom);

                    // background
                    if (style.has("background")) {

                        String background = style.getString("background");
                        final int corner_radius = style.has("corner_radius") ? (int) JasonHelper.pixels(root_context, style.getString("corner_radius"), type) : 0;
                        if (background.matches("(file|http[s]?):\\/\\/.*")) {
                            JSONObject c = new JSONObject();
                            c.put("url", background);
                            DiskCacheStrategy cacheStrategy = DiskCacheStrategy.AUTOMATIC;
                            Glide.with(root_context)
                                    .load(JasonImageComponent.resolve_url(c, root_context))
                                    .diskCacheStrategy(cacheStrategy)
                                    .into(new BackgroundImage(layout, corner_radius));
                        } else {
                            // plain background
                            if (corner_radius > 0) {
                                Bitmap backgroundColor = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
                                backgroundColor.eraseColor(JasonHelper.parse_color(style.getString("background")));
                                RoundedBitmapDrawable bitmapDrawable = RoundedBitmapDrawableFactory.create(root_context.getResources(), backgroundColor);
                                bitmapDrawable.setCornerRadius(corner_radius);
                                layout.setBackground(bitmapDrawable);
                            } else {
                                layout.setBackgroundColor(JasonHelper.parse_color(style.getString("background")));
                            }
                        }
                    }

                    // spacing
                    for (int i = 0; i < components.length(); i++) {
                        JSONObject component = components.getJSONObject(i);
                        String component_type = component.getString("type");
                        if (component_type.equalsIgnoreCase("vertical") || component_type.equalsIgnoreCase("horizontal")) {
                            // the child is also a layout
                            LinearLayout child_layout = buildLayout(new LinearLayout(context), component, item, ++level);

                            layout.addView(child_layout);
                            if (i > 0) {
                                add_spacing(child_layout, item, type);
                            }
                            attach_layout_actions(child_layout, component);
                            // From item1, start adding margin-top (item0 shouldn't have margin-top)
                        } else {
                            View child_component = buildComponent(component, item);
                            // the child is a leaf node
                            layout.addView(child_component);
                            if (i > 0) {
                                add_spacing(child_component, item, type);
                            }
                        }
                    }

                    // align
                    if (style.has("align")) {
                        if (style.getString("align").equalsIgnoreCase("center")) {
                            layout.setGravity(Gravity.CENTER);
                        } else if (style.getString("align").equalsIgnoreCase("right")) {
                            layout.setGravity(Gravity.RIGHT);
                        } else {
                            layout.setGravity(Gravity.LEFT);
                        }
                    }

                    if (style.has("corner_radius") && !style.has("shadow_border")) {
                        float corner = JasonHelper.pixels(root_context, style.getString("corner_radius"), "horizontal");
                        int color = ContextCompat.getColor(root_context, android.R.color.transparent);
                        GradientDrawable cornerShape = new GradientDrawable();
                        cornerShape.setShape(GradientDrawable.RECTANGLE);
                        if (style.has("background") && !style.getString("background").matches("(file|http[s]?):\\/\\/.*")) {
                            color = JasonHelper.parse_color(style.getString("background"));
                        }
                        cornerShape.setColor(color);
                        cornerShape.setCornerRadius(corner);

                        // border + corner_radius handling
                        if (style.has("border_width")){
                            int border_width = (int)JasonHelper.pixels(root_context, style.getString("border_width"), "horizontal");
                            if(border_width > 0){
                                int border_color;
                                if (style.has("border_color")){
                                    border_color = JasonHelper.parse_color(style.getString("border_color"));
                                } else {
                                    border_color = JasonHelper.COLOR_BLACK;
                                }
                                cornerShape.setStroke(border_width, border_color);
                            }
                        }
                        cornerShape.invalidateSelf();
                        layout.setBackground(cornerShape);
                        layout.setClipToOutline(true);
                    } else {
                        // border handling (no corner radius)
                        if (style.has("border_width")){
                            int border_width = (int)JasonHelper.pixels(root_context, style.getString("border_width"), "horizontal");
                            if(border_width > 0){
                                int border_color;
                                if (style.has("border_color")){
                                    border_color = JasonHelper.parse_color(style.getString("border_color"));
                                } else {
                                    border_color = JasonHelper.COLOR_BLACK;
                                }
                                GradientDrawable cornerShape = new GradientDrawable();
                                cornerShape.setStroke(border_width, border_color);
                                cornerShape.invalidateSelf();
                                layout.setBackground(cornerShape);
                            }
                        }
                    }

                    layout.requestLayout();

                    // accessibility


                    layout.setFocusable(false);
                    if(item.has("alt")) {
                        String content_description = item.getString("alt");

                        if(item.has("label")) {
                            content_description = item.getString("label").concat(", ").concat(content_description);
                        }

                        if (content_description.length() == 0) {
                            content_description = null;
                        } else {
                            layout.setFocusable(true);
                        }
                        layout.setContentDescription(content_description);
                    }

                    if(item.has("hide_accessible_children")) {
                        for (int i = 0; i < layout.getChildCount(); i++) {
                            View v = layout.getChildAt(i);
                            v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
                        }
                    }

                    final String role = item.has("role") ? item.getString("role") : "";
                    final Boolean hasAction = !item.has("action") && !item.has("href");

                    layout.setAccessibilityDelegate(new View.AccessibilityDelegate() {
                        public void onInitializeAccessibilityNodeInfo(View host,
                                                                      AccessibilityNodeInfo info) {
                            super.onInitializeAccessibilityNodeInfo(host, info);
                            // Set some other information.
                            info.setSelected(role.contains("selected"));
                            info.setClickable(role.contains("button"));
                            info.setCheckable(role.contains("checkbox"));
                            info.setChecked(role.contains("checked"));
                            if(android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                info.setHeading(role.contains("header"));
                            }


                            // if there is no action remove the default action that indicates there is one
                            if (hasAction) {
                                info.getActionList().removeAll(info.getActionList());
                            }
                        }
                    });


                } catch (JSONException e) {

                }
                return layout;
            }
        }

        public View buildComponent(JSONObject component, JSONObject parent) {
            View view;

            JSONObject style = JasonHelper.style(component, root_context);

            if (exists) {
                view = (View) this.subviews.get(this.index++);
                JasonComponentFactory.build(view, component, parent, root_context);
                return view;
            } else {
                view = JasonComponentFactory.build(null, component, parent, root_context);
                view.setId(this.subviews.size());
                this.subviews.add(view);
                return view;
            }
        }

        // handle adding or removing click handlers on recycled nested layouts
        private void attach_layout_actions(View view, JSONObject item) {
            // allow nested layouts to handle actions
            if (item.has("action") || item.has("href")) {
                view.setClickable(true);
                view.setTag(item);
                View.OnClickListener clickListener = new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        JSONObject item = (JSONObject) view.getTag();
                        try {
                            if (item.has("action")) {
                                JSONObject action = item.getJSONObject("action");
                                ((JasonViewActivity) root_context).call(action.toString(), new JSONObject().toString(), "{}", view.getContext());
                            } else if (item.has("href")) {
                                JSONObject href = item.getJSONObject("href");
                                JSONObject action = new JSONObject().put("type", "$href").put("options", href);
                                ((JasonViewActivity) root_context).call(action.toString(), new JSONObject().toString(), "{}", view.getContext());
                            }
                        } catch (JSONException e) {
                            Log.d("Warning", e.getStackTrace()[0].getMethodName() + " : " + e.toString());
                        }
                    }
                };
                view.setOnClickListener(clickListener);
            } else {
                view.setOnClickListener(null);
                view.setClickable(false);
            }
        }

        private void add_spacing(View view, JSONObject item, String type) {
            try {
                String spacing = "0";
                JSONObject style = JasonHelper.style(item, root_context);
                if (style.has("spacing")) {
                    spacing = style.getString("spacing");
                } else {
                    spacing = "0";
                }

                if (type.equalsIgnoreCase("vertical")) {
                    int m = (int) JasonHelper.pixels(context, spacing, item.getString("type"));
                    LinearLayout.LayoutParams layoutParams;
                    if(view.getLayoutParams() == null) {
                        layoutParams = new LinearLayout.LayoutParams(0,0);
                    } else {
                        layoutParams = (LinearLayout.LayoutParams) view.getLayoutParams();
                    }
                    layoutParams.topMargin = m;
                    layoutParams.bottomMargin = 0;
                    view.setLayoutParams(layoutParams);
                } else if (type.equalsIgnoreCase("horizontal")) {
                    int m = (int) JasonHelper.pixels(root_context, spacing, item.getString("type"));
                    LinearLayout.LayoutParams layoutParams;
                    if(view.getLayoutParams() == null) {
                        layoutParams = new LinearLayout.LayoutParams(0,0);
                    } else {
                        layoutParams = (LinearLayout.LayoutParams) view.getLayoutParams();
                    }
                    layoutParams.leftMargin = m;
                    layoutParams.rightMargin = 0;
                    view.setLayoutParams(layoutParams);
                }
                view.requestLayout();
            } catch (Exception e) {
                Log.d("Warning", e.getStackTrace()[0].getMethodName() + " : " + e.toString());
            }
        }
    }
}