package com.quiettv.home;

import android.content.*;
import android.graphics.PixelFormat;
import android.os.Build;
import android.provider.Settings;
import android.view.*;

/** Static, noninteractive warm/dim layer; never captures the underlying screen. */
final class NightScreen {
    static final String TOGGLE="com.quiettv.home.TOGGLE_NIGHT",REFRESH="com.quiettv.home.REFRESH";
    final Context context;
    final WindowManager windows;
    View filter;
    NightScreen(Context c){context=c;windows=(WindowManager)c.getSystemService(Context.WINDOW_SERVICE);}
    static boolean allowed(Context c){return Settings.canDrawOverlays(c);}
    static boolean enabled(Context c){return c.getSharedPreferences("quiet",0).getBoolean("nightScreen",false);}
    static boolean set(Context c,boolean on){
        if(on&&!allowed(c))return false;
        c.getSharedPreferences("quiet",0).edit().putBoolean("nightScreen",on).apply();return true;
    }
    static void refresh(Context c){Intent i=new Intent(c,HomeService.class).setAction(REFRESH);if(Build.VERSION.SDK_INT>=26)c.startForegroundService(i);else c.startService(i);}
    void update(){
        if(!enabled(context)||!allowed(context)){remove();return;}
        int level=context.getSharedPreferences("quiet",0).getInt("nightStrength",1);
        int alpha=level==0?64:level==2?140:102;
        if(filter==null){
            filter=new View(context);filter.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            WindowManager.LayoutParams params=new WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT,WindowManager.LayoutParams.MATCH_PARENT,Build.VERSION.SDK_INT>=26?WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY:WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,PixelFormat.TRANSLUCENT);
            params.gravity=Gravity.TOP|Gravity.LEFT;params.setTitle("Quiet TV Night Screen");
            filter.setBackgroundColor((alpha<<24)|0x00401800);
            try{windows.addView(filter,params);}catch(RuntimeException ex){filter=null;context.getSharedPreferences("quiet",0).edit().putBoolean("nightScreen",false).apply();}
        }else filter.setBackgroundColor((alpha<<24)|0x00401800);
    }
    void remove(){if(filter!=null){try{windows.removeViewImmediate(filter);}catch(RuntimeException ignored){}filter=null;}}
}
