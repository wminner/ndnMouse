package edu.ucla.cs.ndnmouse;

import android.content.res.Resources;
import android.support.constraint.solver.SolverVariable;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MouseActivity extends AppCompatActivity {

    private static final String TAG = MouseActivity.class.getSimpleName();

    private static int[] mTouchpadCoords = new int[2];
    private static int mTouchpadX;
    private static int mTouchpadY;
    private static int mTouchpadWidth;
    private static int mTouchpadHeight;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mouse);

        // Find out upper-left coordinate, and width/height of touchpad box
        final TextView mTouchpadTextView = (TextView) findViewById(R.id.tv_touch_pad);
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
            }
        });
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
                Log.d(TAG, String.format("ACTION_MOVE: %d %d", x, y));
                break;
            case MotionEvent.ACTION_UP:
                Log.d(TAG, String.format("ACTION_UP: %d %d", x, y));
                break;
        }
        return false;
    }
}
