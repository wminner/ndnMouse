package edu.ucla.cs.ndnmouse;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.preference.PreferenceManager;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();

    private static String mServerPassword = "1234";
    private static boolean mTapToLeftClick = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start);
        setupButtonCallbacks();
        setupSharedPreferences();
    }

    /**
     * Function to setup all button callbacks
     */
    private void setupButtonCallbacks() {
        final Button startButton = (Button) findViewById(R.id.b_start);
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Context context = MainActivity.this;
                Class destinationClass = MouseActivity.class;
                Intent intentToStartMouseActivity = new Intent(context, destinationClass);
                intentToStartMouseActivity.putExtra(Intent.EXTRA_TEXT, mServerPassword);
                startActivity(intentToStartMouseActivity);
            }
        });

//        final ImageButton optionsButton = (ImageButton) findViewById(R.id.b_options);
//        optionsButton.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                Context context = MainActivity.this;
//                Class destinationClass = SettingsActivity.class;
//                Intent intentToStartOptionsActivity = new Intent(context, destinationClass);
//                startActivity(intentToStartOptionsActivity);
//            }
//        });
    }

    /**
     * Function to load preferences
     */
    private void setupSharedPreferences() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        mTapToLeftClick = sharedPreferences.getBoolean(getString(R.string.pref_tap_to_left_click_key), getResources().getBoolean(R.bool.pref_tap_to_left_click_default));
    }
}
