package com.jasonette.seed.Core;
import android.content.Intent;
import android.os.Bundle;

import com.jasonette.seed.R;

import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Create the new intent to open the first JasonViewActivity
        Intent intent = new Intent(SplashActivity.this, JasonViewActivity.class);
        startActivity(intent);
        // close this activity
        finishAfterTransition();
        // Set the transition to use a fade in/out so that we don't get a wipe animation. turning off animations didn't seem to work
        overridePendingTransition(R.anim.cwac_cam2_fade_in, R.anim.cwac_cam2_fade_out);
    }
}