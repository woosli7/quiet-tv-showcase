package com.quiettv.home;

/** Only the stock home screen, after a tracked app was actually opened, is redirected. */
final class AppReturnPolicy {
    static final String STOCK_HOME="com.amazon.tv.launcher",QUIET_HOME="com.quiettv.home";
    static String resumedPackage(String line){
        if(!line.contains("am_resume_activity"))return null;
        int end=line.lastIndexOf(']');if(end<0)return null;
        int start=line.lastIndexOf(',',end);if(start<0)return null;
        String component=line.substring(start+1,end).trim();int slash=component.indexOf('/');
        if(slash<1)return null;String pkg=component.substring(0,slash);
        return pkg.matches("[a-zA-Z0-9_]+(\\.[a-zA-Z0-9_]+)+")?pkg:null;
    }
    static boolean shouldReturn(String target,boolean observed,long started,long now,String foreground){
        return target!=null&&!target.isEmpty()&&!QUIET_HOME.equals(target)&&!STOCK_HOME.equals(target)
            &&observed&&now>=started&&now-started<86400000L&&STOCK_HOME.equals(foreground);
    }
    static boolean shouldWake(boolean enabled,boolean interactive,long now,long deadline,String foreground){
        return enabled&&interactive&&deadline>0&&now<deadline&&STOCK_HOME.equals(foreground);
    }
}
