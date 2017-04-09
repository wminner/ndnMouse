package edu.ucla.cs.ndnmouse;

import android.content.Intent;
import android.graphics.Point;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.io.IOException;

import edu.ucla.cs.ndnmouse.utilities.ServerUDP;

public class MouseActivity extends AppCompatActivity {

    private static final String TAG = MouseActivity.class.getSimpleName();

    private static int mPort;
    private static String mPassword;
    private static int mTouchpadWidth;
    private static int mTouchpadHeight;
    private TextView mTouchpadTextView;
    private ServerUDP mServer;
    private Thread mServerThread;

    private Point mCurrPos;
    private int mMovementThreshold = 5;  // In pixels, may require tuning

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mouse);

        // Get extras from Intent
        Intent intent = getIntent();
        mPort = intent.getIntExtra(getString(R.string.intent_extra_port), 10888);
        mPassword = intent.getStringExtra(getString(R.string.intent_extra_password));

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
                mServer = new ServerUDP(MouseActivity.this, mPort, mTouchpadWidth, mTouchpadHeight);
                mServer.start();
            }
        });

        setupCallbacks();
        mCurrPos = new Point();
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
        if (id == R.id.action_settings) {
            Intent startSettingsActivity = new Intent(this, SettingsActivity.class);
            startActivity(startSettingsActivity);
            return true;
        } else if (id == R.id.action_keyboard) {
            Intent startKeyboardActivity = new Intent(this, KeyboardActivity.class);
            startActivity(startKeyboardActivity);
            return true;
        }
        return super.onOptionsItemSelected(item);
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
                        Log.d(TAG, String.format("ACTION_DOWN: %d %d", x, y));
                        break;
                    case MotionEvent.ACTION_MOVE:
                        // Log.d(TAG, String.format("ACTION_MOVE: %d %d", x, y));
                        break;
                    case MotionEvent.ACTION_UP:
                        Log.d(TAG, String.format("ACTION_UP: %d %d", x, y));
                        break;
                }
                displayCoordinate(x, y);
                updateCurrentPosition(x, y);
                return true;
            }
        });
    }

    /**
     * Update mCurrPos variable if the new position is different enough from the previous position
     * defined by the mMovementThreashold
     *
     * @param x horizontal coordinate on the touchpad TextView
     * @param y vertical coordinate on the touchpad TextView
     */
    private void updateCurrentPosition(int x, int y) {
        if (Math.abs(x - mCurrPos.x) >= mMovementThreshold || Math.abs(y - mCurrPos.y) >= mMovementThreshold) {
            mCurrPos.set(x, y);
        }
    }

    /**
     * Function to display x, y coordinate on the touchpad (for debugging purposes)
     *
     * @param x horizontal coordinate on the touchpad TextView
     * @param y vertical coordinate on the touchpad TextView
     */
    private void displayCoordinate(int x, int y) {
        if (x != mCurrPos.x || y != mCurrPos.y) {
            if ((0 <= x && 0 <= y) && (x <= mTouchpadWidth && y <= mTouchpadHeight)) {
                String newCoord = getString(R.string.touchpad_label) + "\n(" + x + ", " + y + ")";
                mTouchpadTextView.setText(newCoord);
            }
        }
    }

    /**
     * Function to display user's clicks on the touchpad (for debugging purposes)
     *
     * @param click either "left_click" or "right_click", found in res/values/strings.xml
     */
    private void displayClick(String click) {
        mTouchpadTextView.setText(getString(R.string.touchpad_label) + "\n(" + click + ")");
    }

    public Point getCurrentPosition() {
        return mCurrPos;
    }

    public void setServerThread(Thread thread) {
        mServerThread = thread;
    }
}
