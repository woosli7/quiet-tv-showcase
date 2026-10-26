package com.quiettv.home;
import android.app.Activity;
import android.content.*;
import android.net.Uri;

/** Try installed players that actually resolve the deep link, then Silk. */
final class CsWatch {
    static boolean searchHighlights(Activity context,CsHighlights.Source source,CsMatch match){
        // YouTube TV needs its search deep link; a web channel-search URL lands on Home.
        Uri tv=Uri.parse(source.appSearch(match));
        for(String pkg:new String[]{"com.amazon.firetv.youtube","com.google.android.youtube.tv"})if(launch(context,new Intent(Intent.ACTION_VIEW,tv).setPackage(pkg)))return true;
        if(launch(context,new Intent(Intent.ACTION_VIEW,Uri.parse(source.search(match))).setPackage("com.google.android.youtube")))return true;
        return open(context,source.search(match),true);
    }
    static boolean open(Activity context,String url,boolean browserOnly){
        Uri uri=Uri.parse(url);String host=uri.getHost()==null?"":uri.getHost().toLowerCase(java.util.Locale.US);
        if(!browserOnly&&host.endsWith("twitch.tv")){
            java.util.List<String> parts=uri.getPathSegments();if(!parts.isEmpty()&&parts.get(0).matches("[a-zA-Z0-9_]+")){
                for(String pkg:new String[]{"tv.twitch.android.viewer","tv.twitch.android.app"}){
                    if(launch(context,new Intent(Intent.ACTION_VIEW,Uri.parse("twitch://stream/"+parts.get(0))).setPackage(pkg)))return true;
                    if(launch(context,new Intent(Intent.ACTION_VIEW,uri).setPackage(pkg)))return true;
                }
            }
        }
        if(!browserOnly&&(host.endsWith("youtube.com")||host.equals("youtu.be"))){for(String pkg:new String[]{"com.amazon.firetv.youtube","com.google.android.youtube.tv","com.google.android.youtube"})if(launch(context,new Intent(Intent.ACTION_VIEW,uri).setPackage(pkg)))return true;}
        if(launch(context,new Intent(Intent.ACTION_VIEW,uri).setPackage("com.amazon.cloud9")))return true;
        return launch(context,new Intent(Intent.ACTION_VIEW,uri));
    }
    static boolean launch(Activity context,Intent intent){try{ComponentName target=intent.resolveActivity(context.getPackageManager());if(target==null)return false;HomeService.trackApp(context,target.getPackageName());context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));if(context instanceof MainActivity)((MainActivity)context).contentLaunched();return true;}catch(Exception ignored){HomeService.clearTrackedApp(context);return false;}}
}
