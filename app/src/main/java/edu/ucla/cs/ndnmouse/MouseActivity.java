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

import java.io.IOException;
import java.util.Calendar;

import edu.ucla.cs.ndnmouse.utilities.Server;
import edu.ucla.cs.ndnmouse.utilities.ServerNDN;
import edu.ucla.cs.ndnmouse.utilities.ServerUDP;

public class MouseActivity extends AppCompatActivity implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = MouseActivity.class.getSimpleName();

    private static int mPort;
    private static String mPassword;
    private static boolean mUseNDN;
    private static int mTouchpadWidth;
    private static int mTouchpadHeight;
    private TextView mTouchpadTextView;
    private Server mServer;
    // private Thread mServerThread;

    // Relative and absolute movement variables
    private Point mAbsPos;
    private Point mLastRelPos;
    private boolean mBufferAbsPos = true;
    private boolean mTouchDown = false;
    private boolean mUseRelativeMovement = true;
    private static final int mMinMovementPixelThreshold = 5;  // May require a user setting or tuning

    // Tap to left click variables
    private boolean mTapToLeftClick = false;
    private long mTouchDownTime = -1;
    private Point mTouchDownPos;
    private static final long mTapClickMillisThreshold = 500; // May require a user setting or tuning
    private static final int mTapClickPixelThreshold = 5;     // May require tuning

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mouse);
        setupSharedPreferences();

        // Get extras from Intent
        Intent intent = getIntent();
        mPort = intent.getIntExtra(getString(R.string.intent_extra_port), 10888);
        mPassword = intent.getStringExtra(getString(R.string.intent_extra_password));
        mUseNDN = intent.getBooleanExtra(getString(R.string.intent_extra_protocol), getResources().getBoolean(R.bool.use_ndn_protocol_default));

        mTouchpadTextView = (TextView) findViewById(R.id.tv_touchpad);
        // Find out upper-left coordinate, and width/height of touchpad box
        final TextView mTouchpadTextView = (TextView) findViewById(R.id.tv_touchpad);
        mTouchpadTextView.post(new Runnable() {
            @Override
            public void run() {
                mTouchpadWidth = mTouchpadTextView.getWidth();
                mTouchpadHeight = mTouchpadTextView.getHeight();
                Log.d(TAG, String.format("Touchpad width is %d", mTouchpadWidth));
                Log.d(TAG, String.format("Touchpad height is %d", mTouchpadHeight));

                // Create and start mServer
                if (mUseNDN) {
                    mServer = new ServerNDN(MouseActivity.this, mTouchpadWidth, mTouchpadHeight, mUseRelativeMovement);
                    Log.d(TAG, "Creating NDN server...");
                } else {
                    mServer = new ServerUDP(MouseActivity.this, mPort, mTouchpadWidth, mTouchpadHeight, mUseRelativeMovement);
                    Log.d(TAG, "Creating UDP server...");
                }

                mServer.start();
            }
        });

        setupCallbacks();
        mAbsPos = new Point();
        mLastRelPos = new Point();
        mTouchDownPos = new Point();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mServer.stop();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.mouse_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
//        if (id == R.id.action_settings) {
//            Intent startSettingsActivity = new Intent(this, SettingsActivity.class);
//            startActivity(startSettingsActivity);
//            return true;
//        }
        if (id == R.id.action_keyboard) {
            Intent startKeyboardActivity = new Intent(this, KeyboardActivity.class);
            startActivity(startKeyboardActivity);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(getString(R.string.pref_movement_key))) {
            String movement = sharedPreferences.getString(key, getResources().getString(R.string.pref_movement_default));
            mUseRelativeMovement = !movement.equals(getString(R.string.pref_move_abs_value));
        } else if (key.equals(getString(R.string.pref_tap_to_left_click_key))) {
            mTapToLeftClick = sharedPreferences.getBoolean(key, getResources().getBoolean(R.bool.pref_tap_to_left_click_default));
        }
    }

    /**
     * Helper function to setup preferences from Settings activity
     */
    private void setupSharedPreferences() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        String movement = sharedPreferences.getString(getString(R.string.pref_movement_key), getResources().getString(R.string.pref_movement_default));
        mUseRelativeMovement = !movement.equals(getString(R.string.pref_move_abs_value));
        mTapToLeftClick = sharedPreferences.getBoolean(getString(R.string.pref_tap_to_left_click_key), getResources().getBoolean(R.bool.pref_tap_to_left_click_default));
    }

    /**
     * Helper function to setup each callback (for buttons and touchpad)
     */
    private void setupCallbacks() {
        // Left click touch down and touch up
        final Button leftClickButton = (Button) findViewById(R.id.b_left_click);
        leftClickButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    try {
                        mServer.ExecuteClick(R.string.action_left_click_down);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    displayClick(getString(R.string.action_left_click_down));
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    try {
                        mServer.ExecuteClick(R.string.action_left_click_up);
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
                        mServer.ExecuteClick(R.string.action_right_click_down);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    displayClick(getString(R.string.action_right_click_down));
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    try {
                        mServer.ExecuteClick(R.string.action_right_click_up);
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
        mTouchpadTextView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                int x = (int) event.getX();
                int y = (int) event.getY();

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        // mTouchDownTime = Calendar.getInstance().getTimeInMillis();
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
                            // long now = Calendar.getInstance().getTimeInMillis();
                            long now = System.currentTimeMillis();
                            if (now - mTouchDownTime <= mTapClickMillisThreshold) {
                                try {
                                    mServer.ExecuteClick(R.string.action_left_click_full);
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
                displayCoordinate();
                return true;
            }
        });
    }

    /**
     * Update mAbsPos variable if the new position is different enough from the previous position
     * defined by the mMovementThreashold
     *
     * @param x horizontal coordinate on the touchpad TextView
     * @param y vertical coordinate on the touchpad TextView
     */
    private void updateAbsolutePosition(int x, int y) {
        if (Math.abs(x - mAbsPos.x) >= mMinMovementPixelThreshold || Math.abs(y - mAbsPos.y) >= mMinMovementPixelThreshold) {
            mAbsPos.set(x, y);
        }
    }

    /**
     * Function to display x, y coordinate on the touchpad (for debugging purposes)
     */
    private void displayCoordinate() {
        String newCoord = getString(R.string.touchpad_label) + "\n(" + mAbsPos.x + ", " + mAbsPos.y + ")";
        mTouchpadTextView.setText(newCoord);
    }

    /**
     * Function to display user's clicks on the touchpad (for debugging purposes)
     *
     * @param click either "left_click" or "right_click", found in res/values/strings.xml
     */
    private void displayClick(String click) {
        mTouchpadTextView.setText(getString(R.string.touchpad_label) + "\n(" + click + ")");
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
     *
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

//    public void setServerThread(Thread thread) {
//        mServerThread = thread;
//    }
}
