package edu.ucla.cs.ndnmouse;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
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
