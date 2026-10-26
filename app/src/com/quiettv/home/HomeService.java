package com.quiettv.home;

import android.app.*;
import android.os.*;
import android.content.*;
import java.io.*;

/** Local-only Home/app-exit routing. Log lines are neither stored nor transmitted. */
public class HomeService extends Service {
    volatile boolean alive;
    volatile java.lang.Process reader;
    final Handler handler=new Handler(Looper.getMainLooper());
    long lastHome;
    long homeDeadline;
    final Runnable homeFallback=()->{
        if(alive&&homeDeadline>SystemClock.elapsedRealtime()&&getSharedPreferences("quiet",MODE_PRIVATE).getBoolean("redirect",false))launchHome();
    };
    long lastNight;
    final NightPlayback nightPlayback=new NightPlayback();
    final Runnable resumeNightPlayback=()->{
        if(!alive||!hasNightButton(this)){nightPlayback.cancel();return;}
        PowerManager power=(PowerManager)getSystemService(POWER_SERVICE);
        android.media.AudioManager audio=(android.media.AudioManager)getSystemService(AUDIO_SERVICE);
        if(audio!=null&&nightPlayback.take(this.foregroundPackage,SystemClock.elapsedRealtime(),power!=null&&power.isInteractive(),audio.isMusicActive()))try{
            long now=SystemClock.uptimeMillis();
            audio.dispatchMediaKeyEvent(new android.view.KeyEvent(now,now,android.view.KeyEvent.ACTION_DOWN,android.view.KeyEvent.KEYCODE_MEDIA_PLAY,0));
            audio.dispatchMediaKeyEvent(new android.view.KeyEvent(now,now,android.view.KeyEvent.ACTION_UP,android.view.KeyEvent.KEYCODE_MEDIA_PLAY,0));
        }catch(RuntimeException ignored){}
    };
    NightScreen nightScreen;
    volatile int watchGeneration;
    boolean watching;
    String foregroundPackage;
    long wakeDeadline;
    final Runnable endWake=()->cancelWake();
    final Runnable wakeHome=()->{
        PowerManager power=(PowerManager)getSystemService(POWER_SERVICE);
        if(AppReturnPolicy.shouldWake(getSharedPreferences("quiet",0).getBoolean("redirect",false),power!=null&&power.isInteractive(),SystemClock.elapsedRealtime(),wakeDeadline,foregroundPackage)){
            cancelWake();clearTrackedApp(this);launchHome();
        }
    };
    final BroadcastReceiver screenEvents=new BroadcastReceiver(){
        public void onReceive(Context context,Intent intent){
            if(Intent.ACTION_SCREEN_OFF.equals(intent.getAction())){nightPlayback.cancel();handler.removeCallbacks(resumeNightPlayback);cancelWake();homeDeadline=0;handler.removeCallbacks(homeFallback);handler.removeCallbacks(returnHome);}
            else if(Intent.ACTION_SCREEN_ON.equals(intent.getAction())||Intent.ACTION_USER_PRESENT.equals(intent.getAction()))beginWake();
        }
    };
    void cancelWake(){wakeDeadline=0;handler.removeCallbacks(wakeHome);handler.removeCallbacks(endWake);}
    void beginWake(){
        cancelWake();if(!getSharedPreferences("quiet",0).getBoolean("redirect",false))return;
        wakeDeadline=SystemClock.elapsedRealtime()+8000;
        handler.postDelayed(wakeHome,750);handler.postDelayed(wakeHome,2000);handler.postDelayed(endWake,8000);
    }
    static boolean hasNightButton(Context context){
        return context.getPackageManager().checkSignatures(context.getPackageName(),"com.att.tv")==android.content.pm.PackageManager.SIGNATURE_MATCH;
    }
    static boolean shouldRun(Context context){return context.getSharedPreferences("quiet",0).getBoolean("redirect",false)||NightScreen.enabled(context)||hasNightButton(context);}
    final Runnable returnHome=()->{
        android.content.SharedPreferences prefs=getSharedPreferences("quiet",MODE_PRIVATE);
        if(prefs.getBoolean("redirect",false)&&AppReturnPolicy.shouldReturn(prefs.getString("returnTarget",""),prefs.getBoolean("returnObserved",false),prefs.getLong("returnStarted",0),SystemClock.elapsedRealtime(),foregroundPackage))launchHome();
    };
    static void trackApp(Context context,String pkg){
        android.content.SharedPreferences prefs=context.getSharedPreferences("quiet",MODE_PRIVATE);
        if(prefs.getBoolean("redirect",false)&&pkg!=null)prefs.edit().putString("returnTarget",pkg).putBoolean("returnObserved",false).putLong("returnStarted",SystemClock.elapsedRealtime()).apply();
    }
    static void clearTrackedApp(Context context){
        android.content.SharedPreferences prefs=context.getSharedPreferences("quiet",MODE_PRIVATE);
        if(prefs.contains("returnTarget"))prefs.edit().remove("returnTarget").remove("returnObserved").remove("returnStarted").apply();
    }
    public void onCreate(){
        super.onCreate();
        NotificationManager manager=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if(Build.VERSION.SDK_INT>=26)manager.createNotificationChannel(new NotificationChannel("quiet_home","Home button",NotificationManager.IMPORTANCE_LOW));
        Notification.Builder note=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,"quiet_home"):new Notification.Builder(this);
        note.setContentTitle("Quiet TV").setContentText("Home / app return and night screen").setSmallIcon(getApplicationInfo().icon).setOngoing(true);
        startForeground(10,note.build());alive=true;nightScreen=new NightScreen(this);
        IntentFilter screenFilter=new IntentFilter(Intent.ACTION_SCREEN_ON);screenFilter.addAction(Intent.ACTION_SCREEN_OFF);screenFilter.addAction(Intent.ACTION_USER_PRESENT);registerReceiver(screenEvents,screenFilter);
    }
    void refreshWatcher(){
        boolean needed=getSharedPreferences("quiet",0).getBoolean("redirect",false)||hasNightButton(this);
        if(needed&&!watching){watching=true;int generation=++watchGeneration;new Thread(()->watch(generation),"QuietTV-Home").start();}
        else if(!needed&&watching){watching=false;watchGeneration++;if(reader!=null)reader.destroy();}
    }
    void watch(int generation){
        String since=new java.text.SimpleDateFormat("MM-dd HH:mm:ss.SSS",java.util.Locale.US).format(new java.util.Date());
        seedForeground(generation);if(hasNightButton(this))seedPlayback(generation);
        while(alive&&generation==watchGeneration){
            try{
                String[] command=hasNightButton(this)?new String[]{"logcat","-b","system","-b","main","-b","events","-v","brief","-T",since,"-e","FireTVKeyPolicyManager[.]handleShortPressOnHome|App directv is not trusted[.] Aborting launch[.]|StateStore:storeState:capability=MediaSession |Remote button pressed[.] keyCode:|^[[][0-9]+,[0-9]+,[0-9]+,[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+[]]$","ContextImpl:W","*:I"}:
                    new String[]{"logcat","-b","system","-b","main","-b","events","-v","brief","-T",since,"ContextImpl:W","am_resume_activity:I","GenericAppAdapterManager:E","*:S"};
                reader=new ProcessBuilder(command).redirectErrorStream(true).start();
                try(BufferedReader lines=new BufferedReader(new InputStreamReader(reader.getInputStream()))){
                    String line;while(alive&&generation==watchGeneration&&(line=lines.readLine())!=null){
                        if(line.contains("FireTVKeyPolicyManager.handleShortPressOnHome"))handler.post(()->{if(alive&&generation==watchGeneration)home();});
                        if(line.contains("GenericAppAdapterManager")&&line.contains("App directv is not trusted. Aborting launch.")){
                            android.media.AudioManager audio=(android.media.AudioManager)getSystemService(AUDIO_SERVICE);
                            final boolean audible=audio!=null&&audio.isMusicActive();
                            handler.post(()->{if(alive&&generation==watchGeneration&&hasNightButton(this)){nightPlayback.press(foregroundPackage,SystemClock.elapsedRealtime(),audible);toggleNight();}});
                        }
                        if(line.contains("StateStore:storeState:capability=MediaSession ")||line.contains("KeyPressReceiver")){final String mediaLine=line;handler.post(()->{if(alive&&generation==watchGeneration&&hasNightButton(this))playbackLine(mediaLine);});}
                        String pkg=AppReturnPolicy.resumedPackage(line);if(pkg!=null)handler.post(()->{if(alive&&generation==watchGeneration)resumed(pkg);});
                    }
                }
            }catch(Exception ignored){}
            since=new java.text.SimpleDateFormat("MM-dd HH:mm:ss.SSS",java.util.Locale.US).format(new java.util.Date());
            if(alive&&generation==watchGeneration)try{Thread.sleep(2000);}catch(InterruptedException ignored){}
        }
    }
    void playbackLine(String line){
        nightPlayback.remote(line);
        if(nightPlayback.line(line,SystemClock.elapsedRealtime())){handler.removeCallbacks(resumeNightPlayback);handler.postDelayed(resumeNightPlayback,100);}
    }
    void seedPlayback(int generation){
        // Seed only recent local playback metadata; never store or transmit it.
        try{
            java.lang.Process snapshot=new ProcessBuilder("logcat","-b","main","-d","-t","600","-v","brief","whad:I","*:S").start();
            try(BufferedReader lines=new BufferedReader(new InputStreamReader(snapshot.getInputStream()))){String line;while((line=lines.readLine())!=null){if(line.contains("StateStore:storeState:capability=MediaSession ")){final String mediaLine=line;handler.post(()->{if(alive&&generation==watchGeneration)nightPlayback.line(mediaLine,SystemClock.elapsedRealtime());});}}}
            snapshot.destroy();
        }catch(Exception ignored){}
    }
    void seedForeground(int generation){
        // Read only filtered resume metadata once, so an already-visible launcher is known after a service restart.
        try{
            java.lang.Process snapshot=new ProcessBuilder("logcat","-b","events","-d","-v","brief","am_resume_activity:I","*:S").start();
            String latest=null;
            try(BufferedReader lines=new BufferedReader(new InputStreamReader(snapshot.getInputStream()))){String line;while((line=lines.readLine())!=null){String pkg=AppReturnPolicy.resumedPackage(line);if(pkg!=null)latest=pkg;}}
            snapshot.destroy();final String seed=latest;
            handler.post(()->{if(alive&&generation==watchGeneration&&foregroundPackage==null)foregroundPackage=seed;});
        }catch(Exception ignored){}
    }
    void resumed(String pkg){
        if(!alive)return;nightPlayback.foreground(pkg);foregroundPackage=pkg;handler.removeCallbacks(returnHome);
        android.content.SharedPreferences prefs=getSharedPreferences("quiet",MODE_PRIVATE);
        if(AppReturnPolicy.QUIET_HOME.equals(pkg)){homeDeadline=0;handler.removeCallbacks(homeFallback);cancelWake();clearTrackedApp(this);return;}
        if(pkg.equals(prefs.getString("returnTarget",""))&&!prefs.getBoolean("returnObserved",false))prefs.edit().putBoolean("returnObserved",true).apply();
        if(AppReturnPolicy.STOCK_HOME.equals(pkg)){
            // Wait for Fire OS to commit Home, then return immediately instead of racing it.
            if(homeDeadline>SystemClock.elapsedRealtime()&&prefs.getBoolean("redirect",false)){handler.removeCallbacks(homeFallback);launchHome();}
            else handler.postDelayed(returnHome,350);
            if(wakeDeadline>SystemClock.elapsedRealtime())handler.post(wakeHome);
        }
    }
    void launchHome(){
        PowerManager power=(PowerManager)getSystemService(POWER_SERVICE);if(power==null||!power.isInteractive())return;
        Intent launch=new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT|Intent.FLAG_ACTIVITY_NO_ANIMATION);
        // Restore only our own existing task. Fire OS queues a fresh activity start
        // after Home; AppTask restoration avoids that queue without extra permissions.
        ActivityManager manager=(ActivityManager)getSystemService(ACTIVITY_SERVICE);
        if(manager!=null)try{
            for(ActivityManager.AppTask task:manager.getAppTasks()){
                ActivityManager.RecentTaskInfo info=task.getTaskInfo();
                ComponentName base=info.baseIntent==null?null:info.baseIntent.getComponent();
                if(base!=null&&getPackageName().equals(base.getPackageName())&&MainActivity.class.getName().equals(base.getClassName())){task.moveToFront();break;}
            }
        }catch(Exception ignored){}
        try{startActivity(launch);}catch(Exception ignored){}
    }
    void home(){
        nightPlayback.cancel();handler.removeCallbacks(resumeNightPlayback);
        long now=SystemClock.elapsedRealtime();if(now-lastHome<100)return;lastHome=now;
        if(!getSharedPreferences("quiet",MODE_PRIVATE).getBoolean("redirect",false))return;
        homeDeadline=now+1500;
        handler.removeCallbacks(homeFallback);
        if(AppReturnPolicy.STOCK_HOME.equals(foregroundPackage))launchHome();
        else handler.postDelayed(homeFallback,200);
    }
    public int onStartCommand(Intent i,int flags,int id){
        if(i!=null&&NightScreen.TOGGLE.equals(i.getAction()))toggleNight();
        nightScreen.update();refreshWatcher();if(!shouldRun(this))stopSelf();return START_STICKY;
    }
    void toggleNight(){
        long now=SystemClock.elapsedRealtime();if(now-lastNight<=900)return;lastNight=now;
        boolean on=!NightScreen.enabled(this);
        if(NightScreen.set(this,on)){nightScreen.update();android.widget.Toast.makeText(this,on?"Night screen on":"Night screen off",android.widget.Toast.LENGTH_SHORT).show();}
        else {nightPlayback.cancel();android.widget.Toast.makeText(this,"Allow Quiet TV screen-filter permission first",android.widget.Toast.LENGTH_LONG).show();}
    }
    public IBinder onBind(Intent i){return null;}
    public void onDestroy(){alive=false;watchGeneration++;unregisterReceiver(screenEvents);if(reader!=null)reader.destroy();if(nightScreen!=null)nightScreen.remove();handler.removeCallbacksAndMessages(null);super.onDestroy();}
}
