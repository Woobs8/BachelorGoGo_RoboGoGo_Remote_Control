package bachelorgogo.com.robotcontrolapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentTransaction;
import android.util.Log;
import android.view.View;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AppCompatActivity;
import android.view.MenuItem;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

// Implements NavigationView to be able to use Drawer
public class ControlActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    // TAG String Mainly for Debug Purposes
    private final String TAG = "ControlActivity";

    // Fragment argument keys
    public final static String DEVICE_MAC_STRING = "deviceMac";
    public final static String DEVICE_STORAGE_REMAINING_STRING = "deviceStorageRemaining";
    public final static String DEVICE_CAMERA_BOOL = "deviceCameraInfo";
    public final static String DEVICE_STORAGE_SPACE_STRING = "deviceStorageSpace";

    // UI Elements
    private ImageButton mMenuBtn;           // Menu/Drawer Button
    private NavigationView mNavigationView; // Navigation View for Drawer
    private DrawerLayout mDrawer;           // The Actual Drawer

    // Phone Back Button Flag (avoid leaving activity if back is pressed unintended )
    private boolean BACK_PRESSED_ONCE = false;

    // Fragment
    private FragmentTransaction fragmentTransaction;
    private ControlFragment controlFragment;

    // Shared Preferences
    private SharedPreferences mSharedPrefs;

    // Local Variables
    private final int DOUBLE_BACK_PRESS_TIMEFRAME_MS = 1000;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set Content for Activity
        setContentView(R.layout.activity_control);

        // The Drawer Instance is Gotten and Activity Functionality is Set
        setupDrawerFunctionality();

        // The Navigation View Instance is Gotten and Activity Functionality is Set
        setupNavigationViewFunctionality();

        // The Menu Button Instance is Gotten and Activity Functionality is Set
        setupMenuButtonFunctionality();

        // The ControlFragment is Instantiated, Committed and setup
        setupControlFragment();

    }

    @Override
    public void onBackPressed() {
        // Make Back Button be forced to be doubleclicked instead of single clicked
        // The reason is that the Joystick will be placed close to the back button,
        // thus single click back is being disabled to ensure no false back press.

        if (BACK_PRESSED_ONCE) {
            // Disconnect From WiFiDirectService If Leaving The Activity
            disconnectWiFi();
            super.onBackPressed();
            return;
        }
        this.BACK_PRESSED_ONCE = true;
        // Tell User to Press Back Again if Wanting to Exit the Activity
        final Toast toast = Toast.makeText(this, "Press Back agian to exit", Toast.LENGTH_SHORT);

        // Make Sure The Toast is only Shown in the TimeFrame We allow to Double Back Pressed
        toast.show();
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                BACK_PRESSED_ONCE=false;
                toast.cancel();
            }
        }, DOUBLE_BACK_PRESS_TIMEFRAME_MS);
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        Log.d(TAG, "onNavigationItemSelected: called");

        // Handle navigation view item clicks here.
        int id = item.getItemId();

        if (id == R.id.nav_information) {
            // Show Information Dialog Fragment with the Data Received from The Robot
            StatusMessage setStatus = controlFragment.getStatus(); // Data Received from Robot
            showDeviceInfoDialog(setStatus.getMac(), setStatus.getStorageSpace(), setStatus.getStorageRemaining(), setStatus.getCameraAvailable());
        }
        else if (id == R.id.nav_settings) {
            // Start the SettingsActivity if Setting is pressed
            Intent settingsIntent = new Intent(ControlActivity.this, SettingsActivity.class);
            startActivity(settingsIntent);
        }
        else if (id == R.id.nav_help) {
            // Start the HelpActivity if Help is pressed
            Intent startControlActivity = new Intent(ControlActivity.this, HelpActivity.class);
            startActivity(startControlActivity);
        }
        else if (id == R.id.nav_disconnect) {
            // Disconnect From Wifi and Leave the Activity if Disconnect is Pressed
            disconnectWiFi();
            finish();
        }

        // Close The Navigation Drawer If An Item Was Clicked
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    protected void showDeviceInfoDialog(String Mac, String storageSpace, String StorageRemaining, boolean hasCamera) {
        DeviceInfoDialogFragment dialog = new DeviceInfoDialogFragment();
        Bundle args = new Bundle();
        args.putString(DEVICE_MAC_STRING, Mac);
        args.putString(DEVICE_STORAGE_SPACE_STRING, storageSpace);
        args.putString(DEVICE_STORAGE_REMAINING_STRING, StorageRemaining);
        args.putBoolean(DEVICE_CAMERA_BOOL, hasCamera);
        dialog.setArguments(args);
        dialog.show(getSupportFragmentManager(), "InfoDialog");
    }

    private void disconnectWiFi(){
        WiFiDirectService wiFiDirectService = ((ControlFragment)getSupportFragmentManager().findFragmentById(R.id.containerView)).getService();
        wiFiDirectService.disconnectFromDevice();
    }


    ////////////////////////////////////
    //    Private Setup Functions     //
    ////////////////////////////////////

    private void setupControlFragment(){

        // Get An Instance of the ControlFragment
        controlFragment = new ControlFragment();

        // Commit The controlFragmnet with a FragmentTransaction on to the ContainerView
        fragmentTransaction = getSupportFragmentManager().beginTransaction();
        fragmentTransaction.replace(R.id.containerView,controlFragment,null);
        fragmentTransaction.commit();

        // Get the Shared Preferences to setup Data in Control Fragment
        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);

        // Get Relevant data based on the correct Keys
        String carName = mSharedPrefs.getString(getString(R.string.settings_device_name_key),getString(R.string.robotName));
        String carMACAddress = mSharedPrefs.getString(getString(R.string.settings_device_MAC_address_key),"unknown");

        // Set the Data in the controlFragment object StatusMessage
        controlFragment.getStatus().setCarName(carName);
        controlFragment.getStatus().setMacAddr(carMACAddress);
    }

    private void setupNavigationViewFunctionality(){
        // Get The Navigation View
        mNavigationView = (NavigationView) findViewById(R.id.nav_view);
        mNavigationView.setNavigationItemSelectedListener(this);
    }

    private void setupMenuButtonFunctionality(){
        // Get The Menu Button (ImageButton)
        mMenuBtn =(ImageButton)findViewById(R.id.drawer_menu_btn);

        // Set An OnClickListener to start the drawer in case the Menu Button was clicked
        mMenuBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mDrawer.openDrawer(GravityCompat.START);
            }
        });
    }

    private void setupDrawerFunctionality(){
        // Get The Drawer
        mDrawer = (DrawerLayout) findViewById(R.id.drawer_layout);

        // Add A Listener to the Drawer
        mDrawer.addDrawerListener(new DrawerLayout.DrawerListener() {
            @Override
            public void onDrawerSlide(View drawerView, float slideOffset) {
                // Must be Overwritten When Adding DrawerListener
                // Do Nothing
            }

            @Override
            public void onDrawerOpened(View drawerView) {
                // If the Drawer is opened
                // we can get the TextView where the Device Name Is Shown
                TextView myTv = (TextView)findViewById(R.id.tvDeviceNameHere);
                // From the statsMessage object in the controlFragment we can
                // the actual Device Name and set the Text in the TextView.
                        // We get this from the StatusMessage object even though the name is saved
                        // in shared preferences in case a status message shows the car name Changed
                String name = mSharedPrefs.getString(getString(R.string.settings_device_name_key),
                                                                    getString(R.string.robotName));
                myTv.setText(name);
            }

            @Override
            public void onDrawerClosed(View drawerView) {
                // Must be Overwritten When Adding DrawerListener
                // Do Nothing
            }

            @Override
            public void onDrawerStateChanged(int newState) {
                // Must be Overwritten When Adding DrawerListener
                // Do Nothing
            }
        });
    }
}
