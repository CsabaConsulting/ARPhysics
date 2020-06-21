package dev.csaba.arphysics;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

class SettingsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_settings);
        // display fragment
        // getFragmentManager()
        getSupportFragmentManager()
            .beginTransaction()
            .replace(R.id.settings_container, new SettingsFragment())
            .commit();
    }
}
