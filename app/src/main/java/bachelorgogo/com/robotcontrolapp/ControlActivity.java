package bachelorgogo.com.robotcontrolapp;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.v4.app.ActionBarDrawerToggle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.util.Log;
import android.view.DragEvent;
import android.view.View;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import org.w3c.dom.Text;

public class ControlActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    final String TAG = "ControlActivity";
    // Fragment argument keys
    public final static String DEVICE_MAC_STRING = "deviceMac";
    public final static String DEVICE_STORAGE_REMAINING_STRING = "deviceStorageRemaining";
    public final static String DEVICE_CAMERA_BOOL = "deviceCameraInfo";
    public final static String DEVICE_STORAGE_SPACE_STRING = "deviceStorageSpace";

    ImageButton mMenuBtn;
    boolean BACK_PRESSED_ONCE = false;

    private NavigationView mNavigationView;

    FragmentTransaction fragmentTransaction;
    Fragment controlFragment;

    @Override
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        Log.d(TAG, "onPostCreate: called");
        super.onPostCreate(savedInstanceState);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_control);

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.addDrawerListener(new DrawerLayout.DrawerListener() {
            @Override
            public void onDrawerSlide(View drawerView, float slideOffset) {

            }

            @Override
            public void onDrawerOpened(View drawerView) {
                TextView myTv = (TextView)findViewById(R.id.tvDeviceNameHere);
                StatusMessage setStatus = ((ControlFragment)getSupportFragmentManager().findFragmentById(R.id.containerView)).getStatus();
                myTv.setText(setStatus.getCarName());
            }

            @Override
            public void onDrawerClosed(View drawerView) {

            }

            @Override
            public void onDrawerStateChanged(int newState) {

            }
        });
        
        mNavigationView = (NavigationView) findViewById(R.id.nav_view);
        mNavigationView.setNavigationItemSelectedListener(this);
        
        mMenuBtn =(ImageButton)findViewById(R.id.drawer_menu_btn);
        mMenuBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
                drawer.openDrawer(GravityCompat.START);
            }
        });
        
        

        controlFragment = new ControlFragment();
        fragmentTransaction = getSupportFragmentManager().beginTransaction();
        fragmentTransaction.replace(R.id.containerView,controlFragment,null);
        fragmentTransaction.commit();
    }




    @Override
    public void onBackPressed() {
        // Make Back Button be forced to be doubleclicked instead of single clicked
        // The reason is that the Joystick will be placed close to the back button,
        // thus single click back is being disabled to ensure no false back press.

        if (BACK_PRESSED_ONCE) {
            WiFiDirectService wiFiDirectService = ((ControlFragment)getSupportFragmentManager().findFragmentById(R.id.containerView)).getService();
            wiFiDirectService.disconnectFromDevice();
            super.onBackPressed();
            // Maybe back to connected screen instead super should implement this when connect activity is main.
            // for development reasons contrl/
            return;
        }

        this.BACK_PRESSED_ONCE = true;
        final Toast toast = Toast.makeText(this, "Press Back agian to exit", Toast.LENGTH_SHORT);
        toast.show();

        new Handler().postDelayed(new Runnable() {

            @Override
            public void run() {
                BACK_PRESSED_ONCE=false;
                toast.cancel();
            }
        }, 1000);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.control, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        Log.d(TAG, "onNavigationItemSelected: called");
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        if (id == R.id.nav_car) {

        }
        else if (id == R.id.nav_information) {
            //Toast.makeText(this, "Information Clicked", Toast.LENGTH_SHORT).show();
            StatusMessage setStatus = ((ControlFragment)getSupportFragmentManager().findFragmentById(R.id.containerView)).getStatus();
            showDeviceInfoDialog(setStatus.getMac(), setStatus.getStorageSpace(), setStatus.getStorageRemaining(), setStatus.getCameraAvailable());
        }
        else if (id == R.id.nav_settings) {
            //Toast.makeText(this, "SettingsClicked", Toast.LENGTH_SHORT).show();
            Intent settingsIntent = new Intent(ControlActivity.this, SettingsActivity.class);
            startActivity(settingsIntent);
        }
        else if (id == R.id.nav_help) {
            Intent startControlActivity = new Intent(ControlActivity.this, HelpActivity.class);
            startActivity(startControlActivity);
        }
        else if (id == R.id.nav_disconnect) {
            WiFiDirectService wiFiDirectService = ((ControlFragment)getSupportFragmentManager().findFragmentById(R.id.containerView)).getService();
            wiFiDirectService.disconnectFromDevice();
            finish();
        }
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
}
