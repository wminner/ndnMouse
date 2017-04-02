package edu.ucla.cs.ndnmouse;

import android.os.Bundle;
import android.support.v7.preference.PreferenceFragmentCompat;

/**
 * Created by wesle on 4/2/2017.
 */

public class SettingsFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.preferences);
    }
}
