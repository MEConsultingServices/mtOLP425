package com.mithings.mtOLP425;

import java.util.List;

import com.mithings.bleservice.BLEService;
import com.mithings.bleservice.GattAttributes;

import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.IBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.app.Activity;
import android.app.Fragment;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.widget.VideoView;

/** This fragment manages the OLP425 tab */
public class MainAppFragment extends Fragment
{
    private static final boolean D = true; // Debugging on/off
    private final static String TAG = MainAppFragment.class.getSimpleName();

    // BLE variables
    private String mDeviceName = "";
    private String mDeviceAddress = "";
    private BLEService mBluetoothLeService = null;
    private BluetoothGattCharacteristic greenLEDCharacteristic = null;
    private BluetoothGattCharacteristic redLEDCharacteristic = null;
    private final int NOT_CONNECTED = 0;
    private final int CONNECTED = 1;
    private final int CONNECTING = 2;
    private final int DIS_CONNECTING = 3;
    private int mConnected = NOT_CONNECTED;
    private ServiceConnectionHandler mBLEConnectionHandler = null;
    private BroadcastReceiver mBLEReceiverHandler = null;
    private static final long SCAN_RSSI_PERIOD = 1000;

    // OLP425 variables
    private TextView temperature;
    private TextView rssi;
    private TextView batteryLevel;
    private TextView accelerometerRange;
    private TextView mTextField;
    private ProgressBar mAccX;
    private ProgressBar mAccY;
    private ProgressBar mAccZ;
    private ProgressBar mAccM;
    private int M, N;
    private int X0 = 0, Y0 = 0, Z0 = 0;
    private int X1 = 0, Y1 = 0, Z1 = 0;
    private TextView mpercent;
    private int mPercent = 0;
    private Switch greenLED;
    private Switch redLED;
    private Sensor sensor;
    private int Max = 0;
    private VideoView videoView;

    // An empty constructor is required by the system in certain situations ...
    public MainAppFragment() {}

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState); if(D) Log.i(TAG, "onCreate");

        // Reset BLE variables - note that new() on the fragment is done once, but onCreate every time tab is chosen
        resetBLEVariables();

        // Check if data is passed down from the container activity, if so get the data
        Bundle data = getArguments();
        if (data != null)
        {
            if(D) Log.i(TAG, "onCreate, data available from container activity");
            mDeviceName = data.getString(ContainerActivity.EXTRAS_DEVICE_NAME, "");
            mDeviceAddress = data.getString(ContainerActivity.EXTRAS_DEVICE_ADDRESS, "");
        }
        else
        {
            if(D) Log.i(TAG, "onCreate, data NOT available from container activity");
        }

        // Check if device is selected, i.e. device name and address available
        if (mDeviceName.length() != 0 && mDeviceAddress.length() != 0)
        {
            // Update header
            if(D) Log.i(TAG, "onCreate, DeviceName: " + mDeviceName + ", DeviceAddress: "+ mDeviceAddress);
            getActivity().getActionBar().setTitle(mDeviceName);

            // Create a service connection handler to pass down to the BLE service
            mBLEConnectionHandler = new ServiceConnectionHandler();

            // Create a broadcast receiver handler to pass down to the BLE service
            mBLEReceiverHandler = new BroadcastReceiverHandler();

            // Bind to the BLE service, connection status is given through the ServiceConnectionHandler
            Intent gattServiceIntent = new Intent(getActivity(), BLEService.class);
            if (getActivity().bindService(gattServiceIntent, mBLEConnectionHandler, Context.BIND_AUTO_CREATE))
            {
                // Register receiver of messages from the BLE service / device
                if(D) Log.i(TAG, "onCreate, bind to BLE service successful");
                if(D) Log.i(TAG, "onCreate, registration of BLE message receiver");
                getActivity().registerReceiver(mBLEReceiverHandler, makeGattReceiveFilter());
                // Create object to handle sensor values
                sensor = new Sensor();
            }
            else
            {
                if(D) Log.e(TAG, "onCreate, bind to BLE service NOT successful");
                getActivity().getActionBar().setSubtitle(getResources().getString(R.string.strServiceNotBound));
            }

            // This fragment wants to add items to the options menu
            setHasOptionsMenu(true);
        }
        else
        {
            // Update header
            if(D) Log.i(TAG, "onCreate, device not selected");
            getActivity().getActionBar().setTitle(R.string.strNoDevice);
            getActivity().getActionBar().setSubtitle(getResources().getString(R.string.strDeviceNotSelected));

            // This fragment should currently not add items to the options menu
            setHasOptionsMenu(false);
        }
    }

    private void resetBLEVariables()
    {
        mDeviceName = "";
        mDeviceAddress = "";
        mBluetoothLeService = null;
        greenLEDCharacteristic = null;
        redLEDCharacteristic = null;
        mConnected = NOT_CONNECTED;
        mBLEConnectionHandler = null;
        mBLEReceiverHandler = null;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        // Set this fragment layout
        View v = inflater.inflate(R.layout.fragment_mainapp, container, false); if(D) Log.i(TAG, "onCreateView");

        // Get handlers to input objects
        temperature = (TextView) v.findViewById(R.id.temperature);
        rssi = (TextView) v.findViewById(R.id.rssi);
        batteryLevel = (TextView) v.findViewById(R.id.battery_level);
        accelerometerRange = (TextView) v.findViewById(R.id.btnCancel);
        mAccX = (ProgressBar) v.findViewById(R.id.progressbar_acc_x);
        mAccY = (ProgressBar) v.findViewById(R.id.progressbar_acc_y);
        mAccZ = (ProgressBar) v.findViewById(R.id.progressbar_acc_z);
        mAccM = (ProgressBar) v.findViewById(R.id.progressbar_acc_m);
        greenLED = (Switch) v.findViewById(R.id.greenled);
        redLED = (Switch) v.findViewById(R.id.redled);
        mpercent = (TextView) v.findViewById(R.id.mPercent);
        mTextField = (TextView) v.findViewById(R.id.textField);

        resetOLP425UI();

        // Set listeners for detecting user changes to the LED states, and update the corresponding BLE device LEDs
        greenLED.setOnCheckedChangeListener(new GreenLEDListener());
        redLED.setOnCheckedChangeListener(new RedLEDListener());

        //final VideoView videoView = (VideoView) v.findViewById(R.id.videoView);
        videoView = (VideoView) v.findViewById(R.id.videoView);
        videoView.setVideoPath("http://rmcdn.2mdn.net/MotifFiles/html/1248596/android_1330378998288.mp4");
        videoView.start();

        new CountDownTimer(30000000, 1000) {

            public void onTick(long millisUntilFinished) {
                //mTextField.setText("Seconds remaining: " + millisUntilFinished / 500);

                //View v = inflater.inflate(R.layout.fragment_mainapp, container, false); if(D) Log.i(TAG, "onCreateView");
                //final VideoView videoView = (VideoView) v.findViewById(R.id.videoView);
                //videoView = (VideoView) v.findViewById(R.id.videoView);

                if ((Math.abs(M) < 150) & (N != 0))
                {
                    N = 0;
                    videoView.seekTo(10000);
                }
                else if ((Math.abs(M) > 150) & (Math.abs(M) < 200) & (N != 1))
                {
                    N = 1;
                    videoView.seekTo(38000);
                }
                else if ((Math.abs(M) > 250) & (N != 2))
                {
                    N = 2;
                    videoView.seekTo(75000);
                }

                mTextField.setText(String.valueOf(String.valueOf(Max)));
                //Min = 0;
                Max = 0;
            }

            public void onFinish() {
                mTextField.setText("Done");
            }

        }.start();

        // Return fragment view to system
        return(v);
    }

    private void resetOLP425UI()
    {
        temperature.setText(R.string.strNoDataAvailable);
        rssi.setText(R.string.strNoDataAvailable);
        batteryLevel.setText(R.string.strNoDataAvailable);
        accelerometerRange.setText(R.string.strNoDataAvailable);
        mAccX.setProgress(127);
        mAccY.setProgress(127);
        mAccZ.setProgress(127);
        mAccM.setProgress(127);
        greenLED.setChecked(false);
        redLED.setChecked(false);
    }

    /** BLE service connection handler*/
    public class ServiceConnectionHandler implements ServiceConnection
    {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service)
        {
            // Get BLE service handler
            mBluetoothLeService = ((BLEService.LocalBinder) service).getService();

            if (mBluetoothLeService != null)
            {
                // Initialize BLE
                if (!mBluetoothLeService.initialize())
                {
                    if(D) Log.e(TAG, "ServiceConnectionHandler, onServiceConnected, Unable to initialize BLE");
                    getActivity().getActionBar().setSubtitle(getResources().getString(R.string.strServiceNotInitialized));
                    // getActivity().finish();
                }
                else
                {
                    // Connect to BLE device
                    if(D) Log.i(TAG, "ServiceConnectionHandler, onServiceConnected, BLE initilized");
                    if (mBluetoothLeService.connect(mDeviceAddress))
                    {
                        if(D) Log.i(TAG, "ServiceConnectionHandler, onServiceConnected, BLE device connecting");
                        mConnected = CONNECTING;
                        getActivity().getActionBar().setSubtitle(getResources().getString(R.string.strConnecting));
                        getActivity().invalidateOptionsMenu(); // Update the options menu
                        mBluetoothLeService.setRSSIScan(true, SCAN_RSSI_PERIOD);
                    }
                    else
                    {
                        if(D) Log.w(TAG, "ServiceConnectionHandler, onServiceConnected, BLE device NOT connected");
                        getActivity().getActionBar().setSubtitle(getResources().getString(R.string.strNotConnected));
                    }
                }
            }
            else
            {
                if(D) Log.e(TAG, "ServiceConnectionHandler, onServiceConnected, mBluetoothLeService == null");
                getActivity().getActionBar().setSubtitle(getResources().getString(R.string.strServiceNotAvailable));
                // getActivity().finish();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) { mBluetoothLeService = null; }
    }

    private static IntentFilter makeGattReceiveFilter()
    {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BLEService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BLEService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BLEService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BLEService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(BLEService.ACTION_RSSI_AVAILABLE);
        intentFilter.addAction(BLEService.ACTION_TOAST);
        return(intentFilter);
    }

    /** BLE service / device message receiver */
    public class BroadcastReceiverHandler extends BroadcastReceiver
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            final String action = intent.getAction();

            if (BLEService.ACTION_RSSI_AVAILABLE.equals(action))
            {
                // if(D) Log.i(TAG, "BroadcastReceiverHandler, onReceive, ACTION_RSSI_AVAILABLE");
                rssi.setText(intent.getStringExtra(BLEService.EXTRA_DECODED_DATA) + " dB");
            }
            else if (BLEService.ACTION_GATT_CONNECTED.equals(action)) // Connected to a GATT server
            {
                if(D) Log.i(TAG, "BroadcastReceiverHandler, onReceive, ACTION_GATT_CONNECTED");
                mConnected = CONNECTED;
                getActivity().getActionBar().setSubtitle(getResources().getString(R.string.strConnected));
                getActivity().invalidateOptionsMenu(); // Update the options menu
            }
            else if (BLEService.ACTION_GATT_DISCONNECTED.equals(action)) // Disconnected from a GATT server
            {
                if(D) Log.i(TAG, "BroadcastReceiverHandler, onReceive, ACTION_GATT_DISCONNECTED");
                mConnected = NOT_CONNECTED;
                getActivity().getActionBar().setSubtitle(getResources().getString(R.string.strNotConnected));
                resetOLP425UI();
                getActivity().invalidateOptionsMenu(); // Update the options menu
            }
            else if (BLEService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) // Discovered GATT services
            {
                // Configure the supported services and characteristics
                if(D) Log.i(TAG, "BroadcastReceiverHandler, onReceive, ACTION_GATT_SERVICES_DISCOVERED");
                if (mBluetoothLeService != null)
                {
                    configureGattServices(mBluetoothLeService.getSupportedGattServices());
                }
                else { if(D) Log.e(TAG, "BroadcastReceiverHandler, onReceive, ACTION_GATT_SERVICES_DISCOVERED, mBluetoothLeService == null"); }
            }
            else if (BLEService.ACTION_DATA_AVAILABLE.equals(action)) // Received data from the device, may be result from a read or notification
            {
                if(D) Log.i(TAG, "BroadcastReceiverHandler, onReceive, ACTION_DATA_AVAILABLE");
                displayOLP425Data(intent.getStringExtra(BLEService.EXTRA_UUID), intent.getStringExtra(BLEService.EXTRA_DATA), intent.getStringExtra(BLEService.EXTRA_DECODED_DATA));
            }
            else if (BLEService.ACTION_TOAST.equals(action)) // Toast from BLE Service, i.e. error messages from lower layers
            {
                Toast.makeText(getActivity(), intent.getStringExtra(BLEService.EXTRA_DECODED_DATA), Toast.LENGTH_LONG).show();
            }
            else
            {
                if(D) Log.w(TAG, "BroadcastReceiverHandler, onReceive, unknown action: " + action);
            }
        }
    }

    private void displayOLP425Data(String uuid, String data, String decodedData)
    {
        if(D) Log.i(TAG, "displayOLP425Data, uuid: " + uuid + ", data: " + data + "decodedData: " + decodedData);

        // Check which uuid and display corresponding data
        if (GattAttributes.ACCELERATOR_X_MEASUREMENT.equals(uuid))
        {
//        	mAccX.setProgress(Integer.parseInt(decodedData));
            // value is -127--128, add 127 to present in progressbar 0-255
            //mAccX.setProgress(sensor.handleLinearAcceSensor(Integer.parseInt(decodedData), 0) + 127);
//        	mAccX.setProgress(Integer.parseInt(decodedData));
            X0 = X1;
            X1 = sensor.handleLinearAcceSensor(Integer.parseInt(decodedData), 0) + 127;
            mAccX.setProgress(X1);
        }
        else if (GattAttributes.ACCELERATOR_Y_MEASUREMENT.equals(uuid))
        {
            // value is -127--128, add 127 to present in progressbar 0-255
            //mAccY.setProgress(sensor.handleLinearAcceSensor(Integer.parseInt(decodedData), 1) + 127);
//        	mAccY.setProgress(Integer.parseInt(decodedData));
            Y0 = Y1;
            Y1 = sensor.handleLinearAcceSensor(Integer.parseInt(decodedData), 0) + 127;
            mAccY.setProgress(Y1);
        }
        else if (GattAttributes.ACCELERATOR_Z_MEASUREMENT.equals(uuid))
        {
            // value is -127--128, add 127 to present in progressbar 0-255
            //mAccZ.setProgress(sensor.handleLinearAcceSensor(Integer.parseInt(decodedData), 2) + 127);
//        	mAccZ.setProgress(Integer.parseInt(decodedData));
            Z0 = Z1;
            Z1 = sensor.handleLinearAcceSensor(Integer.parseInt(decodedData), 0) + 127;
            mAccZ.setProgress(Z1);
        }
        else if (GattAttributes.TEMPERATURE_MEASUREMENT.equals(uuid))
        {
            temperature.setText(decodedData + " \u00b0C");
        }
        else if (GattAttributes.ACCELERATOR_RANGE.equals(uuid))
        {
            accelerometerRange.setText("+- " + decodedData + "G");
        }
        else if (GattAttributes.BATTERY_LEVEL.equals(uuid))
        {
            batteryLevel.setText(decodedData + " %");
        }
        else if (GattAttributes.GREEN_LED.equals(uuid))
        {
            if(D) Log.i(TAG, "displayOLP425Data, green led decodedData: " + decodedData);
            if (decodedData.equals("1")) { greenLED.setChecked(true); }
            else { greenLED.setChecked(false);  }
        }
        else if (GattAttributes.RED_LED.equals(uuid))
        {
            if(D) Log.i(TAG, "displayOLP425Data, red led decodedData: " + decodedData);
            if (decodedData.equals("1")) { redLED.setChecked(true); }
            else { redLED.setChecked(false);  }
        }
        else
        {
            if(D) Log.w(TAG, "displayOLP425Data, unknown uuid: " + uuid);
        }

        if (Math.abs(X0 - X1) > Math.abs(Y0 - Y1))
        {
            M = X1;
        }
        else if (Math.abs(Y0 - Y1) > Math.abs(Z0 - Z1))
        {
            M = Y1;
        }
        else
        {
            M = Z1;
        }
        mAccM.setProgress(M);
        mpercent.setText(String.valueOf(((float) M - 127f) / 1.27f));

        //if (M < Min)
        //{
        //    Min = M;
        //]
        if (M > Max)
        {
            Max = M;
        }

    }

    /** Listens to user changes to the green LED state and updates the LED at the BLE device */
    public class GreenLEDListener implements CompoundButton.OnCheckedChangeListener
    {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
        {
            if(D) Log.i(TAG, "GreenLEDListener, onCheckedChanged");

            if (greenLEDCharacteristic != null)
            {
                // Check if led is on/off and set the characteristics
                if (isChecked)
                {
                    if(D) Log.i(TAG, "GreenLEDListener, onCheckedChanged, LED is on");
                    greenLEDCharacteristic.setValue(1, BluetoothGattCharacteristic.FORMAT_UINT8, 0);
                }
                else
                {
                    if(D) Log.i(TAG, "GreenLEDListener, onCheckedChanged, LED is off");
                    greenLEDCharacteristic.setValue(0, BluetoothGattCharacteristic.FORMAT_UINT8, 0);
                }

                // Write updated LED state to the BLE device
                final int charaProp = greenLEDCharacteristic.getProperties();
                if ((charaProp | BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) > 0)
                {
                    if (mBluetoothLeService != null)
                    {
                        if(D) Log.i(TAG, "GreenLEDListener, onCheckedChanged, write new LED state to BLE device");
                        mBluetoothLeService.writeCharacteristic(greenLEDCharacteristic);
                    }
                    else { if(D) Log.e(TAG, "GreenLEDListener, onCheckedChanged, write new LED state to BLE device, mBluetoothLeService == null"); }
                }
                else
                {
                    if(D) Log.w(TAG, "GreenLEDListener, onCheckedChanged, new LED state NOT written to BLE device");
                }
            }
            else
            {
                if(D) Log.e(TAG, "GreenLEDListener, onCheckedChanged, greenLEDCharacteristic == null");
            }
        }
    }

    /** Listens to user changes to the red LED state and updates the LED at the BLE device */
    public class RedLEDListener implements CompoundButton.OnCheckedChangeListener
    {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
        {
            if(D) Log.i(TAG, "RedLEDListener, onCheckedChanged");

            if (redLEDCharacteristic != null)
            {
                // Check if led is on/off and set the characteristics
                if (isChecked)
                {
                    if(D) Log.i(TAG, "RedLEDListener, onCheckedChanged, LED is on");
                    redLEDCharacteristic.setValue(1, BluetoothGattCharacteristic.FORMAT_UINT8, 0);
                }
                else
                {
                    if(D) Log.i(TAG, "RedLEDListener, onCheckedChanged, LED is off");
                    redLEDCharacteristic.setValue(0, BluetoothGattCharacteristic.FORMAT_UINT8, 0);
                }

                // Write updated LED state to the BLE device
                final int charaProp = redLEDCharacteristic.getProperties();
                if ((charaProp | BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) > 0)
                {
                    if (mBluetoothLeService != null)
                    {
                        if(D) Log.i(TAG, "RedLEDListener, onCheckedChanged, write new LED state to BLE device");
                        mBluetoothLeService.writeCharacteristic(redLEDCharacteristic);
                    }
                    else { if(D) Log.e(TAG, "RedLEDListener, onCheckedChanged, write new LED state to BLE device, mBluetoothLeService == null"); }
                }
                else
                {
                    if(D) Log.w(TAG, "GreenLEDListener, onCheckedChanged, new LED state NOT written to BLE device");
                }
            }
            else
            {
                if(D) Log.e(TAG, "GreenLEDListener, onCheckedChanged, greenLEDCharacteristic == null");
            }
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater)
    {
        if(D) Log.i(TAG, "onCreateOptionsMenu");
        inflater.inflate(R.menu.connect, menu);

        if (mDeviceAddress.length() != 0)
        {
            if(D) Log.i(TAG, "onCreateOptionsMenu, mDeviceAdress available ==> Device selected");
            if (mConnected == CONNECTED)
            {
                if(D) Log.i(TAG, "onCreateOptionsMenu, Connected");
                menu.findItem(R.id.menu_connect).setVisible(false);
                menu.findItem(R.id.menu_disconnect).setVisible(true);
            }
            else if (mConnected == NOT_CONNECTED)
            {
                if(D) Log.i(TAG, "onCreateOptionsMenu, Not connected");
                menu.findItem(R.id.menu_connect).setVisible(true);
                menu.findItem(R.id.menu_disconnect).setVisible(false);
            }
            else  // mConnected == CONNECTING or DIS_CONNECTING
            {
                if(D) Log.i(TAG, "onCreateOptionsMenu, Connecting or Disconencting");
                menu.findItem(R.id.menu_connect).setVisible(false);
                menu.findItem(R.id.menu_disconnect).setVisible(false);
            }
        }
        else
        {
            if(D) Log.i(TAG, "onCreateOptionsMenu, mDeviceAdress not available ==> Device not selected");
            menu.findItem(R.id.menu_connect).setVisible(false);
            menu.findItem(R.id.menu_disconnect).setVisible(false);
        }

        super.onCreateOptionsMenu(menu,inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch(item.getItemId())
        {
            case R.id.menu_connect:
                if (mBluetoothLeService != null)
                {
                    if(D) Log.i(TAG, "onOptionsItemSelected, connect to: " + mDeviceAddress); // mDeviceAddress should be available here
                    mConnected = CONNECTING;
                    getActivity().getActionBar().setSubtitle(getResources().getString(R.string.strConnecting));
                    getActivity().invalidateOptionsMenu(); // Update the options menu
                    mBluetoothLeService.connect(mDeviceAddress);
                }
                else { if(D) Log.e(TAG, "onOptionsItemSelected, connect to: " + mDeviceAddress + ", mBluetoothLeService == null"); }
                return(true);

            case R.id.menu_disconnect:
                if (mBluetoothLeService != null)
                {
                    if(D) Log.i(TAG, "onOptionsItemSelected, disconnect");
                    mConnected = DIS_CONNECTING;
                    getActivity().getActionBar().setSubtitle(getResources().getString(R.string.strDisconnecting));
                    getActivity().invalidateOptionsMenu(); // Update the options menu
                    mBluetoothLeService.disconnect();
                }
                else { if(D) Log.e(TAG, "onOptionsItemSelected, disconnect, mBluetoothLeService != null"); }
                return(true);

            case android.R.id.home: getActivity().onBackPressed(); return(true);
            default: return(super.onOptionsItemSelected(item));
        }
    }

    /** Iterate through the supported GATT Services/Characteristics */
    private void configureGattServices(List<BluetoothGattService> gattServices)
    {
        if(D) Log.i(TAG, "configureGattServices");
        if (gattServices == null) { if(D) Log.i(TAG, "configureGattServices, gattServices == null"); return; }

        // Loop through available GATT Services
        int i = 0;
        for (BluetoothGattService gattService : gattServices)
        {
            if(D) Log.i(TAG, "configureGattServices, gattService found: " + i++);
            List<BluetoothGattCharacteristic> gattCharacteristics = gattService.getCharacteristics();

            // Loop through available characteristics
            for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics)
            {
                configureGattCharacteristics(gattCharacteristic);
            }
        }
    }

    private void configureGattCharacteristics(BluetoothGattCharacteristic characteristic)
    {
        String uuid = characteristic.getUuid().toString();
        if(D) Log.i(TAG, "configureGattCharacteristics, uuid: "+ uuid );
        final int charaProp = characteristic.getProperties();

        // Check if this is a characteristic we are supposed to use in this view
        if (GattAttributes.BATTERY_LEVEL.equals(characteristic.getUuid().toString()) ||
                GattAttributes.ACCELERATOR_RANGE.equals(characteristic.getUuid().toString()) ||
                GattAttributes.TEMPERATURE_MEASUREMENT.equals(characteristic.getUuid().toString()) )
        {
            if(D) Log.i(TAG, "configureGattCharacteristics, BATTERY_LEVEL or ACCELERATOR_RANGE or TEMPERATURE_MEASUREMENT found");
            if ((charaProp | BluetoothGattCharacteristic.PROPERTY_READ) > 0)
            {
                if(D) Log.i(TAG, "configureGattCharacteristics, BATTERY_LEVEL or ACCELERATOR_RANGE or TEMPERATURE_MEASUREMENT found, PROPERTY_READ");
                mBluetoothLeService.readCharacteristic(characteristic);
            }
        }

        if (GattAttributes.ACCELERATOR_X_MEASUREMENT.equals(characteristic.getUuid().toString()) ||
                GattAttributes.ACCELERATOR_Y_MEASUREMENT.equals(characteristic.getUuid().toString()) ||
                GattAttributes.ACCELERATOR_Z_MEASUREMENT.equals(characteristic.getUuid().toString()) ||
                GattAttributes.TEMPERATURE_MEASUREMENT.equals(characteristic.getUuid().toString()) )
        {
            if(D) Log.i(TAG, "configureGattCharacteristics, ACCELERATOR_n_MEASUREMENT or TEMPERATURE_MEASUREMENT found");
            if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0)
            {
                if (mBluetoothLeService != null)
                {
                    if(D) Log.i(TAG, "configureGattCharacteristics, ACCELERATOR_n_MEASUREMENT or TEMPERATURE_MEASUREMENT found, PROPERTY_NOTIFY");
                    mBluetoothLeService.setCharacteristicNotification(characteristic, true);
                }
                else { if(D) Log.e(TAG, "configureGattCharacteristics, ACCELERATOR_n_MEASUREMENT or TEMPERATURE_MEASUREMENT found, PROPERTY_NOTIFY, mBluetoothLeService == null"); }
            }
        }
        if (GattAttributes.GREEN_LED.equals(characteristic.getUuid().toString()) )
        {
            if(D) Log.i(TAG, "configureGattCharacteristics, GREEN_LED found");
            greenLEDCharacteristic = characteristic;
            if ((charaProp | BluetoothGattCharacteristic.PROPERTY_READ) > 0)
            {
                if (mBluetoothLeService != null)
                {
                    if(D) Log.i(TAG, "configureGattCharacteristics, GREEN_LED found, PROPERTY_READ");
                    mBluetoothLeService.readCharacteristic(characteristic);
                }
                else { if(D) Log.e(TAG, "configureGattCharacteristics, GREEN_LED found, PROPERTY_READ, mBluetoothLeService == null"); }
            }
        }
        if (GattAttributes.RED_LED.equals(characteristic.getUuid().toString()) )
        {
            if(D) Log.i(TAG, "configureGattCharacteristics, RED_LED found");
            redLEDCharacteristic = characteristic;
            if ((charaProp | BluetoothGattCharacteristic.PROPERTY_READ) > 0)
            {
                if (mBluetoothLeService != null)
                {
                    if(D) Log.i(TAG, "configureGattCharacteristics, RED_LED found, PROPERTY_READ");
                    mBluetoothLeService.readCharacteristic(characteristic);
                }
                else { if(D) Log.e(TAG, "configureGattCharacteristics, RED_LED found, PROPERTY_READ, mBluetoothLeService == null"); }
            }
        }
    }

    @Override
    public void onDestroy()
    {
        if(D) Log.i(TAG, "onDestroy");

        if (mBluetoothLeService != null) { mBluetoothLeService.setRSSIScan(false); mBluetoothLeService.disconnect(); }
        if (mBLEReceiverHandler != null) { getActivity().unregisterReceiver(mBLEReceiverHandler); }
        if (mBLEConnectionHandler != null) { getActivity().unbindService(mBLEConnectionHandler); }
        getActivity().getActionBar().setSubtitle(getResources().getString(R.string.strNotConnected));
        resetBLEVariables();
        resetOLP425UI();
        getActivity().invalidateOptionsMenu();

        super.onDestroy();
    }

    // Used to follow the fragment life cycle ...
    @Override
    public void onAttach(Activity activity) { super.onAttach(activity); if(D) Log.i(TAG, "onAttach"); }
    @Override
    public void onActivityCreated(Bundle savedInstanceState) { super.onActivityCreated(savedInstanceState); if(D) Log.i(TAG, "onActivityCreated"); }
    @Override
    public void onStart() { super.onStart(); if(D) Log.i(TAG, "onStart"); }
    @Override
    public void onResume() { super.onResume(); if(D) Log.i(TAG, "onResume"); }
    @Override
    public void onPause() { super.onPause(); if(D) Log.i(TAG, "onPause"); }
    @Override
    public void onStop() { super.onStop(); if(D) Log.i(TAG, "onStop"); }
    @Override
    public void onDestroyView() { if(D) Log.i(TAG, "onDestroyView"); super.onDestroyView(); }
    @Override
    public void onDetach() { if(D) Log.i(TAG, "onDetach"); super.onDetach();  }
}
