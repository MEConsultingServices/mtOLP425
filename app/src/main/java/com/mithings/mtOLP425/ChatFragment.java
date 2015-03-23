package com.mithings.mtOLP425;

import java.util.List;

import com.mithings.bleservice.BLEService;
import com.mithings.bleservice.GattAttributes;

import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
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

/** This fragment manages the Chat tab */
public class ChatFragment extends Fragment 
{
private static final boolean D = true; // Debugging on/off
private final static String TAG = ChatFragment.class.getSimpleName();

// BLE variables
private String mDeviceName = "";
private String mDeviceAddress = "";
private BLEService mBluetoothLeService = null;
private ServiceConnectionHandler mBLEConnectionHandler = null;
private BroadcastReceiver mBLEReceiverHandler = null;
private BluetoothGattCharacteristic charFIFO = null;
private final int NOT_CONNECTED = 0;
private final int CONNECTED = 1;
private final int CONNECTING = 2;
private final int DIS_CONNECTING = 3;
private int mConnected = NOT_CONNECTED;

// Chat variables
private ArrayAdapter<String> mConversationArrayAdapter = null;
private ListView mConversationView = null;
private EditText mOutEditText = null;
private TextView mSerialPortAvailable = null;
private Button mSendButton = null;
private boolean bSerialPortServiceNotSupported = false;

// An empty constructor is required by the system in certain situations ...
	public ChatFragment() {}
	
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
    	mConnected = NOT_CONNECTED;
    	mBLEConnectionHandler = null;
    	mBLEReceiverHandler = null;    	
    	charFIFO = null;
    }
    
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) 
    {    	
    // Set this fragment layout
        View v = inflater.inflate(R.layout.fragment_chat, container, false); if(D) Log.i(TAG, "onCreateView");
        
        bSerialPortServiceNotSupported = false;
    	                
    // Initialize the chat list
        mConversationArrayAdapter = new ArrayAdapter<String>(getActivity(), R.layout.message_chat);
        mConversationView = (ListView) v.findViewById(R.id.in);
        mConversationView.setAdapter(mConversationArrayAdapter);

    // Initialize the chat edit field with a listener for the return key
        mSerialPortAvailable = (TextView) v.findViewById(R.id.txtServicePort);
        mSerialPortAvailable.setText(R.string.strSerialPortServiceNotAvailable);
        mOutEditText = (EditText) v.findViewById(R.id.edit_text_out);
        mOutEditText.setText("");
        mOutEditText.setHint(R.string.strEnterChatText);       
        mOutEditText.setOnEditorActionListener(new WriteListener());

    // Initialize the send button with a listener
        mSendButton = (Button) v.findViewById(R.id.button_send);
        mSendButton.setOnClickListener(new ButtonListener()); 

    // Return fragment view to system 
        return(v);
    }
    
// Listener for the input text field
    private class WriteListener implements TextView.OnEditorActionListener
    {
        public boolean onEditorAction(TextView view, int actionId, KeyEvent event) 
        {
        // If the action is a key-up event on the return key, send the message
            if(D) Log.i(TAG, "WriteListener");
            if (actionId == EditorInfo.IME_NULL && event.getAction() == KeyEvent.ACTION_UP) 
            {
                String strMessage = view.getText().toString();
                sendChatMessage(strMessage);
            }
            return(true);
        }
    }

    private class ButtonListener implements OnClickListener
    {
        public void onClick(View v) 
        {
        // Send a message using content from the chat text field
            String strMessage = mOutEditText.getText().toString();
            sendChatMessage(strMessage);
        }    	
    }
    
    private void sendChatMessage(String strMessage) 
    {
    // Check and send chat message if connected
    	if (mConnected != CONNECTED) { Toast.makeText(getActivity(), R.string.strNotConnected, Toast.LENGTH_LONG).show(); return; }

    // Check that there's actually something to send
        if (strMessage.length() == 0) { Toast.makeText(getActivity(), R.string.strNothingToSend, Toast.LENGTH_LONG).show(); return; }
        
    // Send data on the serial port
        if (charFIFO != null)
        {
	        charFIFO.setValue(strMessage);
	        final int charaProp = charFIFO.getProperties();
	        if ((charaProp | BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) > 0) 
	        {
	        	if (mBluetoothLeService != null)
	        	{
	        		if(D) Log.i(TAG, "sendChatMessage, write data on the serial port");
	        		mBluetoothLeService.writeCharacteristic(charFIFO);
	        	}
	        	else { if(D) Log.e(TAG, "sendChatMessage, write data on the serial port, mBluetoothLeService == null"); }
	        }
	        else
	        {
	            if(D) Log.w(TAG, "sendChatMessage, write not allowed");                	
	        }
        }
        else
        {
            if(D) Log.w(TAG, "sendChatMessage, charFIFO == null");                	
        }
        
    // Clear the edit text field
        // mOutEditText.setText("");

    // Display message in chat dialogue
        mConversationArrayAdapter.add("Me:  " + strMessage);
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
            
            if (BLEService.ACTION_GATT_CONNECTED.equals(action)) // Connected to a GATT server
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
            	// intent.getStringExtra(BLEService.EXTRA_UUID)
            	// intent.getStringExtra(BLEService.EXTRA_DATA)
            	// intent.getStringExtra(BLEService.EXTRA_DECODED_DATA)
                mConversationArrayAdapter.add(mDeviceName + ":  " + intent.getStringExtra(BLEService.EXTRA_DECODED_DATA));
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
	 	if (GattAttributes.FLOW_CONTROL_MODE.equals(characteristic.getUuid().toString()))
	 	{
	 	// The characteristic flow control mode is only available in the old BLE device software, currently not supported by the application
	        mSerialPortAvailable.setText(R.string.strSerialPortServiceNotSupported);
	        bSerialPortServiceNotSupported = true;

	 		/*
	 		if(D) Log.i(TAG, "configureGattCharacteristics, FLOW_CONTROL_MODE found");
	 		if ((charaProp | BluetoothGattCharacteristic.PROPERTY_READ) > 0) 
	 		{
		 		if(D) Log.i(TAG, "configureGattCharacteristics, FLOW_CONTROL_MODE found, PROPERTY_READ");
	 			mBluetoothLeService.readCharacteristic(characteristic);
	 		}
	 		if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) 
	 		{
		 		if(D) Log.i(TAG, "configureGattCharacteristics, FLOW_CONTROL_MODE found, PROPERTY_NOTIFY");
	 			mBluetoothLeService.setCharacteristicNotification(characteristic, true);
	 		}
	 		*/	
	 	}
	 	if (GattAttributes.FIFO.equals(characteristic.getUuid().toString()))
	 	{
	 		if(D) Log.i(TAG, "configureGattCharacteristics, FIFO found");
	 		if (!bSerialPortServiceNotSupported)
	 		{
		        mSerialPortAvailable.setText(R.string.strSerialPortServiceAvailable);	 			
	 		}
	 		/*
	 		if ((charaProp | BluetoothGattCharacteristic.PROPERTY_READ) > 0) // TODO read is set, but in reality not allowed ==> BLE stack error
	 		{
		 		if(D) Log.i(TAG, "configureGattCharacteristics, FIFO found, PROPERTY_READ");
	 			mBluetoothLeService.readCharacteristic(characteristic);
	 		}
	 		*/
	 		if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) 
	 		{
		 		if(D) Log.i(TAG, "configureGattCharacteristics, FIFO found, PROPERTY_NOTIFY");
		 		charFIFO = characteristic;
	 			mBluetoothLeService.setCharacteristicNotification(characteristic, true);
	 		}	
	 	}
	 	if (GattAttributes.FLOW_CONTROL_CREDITS.equals(characteristic.getUuid().toString()) ) 
	 	{
	 		/*
	 		if(D) Log.i(TAG, "configureGattCharacteristics, FLOW_CONTROL_CREDITS found");
	 		if ((charaProp | BluetoothGattCharacteristic.PROPERTY_READ) > 0) 
	 		{
		 		if(D) Log.i(TAG, "configureGattCharacteristics, FLOW_CONTROL_CREDITS found, PROPERTY_READ");
	 			mBluetoothLeService.readCharacteristic(characteristic);
	 		}
	 		if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) 
	 		{
		 		if(D) Log.i(TAG, "configureGattCharacteristics, FLOW_CONTROL_CREDITS found, PROPERTY_NOTIFY");
	 			mBluetoothLeService.setCharacteristicNotification(characteristic, true);
	 		}
	 		*/	
	 	}
	 }
	
    @Override
    public void onDestroy() 
    { 
    	if(D) Log.i(TAG, "onDestroy");

    // Reset fields
        mSerialPortAvailable.setText(R.string.strSerialPortServiceNotAvailable);
        mOutEditText.setText("");

    // Make sure soft keyboard is not up when leaving the tab
    	InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
    	imm.hideSoftInputFromWindow(mOutEditText.getWindowToken(), 0);
    		
    	if (mBluetoothLeService != null) { mBluetoothLeService.disconnect(); }
        if (mBLEReceiverHandler != null) { getActivity().unregisterReceiver(mBLEReceiverHandler); }        
        if (mBLEConnectionHandler != null) { getActivity().unbindService(mBLEConnectionHandler); }
    	getActivity().getActionBar().setSubtitle(getResources().getString(R.string.strNotConnected));
    	resetBLEVariables();
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