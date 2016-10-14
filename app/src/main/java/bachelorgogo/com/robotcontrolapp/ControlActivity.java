package bachelorgogo.com.robotcontrolapp;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.view.View;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageButton;
import android.widget.Toast;

public class ControlActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {
    ImageButton mMenuBtn;
    boolean BACK_PRESSED_ONCE = false;

    FragmentTransaction fragmentTransaction;
    Fragment controlFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_control);

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

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
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        if (id == R.id.nav_car) {

        }
        else if (id == R.id.nav_information) {
            Toast.makeText(this, "Information Clicked", Toast.LENGTH_SHORT).show();
        }
        else if (id == R.id.nav_settings) {
            Toast.makeText(this, "SettingsClicked", Toast.LENGTH_SHORT).show();
        }
        else if (id == R.id.nav_help) {
            Intent startControlActivity = new Intent(ControlActivity.this, HelpActivity.class);
            startActivity(startControlActivity);
        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    protected void showDeviceInfoDialog(String name, String address, String storageSpace, boolean hasCamera) {
        DeviceInfoDialogFragment dialog = new DeviceInfoDialogFragment();
        Bundle args = new Bundle();
        args.putString(ControlFragment.DEVICE_NAME_STRING, name);
        args.putString(ControlFragment.DEVICE_ADDRESS_STRING, address);
        args.putString(ControlFragment.DEVICE_STORAGE_STRING, storageSpace);
        args.putBoolean(ControlFragment.DEVICE_CAMERA_BOOL, hasCamera);
        dialog.setArguments(args);
        dialog.show(getSupportFragmentManager(), "InfoDialog");
    }
}
