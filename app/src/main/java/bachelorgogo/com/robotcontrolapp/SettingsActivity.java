package bachelorgogo.com.robotcontrolapp;

import android.content.ComponentName;
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
import android.text.InputFilter;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.util.Log;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.Toast;

/*
    Adapted from the template Settings Activity and Android doc @ https://developer.android.com/reference/android/preference/PreferenceActivity.html
    This activity inflates a fragment with the desired layout.
    In order to register changes on SharedPreferences this activity also implements an
    onSharedPreferenceChangeListener.
*/
public class SettingsActivity extends AppCompatPreferenceActivity implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = "SettingsActivity";

    // WiFiDirectService related
    private static WiFiDirectService mService;
    private static boolean mBound;

    // SharedPreferences related
    private static boolean mUnsyncedChanges;
    private static PreferenceFragment mPreferenceFragment;
    private static AlertDialog mConfirmationDialog;
    private static SharedPreferences mSharedPrefs;

    //UI elements
    private static SettingsObject mSettings;
    private static Preference upload_btn;
    private static EditText device_name_edit_txt;

    //Restore points
    private static String mDeviceName;
    private static String mVideoQualityIndex;
    private static boolean mPowerSaveMode;
    private static boolean mAssistedDrivingMode;

    //saveInstanceState keys
    private String DEVICE_NAME_KEY = "device_name_key";
    private String VIDEO_QUALITY_KEY = "video_quality_key";
    private String POWER_MODE_KEY = "power_mode_key";
    private String DRIVE_MODE_KEY = "drive_mode_key";
    private String UNSYNCED_CHANGES_KEY = "unsynced_changes_key";

    /**
     * A preference value change listener that updates the preference's summary
     * to reflect its new value.
     */
    private static Preference.OnPreferenceChangeListener sBindPreferenceSummaryToValueListener = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object value) {
            String stringValue = value.toString();

            if (preference instanceof ListPreference) {
                // For ListPreferences, look up the correct display value in
                // the preference's 'entries' list.
                ListPreference listPreference = (ListPreference) preference;
                int index = listPreference.findIndexOfValue(stringValue);

                // Set the summary to reflect the new value.
                preference.setSummary(
                        index >= 0
                                ? listPreference.getEntries()[index]
                                : null);

            } else if (preference instanceof EditTextPreference) {
                // For EditText preferences, set the summary to the value's
                // string representation.
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
        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        // Create restore point to handle unsaved changes
        if(savedInstanceState != null) {
            Log.d(TAG,"Creating restore point from saved instance");
            mDeviceName = savedInstanceState.getString(DEVICE_NAME_KEY,getString(R.string.robotName));
            mVideoQualityIndex = savedInstanceState.getString(VIDEO_QUALITY_KEY,"Unknown");
            mPowerSaveMode = savedInstanceState.getBoolean(POWER_MODE_KEY,false);
            mAssistedDrivingMode = savedInstanceState.getBoolean(DRIVE_MODE_KEY,false);
            mUnsyncedChanges = savedInstanceState.getBoolean(UNSYNCED_CHANGES_KEY,false);
        } else {
            Log.d(TAG,"Creating restore point");
            mDeviceName = mSharedPrefs.getString(getString(R.string.settings_device_name_key), getString(R.string.robotName));
            mVideoQualityIndex = mSharedPrefs.getString(getString(R.string.settings_video_key), "1");
            mPowerSaveMode = mSharedPrefs.getBoolean(getString(R.string.settings_power_save_mode_key), false);
            mAssistedDrivingMode = mSharedPrefs.getBoolean(getString(R.string.settings_assisted_driving_mode_key), false);
            mUnsyncedChanges = false;
        }

        mPreferenceFragment = new PreferenceFragment();
        getFragmentManager().beginTransaction().replace(android.R.id.content, mPreferenceFragment).commit();
        setupActionBar();
        PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(this);

        // AlertDialog warns user about unsaved changes
        AlertDialog.Builder builder = new AlertDialog.Builder(this,R.style.AppCompatAlertDialogStyle);
        builder.setTitle(getString(R.string.settings_unsaved_changes_dialog_title));
        builder.setMessage(getString(R.string.settings_unsaved_changes_dialog_message));
        builder.setPositiveButton(getString(R.string.settings_unsaved_changes_dialog_ok_btn_text), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                restorePreferences();
                finish();
            }
        });
        builder.setNegativeButton(getString(R.string.settings_unsaved_changes_dialog_cancel_btn_text),null);
        mConfirmationDialog = builder.create();

        //binding to service currently exists for whole lifetime of activity
        Intent wifiServiceIntent = new Intent(this, WiFiDirectService.class);
        bindToService(wifiServiceIntent);
    }

    /*
        Persisting restore point and unsaved changes flag
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putString(DEVICE_NAME_KEY,mDeviceName);
        outState.putString(VIDEO_QUALITY_KEY,mVideoQualityIndex);
        outState.putBoolean(POWER_MODE_KEY,mPowerSaveMode);
        outState.putBoolean(DRIVE_MODE_KEY,mAssistedDrivingMode);
        outState.putBoolean(UNSYNCED_CHANGES_KEY,mUnsyncedChanges);
        super.onSaveInstanceState(outState);
    }

    /*
        Listen for changes on SharedPreferences. If any changes occurs on the preferences
        related to the robot, a flag is set.
     */
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if(key.equals(getString(R.string.settings_video_key))
                || key.equals(getString(R.string.settings_device_name_key))
                || key.equals(getString(R.string.settings_power_save_mode_key))
                || key.equals(getString(R.string.settings_assisted_driving_mode_key))) {
            Log.d(TAG,"Robot preferences changed");
            mUnsyncedChanges = true;
            upload_btn.setSummary(getString(R.string.settings_not_synced));
        }
    }

    @Override
    protected void onDestroy() {
        unbindFromService();
        super.onDestroy();
    }

    /*
        Action bar back button
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        switch (itemId) {
            case android.R.id.home:
                if(mUnsyncedChanges)
                    mConfirmationDialog.show();
                else
                    finish();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    /*
        Android device back button
     */
    @Override
    public void onBackPressed() {
        if(mUnsyncedChanges)
            mConfirmationDialog.show();
        else
            finish();
    }

    /*
        Sets up the action bar and enables the back button on the action bar
     */
    private void setupActionBar() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            // Show the Up button in the action bar.
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle("Settings");
        }
    }

    /*
        This function restores preferences from a restore point.
        This is used to restore preference when the user exits with unsaved changes.
     */
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

    /* Defines callbacks for service binding, passed to bindService() */
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

    /*
        Binds to the WiFiDirectService
     */
    protected void bindToService(Intent service) {
        Log.d("bindToService", "called");
        bindService(service, mConnection, 0);
    }

    /*
        Unbinds from the WiFiDirectService
     */
    protected void unbindFromService() {
        // Unbind from the service
        if (mBound) {
            unbindService(mConnection);
        }
    }

    /*
        This PreferenceFragment is inflated with desired layout for each header in the SettingsActivity.
     */
    public static class PreferenceFragment extends android.preference.PreferenceFragment
    {
        @Override
        public void onCreate(final Bundle savedInstanceState)
        {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.preference_screen);
            bindPreferenceSummaryToValue(findPreference("device_name_preference"));
            bindPreferenceSummaryToValue(findPreference("video_preference"));

            /*
                SettingsObject holds the object to be transmitted to the robot.
                Callbacks should be overridden to handle success or failure scenarios
             */
            mSettings = new SettingsObject() {
                @Override
                public void onSuccess(String command) {
                    Log.d(TAG,"Settings successfully saved to robot. Creating new restore point");
                    mDeviceName = mSharedPrefs.getString(getString(R.string.settings_device_name_key), getString(R.string.robotName));
                    mVideoQualityIndex = mSharedPrefs.getString(getString(R.string.settings_video_key), "1");
                    mPowerSaveMode = mSharedPrefs.getBoolean(getString(R.string.settings_power_save_mode_key), false);
                    mAssistedDrivingMode = mSharedPrefs.getBoolean(getString(R.string.settings_assisted_driving_mode_key), false);
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

            // Button to save preferences on the device
            upload_btn = (Preference)findPreference(getString(R.string.settings_upload_btn_key));
            upload_btn.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    // Button is disabled until SettingsClient invokes a callback signaling
                    // either success or failure to transmit the settings
                    upload_btn.setEnabled(false);

                    // Set the SettingsObject with the current values stored in SharedPreference
                    // and invoke the service to send the settings to the robot
                    mSettings.setSettings(mSharedPrefs.getString(getString(R.string.settings_device_name_key),getString(R.string.robotName)),
                            mSharedPrefs.getString(getString(R.string.settings_video_key),"1"),
                            mSharedPrefs.getBoolean(getString(R.string.settings_power_save_mode_key),false),
                            mSharedPrefs.getBoolean(getString(R.string.settings_assisted_driving_mode_key),false));

                    mService.sendSettingsObject(mSettings);
                    return true;
                }
            });

            // Apply character filter to device name preference in order to limit the allowed characters
            // Only letters (A-Z, a-z) and numbers (0-9) allowed.
            // @http://stackoverflow.com/questions/3349121/how-do-i-use-inputfilter-to-limit-characters-in-an-edittext-in-android
            device_name_edit_txt = ((EditTextPreference) findPreference(getString(R.string.settings_device_name_key))).getEditText();
            InputFilter charFilter = new InputFilter() {
                @Override
                public CharSequence filter(CharSequence source, int start, int end,
                                           Spanned dest, int dstart, int dend) {

                    if (source instanceof SpannableStringBuilder) {
                        SpannableStringBuilder sourceAsSpannableBuilder = (SpannableStringBuilder)source;
                        for (int i = end - 1; i >= start; i--) {
                            char currentChar = source.charAt(i);
                            if (!Character.isLetterOrDigit(currentChar)) {
                                sourceAsSpannableBuilder.delete(i, i+1);
                            }
                        }
                        return source;
                    } else {
                        StringBuilder filteredStringBuilder = new StringBuilder();
                        for (int i = start; i < end; i++) {
                            char currentChar = source.charAt(i);
                            if (Character.isLetterOrDigit(currentChar)) {
                                filteredStringBuilder.append(currentChar);
                            }
                        }
                        return filteredStringBuilder.toString();
                    }
                }
            };
            device_name_edit_txt.setFilters(new InputFilter[] {charFilter});
        }
    }
}
