package edu.ucla.cs.ndnmouse;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import edu.ucla.cs.ndnmouse.utilities.ServerUDP;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();

    private static String mServerPassword = "1234";
    private EditText mPortEditText;
    private boolean mUseNDN = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start);
        setupButtonCallbacks();
        setupSharedPreferences();

        final TextView addressTextView = (TextView) findViewById(R.id.tv_address);
        addressTextView.setText(ServerUDP.getIPAddress(true));

        mPortEditText = (EditText) findViewById(R.id.et_port);
    }

    /**
     * Function to setup all button callbacks
     */
    private void setupButtonCallbacks() {
        final Button startButton = (Button) findViewById(R.id.b_start);
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int port = getPort();
                // If we got bad port, then make a Toast alerting user and give up on intent
                if (port == -1) {
                    Toast.makeText(MainActivity.this, "Invalid port: please enter a port number between 1 and 65535.", Toast.LENGTH_LONG).show();
                } else {
                    Context context = MainActivity.this;
                    Class destinationClass = MouseActivity.class;
                    Intent intentToStartMouseActivity = new Intent(context, destinationClass);
                    intentToStartMouseActivity.putExtra(getString(R.string.intent_extra_port), port);
                    intentToStartMouseActivity.putExtra(getString(R.string.intent_extra_password), mServerPassword);
                    intentToStartMouseActivity.putExtra(getString(R.string.intent_extra_protocol), mUseNDN);
                    startActivity(intentToStartMouseActivity);
                }
            }
        });

        final ImageButton optionsButton = (ImageButton) findViewById(R.id.b_options);
        optionsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Context context = MainActivity.this;
                Class destinationClass = SettingsActivity.class;
                Intent intentToStartOptionsActivity = new Intent(context, destinationClass);
                startActivity(intentToStartOptionsActivity);
            }
        });
    }

    /**
     * Gets and validates port provided by user
     *
     * @return port number between 1-65535, or -1 if invalid port
     */
    private int getPort() {
        try {
            int port = Integer.parseInt(mPortEditText.getText().toString());
            Log.d(TAG, "Port is " + port);
            if (1 <= port && port <= 65535)
                return port;
        } catch (NumberFormatException e) {
            Log.e(TAG, "Invalid port: not a number.");
            return -1;
        }
        Log.e(TAG, "Invalid port: out of range.");
        return -1;
    }

    public void onRadioButtonClicked(View view) {
        boolean checked = ((RadioButton) view).isChecked();
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = sharedPreferences.edit();

        switch(view.getId()) {
        case R.id.rb_ndn:
            if (checked) {
                editor.putBoolean(getString(R.string.radio_button_ndn_setting), true);
                mUseNDN = true;
            }
            break;
        case R.id.rb_udp:
            if (checked) {
                editor.putBoolean(getString(R.string.radio_button_ndn_setting), false);
                mUseNDN = false;
            }
            break;
        }
        editor.apply();
    }

    /**
     * Function to load preferences
     */
    private void setupSharedPreferences() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        final RadioButton rb_ndn = (RadioButton) findViewById(R.id.rb_ndn);
        final RadioButton rb_udp = (RadioButton) findViewById(R.id.rb_udp);

        mUseNDN = sharedPreferences.getBoolean(getString(R.string.radio_button_ndn_setting), getResources().getBoolean(R.bool.pref_radio_button_ndn_default));
        rb_ndn.setChecked(mUseNDN);
        rb_udp.setChecked(!mUseNDN);
    }
}
