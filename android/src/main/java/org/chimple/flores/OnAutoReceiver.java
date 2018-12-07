package org.chimple.flores;

import android.os.Bundle;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import io.flutter.app.FlutterActivity;
import android.app.Application;
import android.content.Context;
import android.util.Log;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import org.chimple.flores.FloresPlugin;

public class OnAutoReceiver extends BroadcastReceiver { 
private static final String TAG = OnAutoReceiver.class.getSimpleName();
@Override
    public void onReceive(Context context, Intent intent) {
    	Log.d(TAG, "launching Main Activity when power connected or boot time");
		Log.d(TAG, "Checking if main activity launched:" + FloresPlugin.isAppLunched());
		if(FloresPlugin.isAppLunched())
		{
			Log.d(TAG, "Main Activity ALREADY LAUNCHED");
		} else 
		{
			if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) && !FloresPlugin.isAppLunched()) {
				Log.d(TAG, "Boot event received ... Launching Flores");
				FloresPlugin.launchApp();
		        Intent i = new Intent(context, FloresPlugin.class);
	    	    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);        
	        	context.startActivity(i);
	        } else if(!FloresPlugin.isAppLunched() && Intent.ACTION_POWER_CONNECTED.equals(intent.getAction()))
	        {
				Log.d(TAG, "Power event received ... Launching Flores");
    			FloresPlugin.launchApp();
		        Intent i = new Intent(context, FloresPlugin.class);
	    	    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);        
	        	context.startActivity(i);  
			} 				    	    				    	        
		}		 
    }   
}   
