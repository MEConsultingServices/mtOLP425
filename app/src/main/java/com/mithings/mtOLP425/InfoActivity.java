package com.mithings.mtOLP425;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.RelativeLayout;

/** This is the start screen activity */
public class InfoActivity extends Activity
{
private static final boolean D = true; // Debugging on/off
private final static String TAG = InfoActivity.class.getSimpleName();
View.OnTouchListener screenListener = null;
	    
    @Override
    protected void onCreate(Bundle savedInstanceState) 
    {
        super.onCreate(savedInstanceState); if(D) Log.i(TAG, "onCreate");
        
		setRequestedOrientation (ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);        
        setContentView(R.layout.activity_info);        
        ((RelativeLayout) findViewById(R.id.layoutStartScreen)).setOnTouchListener(screenListener = new ScreenListener());  
    }
        
// Start the container activity and close the start screen activity when the screen is touched ...
    private class ScreenListener implements View.OnTouchListener
    {
    	@Override 
    	public boolean onTouch(View v, MotionEvent event)
        {
    		screenListener = null; ((RelativeLayout) findViewById(R.id.layoutStartScreen)).setOnTouchListener(null);
            startActivity(new Intent(InfoActivity.this, ContainerActivity.class)); // Start the container activity 
            finish(); // Close the info activity
            return(false);
        }
    }    
}
