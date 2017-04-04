package edu.ucla.cs.ndnmouse;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

public class KeyboardActivity extends AppCompatActivity {

    private static final String TAG = KeyboardActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_keyboard);
    }
}
