package com.quiettv.home;
import android.content.*;
import android.os.Build;
public class BootReceiver extends BroadcastReceiver {
    public void onReceive(Context c,Intent intent){
        if(!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())||!HomeService.shouldRun(c))return;
        Intent helper=new Intent(c,HomeService.class);if(Build.VERSION.SDK_INT>=26)c.startForegroundService(helper);else c.startService(helper);
        if(c.getSharedPreferences("quiet",0).getBoolean("redirect",false))c.startActivity(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }
}
