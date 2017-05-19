package edu.ucla.cs.ndnmouse;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.io.IOException;

import edu.ucla.cs.ndnmouse.utilities.Server;

public class KeyboardActivity extends AppCompatActivity {

    private static final String TAG = KeyboardActivity.class.getSimpleName();

    private TextView mKeyboardStatusTextView;
    private Server mServer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_keyboard);

        mKeyboardStatusTextView = (TextView) findViewById(R.id.tv_keyboard_touchpad);
        setupCallbacks();
    }

    /**
     * Helper function to setup each callback (for buttons and touchpad)
     */
    private void setupCallbacks() {
        // Up Arrow Key
        final Button upArrowButton = (Button) findViewById(R.id.b_up_arrow);
        upArrowButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    mServer.executeCommand(R.string.action_keypress_up_arrow_down);
                    displayKeyPress(getString(R.string.action_keypress_up_arrow_down));
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    mServer.executeCommand(R.string.action_keypress_up_arrow_up);
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
                    mServer.executeCommand(R.string.action_keypress_down_arrow_down);
                    displayKeyPress(getString(R.string.action_keypress_down_arrow_down));
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    mServer.executeCommand(R.string.action_keypress_down_arrow_up);
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
                    mServer.executeCommand(R.string.action_keypress_left_arrow_down);
                    displayKeyPress(getString(R.string.action_keypress_left_arrow_down));
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    mServer.executeCommand(R.string.action_keypress_left_arrow_up);
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
                    mServer.executeCommand(R.string.action_keypress_right_arrow_down);
                    displayKeyPress(getString(R.string.action_keypress_right_arrow_down));
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    mServer.executeCommand(R.string.action_keypress_right_arrow_up);
                    displayKeyPress(getString(R.string.action_keypress_right_arrow_up));
                } else {
                    return false;
                }
                return true;
            }
        });
    }

    private void displayKeyPress(String keyPress) {
        mKeyboardStatusTextView.setText(keyPress);
    }
}
