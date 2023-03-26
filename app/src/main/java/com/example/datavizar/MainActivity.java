package com.example.datavizar;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

public class MainActivity extends AppCompatActivity {

    private boolean mUserRequestedInstall = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        maybeEnableArButton();
        //this.getSupportActionBar().hide();

        Handler handler = new Handler(Looper.getMainLooper());

        final Runnable r = new Runnable() {
            public void run() {
                Intent intent = new Intent(MainActivity.this, CarregarDadosActivity1.class);
                startActivity(intent);
                finish();
            }
        };
        handler.postDelayed(r, 3000);

    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!CameraPermissionHelper.hasCameraPermission(this)) {
            CameraPermissionHelper.requestCameraPermission(this);
            return;
        }

        try {
            if (mSession == null) {
                switch (ArCoreApk.getInstance().requestInstall(this, mUserRequestedInstall)) {
                    case INSTALLED:
                        mSession = new Session(this);
                        break;
                    case INSTALL_REQUESTED:
                        mUserRequestedInstall = false;
                        return;
                }
            }
        } catch (UnavailableUserDeclinedInstallationException e) {
            Toast.makeText(this, "TODO: handle exception "+ e, Toast.LENGTH_LONG).show();
            return;
        }
    }

    @Override
    public void onRequestPermissionResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionResult(requestCode, permissions, results);
        if (!CameraPermissionHelper.hasCameraPermissions(this)) {
            Toast.makeText(this, "Camera permissions is needed to tun this application", Toast.LENGTH_LONG).show();
            if (!CameraPermissionHelper.shouldShowRequestPermissionRationale(this)) {
                CameraPermissionHelper.launchPermissionSettings(this);
            }
        }
    }

    void maybeEnableArButton() {
        ArCoreApk.Availability availability = ArCoreApk.getInstance().checkAvailability(this);
        if (availability.isTransient()) {
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    maybeEnableArButton();
                }
            }, 200);
        }
        if (availability.isSupported()) {
            mArButton.setVisibility(View.VISIBLE);
            mArButton.setEnabled(true);
        } else {
            mArButton.setVisibility(View.INVISIBLE);
            mArButton.setEnabled(false);
        }
    }
}








/*
package com.example.datavizar;

import android.os.Bundle;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;

import androidx.viewpager.widget.ViewPager;
import androidx.appcompat.app.AppCompatActivity;

import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.example.datavizar.ui.main.SectionsPagerAdapter;
import com.example.datavizar.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        SectionsPagerAdapter sectionsPagerAdapter = new SectionsPagerAdapter(this, getSupportFragmentManager());
        ViewPager viewPager = binding.viewPager;
        viewPager.setAdapter(sectionsPagerAdapter);
        TabLayout tabs = binding.tabs;
        tabs.setupWithViewPager(viewPager);
        FloatingActionButton fab = binding.fab;

        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });
    }
}*/