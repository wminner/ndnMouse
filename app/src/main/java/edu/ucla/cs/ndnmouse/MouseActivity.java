package edu.ucla.cs.ndnmouse;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.ViewFlipper;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import javax.crypto.spec.SecretKeySpec;

import edu.ucla.cs.ndnmouse.utilities.Server;
import edu.ucla.cs.ndnmouse.utilities.ServerNDN;
import edu.ucla.cs.ndnmouse.utilities.ServerNDNSecure;
import edu.ucla.cs.ndnmouse.utilities.ServerUDP;
import edu.ucla.cs.ndnmouse.utilities.ServerUDPSecure;

public class MouseActivity extends AppCompatActivity implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = MouseActivity.class.getSimpleName();

    private static int mPort;                                   // Port of the server: NDN = 6363, UDP = 10888
    private static boolean mUseNDN;                             // Setting to use NDN as the server protocol (otherwise UDP)
    private static int mTouchpadWidth;                          // Pixel width of the touchpad
    private static int mTouchpadHeight;                         // Pixel height of the touchpad
    private TextView mTouchpadTextView;                         // Touchpad TextView reference
    private TextView mKeyboardTouchpadTextView;                 // Keyboard status TextView
    private ViewFlipper mViewFlipper;                           // Holds mouse and keyboard views
    private Server mServer;                                     // Server/Producer object that will run in its own thread
    private boolean mKeyboardShowing = false;                   // Tells if the keyboard view is showing or not

    // Relative and absolute movement variables
    private Point mAbsPos;                                      // Current absolute position on touchpad
    private Point mLastRelPos;                                  // Used to calculate relative position differences
    private boolean mBufferAbsPos = true;                       // Used to decide when to buffer an absolute position (to get an accurate relative movement)
    private boolean mTouchDown = false;                         // User is currently touching down on touchpad (has not lifted yet)
    private float mSensitivity;                                 // Sensitivity multiplier for mouse movement control
    private int mPrecision = 5;                                 // Min change in pixels to count as a movement update (otherwise same position)

    // Tap to left click variables
    private boolean mTapToLeftClick = false;                    // Setting to detect tap -> trigger left click
    private long mTouchDownTime = -1;                           // Time when user last touched down on touchpad
    private Point mTouchDownPos;                                // Location when user last touched down on touchpad
    private static final long mTapClickMillisThreshold = 500;   // Max num of ms between touch down and touch up to count as a tap-click
    private static final int mTapClickPixelThreshold = 5;       // Max num of pixel difference between touch down and touch up to count as a tap-click

    // Password variables
    private static String mPassword;                            // User entered password

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mouse);
        setupSharedPreferences();
        PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(this);

        // Get extras from Intent
        Intent intent = getIntent();
        mPort = intent.getIntExtra(getString(R.string.intent_extra_port), 10888);
        mPassword = intent.getStringExtra(getString(R.string.intent_extra_password));
        mUseNDN = intent.getBooleanExtra(getString(R.string.intent_extra_protocol), getResources().getBoolean(R.bool.use_ndn_protocol_default));

        mTouchpadTextView = (TextView) findViewById(R.id.tv_touchpad);
        mKeyboardTouchpadTextView = (TextView) findViewById(R.id.tv_keyboard_touchpad);
        mViewFlipper = (ViewFlipper) findViewById(R.id.viewFlipper);

        // Find out upper-left coordinate, and width/height of touchpad box
        final TextView mTouchpadTextView = (TextView) findViewById(R.id.tv_touchpad);
        // Ensures that touchpad textview is initialized before these lines are executed
        mTouchpadTextView.post(new Runnable() {
            @Override
            public void run() {
                mTouchpadWidth = mTouchpadTextView.getWidth();
                mTouchpadHeight = mTouchpadTextView.getHeight();
                Log.d(TAG, String.format("Touchpad width is %d", mTouchpadWidth));
                Log.d(TAG, String.format("Touchpad height is %d", mTouchpadHeight));

                // Create and start mServer
                if (mUseNDN) {
                    if (mPassword.isEmpty())
                        mServer = new ServerNDN(MouseActivity.this, mSensitivity);
                    else
                        mServer = new ServerNDNSecure(MouseActivity.this, mSensitivity, mPassword);
                    Log.d(TAG, "Creating NDN server...");
                } else {
                    if (mPassword.isEmpty())
                        mServer = new ServerUDP(MouseActivity.this, mPort, mSensitivity);
                    else
                        mServer = new ServerUDPSecure(MouseActivity.this, mPort, mSensitivity, mPassword);
                    Log.d(TAG, "Creating UDP server...");
                }
                mServer.start();
            }
        });

        setupMouseCallbacks();
        setupKeyboardCallbacks();
        mAbsPos = new Point();
        mLastRelPos = new Point();
        mTouchDownPos = new Point();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        PreferenceManager.getDefaultSharedPreferences(this).unregisterOnSharedPreferenceChangeListener(this);
        mServer.stop();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.mouse_menu, menu);
        if (mKeyboardShowing) {
            menu.findItem(R.id.action_toggle_keyboard).setIcon(R.drawable.mouse_pointer_icon);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            Intent startSettingsActivity = new Intent(this, SettingsActivity.class);
            startActivity(startSettingsActivity);
            return true;
        }
        if (id == R.id.action_toggle_keyboard) {
            toggleKeyboardView();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(getString(R.string.pref_tap_to_left_click_key))) {
            // No need to update server setting because clicks are detected and executed by MouseActivity
            mTapToLeftClick = sharedPreferences.getBoolean(key, getResources().getBoolean(R.bool.pref_tap_to_left_click_default));
        } else if (key.equals(getString(R.string.pref_sensitivity_key))) {
            mSensitivity = Float.valueOf(sharedPreferences.getString(key, getString(R.string.pref_sensitivity_default)));
            mServer.UpdateSettings(R.string.pref_sensitivity_key, mSensitivity);
        } else if (key.equals(getString(R.string.pref_precision_key))) {
            mPrecision = Integer.valueOf(sharedPreferences.getString(key, getString(R.string.pref_precision_default)));
        }
    }

    /**
     * Helper function to setup preferences from Settings activity
     */
    private void setupSharedPreferences() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mTapToLeftClick = sharedPreferences.getBoolean(getString(R.string.pref_tap_to_left_click_key), getResources().getBoolean(R.bool.pref_tap_to_left_click_default));
        mSensitivity = Float.valueOf(sharedPreferences.getString(getString(R.string.pref_sensitivity_key), getString(R.string.pref_sensitivity_default)));
        mPrecision = Integer.valueOf(sharedPreferences.getString(getString(R.string.pref_precision_key), getString(R.string.pref_precision_default)));
    }

    /**
     * Helper function to setup each mouse button/view callback
     */
    private void setupMouseCallbacks() {
        // Left click touch down and touch up
        final Button leftClickButton = (Button) findViewById(R.id.b_left_click);
        leftClickButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    try {
                        mServer.executeCommand(R.string.action_left_click_down);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    displayClick(getString(R.string.action_left_click_down));
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    try {
                        mServer.executeCommand(R.string.action_left_click_up);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    displayClick(getString(R.string.action_left_click_up));

                } else {
                    return false;
                }
                return true;
            }
        });

        // Right click touch down and touch up
        final Button rightClickButton = (Button) findViewById(R.id.b_right_click);
        rightClickButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    try {
                        mServer.executeCommand(R.string.action_right_click_down);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    displayClick(getString(R.string.action_right_click_down));
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    try {
                        mServer.executeCommand(R.string.action_right_click_up);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    displayClick(getString(R.string.action_right_click_up));
                } else {
                    return false;
                }
                return true;
            }
        });

        // Touchpad
        mTouchpadTextView.setOnTouchListener(new TouchpadListener());
    }


    /**
     * Helper function to setup each keyboard button/view callback
     */
    private void setupKeyboardCallbacks() {
        // Spacebar
        final Button spacebarButton = (Button) findViewById(R.id.b_spacebar);
        spacebarButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    try {
                        mServer.executeCommand(R.string.action_keypress_spacebar_down);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    displayKeyPress(getString(R.string.action_keypress_spacebar_down));
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    try {
                        mServer.executeCommand(R.string.action_keypress_spacebar_up);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    displayKeyPress(getString(R.string.action_keypress_spacebar_up));

                } else {
                    return false;
                }
                return true;
            }
        });

        // Up Arrow Key
        final Button upArrowButton = (Button) findViewById(R.id.b_up_arrow);
        upArrowButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    try {
                        mServer.executeCommand(R.string.action_keypress_up_arrow_down);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    displayKeyPress(getString(R.string.action_keypress_up_arrow_down));
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    try {
                        mServer.executeCommand(R.string.action_keypress_up_arrow_up);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    displayKeyPress(getString(R.string.action_keypress_up_arrow_up));

                } else {
                    return false;
                }
                return true;
            }
        });

        // Down Arrow Key
        final Button downArrowButton = (Button) findViewById(R.id.b_down_arrow);
        downArrowButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    try {
                        mServer.executeCommand(R.string.action_keypress_down_arrow_down);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    displayKeyPress(getString(R.string.action_keypress_down_arrow_down));
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    try {
                        mServer.executeCommand(R.string.action_keypress_down_arrow_up);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    displayKeyPress(getString(R.string.action_keypress_down_arrow_up));

                } else {
                    return false;
                }
                return true;
            }
        });

        // Left Arrow Key
        final Button leftArrowButton = (Button) findViewById(R.id.b_left_arrow);
        leftArrowButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    try {
                        mServer.executeCommand(R.string.action_keypress_left_arrow_down);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    displayKeyPress(getString(R.string.action_keypress_left_arrow_down));
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    try {
                        mServer.executeCommand(R.string.action_keypress_left_arrow_up);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    displayKeyPress(getString(R.string.action_keypress_left_arrow_up));

                } else {
                    return false;
                }
                return true;
            }
        });

        // Right Arrow Key
        final Button rightArrowButton = (Button) findViewById(R.id.b_right_arrow);
        rightArrowButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    try {
                        mServer.executeCommand(R.string.action_keypress_right_arrow_down);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    displayKeyPress(getString(R.string.action_keypress_right_arrow_down));
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    try {
                        mServer.executeCommand(R.string.action_keypress_right_arrow_up);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    displayKeyPress(getString(R.string.action_keypress_right_arrow_up));

                } else {
                    return false;
                }
                return true;
            }
        });

        mKeyboardTouchpadTextView.setOnTouchListener(new TouchpadListener());
    }

    private class TouchpadListener implements View.OnTouchListener {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            int x = (int) event.getX();
            int y = (int) event.getY();

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    mTouchDownTime = System.currentTimeMillis();
                    mTouchDownPos.set(x, y);
                    mTouchDown = true;

                    Log.d(TAG, String.format("ACTION_DOWN: %d %d", x, y));
                    break;
                case MotionEvent.ACTION_MOVE:
                    // Log.d(TAG, String.format("ACTION_MOVE: %d %d", x, y));
                    break;
                case MotionEvent.ACTION_UP:
                    // Check if user tapped (for tap-to-click)
                    if (mTapToLeftClick && ((Math.abs(x - mTouchDownPos.x) <= mTapClickPixelThreshold) && (Math.abs(y - mTouchDownPos.y) <= mTapClickPixelThreshold))) {
                        long now = System.currentTimeMillis();
                        if (now - mTouchDownTime <= mTapClickMillisThreshold) {
                            try {
                                mServer.executeCommand(R.string.action_left_click_full);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                    mTouchDown = false;
                    // Need to buffer an absolute position next time relative difference needs to be calculated
                    mBufferAbsPos = true;

                    Log.d(TAG, String.format("ACTION_UP: %d %d", x, y));
                    break;
            }

            updateAbsolutePosition(x, y);
            displayCoordinate((TextView) v);
            return true;
        }
    }

    /**
     * Update mAbsPos variable if the new position is different enough from the previous position
     * defined by the mMovementThreshold
     * @param x horizontal coordinate on the touchpad TextView
     * @param y vertical coordinate on the touchpad TextView
     */
    private void updateAbsolutePosition(int x, int y) {
        if (Math.abs(x - mAbsPos.x) >= mPrecision || Math.abs(y - mAbsPos.y) >= mPrecision) {
            if ((0 <= x && 0 <= y) &&(x <= mTouchpadWidth && y <= mTouchpadHeight)) {
                mAbsPos.set(x, y);
            }
        }
    }

    /**
     * Function to display x, y coordinate on the touchpad (for debugging purposes)
     * @param textView touchpad for which the coordinate should be displayed
     */
    private void displayCoordinate(TextView textView) {
        String newCoord = getString(R.string.touchpad_label) + "\n(" + mAbsPos.x + ", " + mAbsPos.y + ")";
        textView.setText(newCoord);
    }

    /**
     * Function to display user's clicks on the touchpad (for debugging purposes)
     * @param click type of click, found in strings.xml
     */
    private void displayClick(String click) {
        mTouchpadTextView.setText(getString(R.string.touchpad_label) + "\n(" + click + ")");
    }

    /**
     * Function to display user's key press on the keyboard status textview (for debugging purposes)
     * @param keyPress type of keypress, found in strings.xml
     */
    private void displayKeyPress(String keyPress) {
        mKeyboardTouchpadTextView.setText(getString(R.string.touchpad_label) + "\n(" + keyPress + ")");
    }

    /**
     * @return absolute position on the touchpad
     */
    public Point getAbsolutePosition() {
        return mAbsPos;
    }

    /**
     * Function to get the relative position of the last user's touch.  Should behave similar to a
     * laptop trackpad.
     * @return position difference from last touch down
     */
    public Point getRelativePosition() {
        Point relativeDiff = new Point(0, 0);
        // Only calculate relative difference if user has been touching and dragging across touchpad
        if (mTouchDown) {
            // If true, then don't calculate the relative difference (let the absolute position buffer for one round)
            // This prevents a jump in position if the user lifts and touches down in a different spot
            if (mBufferAbsPos)
                mBufferAbsPos = false;
            else
                relativeDiff.set(mAbsPos.x - mLastRelPos.x, mAbsPos.y - mLastRelPos.y);
        }
        mLastRelPos.set(mAbsPos.x, mAbsPos.y);
        return relativeDiff;
    }

    /**
     * Creates a SecretKeySpec from the user's password
     * @param password from the user
     * @return 128 bit (16 B) secret key
     * @throws UnsupportedEncodingException for message digest's encoding
     * @throws NoSuchAlgorithmException for secret key algorithm
     */
    public SecretKeySpec makeKeyFromPassword(String password) throws UnsupportedEncodingException, NoSuchAlgorithmException {
        byte[] key = password.getBytes("UTF-8");
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        // Copy only 128 bits (16 B) from digest to use for secret key
        return new SecretKeySpec(Arrays.copyOf(sha.digest(key), 16), "AES");
    }

    /**
     * Creates a SecretKeySpec from the user's password and salt
     * @param password from the user
     * @param salt to add to password
     * @return 128 bit (16 B) secret key
     * @throws UnsupportedEncodingException for message digest's encoding
     * @throws NoSuchAlgorithmException for secret key algorithm
     */
    public SecretKeySpec makeKeyFromPassword(String password, byte[] salt) throws UnsupportedEncodingException, NoSuchAlgorithmException {
        byte[] key = password.getBytes("UTF-8");

        // Append salt to key
        byte[] keyAndSalt = new byte[key.length + salt.length];
        System.arraycopy(key, 0, keyAndSalt, 0, key.length);
        System.arraycopy(salt, 0, keyAndSalt, key.length, salt.length);

        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        // Copy only 128 bits (16 B) from digest to use for secret key
        return new SecretKeySpec(Arrays.copyOf(sha.digest(keyAndSalt), 16), "AES");
    }

    /**
     * Shows/hides the keyboard view
     */
    private void toggleKeyboardView() {
        mViewFlipper.showNext();
        invalidateOptionsMenu();

        // Hide keyboard
        if (mKeyboardShowing) {
            setTitle(getString(R.string.mouse_label));
            mKeyboardShowing = false;
        // Show keyboard
        } else {
            setTitle(getString(R.string.keyboard_label));
            mKeyboardShowing = true;
        }
    }
}
