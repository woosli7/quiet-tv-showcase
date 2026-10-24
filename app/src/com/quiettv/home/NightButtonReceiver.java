package com.quiettv.home;
import android.content.*;
import android.os.Build;

/** Only the companion signed with Quiet TV's key may send this broadcast. */
public class NightButtonReceiver extends BroadcastReceiver {
    public void onReceive(Context context,Intent intent){
        if(!NightScreen.TOGGLE.equals(intent.getAction()))return;
        Intent service=new Intent(context,HomeService.class).setAction(NightScreen.TOGGLE);
        if(Build.VERSION.SDK_INT>=26)context.startForegroundService(service);else context.startService(service);
    }
}
