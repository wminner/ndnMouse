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
    private static final int mPort = 10888;

    private static int mTouchpadX;
    private static int mTouchpadY;
    private static int mTouchpadWidth;
    private static int mTouchpadHeight;
    private TextView mTouchpadTextView;

    private ServerUDP mServer;
    private Thread mServerThread;

    private Point mCurrPos;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mouse);

        mTouchpadTextView = (TextView) findViewById(R.id.tv_touchpad);
        // Find out upper-left coordinate, and width/height of touchpad box
        final TextView mTouchpadTextView = (TextView) findViewById(R.id.tv_touchpad);
        mTouchpadTextView.post(new Runnable() {
            @Override
            public void run() {
                int[] touchpadCoords = new int[2];
                mTouchpadTextView.getLocationOnScreen(touchpadCoords);
                mTouchpadX = touchpadCoords[0];
                mTouchpadY = touchpadCoords[1];

                mTouchpadWidth = mTouchpadTextView.getWidth();
                mTouchpadHeight = mTouchpadTextView.getHeight();
                Log.d(TAG, String.format("Touchpad X is %d", mTouchpadX));
                Log.d(TAG, String.format("Touchpad Y is %d", mTouchpadY));
                Log.d(TAG, String.format("Touchpad width is %d", mTouchpadWidth));
                Log.d(TAG, String.format("Touchpad height is %d", mTouchpadHeight));

                // Create and start mServer
                mServer = new ServerUDP(MouseActivity.this, mPort, mTouchpadWidth, mTouchpadHeight);
                mServer.start();
            }
        });

        setupButtonCallbacks();
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

    @Override
    public boolean onTouchEvent(MotionEvent event) {
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
        return true;
    }

    /**
     * Function to setup each button callback
     */
    private void setupButtonCallbacks() {
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
    }

    /**
     * Function to display x, y coordinate on the touchpad (for debugging purposes)
     *
     * @param x absolute horizontal coordinate on screen
     * @param y absolute vertical coordinate on screen
     */
    private void displayCoordinate(int x, int y) {
        int relative_x = x - mTouchpadX;
        int relative_y = y - mTouchpadY;
        if ((0 <= relative_x && 0 <= relative_y) && (relative_x <= mTouchpadWidth && relative_y <= mTouchpadHeight)) {
            mCurrPos.set(relative_x, relative_y);
            String newCoord = getString(R.string.touchpad_label) +  "\n(" + relative_x + ", " + relative_y + ")";
            mTouchpadTextView.setText(newCoord);
        }
    }

    /**
     * Function to display user's clicks on the touchpad (for debugging purposes)
     *
     * @param click either "left_click" or "right_click", found in res/values/strings.xml
     */
    private void displayClick(String click) {
//        String newClick = "";
//        if (click.equals(getString(R.string.action_left_click_down))) {
//            newClick = getString(R.string.action_left_click_down);
//        } else if (click.equals(getString(R.string.action_right_click_down))) {
//            newClick = getString(R.string.action_right_click_down);
//        }
        mTouchpadTextView.setText(getString(R.string.touchpad_label) + "\n(" + click + ")");
    }

    public Point getCurrentPosition() {
        return mCurrPos;
    }

    public void setServerThread(Thread thread) {
        mServerThread = thread;
    }
}
