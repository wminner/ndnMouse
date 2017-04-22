package edu.ucla.cs.ndnmouse;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import edu.ucla.cs.ndnmouse.utilities.ServerUDP;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();

    private static String mServerPassword = "1234";
    private TextView mPortTextView;
    private TextView mAddressTextView;
    private boolean mUseNDN = true;
    private boolean mMonitorIPAddress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start);
        setupButtonCallbacks();
        setupSharedPreferences();

        mAddressTextView = (TextView) findViewById(R.id.tv_address);
        mAddressTextView.setText(ServerUDP.getIPAddress(true));

        mPortTextView = (TextView) findViewById(R.id.et_port);
        mPortTextView.setText(mUseNDN ? getString(R.string.ndn_port) : getString(R.string.udp_port));
    }

    @Override
    protected void onPause() {
        super.onPause();
        mMonitorIPAddress = false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        mMonitorIPAddress = true;
        periodicallyUpdateIPAddress();
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
                    mMonitorIPAddress = false;
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
            int port = Integer.parseInt(mPortTextView.getText().toString());
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
                mPortTextView.setText(getString(R.string.ndn_port));
                mUseNDN = true;
            }
            break;
        case R.id.rb_udp:
            if (checked) {
                editor.putBoolean(getString(R.string.radio_button_ndn_setting), false);
                mPortTextView.setText(getString(R.string.udp_port));
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

    /**
     * Periodically update the IP address TextView in case network changes
     */
    private void periodicallyUpdateIPAddress() {
        if (mMonitorIPAddress) {
            mAddressTextView.setText(ServerUDP.getIPAddress(true));
            Log.d(TAG, "Updating IP address to " + ServerUDP.getIPAddress(true));
            final Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    periodicallyUpdateIPAddress();
                }
            }, 5000);
        }
    }
}
