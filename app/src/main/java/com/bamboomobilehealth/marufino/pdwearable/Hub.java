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
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.NumberPicker;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.TimePicker;
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

    private static ImageView connectedIcon;
    private static TextView connectedText;


    private boolean isConnected = false;

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
            isConnected = false;
            connectedText.setText("Not Connected");
            connectedIcon.setImageResource(android.R.drawable.button_onoff_indicator_off);
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

        instance = this;

        Intent intent = getIntent();

        mDeviceAddress = intent.getStringExtra(Device.EXTRA_DEVICE_ADDRESS);
        mDeviceName = intent.getStringExtra(Device.EXTRA_DEVICE_NAME);

        Intent gattServiceIntent = new Intent(this, RBLService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

        // load main fragment
        FragmentManager fragmentManager = getSupportFragmentManager();
        fragmentManager.beginTransaction()
                .replace(R.id.container, PlaceholderFragment.newInstance(0))
                .commit();
    }


    @Override
    protected void onResume() {
        super.onResume();

        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
    }

    @Override
    public void onNavigationDrawerItemSelected(int position) {
        // update the main content by replacing fragments
        FragmentManager fragmentManager = getSupportFragmentManager();

        switch (position) {
            case 0:
                fragmentManager.beginTransaction()
                        .replace(R.id.container, PlaceholderFragment.newInstance(position))
                        .commit();
                break;
            case 1:
                fragmentManager.beginTransaction()
                        .replace(R.id.container, ButtonsFragment.newInstance(position))
                        .commit();
                break;
            case 2:
                fragmentManager.beginTransaction()
                        .replace(R.id.container, AlarmFragment.newInstance(position))
                        .commit();
                break;

        }
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
                    isConnected=true;
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

    /**
    * A placeholder fragment containing a simple view.
    */
    public static class PlaceholderFragment extends Fragment {
    /**
    * The fragment argument representing the section number for this
    * fragment.
    */
        private static final String ARG_SECTION_NUMBER = "section_number";

        /**
        * Returns a new instance of this fragment for the given section
        * number.
        */
        public static PlaceholderFragment newInstance(int sectionNumber) {
            PlaceholderFragment fragment = new PlaceholderFragment();
            Bundle args = new Bundle();
            args.putInt(ARG_SECTION_NUMBER, sectionNumber);
            fragment.setArguments(args);
            return fragment;
        }

        public PlaceholderFragment() {
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                                   Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_hub, container, false);

            tv = (TextView) rootView.findViewById(R.id.textView);
            tv.setMovementMethod(new ScrollingMovementMethod());

            connectedText = (TextView) rootView.findViewById(R.id.connectedText);
            connectedIcon = (ImageView) rootView.findViewById(R.id.connectedIcon);

            if(mDeviceAddress!=null)
            {

                connectedText.setText("Connected");
                connectedIcon.setImageResource(android.R.drawable.button_onoff_indicator_on);
            }

            // fill subtitle of buttons based on preferences
            SharedPreferences prefs = this.getActivity().getSharedPreferences("button_settings",
                    MODE_PRIVATE);

            TextView greenLabel = (TextView) rootView.findViewById(R.id.textView2);
            TextView redLabel = (TextView) rootView.findViewById(R.id.textView3);
            TextView yellowLabel = (TextView) rootView.findViewById(R.id.textView4);
            TextView blueLabel = (TextView) rootView.findViewById(R.id.textView5);

            greenLabel.setText(prefs.getString("redpref", "Symptom Stop") + " Start");
            redLabel.setText(prefs.getString("greenpref", "Symptom Start") + " Stop");
            yellowLabel.setText(prefs.getString("bluepref", "Medication"));
            blueLabel.setText(prefs.getString("yellowpref", "Event"));

            return rootView;
        }

        @Override
        public void onAttach(Activity activity) {
            super.onAttach(activity);
            ((Hub) activity).onSectionAttached(
                    getArguments().getInt(ARG_SECTION_NUMBER));
        }
    }


    /**
     * A placeholder fragment containing a simple view.
     */
    public static class ButtonsFragment extends Fragment {
        /**
         * The fragment argument representing the section number for this
         * fragment.
         */
        private static final String ARG_SECTION_NUMBER = "section_number";

        /**
         * Returns a new instance of this fragment for the given section
         * number.
         */
        public static ButtonsFragment newInstance(int sectionNumber) {
            ButtonsFragment fragment = new ButtonsFragment();
            Bundle args = new Bundle();
            args.putInt(ARG_SECTION_NUMBER, sectionNumber);
            fragment.setArguments(args);
            return fragment;
        }

        public ButtonsFragment() {
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_buttons, container, false);

            //Initialize Symptom Spinner
            final Spinner symptoms = (Spinner)rootView.findViewById(R.id.greenspinner);
            String[] symptomItems = new String[]{"Bradykinesia", "Freezing", "Tremor", "Balance-Walking"};
            ArrayAdapter<String> adapter = new ArrayAdapter<String>(getActivity(), android.R.layout.simple_spinner_item, symptomItems);
            symptoms.setAdapter(adapter);

            return rootView;
        }

        @Override
        public void onAttach(Activity activity) {
            super.onAttach(activity);
            ((Hub) activity).onSectionAttached(
                    getArguments().getInt(ARG_SECTION_NUMBER));
        }
    }

    /**
     * A placeholder fragment containing a simple view.
     */
    public static class AlarmFragment extends Fragment {
        /**
         * The fragment argument representing the section number for this
         * fragment.
         */
        public static final String EXTRAS_DEVICE = "EXTRAS_DEVICE";
        private TextView tv = null;
        private EditText et = null;
        private Button btn = null;
        private Button settingsbtn = null;
        private TimePicker med1;
        private TimePicker med2;
        private TimePicker med3;
        private TimePicker med4;
        private TimePicker med5;
        private Switch medswitch1;
        private Switch medswitch2;
        private String alarm1;
        private String alarm2;
        private String alarm3;
        private String alarm4;
        private String alarm5;
        private String mDeviceName;
        private RadioGroup yellowGroup;
        private RadioGroup blueGroup;
        private String[] yellowItems;
        private String[] blueItems;
        public static String stop = "Symptom Stop";
        public static String start = "Symptom Start";
        public static String blue = "Medication";
        public static String yellow = "Event";
        public static final String MY_PREFS_NAME = "PDSettings";

        private static final String ARG_SECTION_NUMBER = "section_number";

        /**
         * Returns a new instance of this fragment for the given section
         * number.
         */
        public static AlarmFragment newInstance(int sectionNumber) {
            AlarmFragment fragment = new AlarmFragment();
            Bundle args = new Bundle();
            args.putInt(ARG_SECTION_NUMBER, sectionNumber);
            fragment.setArguments(args);

            return fragment;
        }

        public void savePrefs(String green, String red, String yellow, String blue, int num,
                              int hour1, int minute1, int hour2, int minute2, boolean switch1,
                              boolean switch2, String deviceName, String id, int hour3, int minute3, int hour4, int minute4, int hour5, int minute5,
                              boolean switch3, boolean switch4, boolean switch5) {
            SharedPreferences prefs = this.getActivity().getSharedPreferences("button_settings", MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString("redpref", red);
            editor.putString("greenpref", green);
            editor.putString("yellowpref", yellow);
            editor.putString("bluepref", blue);
            editor.putInt("numberpicker", num);
            editor.putInt("hour1", hour1);
            editor.putInt("minute1", minute1);
            editor.putInt("hour2", hour2);
            editor.putInt("minute2", minute2);
            editor.putInt("hour3", hour3);
            editor.putInt("minute3", minute3);
            editor.putInt("hour4", hour4);
            editor.putInt("minute4", minute4);
            editor.putInt("hour5", hour5);
            editor.putInt("minute5", minute5);
            editor.putBoolean("switch1", switch1);
            editor.putBoolean("switch2", switch2);
            editor.putBoolean("switch3", switch3);
            editor.putBoolean("switch4", switch4);
            editor.putBoolean("switch5", switch5);
            editor.putString("deviceName", deviceName);
            editor.putString("projectID", id);
            editor.commit();

            //Let user know that settings were saved
            Toast.makeText(getActivity(), "Settings Saved", Toast.LENGTH_SHORT).show();

        }


        public AlarmFragment() {
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_alarm, container, false);

            //Read files from preference file
            SharedPreferences prefs = this.getActivity().getSharedPreferences("button_settings",
                    MODE_PRIVATE);

            //Initialize Edit Texts
            final EditText deviceName = (EditText) rootView.findViewById(R.id.editText);
            deviceName.setText(prefs.getString("deviceName", "Enter Device Name Here"));

            final EditText projectID = (EditText) rootView.findViewById(R.id.editText2);
            projectID.setText(prefs.getString("projectID", "Enter Project ID Here"));

            //Initialize Symptom Spinner
            final Spinner symptoms = (Spinner)rootView.findViewById(R.id.startDropdown);
            String[] symptomItems = new String[]{"Bradykinesia", "Freezing", "Tremor", "Balance-Walking"};
            ArrayAdapter<String> adapter = new ArrayAdapter<String>(getActivity(), android.R.layout.simple_spinner_item, symptomItems);
            symptoms.setAdapter(adapter);

            //Initialize Yellow Spinner
            yellowGroup = (RadioGroup) rootView.findViewById(R.id.yellowGroup);
            final Spinner yellowDrop = (Spinner)rootView.findViewById(R.id.yellowDropdown);
            yellowGroup.clearCheck();

            yellowGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {

                public void onCheckedChanged(RadioGroup group, int checkedId) {
                    if (checkedId == R.id.rYellowMeds) {
                        yellowItems = new String[]{"Azilect (Rasagline) 1.0 mg", "Azilect (Rasagline) 0.5 mg", "Eldepryl (Selegiline) 5 mg",
                                "Mirapex (Pramipexole) 1.0 mg", "Mirapex (Pramipexole) 1.5 mg", "Neupro (Rotigotine) 2.0 mg", "Requip (Ropinirole) 1.0 mg",
                                "Requip (Ropinirole) 2.0 mg", "Sinemet 12.5 mg/50 mg", "Symmetrel (Amantadine) 100 mg", "Sinemet 25 mg/100 mg"};
                    }
                    else if (checkedId == R.id.rYellowEvent) {
                        yellowItems = new String[]{"Dyskinesia", "Fatigue", "Depression", "Impulsive Behavior", "Memory Difficult"};
                    }
                    ArrayAdapter<String> adapter2 = new ArrayAdapter<String>(getActivity(), android.R.layout.simple_spinner_item, yellowItems);
                    yellowDrop.setAdapter(adapter2);
                }
            });

            final RadioButton yellowMeds = (RadioButton) rootView.findViewById(R.id.rYellowMeds);
            yellowMeds.setChecked(true);
            final RadioButton yellowEvent = (RadioButton) rootView.findViewById(R.id.rYellowEvent);
            yellowMeds.setChecked(true);

            //Initialize Blue Spinner
            blueGroup = (RadioGroup) rootView.findViewById(R.id.blueGroup);
            final Spinner blueDrop = (Spinner)rootView.findViewById(R.id.blueDropdown);
            blueGroup.clearCheck();

            blueGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {

                public void onCheckedChanged(RadioGroup group, int checkedId) {
                    if (checkedId == R.id.rBlueMeds) {
                        blueItems = new String[]{"Azilect (Rasagline) 1.0 mg", "Azilect (Rasagline) 0.5 mg", "Eldepryl(Selegiline) 5 mg",
                                "Mirapex (Pramipexole) 1.0 mg", "Mirapex (Pramipexole) 1.5 mg", "Neupro (Rotigotine) 2.0 mg", "Requip (Ropinirole) 1.0 mg",
                                "Requip (Ropinirole) 2.0 mg", "Sinemet 12.5 mg/50 mg", "Symmetrel (Amantadine) 100 mg", "Sinemet 25 mg/100 mg"};
                    }
                    else if (checkedId == R.id.rBlueEvent) {
                        blueItems = new String[]{"Dyskinesia", "Fatigue", "Depression", "Impulsive Behavior", "Memory Difficulty"};
                    }
                    ArrayAdapter<String> adapter3 = new ArrayAdapter<String>(getActivity(), android.R.layout.simple_spinner_item, blueItems);
                    blueDrop.setAdapter(adapter3);
                }
            });

            final RadioButton blueMeds = (RadioButton) rootView.findViewById(R.id.rBlueMeds);
            blueMeds.setChecked(true);
            final RadioButton blueEvent = (RadioButton) rootView.findViewById(R.id.rBlueEvent);
            blueEvent.setChecked(true);

            //Initialize Number Picker
            String[] values = new String[10];
            for (int i=0;i<values.length;i++){
                values[i]=Integer.toString((i+1)*5);
            }

            final NumberPicker np = (NumberPicker) rootView.findViewById(R.id.numberPicker2);
            np.setMaxValue(values.length-1);
            np.setMinValue(1);
            np.setDisplayedValues(values);
            np.setWrapSelectorWheel(false);
            np.setValue(prefs.getInt("numberpicker", 5));   //Restore previous setting value



            //Initialize Time Pickers
            final Switch switch1 = (Switch) rootView.findViewById(R.id.medSwitch1);
            final Switch switch2 = (Switch) rootView.findViewById(R.id.medSwitch2);
            final Switch switch3 = (Switch) rootView.findViewById(R.id.medSwitch3);
            final Switch switch4 = (Switch) rootView.findViewById(R.id.medSwitch4);
            final Switch switch5 = (Switch) rootView.findViewById(R.id.medSwitch5);

            switch1.setChecked(prefs.getBoolean("switch1", true));
            switch2.setChecked(prefs.getBoolean("switch2", true ));
            switch3.setChecked(prefs.getBoolean("switch3", true));
            switch4.setChecked(prefs.getBoolean("switch4", true ));
            switch5.setChecked(prefs.getBoolean("switch5", true));

            med1 = (TimePicker) rootView.findViewById(R.id.medTime1);
            med1.setCurrentHour(prefs.getInt("hour1", 0));
            med1.setCurrentMinute(prefs.getInt("minute1", 0));
            if (switch1.isChecked()) {
                med1.setVisibility(View.VISIBLE);
            }

            med2 = (TimePicker) rootView.findViewById(R.id.medTime2);
            med2.setCurrentHour(prefs.getInt("hour2", 0));
            med2.setCurrentMinute(prefs.getInt("minute2", 0));
            if (switch2.isChecked()) {
                med2.setVisibility(View.VISIBLE);
            }

            med3 = (TimePicker) rootView.findViewById(R.id.medTime3);
            med3.setCurrentHour(prefs.getInt("hour3", 0));
            med3.setCurrentMinute(prefs.getInt("minute3", 0));
            if (switch3.isChecked()) {
                med3.setVisibility(View.VISIBLE);
            }

            med4 = (TimePicker) rootView.findViewById(R.id.medTime4);
            med4.setCurrentHour(prefs.getInt("hour4", 0));
            med4.setCurrentMinute(prefs.getInt("minute4", 0));
            if (switch4.isChecked()) {
                med4.setVisibility(View.VISIBLE);
            }

            med5 = (TimePicker) rootView.findViewById(R.id.medTime5);
            med5.setCurrentHour(prefs.getInt("hour5", 0));
            med5.setCurrentMinute(prefs.getInt("minute5", 0));
            if (switch5.isChecked()) {
                med5.setVisibility(View.VISIBLE);
            }


            switch1.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    if (isChecked) {
                        med1.setVisibility(View.VISIBLE);
                    } else {
                        med1.setVisibility(View.INVISIBLE);
                    }
                }
            });

            switch2.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    if (isChecked) {
                        med2.setVisibility(View.VISIBLE);
                    } else {
                        med2.setVisibility(View.INVISIBLE);
                    }
                }
            });

            switch3.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    if (isChecked) {
                        med3.setVisibility(View.VISIBLE);
                    } else {
                        med3.setVisibility(View.INVISIBLE);
                    }
                }
            });

            switch4.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    if (isChecked) {
                        med4.setVisibility(View.VISIBLE);
                    } else {
                        med4.setVisibility(View.INVISIBLE);
                    }
                }
            });

            switch5.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    if (isChecked) {
                        med5.setVisibility(View.VISIBLE);
                    } else {
                        med5.setVisibility(View.INVISIBLE);
                    }
                }
            });


            //All number pickers and drop boxes are initialized
            //Now it's time to send data to the BLE when the Save button is pressed

            //When save button pressed, send data to RBL
            btn = (Button) rootView.findViewById(R.id.btn_save);
            btn.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v) {
                    BluetoothGattCharacteristic characteristic = Hub.map.get(RBLService.UUID_BLE_SHIELD_TX);

                    //Interval Setting
                    final String interval = "i" + Integer.toString(np.getValue());

                    //Alarm 1
                    String hour1 = Integer.toString(med1.getCurrentHour());
                    String minute1 = Integer.toString(med1.getCurrentMinute());

                    if (med1.getCurrentMinute() < 10) {
                        minute1 = "0" + minute1;
                    }

                    if (switch1.isChecked()) {
                        alarm1 = "a" + hour1 + ":" + minute1;
                    }
                    else {
                        alarm1 = "aXX:XX";
                    }

                    //Alarm 2
                    String hour2 = Integer.toString(med2.getCurrentHour());
                    String minute2 = Integer.toString(med2.getCurrentMinute());

                    if (med2.getCurrentMinute() < 10) {
                        minute2 = "0" + minute2;
                    }

                    if (switch2.isChecked()) {
                        alarm2 = "b" + hour2 + ":" + minute2;
                    }
                    else {
                        alarm2 = "bXX:XX";
                    }

                    //Alarm 3
                    final String hour3 = Integer.toString(med3.getCurrentHour());
                    String minute3 = Integer.toString(med3.getCurrentMinute());

                    if (med3.getCurrentMinute() < 10) {
                        minute3 = "0" + minute3;
                    }

                    if (switch3.isChecked()) {
                        alarm3 = "b" + hour3 + ":" + minute3;
                    }
                    else {
                        alarm3 = "xXX:XX";
                    }

                    //Alarm 4
                    final String hour4 = Integer.toString(med4.getCurrentHour());
                    String minute4 = Integer.toString(med4.getCurrentMinute());

                    if (med4.getCurrentMinute() < 10) {
                        minute4 = "0" + minute4;
                    }

                    if (switch4.isChecked()) {
                        alarm4 = "b" + hour4 + ":" + minute4;
                    }
                    else {
                        alarm4 = "yXX:XX";
                    }

                    //Alarm 5
                    final String hour5 = Integer.toString(med5.getCurrentHour());
                    String minute5 = Integer.toString(med5.getCurrentMinute());

                    if (med5.getCurrentMinute() < 10) {
                        minute5 = "0" + minute5;
                    }

                    if (switch5.isChecked()) {
                        alarm5 = "b" + hour5 + ":" + minute5;
                    }
                    else {
                        alarm5 = "zXX:XX";
                    }

/*
                    //Send Interval setting
                    try {
                        characteristic.setValue(interval);
                        mBluetoothLeService.writeCharacteristic(characteristic);
                    } catch (Exception e) {
                        Toast.makeText(getActivity(), "No Bluetooth", Toast.LENGTH_SHORT).show();
                    }

                    try {
                        Thread.sleep(500);                 //Wait 1/2 second
                    } catch(InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }

                    //Send alarm 1 only if checked
                    try {
                        characteristic.setValue(alarm1);
                        mBluetoothLeService.writeCharacteristic(characteristic);
                    } catch (Exception e) {
                        Toast.makeText(getActivity(), "No Bluetooth", Toast.LENGTH_SHORT).show();
                    }


                    try {
                        Thread.sleep(500);                 //Wait 1/2 second
                    } catch(InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }

                    //Send alarm 2 only if checked
                    try {
                        characteristic.setValue(alarm2);
                        mBluetoothLeService.writeCharacteristic(characteristic);
                    } catch (Exception e) {
                        Toast.makeText(getActivity(), "No Bluetooth", Toast.LENGTH_SHORT).show();
                    }

                    try {
                        Thread.sleep(500);                 //Wait 1/2 second
                    } catch(InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }

                    //Send alarm 3 only if checked
                    try {
                        characteristic.setValue(alarm3);
                        mBluetoothLeService.writeCharacteristic(characteristic);
                    } catch (Exception e) {
                        Toast.makeText(getActivity(), "No Bluetooth", Toast.LENGTH_SHORT).show();
                    }

                    try {
                        Thread.sleep(500);                 //Wait 1/2 second
                    } catch(InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }

                    //Send alarm 4 only if checked
                    try {
                        characteristic.setValue(alarm4);
                        mBluetoothLeService.writeCharacteristic(characteristic);
                    } catch (Exception e) {
                        Toast.makeText(getActivity(), "No Bluetooth", Toast.LENGTH_SHORT).show();
                    }

                    try {
                        Thread.sleep(500);                 //Wait 1/2 second
                    } catch(InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }

                    //Send alarm 5 only if checked
                    try {
                        characteristic.setValue(alarm5);
                        mBluetoothLeService.writeCharacteristic(characteristic);
                    } catch (Exception e) {
                        Toast.makeText(getActivity(), "No Bluetooth", Toast.LENGTH_SHORT).show();
                    }
*/
                    try {
                        Thread.sleep(500);                 //Wait 1/2 second
                    } catch(InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }


                    //Button Customization Strings
                    start = symptoms.getSelectedItem().toString();
                    stop = symptoms.getSelectedItem().toString();
                    yellow = yellowDrop.getSelectedItem().toString();
                    blue = blueDrop.getSelectedItem().toString();
                    String name = deviceName.getText().toString();
                    String id = projectID.getText().toString();

                    //Save all Settings
                    savePrefs(start, stop, yellow, blue, np.getValue(), med1.getCurrentHour(),
                            med1.getCurrentMinute(), med2.getCurrentHour(), med2.getCurrentMinute(),
                            switch1.isChecked(), switch2.isChecked(), name, id, med3.getCurrentHour(), med3.getCurrentMinute(),
                            med4.getCurrentHour(), med4.getCurrentMinute(), med5.getCurrentHour(), med5.getCurrentMinute(),
                            switch3.isChecked(), switch4.isChecked(), switch5.isChecked());

                }

            });

            return rootView;
        }

        @Override
        public void onDestroy(){

        }

        @Override
        public void onAttach(Activity activity) {
            super.onAttach(activity);
            ((Hub) activity).onSectionAttached(
                    getArguments().getInt(ARG_SECTION_NUMBER));
        }
    }

}
