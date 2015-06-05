package com.bamboomobilehealth.marufino.pdwearable;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.app.ActionBar;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.support.v4.widget.DrawerLayout;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.w3c.dom.Text;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import retrofit.Callback;
import retrofit.RestAdapter;
import retrofit.RetrofitError;
import retrofit.client.Response;
import retrofit.http.Multipart;
import retrofit.http.POST;
import retrofit.http.PUT;
import retrofit.http.Part;
import retrofit.http.Path;

public class Hub extends ActionBarActivity
        implements NavigationDrawerFragment.NavigationDrawerCallbacks {

    /**
     * Fragment managing the behaviors, interactions and presentation of the navigation drawer.
     */
    private NavigationDrawerFragment mNavigationDrawerFragment;

    /**
     * Used to store the last screen title. For use in {@link #restoreActionBar()}.
     */

    private static TextView tv;

    private CharSequence mTitle;

    private final static String TAG = Hub.class.getSimpleName();

    public static final String EXTRAS_DEVICE = "EXTRAS_DEVICE";
    private static final String KEY_TV_STATE = "KEY_TV_STATE";

    public static String mDeviceAddress;
    private RBLService mBluetoothLeService;
    public static Map<UUID, BluetoothGattCharacteristic> map = new HashMap<UUID, BluetoothGattCharacteristic>();

    private BluetoothAdapter mBluetoothAdapter;
    private static final int REQUEST_ENABLE_BT = 1;
    private static final long SCAN_PERIOD = 3000;
    private Dialog mDialog;
    public static List<BluetoothDevice> mDevices = new ArrayList<BluetoothDevice>();
    public static Hub instance = null;

    private String redString;
    private String greenString;
    private String yellowString;
    private String blueString;
    private String mDeviceName;

    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName,
                                       IBinder service) {
            mBluetoothLeService = ((RBLService.LocalBinder) service)
                    .getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the device upon successful start-up
            // initialization.
            mBluetoothLeService.connect(mDeviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {


        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();


            if (RBLService.ACTION_GATT_DISCONNECTED.equals(action)) {
            } else if (RBLService.ACTION_GATT_SERVICES_DISCOVERED
                    .equals(action)) {
                getGattService(mBluetoothLeService.getSupportedGattService());
            } else if (RBLService.ACTION_DATA_AVAILABLE.equals(action)) {
                displayData(intent.getByteArrayExtra(RBLService.EXTRA_DATA));

            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hub);

        mNavigationDrawerFragment = (NavigationDrawerFragment)
                getSupportFragmentManager().findFragmentById(R.id.navigation_drawer);
        mTitle = getTitle();

        // Set up the drawer.
        mNavigationDrawerFragment.setUp(
                R.id.navigation_drawer,
                (DrawerLayout) findViewById(R.id.drawer_layout));


        tv = (TextView) findViewById(R.id.textView);
        tv.setMovementMethod(new ScrollingMovementMethod());

        if (!getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "Ble not supported", Toast.LENGTH_SHORT)
                    .show();
            finish();
        }

        final BluetoothManager mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "Ble not supported", Toast.LENGTH_SHORT)
                    .show();
            finish();
            return;
        }

        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(
                    BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        /*Button btn = (Button)findViewById(R.id.btn);
        btn.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                scanLeDevice();

                showRoundProcessDialog(Hub.this, R.layout.loading_process_dialog_anim);

                Timer mTimer = new Timer();
                mTimer.schedule(new TimerTask() {

                    @Override
                    public void run() {
                        Intent deviceListIntent = new Intent(getApplicationContext(),
                                Device.class);
                        startActivity(deviceListIntent);
                        mDialog.dismiss();
                    }
                }, SCAN_PERIOD);


            }
        });*/

        instance = this;

        Intent intent = getIntent();

        mDeviceAddress = intent.getStringExtra(Device.EXTRA_DEVICE_ADDRESS);
        mDeviceName = intent.getStringExtra(Device.EXTRA_DEVICE_NAME);

        Intent gattServiceIntent = new Intent(this, RBLService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
    }


    @Override
    protected void onResume() {
        super.onResume();

        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
    }

    @Override
    public void onNavigationDrawerItemSelected(int position) {
        // update the main content by replacing fragments
        /*FragmentManager fragmentManager = getSupportFragmentManager();
        fragmentManager.beginTransaction()
                .replace(R.id.container, PlaceholderFragment.newInstance(position + 1))
                .commit();*/
    }

    public void onSectionAttached(int number) {
        switch (number) {
            case 1:
                mTitle = getString(R.string.title_section1);
                break;
            case 2:
                mTitle = getString(R.string.title_section2);
                break;
            case 3:
                mTitle = getString(R.string.title_section3);
                break;
            case 4:
                mTitle = getString(R.string.title_section4);
                break;
            case 5:
                mTitle = getString(R.string.title_section5);
                break;
        }
    }

    public void restoreActionBar() {
        ActionBar actionBar = getSupportActionBar();
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
        actionBar.setDisplayShowTitleEnabled(true);
        actionBar.setTitle(mTitle);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (!mNavigationDrawerFragment.isDrawerOpen()) {
            // Only show items in the action bar relevant to this screen
            // if the drawer is not showing. Otherwise, let the drawer
            // decide what to show in the action bar.
            getMenuInflater().inflate(R.menu.hub, menu);
            restoreActionBar();
            return true;
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            scanLeDevice();

            showRoundProcessDialog(Hub.this, R.layout.loading_process_dialog_anim);

            Timer mTimer = new Timer();
            mTimer.schedule(new TimerTask() {

                @Override
                public void run() {
                    Intent deviceListIntent = new Intent(getApplicationContext(),
                            Device.class);
                    startActivity(deviceListIntent);
                    mDialog.dismiss();
                }
            }, SCAN_PERIOD);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void scanLeDevice() {
        new Thread() {

            @Override
            public void run() {
                mBluetoothAdapter.startLeScan(mLeScanCallback);

                try {
                    Thread.sleep(SCAN_PERIOD);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                mBluetoothAdapter.stopLeScan(mLeScanCallback);
            }
        }.start();
    }

    public void showRoundProcessDialog(Context mContext, int layout) {
        DialogInterface.OnKeyListener keyListener = new DialogInterface.OnKeyListener() {
            @Override
            public boolean onKey(DialogInterface dialog, int keyCode,
                                 KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_HOME
                        || keyCode == KeyEvent.KEYCODE_SEARCH) {
                    return true;
                }
                return false;
            }
        };

        mDialog = new AlertDialog.Builder(mContext).create();
        mDialog.setOnKeyListener(keyListener);
        mDialog.show();
        mDialog.setContentView(layout);
    }

    private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {

        @Override
        public void onLeScan(final BluetoothDevice device, final int rssi,
                             byte[] scanRecord) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (device != null) {
                        if (mDevices.indexOf(device) == -1)
                            mDevices.add(device);
                    }
                }
            });
        }
    };

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // User chose not to enable Bluetooth.
        if (requestCode == REQUEST_ENABLE_BT
                && resultCode == Activity.RESULT_CANCELED) {
            finish();
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }


    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        // Save the current logs into the bundle
        CharSequence currentLogs = tv.getText();
        Log.d(TAG, "Saving " + currentLogs.length() + " characters from TextView");
        outState.putCharSequence(KEY_TV_STATE, currentLogs);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        if(savedInstanceState == null) {
            return;
        }

        // Restore the logs from the bundle
        CharSequence previousLogs = savedInstanceState.getCharSequence(KEY_TV_STATE);
        if(previousLogs == null) {
            return;
        }

        Log.d(TAG, "Restoring " + previousLogs.length() + " characters from Bundle");
        tv.setText(previousLogs);
    }

    private void displayData(byte[] byteArray) {

        if (byteArray != null) {
            String data = new String(byteArray);    // #ddHHmmC

            SharedPreferences prefs = getSharedPreferences("button_settings",
                    MODE_PRIVATE);

            redString = prefs.getString("redpref", "Symptom Stop");
            greenString = prefs.getString("greenpref", "Symptom Start");
            blueString = prefs.getString("bluepref", "Medication");
            yellowString = prefs.getString("yellowpref", "Event");



            data = data.replace(" ", "");


            if (data.length() > 0) {
                data = data.substring(0, data.length() - 1);
                String day = data.substring(1, 3);
                int intDay = Integer.parseInt(day);
                String hour = data.substring(3,5);
                String minute = data.substring(5,7);
                String code = data.substring(7,8);
                String codeString;

                //Determine the month, given the day of the month
                String month = getMonth(intDay);
                String year = "2015"; //getYear(Integer.parseInt(month));



                //Change strings via customization in "settings" page
                if (data.endsWith("1")) {                               //Red Button
                    codeString = redString + " Stop";
                    data = year + "-" + month + "-" + data.substring(1, 3) + " " + data.substring(3, 5) + ":" + data.substring(5, 7) + ":" + " " + redString + " Stop";
                    code = getCode(codeString);
                } else if (data.endsWith("2")) {                          //Yellow Button
                    data = year + "-" + month + "-" + data.substring(1, 3) + " " + data.substring(3, 5) + ":" + data.substring(5, 7) + ":" + " " + yellowString;
                    code = getCode(yellowString);
                } else if (data.endsWith("3")) {                          //Green Button
                    codeString = redString + " Start";
                    data = year + "-" + month + "-" + data.substring(1, 3) + " " + data.substring(3, 5) + ":" + data.substring(5, 7) + ":" + " " + greenString + " Start";
                    code = getCode(codeString);
                } else if (data.endsWith("4")) {                          //Blue Button
                    data = year + "-" + month + "-" + data.substring(1, 3) + " " + data.substring(3, 5) + ":" + data.substring(5, 7) + ":" + " " + blueString;
                    code = getCode(blueString);
                }

                Event e = new Event();
                e.device = prefs.getString("deviceName", "Unknown Device Name");
                e.project = prefs.getString("projectID", "Unknown Project");
                e.time = year + "-" + month + "-" + day + " " + hour + ":" + minute + ":" + "00"; //"2015-04-20 07:20:00"
                e.code = code;
                e.uuid = UUID.randomUUID().toString();
                Log.d("uuid", e.uuid);
                RestAdapter restAdapter = new RestAdapter.Builder().setEndpoint("http://54.152.53.243").build();
                DataViz dataviz = restAdapter.create(DataViz.class);
                dataviz.putEvent(e.uuid, e.device, e.project, e.time, e.code, new Callback<String>() {
                    @Override
                    public void success(String s, Response response) {
                        Toast.makeText(Hub.this, "Success", Toast.LENGTH_LONG).show();
                    }

                    @Override
                    public void failure(RetrofitError error) {
                        //Toast.makeText(Chat.this, error.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
            }

            Log.d(TAG, data);
            tv.append(data + System.getProperty("line.separator"));
            // find the amount we need to scroll. This works by
            // asking the TextView's internal layout for the position
            // of the final line and then subtracting the TextView's height
            final int scrollAmount = tv.getLayout().getLineTop(
                    tv.getLineCount())
                    - tv.getHeight();
            // if there is no need to scroll, scrollAmount will be <=0
            if (scrollAmount > 0)
                tv.scrollTo(0, scrollAmount);
            else
                tv.scrollTo(0, 0);


        }
    }

    interface DataViz {
        @Multipart
        @POST("/dataviz/api.php/raw_event/{uuid}")
        void putEvent(
                @Path("uuid") String uuid, @Part("device") String device, @Part("project") String project,
                @Part("time") String time, @Part("code") String code, Callback<String> rc
        );
    }

    static class Event{
        String device;
        String project;
        String time;
        String code;
        String uuid;
    }

    public String getMonth(int day) {
        Calendar c = Calendar.getInstance();                 //day = 08
        int phoneDay = c.get(Calendar.DAY_OF_MONTH);
        int phoneMonth = c.get(Calendar.MONTH);             //phoneday = 09
        int month;

        if (phoneDay < day) {

            if (phoneMonth != 0) {          //if it's not january
                month = phoneMonth - 1;

            }
            else {          //If it is January...
                month = Calendar.DECEMBER;
            }
        }

        else {              //Same month
            month = phoneMonth;
        }

        month = month + 1;
        String sMonth = Integer.toString(month);

        if (month < 10) {
            sMonth = "0" + sMonth;
        }

        return sMonth;

    }

    public String getYear(int month) {
        Calendar c = Calendar.getInstance();
        int phoneMonth = c.get(Calendar.MONTH);
        int year = 0;

        if (phoneMonth < month) {
            year = c.get(Calendar.YEAR) - 1;
        }
        else {
            year = c.get(Calendar.YEAR);
        }

        String yearString = Integer.toString(year);
        return yearString;
    }

    public String getCode(String code) {

        String newCode = null;
        switch (code) {
            case "Dyskinesia":    newCode = "ED1";
                break;
            case "Fatigue":       newCode = "EF1";
                break;
            case "Depression":    newCode = "EN1";
                break;
            case "Impulsie Behavior":   newCode = "EP1";
                break;
            case "Memory Difficulty":   newCode = "ER1";
                break;
            case "Azilect (Rasagline) 1.0 mg":  newCode = "MA1";
                break;
            case "Azilect (Rasagline) 0.5 mg":  newCode = "MA2";
                break;
            case "Eldepryl (Selegiline) 5 mg":  newCode = "ME1";
                break;
            case "Mirapex (Pramipexole) 1.0 mg":    newCode = "MM1";
                break;
            case "Mirapex (Pramipexole) 1.5 mg":    newCode = "MM2";
                break;
            case "Neupro (Rotigotine) 2.0 mg":      newCode = "MN1";
                break;
            case "Requip (Ropinirole) 1.0 mg":      newCode = "MR1";
                break;
            case "Requip (Ropinirole) 2.0 mg":      newCode = "MR2";
                break;
            case "Sinemet 12.5 mg/50 mg":           newCode = "MS1";
                break;
            case "Symmetrel (Amantadine) 100 mg":   newCode = "MS1";
                break;
            case "Sinemet 25 mg/100 mg":            newCode = "MS2";
                break;
            case "Bradykinesia Stop":                    newCode = "SB0";
                break;
            case "Bradykinesia Start":              newCode = "SB1";
                break;
            case "Freezing Stop":                   newCode = "SF0";
                break;
            case "Freezing Start":                  newCode = "SF1";
                break;
            case "Tremor Stop":                     newCode = "ST0";
                break;
            case "Tremor Start":                    newCode = "ST1";
                break;
            case "Balance-Walking Stop":            newCode = "SW0";
                break;
            case "Balance-Walking Start":           newCode = "SW1";
                break;
            default:        newCode = "Unknown Code";
        }

        return newCode;
    }

    private void getGattService(BluetoothGattService gattService) {
        if (gattService == null)
            return;

        BluetoothGattCharacteristic characteristic = gattService
                .getCharacteristic(RBLService.UUID_BLE_SHIELD_TX);
        map.put(characteristic.getUuid(), characteristic);

        BluetoothGattCharacteristic characteristicRx = gattService
                .getCharacteristic(RBLService.UUID_BLE_SHIELD_RX);
        mBluetoothLeService.setCharacteristicNotification(characteristicRx,
                true);
        mBluetoothLeService.readCharacteristic(characteristicRx);
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();

        intentFilter.addAction(RBLService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(RBLService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(RBLService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(RBLService.ACTION_DATA_AVAILABLE);

        return intentFilter;
    }

}
