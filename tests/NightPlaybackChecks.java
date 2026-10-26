package com.quiettv.home;

/** Branded-button recovery must never become a general auto-play feature. */
public final class NightPlaybackChecks {
    static int count;static final String APP="com.amazon.firetv.youtube",KEY="video|Demo highlights";
    static void ok(boolean value,String name){if(!value)throw new AssertionError(name);System.out.println("PASS "+name);count++;}
    static NightPlayback armed(){NightPlayback p=new NightPlayback();p.observe(APP,KEY,true,false,1000);p.press(APP,1100);return p;}
    public static void main(String[] args){
        NightPlayback p=armed();ok(p.observe(APP,KEY,false,true,1200),"DIRECTV-caused pause is eligible");ok(p.take(APP,1300,true,false),"only previously playing same video resumes");ok(!p.take(APP,1400,true,false),"at most one Play per button press");
        p=new NightPlayback();p.observe(APP,KEY,false,true,1000);p.press(APP,1100);p.observe(APP,KEY,false,true,1200);ok(!p.take(APP,1300,true,false),"already paused video stays paused");
        p=new NightPlayback();p.press(APP,1100);p.observe(APP,KEY,false,true,1200);ok(!p.take(APP,1300,true,false),"unknown prior state never starts playback");
        p=new NightPlayback();p.observe(APP,KEY,true,false,1000);p.press(APP,31000);p.observe(APP,KEY,false,true,31100);ok(!p.take(APP,31200,true,false),"stale prior state never starts playback");
        p=new NightPlayback();p.observe(APP,KEY,true,false,1000);p.press(APP,31000,true);p.observe(APP,KEY,false,true,31100);ok(p.take(APP,31200,true,false),"long playback can recover only with current audio confirmation");
        p=new NightPlayback();p.observe(APP,KEY,false,true,1000);p.press(APP,31000,true);p.observe(APP,KEY,false,true,31100);ok(!p.take(APP,31200,true,false),"audio confirmation cannot override an explicitly paused video");
        p=new NightPlayback();p.observe(APP,KEY,true,false,1000);p.observe("com.spotify.tv.android","music",true,false,30000);p.press(APP,31000,true);p.observe(APP,KEY,false,true,31100);ok(!p.take(APP,31200,true,false),"background music cannot arm an old foreground video");
        p=armed();p.observe(APP,KEY,false,true,2700);ok(!p.take(APP,2800,true,false),"late unrelated pause is ignored");
        p=armed();p.observe(APP,"different-video",false,true,1200);ok(!p.take(APP,1300,true,false),"changed video is not resumed");
        p=armed();p.observe(APP,KEY,false,true,1200);ok(!p.take("com.hulu.plus",1300,true,false),"different foreground app blocks Play");
        p=armed();p.foreground("com.quiettv.home");p.observe(APP,KEY,false,true,1200);ok(!p.take(APP,1300,true,false),"leaving and returning cancels recovery");
        p=armed();p.observe(APP,KEY,false,true,1200);ok(!p.take(APP,1300,false,false),"screen off blocks Play");
        p=armed();p.observe(APP,KEY,false,true,1200);ok(!p.take(APP,1300,true,true),"existing active audio is left alone");
        p=armed();p.observe(APP,KEY,false,true,1200);p.observe(APP,KEY,true,false,1250);ok(!p.take(APP,1300,true,false),"already resumed player receives no redundant Play");
        p=armed();p.observe(APP,KEY,false,false,1200);ok(!p.take(APP,1300,true,false),"stopped or buffering player is not forced to Play");
        p=armed();p.remote("I/OZ-DCS:KeyPressReceiver: KeyEvent { action=ACTION_DOWN, keyCode=KEYCODE_MEDIA_PAUSE, }");p.observe(APP,KEY,false,true,1200);ok(!p.take(APP,1300,true,false),"intentional Pause cancels recovery");
        p=armed();p.remote("I/OZ-DCS:KeyPressReceiver: KeyEvent { action=ACTION_DOWN, keyCode=KEYCODE_DPAD_CENTER, }");p.observe(APP,KEY,false,true,1200);ok(!p.take(APP,1300,true,false),"another remote action cancels recovery");
        p=armed();p.remote("I/OZ-DCS:KeyPressReceiver: KeyEvent { action=ACTION_DOWN, keyCode=KEYCODE_APP_3, }");p.observe(APP,KEY,false,true,1200);ok(p.take(APP,1300,true,false),"DIRECTV event itself does not cancel recovery");
        p=armed();p.cancel();p.observe(APP,KEY,false,true,1200);ok(!p.take(APP,1300,true,false),"explicit cancellation prevents delayed Play");
        p=new NightPlayback();String prefix="I/whad: StateStore:storeState:capability=MediaSession deviceId=demo state=";
        String json="{\"packageName\":\"com.amazon.firetv.youtube\",\"mediaId\":\"video\",\"title\":\"Demo highlights\",\"playbackState\":{\"state\":\"PLAYING\"}}";
        p.line(prefix+json,1000);p.press(APP,1100);ok(p.line(prefix+json.replace("PLAYING","PAUSED"),1200)&&p.take(APP,1300,true,false),"real Fire OS metadata shape supports scoped recovery");
        p=new NightPlayback();p.line(prefix+"{broken}",1000);p.press(APP,1100);p.observe(APP,KEY,false,true,1200);ok(!p.take(APP,1300,true,false),"malformed metadata cannot arm playback");
        p=new NightPlayback();ok(!p.observe("bad package",KEY,true,false,1000)&&p.states.isEmpty(),"invalid package metadata ignored");
        p=new NightPlayback();for(int i=0;i<20;i++)p.observe("com.example.player"+i,KEY,true,false,1000);ok(p.states.size()==8,"transient state cache is bounded");
        System.out.println("COMPLETE: "+count+" night playback checks passed");
    }
}
