package bachelorgogo.com.robotcontrolapp;

import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Toast;

// Adapted from the template Settings Activity and Android doc @ https://developer.android.com/reference/android/preference/PreferenceActivity.html
public class SettingsActivity extends AppCompatPreferenceActivity implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = "SettingsActivity";

    // WifiService stuff
    static WiFiDirectService mService;
    static boolean mBound;
    static boolean mUnsyncedChanges;
    static PreferenceFragment mPreferenceFragment;
    static AlertDialog mConfirmationDialog;

    /**
     * A preference value change listener that updates the preference's summary
     * to reflect its new value.
     */
    private static Preference.OnPreferenceChangeListener sBindPreferenceSummaryToValueListener = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object value) {
            String stringValue = value.toString();

            if (preference instanceof ListPreference) {
                // For list preferences, look up the correct display value in
                // the preference's 'entries' list.
                ListPreference listPreference = (ListPreference) preference;
                int index = listPreference.findIndexOfValue(stringValue);

                // Set the summary to reflect the new value.
                preference.setSummary(
                        index >= 0
                                ? listPreference.getEntries()[index]
                                : null);

            } else if (preference instanceof EditTextPreference) {
                // For all other preferences, set the summary to the value's
                // simple string representation.
                preference.setSummary(stringValue);
            }
            return true;
        }
    };

    /**
     * Binds a preference's summary to its value. More specifically, when the
     * preference's value is changed, its summary (line of text below the
     * preference title) is updated to reflect the value. The summary is also
     * immediately updated upon calling this method. The exact display format is
     * dependent on the type of preference.
     *
     * @see #sBindPreferenceSummaryToValueListener
     */
    private static void bindPreferenceSummaryToValue(Preference preference) {
        // Set the listener to watch for value changes.
        preference.setOnPreferenceChangeListener(sBindPreferenceSummaryToValueListener);

        // Trigger the listener immediately with the preference's
        // current value.
        sBindPreferenceSummaryToValueListener.onPreferenceChange(preference,
                PreferenceManager
                        .getDefaultSharedPreferences(preference.getContext())
                        .getString(preference.getKey(), ""));
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        mPreferenceFragment = new PreferenceFragment();
        getFragmentManager().beginTransaction().replace(android.R.id.content, mPreferenceFragment).commit();
        setupActionBar();
        PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(this);
        mUnsyncedChanges = false;

        AlertDialog.Builder builder = new AlertDialog.Builder(this,R.style.AppCompatAlertDialogStyle);
        builder.setTitle(getString(R.string.settings_unsaved_changes_dialog_title));
        builder.setMessage(getString(R.string.settings_unsaved_changes_dialog_message));
        builder.setPositiveButton(getString(R.string.settings_unsaved_changes_dialog_ok_btn_text), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                mPreferenceFragment.restorePreferences();
                onBackPressed();
            }
        });
        builder.setNegativeButton(getString(R.string.settings_unsaved_changes_dialog_cancel_btn_text),null);
        mConfirmationDialog = builder.create();

        //binding to service currently exists for whole lifetime of activity
        Intent wifiServiceIntent = new Intent(this, WiFiDirectService.class);
        bindToService(wifiServiceIntent);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        Log.d(TAG,"Preferences changed");
        mUnsyncedChanges = true;
    }

    private void setupActionBar() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            // Show the Up button in the action bar.
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle("Settings");
        }
    }

    @Override
    protected void onDestroy() {
        unbindFromService();
        super.onDestroy();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        switch (itemId) {
            case android.R.id.home:
                if(mUnsyncedChanges)
                    mConfirmationDialog.show();
                else
                    onBackPressed();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    /** Defines callbacks for service binding, passed to bindService() */
    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.d("onServiceConnected", "called");
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            WiFiDirectService.LocalBinder binder = (WiFiDirectService.LocalBinder) service;
            mService = binder.getService();
            mBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
        }
    };

    protected void bindToService(Intent service) {
        Log.d("bindToService", "called");
        bindService(service, mConnection, 0/*Context.BIND_AUTO_CREATE*/);
    }

    protected void unbindFromService() {
        // Unbind from the service
        if (mBound) {
            unbindService(mConnection);
        }
    }

    public static class PreferenceFragment extends android.preference.PreferenceFragment
    {
        private SettingsObject mSettings;
        private SharedPreferences mSharedPrefs;
        private Preference upload_btn;

        private String mDeviceName;
        private String mVideoQualityIndex;
        private boolean mPowerSaveMode;
        private boolean mAssistedDrivingMode;

        @Override
        public void onCreate(final Bundle savedInstanceState)
        {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.preference_screen);
            bindPreferenceSummaryToValue(findPreference("device_name_preference"));
            bindPreferenceSummaryToValue(findPreference("video_preference"));

            mUnsyncedChanges = false;

            mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
            // Create restore point
            mDeviceName = mSharedPrefs.getString(getString(R.string.settings_device_name_key),getString(R.string.robotName));
            mVideoQualityIndex = mSharedPrefs.getString(getString(R.string.settings_video_key),"1");
            mPowerSaveMode = mSharedPrefs.getBoolean(getString(R.string.settings_power_save_mode_key),false);
            mAssistedDrivingMode = mSharedPrefs.getBoolean(getString(R.string.settings_assisted_driving_mode_key),false);

            mSettings = new SettingsObject() {
                @Override
                public void onSuccess(String command) {
                    mUnsyncedChanges = false;
                    upload_btn.setEnabled(true);
                    upload_btn.setSummary(getString(R.string.settings_synced));
                }

                @Override
                public void onFailure(String command) {
                    mUnsyncedChanges = true;
                    Toast.makeText(getActivity(), getString(R.string.settings_sync_error_toast), Toast.LENGTH_LONG).show();
                    upload_btn.setEnabled(true);
                    upload_btn.setSummary(getString(R.string.settings_not_synced));
                }
            };

            upload_btn = (Preference)findPreference(getString(R.string.settings_upload_btn_key));
            upload_btn.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    upload_btn.setEnabled(false);
                    mSettings.setSettings(mSharedPrefs.getString(getString(R.string.settings_device_name_key),getString(R.string.robotName)),
                            mSharedPrefs.getString(getString(R.string.settings_video_key),"1"),
                            mSharedPrefs.getBoolean(getString(R.string.settings_power_save_mode_key),false),
                            mSharedPrefs.getBoolean(getString(R.string.settings_assisted_driving_mode_key),false));

                    mService.sendSettingsObject(mSettings);
                    return true;
                }
            });
        }

        public void restorePreferences() {
            if (mUnsyncedChanges) {
                Log.d(TAG,"Restoring preferences");
                SharedPreferences.Editor editor = mSharedPrefs.edit();
                editor.putString(getString(R.string.settings_device_name_key), mDeviceName);
                editor.putString(getString(R.string.settings_video_key), mVideoQualityIndex);
                editor.putBoolean(getString(R.string.settings_power_save_mode_key), mPowerSaveMode);
                editor.putBoolean(getString(R.string.settings_assisted_driving_mode_key), mAssistedDrivingMode);
                editor.commit();
            }
        }

        @Override
        public void onDestroy() {
            restorePreferences();
            super.onDestroy();
        }
    }
}
