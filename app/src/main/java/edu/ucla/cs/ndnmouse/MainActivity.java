package edu.ucla.cs.ndnmouse;

import android.content.Context;
import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();

    private static String mServerPassword = "1234";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start);

        final Button startButton = (Button) findViewById(R.id.b_start);
        startButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Context context = MainActivity.this;
                Class destinationClass = MouseActivity.class;
                Intent intentToStartMouseActivity = new Intent(context, destinationClass);
                intentToStartMouseActivity.putExtra(Intent.EXTRA_TEXT, mServerPassword);
                startActivity(intentToStartMouseActivity);
            }
        });
    }

}
