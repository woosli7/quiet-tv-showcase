package com.quiettv.home;

import java.util.*;
import org.json.*;

/** One-shot recovery for Fire OS's branded-button Pause; no media history is saved. */
final class NightPlayback {
    static final long FRESH=30000,WINDOW=1500;
    static final class State {
        final String key;final boolean playing,paused;final long at;
        State(String key,boolean playing,boolean paused,long at){this.key=key;this.playing=playing;this.paused=paused;this.at=at;}
    }
    final Map<String,State> states=new LinkedHashMap<>();
    String pendingPackage="",pendingKey="",activePackage="";
    long until;
    void cancel(){pendingPackage="";pendingKey="";until=0;}
    void press(String foreground,long now){press(foreground,now,false);}
    void press(String foreground,long now,boolean audibleBeforePress){
        cancel();State state=states.get(foreground);
        if(state!=null&&state.playing&&activePackage.equals(foreground)&&now>=state.at&&(now-state.at<FRESH||audibleBeforePress)){pendingPackage=foreground;pendingKey=state.key;until=now+WINDOW;}
    }
    boolean observe(String pkg,String key,boolean playing,boolean paused,long now){
        if(pkg==null||!pkg.matches("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+")||key==null||key.isEmpty()||key.length()>1000)return false;
        if(!states.containsKey(pkg)&&states.size()>=8)states.remove(states.keySet().iterator().next());
        states.put(pkg,new State(key,playing,paused,now));
        if(playing)activePackage=pkg;else if(activePackage.equals(pkg))activePackage="";
        if(!pkg.equals(pendingPackage))return false;
        if(now>=until||!key.equals(pendingKey)){cancel();return false;}
        return paused;
    }
    boolean line(String line,long now){
        String marker="StateStore:storeState:capability=MediaSession ";
        int start=line.indexOf(marker);if(start<0||line.length()>8192)return false;
        start=line.indexOf(" state=",start+marker.length());if(start<0)return false;
        try{
            JSONObject value=new JSONObject(line.substring(start+7));
            JSONObject state=value.optJSONObject("playbackState");if(state==null)return false;
            String status=state.optString("state"),pkg=value.optString("packageName"),key=value.optString("mediaId")+"|"+value.optString("title");
            if(key.equals("|"))return false;
            return observe(pkg,key,status.equals("PLAYING"),status.equals("PAUSED"),now);
        }catch(Exception ignored){return false;}
    }
    boolean take(String foreground,long now,boolean interactive,boolean audioActive){
        State state=states.get(pendingPackage);
        boolean resume=interactive&&!audioActive&&now<until&&pendingPackage.equals(foreground)&&state!=null&&state.paused&&state.key.equals(pendingKey);
        cancel();return resume;
    }
    void foreground(String pkg){if(!pendingPackage.equals(pkg))cancel();}
    void remote(String line){
        if(line.contains("KeyPressReceiver")&&line.contains("action=ACTION_DOWN")&&!line.contains("keyCode=KEYCODE_APP_3,"))cancel();
    }
}
