package com.quiettv.home;
import java.net.URI;
import java.util.*;
import java.util.regex.Pattern;
import org.json.*;

/** Reviewed organizer identities, intersected with this match's actual broadcast list. */
final class CsBroadcasts {
    static final class Profile {
        Pattern event;final HashSet<String> identities=new HashSet<>();
    }
    final ArrayList<Profile> profiles=new ArrayList<>();
    CsBroadcasts(JSONObject root){
        JSONArray rows=root.optJSONArray("profiles");if(rows==null)return;
        for(int i=0;i<Math.min(40,rows.length());i++)try{
            JSONObject j=rows.getJSONObject(i);Profile p=new Profile();p.event=Pattern.compile(j.getString("event_pattern"),Pattern.CASE_INSENSITIVE);
            for(String platform:new String[]{"twitch","kick","youtube"}){
                JSONArray channels=j.optJSONArray(platform);if(channels==null)continue;
                for(int n=0;n<Math.min(30,channels.length());n++){String value=channels.getString(n);if(!value.startsWith("channel/"))value=value.toLowerCase(Locale.US);p.identities.add(platform+":"+value);}
            }
            profiles.add(p);
        }catch(Exception ignored){} // Malformed rules never grant trust.
    }
    boolean known(String event){for(Profile p:profiles)if(p.event.matcher(event).find())return true;return false;}
    static String identity(String value){
        try{
            URI uri=new URI(value);String host=uri.getHost();if(!"https".equalsIgnoreCase(uri.getScheme())||host==null||uri.getUserInfo()!=null||uri.getPort()!=-1)return "";
            host=host.toLowerCase(Locale.US);String path=uri.getPath()==null?"":uri.getPath();
            if(host.equals("twitch.tv")||host.equals("www.twitch.tv")||host.equals("m.twitch.tv"))return path.matches("/[A-Za-z0-9_]{3,25}/?")?"twitch:"+path.replace("/","").toLowerCase(Locale.US):"";
            if(host.equals("kick.com")||host.equals("www.kick.com"))return path.matches("/[A-Za-z0-9_-]{1,60}/?")?"kick:"+path.replace("/","").toLowerCase(Locale.US):"";
            if(host.equals("youtube.com")||host.equals("www.youtube.com")||host.equals("m.youtube.com")){
                if(path.matches("/@[A-Za-z0-9_.-]+(/live)?/?"))return "youtube:"+path.split("/")[1].toLowerCase(Locale.US);
                if(path.matches("/channel/[A-Za-z0-9_-]+(/live)?/?"))return "youtube:channel/"+path.split("/")[2];
            }
        }catch(Exception ignored){}return "";
    }
    boolean verified(String event,CsStream stream,String youtubeAuthor){
        String key=identity(stream.url);
        // For a video URL, only YouTube's author_url identifies its owner. Display names are not evidence.
        if(key.isEmpty()&&youtubeVideo(stream.url)){String author=identity(youtubeAuthor);if(author.startsWith("youtube:"))key=author;}
        if(key.isEmpty())return false;
        for(Profile p:profiles)if(p.event.matcher(event).find()&&p.identities.contains(key))return true;
        return false;
    }
    static boolean youtubeVideo(String url){try{URI u=new URI(url);String h=u.getHost();return h!=null&&(h.equals("youtu.be")||h.equals("youtube.com")||h.equals("www.youtube.com")||h.equals("m.youtube.com"))&&identity(url).isEmpty()&&CsStream.url(url)!=null;}catch(Exception ignored){return false;}}
    java.util.List<CsStream> select(java.util.List<CsStream> streams,String event,Map<String,String> authors){
        ArrayList<CsStream> out=new ArrayList<>();for(CsStream s:streams)if(verified(event,s,authors.getOrDefault(s.url,""))){s.organizer=true;out.add(s);}
        out.sort((a,b)->Integer.compare(b.priority(),a.priority()));return out;
    }
}
