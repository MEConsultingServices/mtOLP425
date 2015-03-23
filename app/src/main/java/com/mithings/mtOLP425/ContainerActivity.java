package com.mithings.mtOLP425;

import android.app.ActionBar;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.os.Bundle;
import android.app.ListFragment;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.app.Activity;
import android.app.ActionBar.Tab;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;

/** This is the container activity for all the fragments (tabs) */
public class ContainerActivity extends Activity implements ScanFragment.ScanFragmentListener 
{
private static final boolean D = true; // Debugging on/off
private final static String TAG = ContainerActivity.class.getSimpleName();
public static final String EXTRAS_DEVICE_NAME = "com.mithings.mtOLP425.DEVICE_NAME";
public static final String EXTRAS_DEVICE_ADDRESS = "com.mithings.mtOLP425.DEVICE_ADDRESS";
private final double FIVE_INCHES = 5;
private String strDeviceName = "";
private String strDeviceAddress = "";
ActionBar.Tab tabScan, tabServices, tabOLP425, tabChat, tabMainApp;

	ListFragment fragmentScan = new ScanFragment();
	Fragment fragmentServices = new ServicesFragment();
	Fragment fragmentOLP425 = new OLP425Fragment();
	Fragment fragmentChat = new ChatFragment();
    Fragment fragmentMainApp = new MainAppFragment();

	@Override
	public void onCreate(Bundle savedInstanceState) 
	{
		super.onCreate(savedInstanceState); if(D) Log.i(TAG, "onCreate");
		
		setRequestedOrientation (ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
		
		setContentView(R.layout.activity_container);
		
	// Configure action bar and set tab mode	
		ActionBar actionBar = getActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
		actionBar.setDisplayShowHomeEnabled(true);
		actionBar.setDisplayShowTitleEnabled(true);
		actionBar.setSubtitle(getResources().getString(R.string.strNotConnected));
		actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
		
	// If larger display set tab icons and texts horizontal the standard Android way, else vertical to save space 
		if (screenInches() > FIVE_INCHES)
		{
			tabScan = actionBar.newTab().setIcon(R.drawable.scan);
			tabScan.setText(R.string.strScanTab);	
			tabServices = actionBar.newTab().setIcon(R.drawable.services);			
			tabServices.setText(R.string.strServicesTab);		
			tabOLP425 = actionBar.newTab().setIcon(R.drawable.olp425);		
			tabOLP425.setText(R.string.strOLP425Tab);
			tabChat = actionBar.newTab().setIcon(R.drawable.chat);				
			tabChat.setText(R.string.strChatTab);
            tabMainApp = actionBar.newTab().setIcon(R.drawable.chat);
            tabMainApp.setText(R.string.strMainAppTab);
		}
		else
		{
			tabScan = actionBar.newTab().setCustomView(R.layout.tab_custom);
			((ImageView) tabScan.getCustomView().findViewById(R.id.tab_icon)).setBackgroundResource(R.drawable.scan);
			((TextView) tabScan.getCustomView().findViewById(R.id.tab_text)).setText(R.string.strScanTab);
			
			tabServices = actionBar.newTab().setCustomView(R.layout.tab_custom);
			((ImageView) tabServices.getCustomView().findViewById(R.id.tab_icon)).setBackgroundResource(R.drawable.services);
			((TextView) tabServices.getCustomView().findViewById(R.id.tab_text)).setText(R.string.strServicesTab);

			tabOLP425 = actionBar.newTab().setCustomView(R.layout.tab_custom);
			((ImageView) tabOLP425.getCustomView().findViewById(R.id.tab_icon)).setBackgroundResource(R.drawable.olp425);
			((TextView) tabOLP425.getCustomView().findViewById(R.id.tab_text)).setText(R.string.strOLP425Tab);

			tabChat = actionBar.newTab().setCustomView(R.layout.tab_custom);
			((ImageView) tabChat.getCustomView().findViewById(R.id.tab_icon)).setBackgroundResource(R.drawable.chat);
			((TextView) tabChat.getCustomView().findViewById(R.id.tab_text)).setText(R.string.strChatTab);

            tabMainApp = actionBar.newTab().setCustomView(R.layout.tab_custom);
            ((ImageView) tabMainApp.getCustomView().findViewById(R.id.tab_icon)).setBackgroundResource(R.drawable.olp425);
            ((TextView) tabMainApp.getCustomView().findViewById(R.id.tab_text)).setText(R.string.strMainAppTab);
		}
		
	// Set Tab Listeners
		tabScan.setTabListener(new TabListener(fragmentScan));
		tabServices.setTabListener(new TabListener(fragmentServices));
		tabOLP425.setTabListener(new TabListener(fragmentOLP425));
		tabChat.setTabListener(new TabListener(fragmentChat));
        tabMainApp.setTabListener(new TabListener(fragmentMainApp));

	// Add tabs to action bar
		actionBar.addTab(tabScan);
		actionBar.addTab(tabServices);
		actionBar.addTab(tabOLP425);
		actionBar.addTab(tabChat);
        actionBar.addTab(tabMainApp);
	}
		
/** Fragment Scan callback */
	@Override
	public void onScanFragmentData(Bundle data)
	{
		if (data !=null)
		{
			strDeviceName = data.getString(EXTRAS_DEVICE_NAME, "");
			strDeviceAddress = data.getString(EXTRAS_DEVICE_ADDRESS, "");			
			if(D) Log.i(TAG, "onScanFragmentData, strDeviceName: " + strDeviceName + " ,strDeviceAddress: " + strDeviceAddress);
		}
		else { if(D) Log.e(TAG, "onScanFragmentData, data = null"); }
	}
		
/** Listens to tab changes */
	public class TabListener implements ActionBar.TabListener 
	{
	Fragment fragment;

		public TabListener(Fragment fragment) { this.fragment = fragment; }

		@Override
		public void onTabSelected(Tab tab, FragmentTransaction ft) 
		{
		// Set message to fragment to be fetched via getArguments() and change to chosen fragment 
			if(D) Log.i(TAG, "onTabSelected: " + tab.getText());
			Bundle data = new Bundle();
			data.putString(EXTRAS_DEVICE_NAME, strDeviceName);
			data.putString(EXTRAS_DEVICE_ADDRESS, strDeviceAddress);		
			fragment.setArguments(data); 
			ft.replace(R.id.fragment_container, fragment);
		}

		@Override
		public void onTabUnselected(Tab tab, FragmentTransaction ft) 
		{
			if(D) Log.i(TAG, "onTabUnselected: " + tab.getText());
			ft.remove(fragment); 
		}

		@Override
		public void onTabReselected(Tab tab, FragmentTransaction ft) {}
	}
		
	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		super.onCreateOptionsMenu(menu);
		getMenuInflater().inflate(R.menu.activity_main, menu);
		return(true);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch(item.getItemId())
		{
		case R.id.mnuSettings: Toast.makeText(getApplicationContext(), getResources().getString(R.string.strSettingsInfo), Toast.LENGTH_LONG).show(); return(true);								
		case R.id.mnuAbout: Toast.makeText(getApplicationContext(), appDescription(), Toast.LENGTH_LONG).show(); return(true);	
        case android.R.id.home: finish(); return(true);			
		default: return(false);			
		}	
	}
		
	private String appDescription()
	{
		PackageManager manager = this.getPackageManager();
		try 
		{ 
			PackageInfo info = manager.getPackageInfo(this.getPackageName(), 0); 
			//return("Copyright miThings.se 2013.\nDeveloped by F Nilsask\nand K M�rtensson for\nconnectBlue\'s account.\nVerCode:" + info.versionCode + "\nVerName:" + info.versionName);
            return("XYZ");

		}
		catch (NameNotFoundException e) { if(D) Log.e(TAG, "appDescription, exception: " + e.toString()); }	
		return(null);
	}
	
	private double screenInches()
	{
		DisplayMetrics dm = new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getMetrics(dm);
		double x = Math.pow(dm.widthPixels/dm.xdpi, 2);
		double y = Math.pow(dm.heightPixels/dm.ydpi, 2);
		double inches = Math.sqrt(x+y);
		if (D) Log.i(TAG,"screenInches: " + inches);
		return(inches);
	}
}
