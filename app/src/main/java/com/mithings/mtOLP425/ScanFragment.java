package com.mithings.mtOLP425;

import java.util.ArrayList;

import android.os.Bundle;
import android.os.Handler;
// import android.support.v4.app.ListFragment;
import android.app.ListFragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

/** This fragment manages the Scan tab */
public class ScanFragment extends ListFragment 
{
private static final boolean D = true; // Debugging on/off
private final static String TAG = ScanFragment.class.getSimpleName();
private ScanFragmentListener mCallback = null;
private LeDeviceListAdapter mLeDeviceListAdapter = null;
private BluetoothAdapter mBluetoothAdapter = null;
private boolean mScanning = false;
private Handler mHandler = null;
private static final int REQUEST_ENABLE_BT = 1;
private static final long SCAN_PERIOD = 10000; // Stops scanning after x seconds

// The container activity must implement this interface
	public interface ScanFragmentListener { public void onScanFragmentData(Bundle data); }
	
// An empty constructor is required by the system in certain situations ...
	public ScanFragment() {}
	
    @Override
    public void onAttach(Activity activity) 
    {
	    super.onAttach(activity); if(D) Log.i(TAG, "onAttach");
	    
        try 
        {
        	mCallback = (ScanFragmentListener) activity;
        } 
        catch (ClassCastException e) // An exception is thrown if the container activity hasn't implemented the callback interface
        {
            throw new ClassCastException(activity.toString() + " must implement onScanFragmentData");
        }
    }
    
    @Override
    public void onCreate(Bundle savedInstanceState) 
    {
        super.onCreate(savedInstanceState); if(D) Log.i(TAG, "onCreate");
        
    // Reset scan variables - note that new() on the fragment is done once, but onCreate every time tab is chosen 
        resetScanVariables();
        
    // Set header information and reset device name and address
        getActivity().getActionBar().setTitle(R.string.strScanDevice);
        getActivity().getActionBar().setSubtitle(R.string.strDeviceNotSelected);
        Bundle data = new Bundle();
    	data.putString(ContainerActivity.EXTRAS_DEVICE_NAME, "");
    	data.putString(ContainerActivity.EXTRAS_DEVICE_ADDRESS, "");
        mCallback.onScanFragmentData(data);
        
    // Get a generic handler
        mHandler = new Handler();

    // Check if BLE is supported, else toast and finish
        if (!getActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) 
        {
        	if(D) Log.e(TAG, "onCreate, BLE not supported");
            Toast.makeText(getActivity(), R.string.strBLENotSupported, Toast.LENGTH_LONG).show(); getActivity().finish(); return;
        }

    // Initializes a bluetooth adapter, check if bluetooth is supported, if not toast and finish 
        final BluetoothManager bluetoothManager = (BluetoothManager) getActivity().getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) 
        {
        	if(D) Log.e(TAG, "onCreate, BT not supported");
            Toast.makeText(getActivity(), R.string.strBTNotSupported, Toast.LENGTH_LONG).show(); getActivity().finish(); return;
        }
        
    // This fragment wants to add items to the options menu, received in onCreateOptionsMenu   
        setHasOptionsMenu(true);
    }
    
    private void resetScanVariables()
    {
    	mLeDeviceListAdapter = null;
    	mBluetoothAdapter = null;
    	mScanning = false;
    	mHandler = null;
    }

    
    /*
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) 
	{
	// Set this tab / fragment layout
        private View rootScanView = inflater.inflate(R.layout.fragment_scan, container, false); if(D) Log.i(TAG, "onCreateView");
        
        return(rootScanView);
	}
	*/
    
    @Override
	public void onResume() 
    {
        super.onResume(); if(D) Log.i(TAG, "onResume");

    // Check if BT/BLE is enabled, if not ask the user to enable it. Answer in onActivityResult()
        if (!mBluetoothAdapter.isEnabled()) 
        {
        	if(D) Log.i(TAG, "onResume, BT/BLE not enabled");
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

    // Initializes list view adapter.
        mLeDeviceListAdapter = new LeDeviceListAdapter();
        setListAdapter(mLeDeviceListAdapter);
        
    // Auto start scan for BLE devices 
        scanLeDevice(true);
    }
    
    @Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) 
    {
    // User chose not to enable BT/BLE
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) 
        {
        	if(D) Log.w(TAG, "onActivityResult, User chose not to enable BT/BLE");
        	getActivity().finish(); return;
        }
        
        super.onActivityResult(requestCode, resultCode, data);
    }
    
/** Start scan for BLE device */
    private void scanLeDevice(final boolean bStartScan) 
    {
	    if (bStartScan) 
	    {
	    // Start scanning, stop after SCAN_PERIOD milliseconds
        	if(D) Log.i(TAG, "scanLeDevice, start scanning");
	        mScanning = true;
	        mHandler.postDelayed(ScanTimer, SCAN_PERIOD);	
	        mBluetoothAdapter.startLeScan(mLeScanCallback);
	    } 
	    else 
	    {
        	if(D) Log.i(TAG, "scanLeDevice, stop scanning");
	        mScanning = false;
	        mBluetoothAdapter.stopLeScan(mLeScanCallback);
	        mHandler.removeCallbacks(ScanTimer);
	    }
	    getActivity().invalidateOptionsMenu();
    }

/** Timer used to stop BLE device scan after a given time */
    Runnable ScanTimer = new Runnable() 
    {
        @Override
        public void run() 
        {
            mScanning = false;
            mBluetoothAdapter.stopLeScan(mLeScanCallback);                   	
            getActivity().invalidateOptionsMenu();
        }
    };

/** Device scan callback, called when new devices our found during scan */
    private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() 
    {
        @Override
        public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) 
        {
        	final int final_rssi = rssi;
        	
        	getActivity().runOnUiThread(new Runnable() 
        	{
                @Override
                public void run() 
                {
                    mLeDeviceListAdapter.addDevice(device, final_rssi);
                    mLeDeviceListAdapter.notifyDataSetChanged();
                }
            });
        }
    };

/** Called when a device is selected in the BLE device list (devices found after scan) */
    @Override
	public void onListItemClick(ListView l, View v, int position, long id) 
    {
        BluetoothDevice device = mLeDeviceListAdapter.getDevice(position);
        if (device != null)
        {
        // If scanning, stop scanning
        	if(D) Log.i(TAG, "onListItemClick, position: " + position);
	        if (mScanning) 
	        {
	            mBluetoothAdapter.stopLeScan(mLeScanCallback);
	            mScanning = false;
	            getActivity().invalidateOptionsMenu();
	        }
	        
	    // If available pass selected device name and address to container activity and update header and list item
	        if (device.getName().length() > 0 && device.getAddress().length() > 0)
	        {
	        	ImageView imgSelected = (ImageView) v.findViewById(R.id.imgSelected);
		        Bundle data = new Bundle();
	        	if (!mLeDeviceListAdapter.selected(position))
	        	{
	        	// Currently only one device can be selected at the time, so go through the list an unselect 
	        		int iNoOfListItems = mLeDeviceListAdapter.getCount();
	        		for (int i = 0; i < iNoOfListItems; i++)
	        		{
	        			if (mLeDeviceListAdapter.selected(i))
	        			{
			        		mLeDeviceListAdapter.setSelected(i, false);
			        		if (mLeDeviceListAdapter.listView(i) != null)
			        		{
			        			mLeDeviceListAdapter.listView(i).findViewById(R.id.imgSelected).setBackgroundResource(R.drawable.btn_check_buttonless_off);
			        		}
			        		else { if(D) Log.e(TAG, "onListItemClick, listView(i) == null, i: " + i); }
	        			}
	        		}
	        		
	        	// Select
	        		mLeDeviceListAdapter.setSelected(position, true);
	        		imgSelected.setBackgroundResource(R.drawable.btn_check_buttonless_on);
		            getActivity().getActionBar().setTitle(device.getName());
		            getActivity().getActionBar().setSubtitle(R.string.strDeviceSelected);
		        	data.putString(ContainerActivity.EXTRAS_DEVICE_NAME, device.getName());
		        	data.putString(ContainerActivity.EXTRAS_DEVICE_ADDRESS, device.getAddress());
	        	}
	        	else
	        	{
		        // Unselect
	        		mLeDeviceListAdapter.setSelected(position, false);
	        		imgSelected.setBackgroundResource(R.drawable.btn_check_buttonless_off);
		            getActivity().getActionBar().setTitle(R.string.strNoDevice);
		            getActivity().getActionBar().setSubtitle(R.string.strDeviceNotSelected);
		        	data.putString(ContainerActivity.EXTRAS_DEVICE_NAME, "");
		        	data.putString(ContainerActivity.EXTRAS_DEVICE_ADDRESS, "");
	        	}
	        	
	        	if(D) Log.i(TAG, "onListItemClick, send device name and adress to container activity through callback call");
		        mCallback.onScanFragmentData(data);
	        }
	        else
	        {
	        	if(D) Log.w(TAG, "onListItemClick, device name and adress is empty");
	        }
        }
        else
        {
        	if(D) Log.e(TAG, "onListItemClick, device == null");        	
        }
    }
        
/** Adapter for holding devices found through scanning */
    private class LeDeviceListAdapter extends BaseAdapter 
    {
    private ArrayList<DeviceListItem> DeviceList = null;
    private LayoutInflater mInflator;

        public LeDeviceListAdapter() { super(); DeviceList = new ArrayList<DeviceListItem>(); mInflator = getActivity().getLayoutInflater(); }
        public void addDevice(BluetoothDevice device, int rssi) 
        { 
        	DeviceListItem Item = new DeviceListItem();        
        	Item.Device = device;
        	Item.rssi = rssi;
        	Item.view = null;
        	Item.bSelected = false;
        	if(!DeviceList.contains(Item)) { DeviceList.add(Item); } 
        }
        public BluetoothDevice getDevice(int iPos) { return(DeviceList.get(iPos).Device); }
        public int getRSSI(int iPos) { return(DeviceList.get(iPos).rssi); }
        public View listView(int iPos) { return(DeviceList.get(iPos).view); }
        public void setListView(int iPos, View view) { DeviceList.get(iPos).view = view; }
        public void setSelected(int iPos, boolean bSelected) { DeviceList.get(iPos).bSelected = bSelected; }
        public boolean selected(int iPos) { return(DeviceList.get(iPos).bSelected); }
        public void clear() { DeviceList.clear(); }
        @Override public int getCount() { return(DeviceList.size()); }
        @Override public Object getItem(int i) { return(DeviceList.get(i)); }
        @Override public long getItemId(int i) { return(i); }

        @Override public View getView(int i, View view, ViewGroup viewGroup) 
        {
        ViewHolder viewHolder;
        
        // Get UI field pointers to device name and address
            if (view == null) 
            {
            	if(D) Log.i(TAG, "LeDeviceListAdapter, getView, view == null");
                view = mInflator.inflate(R.layout.list_item_scan, null);
	        	setListView(i, view);
                viewHolder = new ViewHolder();
                viewHolder.deviceName = (TextView) view.findViewById(R.id.device_name);
                viewHolder.deviceAddress = (TextView) view.findViewById(R.id.device_address);
                viewHolder.deviceRSSI = (TextView) view.findViewById(R.id.rssi_scan_value);
                viewHolder.deviceSelected = (ImageView) view.findViewById(R.id.imgSelected);
                view.setTag(viewHolder);
            } 
            else { if(D) Log.i(TAG, "LeDeviceListAdapter, getView, view != null"); viewHolder = (ViewHolder) view.getTag(); }

        // Display device name and address if available, i.e. connect adapter to UI object
            BluetoothDevice device = getDevice(i);
            if (device != null)
            {
            	if(D) Log.i(TAG, "LeDeviceListAdapter, getView, device != null");
	            String deviceName = device.getName();
	            String deviceAddress = device.getAddress();
	            if ((deviceName != null && deviceName.length() > 0) && (deviceAddress != null && deviceAddress.length() > 0)) 
	            { 
	            	viewHolder.deviceName.setText(deviceName);
	            	viewHolder.deviceAddress.setText(deviceAddress);
	            	viewHolder.deviceRSSI.setText("RSSI " + getRSSI(i) +" db");
		            if (selected(i)) { viewHolder.deviceSelected.setBackgroundResource(R.drawable.btn_check_buttonless_on); }
		            else { viewHolder.deviceSelected.setBackgroundResource(R.drawable.btn_check_buttonless_off); }	            
	            }
	            else 
	            { 
	            	viewHolder.deviceName.setText(R.string.strUnknownDeviceName);
	            	viewHolder.deviceAddress.setText(R.string.strUnknownDeviceAddress);
	            	viewHolder.deviceRSSI.setText(R.string.strUnknownRSSI);
	            	viewHolder.deviceSelected.setBackgroundResource(R.drawable.btn_check_buttonless_off);
	            }
            }
            else
            {
            	if(D) Log.i(TAG, "LeDeviceListAdapter, getView, device == null");
            	viewHolder.deviceName.setText(R.string.strUnknownDeviceName); 
            	viewHolder.deviceAddress.setText(R.string.strUnknownDeviceAddress);
            	viewHolder.deviceSelected.setBackgroundResource(R.drawable.btn_check_buttonless_off);
            }

            return(view);
        }
    }
    private class DeviceListItem { BluetoothDevice Device; int rssi; View view; boolean bSelected; }    
    static class ViewHolder { TextView deviceName; TextView deviceAddress; TextView deviceRSSI; ImageView deviceSelected; }
    
    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) 
    {
    	getActivity().getMenuInflater().inflate(R.menu.scan, menu);
        if (!mScanning) 
        {
            menu.findItem(R.id.menu_stop).setVisible(false);
            menu.findItem(R.id.menu_scan).setVisible(true);
            menu.findItem(R.id.menu_refresh).setActionView(null);
        } 
        else 
        {
            menu.findItem(R.id.menu_stop).setVisible(true);
            menu.findItem(R.id.menu_scan).setVisible(false);
            menu.findItem(R.id.menu_refresh).setActionView(R.layout.actionbar_indeterminate_progress);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) 
    {
        switch (item.getItemId()) 
        {
            case R.id.menu_scan: mLeDeviceListAdapter.clear(); scanLeDevice(true); break;
            case R.id.menu_stop: scanLeDevice(false); break;
        }
        return true;
    }
    
    @Override
	public void onPause() 
    {
        super.onPause(); if(D) Log.i(TAG, "onPause");
        scanLeDevice(false);
        mLeDeviceListAdapter.clear();
    }    
    
    @Override
    public void onDestroy() 
    { 
    	if(D) Log.i(TAG, "onDestroy");
    	resetScanVariables();
    	super.onDestroy(); 
    }

        
    @Override
    public void onDetach() { if(D) Log.i(TAG, "onDetach"); mCallback = null; super.onDetach();  }
        
// Used to follow the fragment life cycle ...
    @Override
    public void onActivityCreated(Bundle savedInstanceState) { super.onActivityCreated(savedInstanceState); if(D) Log.i(TAG, "onActivityCreated"); }
    @Override
    public void onStart() { super.onStart(); if(D) Log.i(TAG, "onStart"); }
    @Override
    public void onStop() { super.onStop(); if(D) Log.i(TAG, "onStop"); }
    @Override
    public void onDestroyView() { if(D) Log.i(TAG, "onDestroyView"); super.onDestroyView(); }
}