package com.quiettv.home;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.regex.*;
import java.util.zip.GZIPInputStream;
import org.json.*;

/** On-demand fallback using public organizer channel pages, never a fixed-channel guess. */
final class CsOrganizerLive {
    static final int MAX_PAGES=3,MAX_BYTES=524288;
    static final Pattern SCRIPTS=Pattern.compile("(?is)<script\\b([^>]*)>(.*?)</script\\s*>");
    static final Pattern JSON_LD=Pattern.compile("(?i)\\btype\\s*=\\s*([\"'])application/ld\\+json\\1");
    static boolean title(CsMatch match,String value){
        if(value.length()>1500)return false;
        String text=CsHighlights.eventName(value),event=CsHighlights.eventName(match.event);
        if(event.isEmpty())return false;for(String token:event.split(" "))if(!CsHighlights.phrase(text,token))return false;
        String[] pair=value.replaceFirst("^\\s*\\[[^\\]]{1,12}\\]\\s*","").split("(?i)\\s+(?:vs\\.?|v\\.?|versus)\\s+",2);
        if(pair.length!=2)return false;
        String left=CsFavorites.canonical(pair[0]),right=CsFavorites.canonical(pair[1].split("\\s+[-–—|]\\s+",2)[0]);
        String a=CsFavorites.canonical(match.a),b=CsFavorites.canonical(match.b);
        return !a.isEmpty()&&!b.isEmpty()&&!a.equals(b)&&(left.equals(a)&&right.equals(b)||left.equals(b)&&right.equals(a));
    }
    static boolean channel(String embed,String expected){
        try{URI u=new URI(embed);if(!"https".equals(u.getScheme())||!"player.twitch.tv".equals(u.getHost())||u.getUserInfo()!=null||u.getPort()!=-1)return false;
            String value=null;if(u.getRawQuery()==null)return false;for(String part:u.getRawQuery().split("&")){String[] item=part.split("=",2);if(item.length==2&&item[0].equals("channel")){if(value!=null)return false;value=URLDecoder.decode(item[1],"UTF-8");}}
            return ("twitch:"+value).equals(expected);
        }catch(Exception ignored){return false;}
    }
    static boolean graph(JSONObject root,CsMatch match,String expected,long now){
        JSONArray nodes=root.optJSONArray("@graph");if(nodes==null)nodes=new JSONArray().put(root);
        boolean profile=false;for(int i=0;i<Math.min(40,nodes.length());i++){JSONObject node=nodes.optJSONObject(i);if(node==null||!"ProfilePage".equals(node.optString("@type")))continue;JSONObject owner=node.optJSONObject("mainEntity");if(owner!=null&&expected.equals(CsBroadcasts.identity(owner.optString("url"))))profile=true;}
        if(!profile)return false;
        for(int i=0;i<Math.min(40,nodes.length());i++){
            JSONObject node=nodes.optJSONObject(i);if(node==null||!"VideoObject".equals(node.optString("@type")))continue;
            JSONObject live=node.optJSONObject("publication");if(live==null||!"BroadcastEvent".equals(live.optString("@type"))||!Boolean.TRUE.equals(live.opt("isLiveBroadcast")))continue;
            long started=CsMatch.time(live.optString("startDate"));
            if(started<=0||started>now+300000||now-started>18*3600000L||!channel(node.optString("embedUrl"),expected))continue;
            if(title(match,node.optString("description")))return true;
        }
        return false;
    }
    static CsStream verify(String html,CsMatch match,CsStream candidate,long now){
        if(html==null||html.length()>MAX_BYTES||!"current".equals(match.status)||match.ended>0||match.finalScore())return null;
        String expected=CsBroadcasts.identity(candidate.url);if(!expected.startsWith("twitch:"))return null;
        Matcher scripts=SCRIPTS.matcher(html);int count=0;
        while(scripts.find()&&count++<40){if(!JSON_LD.matcher(scripts.group(1)).find())continue;
            try{JSONObject root=new JSONObject(scripts.group(2));if(graph(root,match,expected,now)){candidate.organizer=true;return candidate;}}catch(JSONException ignored){}
        }
        return null;
    }
    static String page(String url,long deadline)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();int timeout=(int)Math.min(2500,Math.max(1,(deadline-System.nanoTime())/1000000));
        c.setConnectTimeout(timeout);c.setReadTimeout(timeout);c.setInstanceFollowRedirects(false);c.setUseCaches(false);
        c.setRequestProperty("User-Agent","Mozilla/5.0 (compatible; QuietTV/1.0)");c.setRequestProperty("Accept","text/html");c.setRequestProperty("Accept-Encoding","gzip");c.setRequestProperty("Cache-Control","no-cache");
        try{if(c.getResponseCode()!=200)throw new IOException("Organizer page unavailable");InputStream raw=c.getInputStream();try(InputStream in="gzip".equalsIgnoreCase(c.getContentEncoding())?new GZIPInputStream(raw):raw){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] chunk=new byte[4096];int n;while((n=in.read(chunk))!=-1){if(System.nanoTime()>deadline||out.size()+n>MAX_BYTES)throw new IOException("Organizer check limit");out.write(chunk,0,n);}return out.toString("UTF-8");}}finally{c.disconnect();}
    }
    static java.util.List<CsStream> find(CsMatch match,CsBroadcasts registry,boolean alternatives){
        ArrayList<CsStream> found=new ArrayList<>();long deadline=System.nanoTime()+12000000000L;
        for(CsStream candidate:registry.twitchCandidates(match.event,MAX_PAGES)){
            if(System.nanoTime()>deadline||Thread.currentThread().isInterrupted())break;
            try{CsStream result=verify(page(candidate.url,deadline),match,candidate,System.currentTimeMillis());if(result!=null){found.add(result);if(!alternatives)break;}}catch(Exception ignored){}
        }
        return found;
    }
}
