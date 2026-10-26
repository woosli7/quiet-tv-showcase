package com.quiettv.home;

import java.io.*;
import java.net.*;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.*;
import java.util.zip.GZIPInputStream;
import org.json.*;

/** On-demand organizer channel search. Public page metadata only; no playback extraction. */
final class CsHighlights {
    static final int MAX_BYTES=2*1024*1024,MAX_CHOICES=2;
    static final long MINUTE=60000L;
    static final class Source {
        final Pattern event;final String channel,name;
        Source(JSONObject j)throws Exception{event=Pattern.compile(j.getString("event_pattern"),Pattern.CASE_INSENSITIVE);channel=j.getString("channel_id");name=CsMatch.clean(j.getString("name"));if(!channel.matches("UC[A-Za-z0-9_-]{22}"))throw new IOException("Invalid channel");}
        String appSearch(CsMatch m){try{return "https://www.youtube.com/tv?launch=search&q="+URLEncoder.encode(name+" "+m.a+" vs "+m.b+" "+m.event,"UTF-8").replace("+","%20");}catch(Exception e){return "";}}
        String search(CsMatch m){try{return "https://www.youtube.com/channel/"+channel+"/search?query="+URLEncoder.encode(m.a+" "+m.b+" highlights","UTF-8")+"&hl=en";}catch(Exception e){return "";}}
    }
    static final class Video {
        final String id,title;final long published;
        Video(String id,String title,long published){this.id=id;this.title=title;this.published=published;}
        String url(){return "https://www.youtube.com/watch?v="+id;}
    }
    static final class Result {
        final Source source;final List<Video> videos;final String message;
        Result(Source source,List<Video> videos,String message){this.source=source;this.videos=Collections.unmodifiableList(new ArrayList<>(videos));this.message=message;}
    }
    static final class Saved {final long at,ttl;final Result result;Saved(long at,long ttl,Result result){this.at=at;this.ttl=ttl;this.result=result;}}
    final ArrayList<Source> sources=new ArrayList<>();final Map<String,List<String>> aliases=new HashMap<>();
    final LinkedHashMap<String,Saved> cache=new LinkedHashMap<>();
    CsHighlights(JSONObject config){
        JSONArray rows=config.optJSONArray("profiles");if(rows!=null)for(int i=0;i<Math.min(12,rows.length());i++)try{sources.add(new Source(rows.getJSONObject(i)));}catch(Exception ignored){}
        JSONObject names=config.optJSONObject("team_aliases");if(names!=null){Iterator<String> keys=names.keys();int count=0;while(keys.hasNext()&&count++<100){String key=keys.next();JSONArray values=names.optJSONArray(key);ArrayList<String> list=new ArrayList<>();if(values!=null)for(int i=0;i<Math.min(8,values.length());i++)list.add(normal(values.optString(i,"")));aliases.put(normal(key),list);}}
    }
    Source source(CsMatch m){for(Source s:sources)if(s.event.matcher(m.event).find())return s;return null;}
    Result find(CsMatch m){
        Source s=source(m);if(s==null)return new Result(null,Collections.emptyList(),"No reviewed highlights channel is configured for this event.");
        long now=System.currentTimeMillis();String key=m.id+":"+m.ended;Saved saved=cache.get(key);if(saved!=null&&now>=saved.at&&now-saved.at<saved.ttl)return saved.result;
        ArrayList<Video> found=new ArrayList<>();String message="No matching official highlights found yet. They may not have been uploaded. Try again later or search the official channel.";long ttl=2*MINUTE;
        try{
            long deadline=System.nanoTime()+15000000000L;List<Video> candidates=search(initial(fetch(s.search(m),deadline),"ytInitialData"),s,m);
            int checked=0;boolean failed=false;for(Video candidate:candidates){if(checked++>=MAX_CHOICES)break;try{Video verified=verify(initial(fetch(candidate.url()+"&hl=en",deadline),"ytInitialPlayerResponse"),s,m,candidate.id,System.currentTimeMillis());if(verified!=null)found.add(verified);}catch(InterruptedIOException e){throw e;}catch(Exception e){failed=true;}}
            if(!found.isEmpty()){message="Official highlights / "+s.name;ttl=10*MINUTE;}else if(failed){message="Couldn't verify the highlights right now. You can search the official channel instead.";ttl=MINUTE;}
        }catch(Exception e){if(Thread.currentThread().isInterrupted())return new Result(s,Collections.emptyList(),"Highlights check cancelled.");message="Couldn't check the official channel right now. You can search it instead.";ttl=MINUTE;}
        Result result=new Result(s,found,message);if(cache.size()>=24)cache.remove(cache.keySet().iterator().next());cache.put(key,new Saved(System.currentTimeMillis(),ttl,result));return result;
    }
    static String normal(String value){return Normalizer.normalize(value,Normalizer.Form.NFKD).replaceAll("\\p{M}","").toLowerCase(Locale.US).replaceAll("[^a-z0-9]+"," ").trim().replaceAll("\\s+"," ");}
    static boolean phrase(String title,String name){return !name.isEmpty()&&(" "+title+" ").contains(" "+name+" ");}
    boolean team(String title,String name){String n=normal(name);if(phrase(title,n))return true;List<String> alternatives=aliases.get(n);if(alternatives!=null)for(String other:alternatives)if(phrase(title,other))return true;String simple=n.replaceAll("\\b(team|esports|gaming|clan)\\b"," ").trim().replaceAll("\\s+"," ");return !simple.equals(n)&&simple.length()>=2&&phrase(title,simple);}
    static String eventName(String value){return normal(value).replaceAll("\\bintel extreme masters\\b","iem").replaceAll("\\bepl\\b","esl pro league").replaceAll("\\bs([0-9]{1,2})\\b","season $1").replaceAll("\\b(cs2|counter strike 2|counter strike|premier)\\b"," ").replaceAll("\\s+"," ").trim();}
    boolean title(CsMatch m,String value){String text=normal(value);if(!phrase(text,"highlights")||!team(text,m.a)||!team(text,m.b))return false;String event=eventName(m.event),video=eventName(value);if(event.isEmpty())return false;for(String token:event.split(" "))if(!phrase(video,token))return false;return true;}
    List<Video> search(JSONObject data,Source s,CsMatch m)throws Exception{
        JSONObject metadata=data.optJSONObject("metadata");JSONObject channel=metadata==null?null:metadata.optJSONObject("channelMetadataRenderer");if(channel==null||!s.channel.equals(channel.optString("externalId")))throw new IOException("Channel structure changed");
        Object contents=data.opt("contents");if(!(contents instanceof JSONObject))throw new IOException("Channel results unavailable");ArrayList<Video> out=new ArrayList<>();walk(contents,s,m,out,new HashSet<>(),new int[]{0},0);return out;
    }
    void walk(Object node,Source s,CsMatch m,List<Video> out,Set<String> seen,int[] count,int depth)throws Exception{
        if(depth>48||++count[0]>16000)throw new IOException("Channel results exceed bounds");
        if(node instanceof JSONArray){JSONArray a=(JSONArray)node;for(int i=0;i<a.length();i++)walk(a.opt(i),s,m,out,seen,count,depth+1);}
        else if(node instanceof JSONObject){JSONObject j=(JSONObject)node;Iterator<String> keys=j.keys();while(keys.hasNext()){String key=keys.next();if(key.equals("adSlotRenderer")||key.equals("promotedSparklesWebRenderer"))continue;Object value=j.opt(key);if(key.equals("videoRenderer")||key.equals("compactVideoRenderer")||key.equals("gridVideoRenderer")){if(!(value instanceof JSONObject))continue;JSONObject row=(JSONObject)value;String id=row.optString("videoId"),title=text(row.optJSONObject("title"));if(out.size()<20&&id.matches("[A-Za-z0-9_-]{11}")&&title(m,title)&&owner(row,s.channel)&&!row.has("upcomingEventData")&&!live(row)&&seen.add(id))out.add(new Video(id,CsMatch.clean(title),0));}else if(value instanceof JSONObject||value instanceof JSONArray)walk(value,s,m,out,seen,count,depth+1);}}
    }
    static boolean owner(JSONObject row,String id){for(String key:new String[]{"shortBylineText","longBylineText","ownerText"}){JSONObject byline=row.optJSONObject(key);JSONArray runs=byline==null?null:byline.optJSONArray("runs");if(runs!=null)for(int i=0;i<runs.length();i++){JSONObject r=runs.optJSONObject(i),navigation=r==null?null:r.optJSONObject("navigationEndpoint"),endpoint=navigation==null?null:navigation.optJSONObject("browseEndpoint");if(endpoint!=null&&id.equals(endpoint.optString("browseId")))return true;}}return false;}
    static boolean live(JSONObject row){return row.toString().contains("BADGE_STYLE_TYPE_LIVE_NOW")||row.toString().contains("\"style\":\"LIVE\"");}
    static String text(JSONObject value){if(value==null)return "";String simple=value.optString("simpleText","");if(!simple.isEmpty())return simple;StringBuilder out=new StringBuilder();JSONArray runs=value.optJSONArray("runs");if(runs!=null)for(int i=0;i<Math.min(20,runs.length());i++){JSONObject run=runs.optJSONObject(i);if(run!=null)out.append(run.optString("text",""));}return out.toString();}
    Video verify(JSONObject player,Source s,CsMatch m,String id,long now){
        JSONObject detail=player.optJSONObject("videoDetails"),micro=player.optJSONObject("microformat"),format=micro==null?null:micro.optJSONObject("playerMicroformatRenderer"),play=player.optJSONObject("playabilityStatus");
        if(detail==null||format==null||play==null||!"OK".equals(play.optString("status"))||!id.equals(detail.optString("videoId"))||!s.channel.equals(detail.optString("channelId"))||!s.channel.equals(format.optString("externalChannelId"))||detail.optBoolean("isLiveContent",true)||format.has("liveBroadcastDetails")||!title(m,detail.optString("title","")))return null;
        long seconds=detail.optLong("lengthSeconds",0),published=CsMatch.time(format.optString("publishDate",""));if(seconds<120||seconds>10800||!m.status.equals("finished")||m.ended<m.start||m.ended>now||published<m.ended-15*MINUTE||published>m.ended+36*60*MINUTE||published>now+5*MINUTE)return null;
        return new Video(id,CsMatch.clean(detail.optString("title")),published);
    }
    static JSONObject initial(String html,String name)throws Exception{
        Pattern marker=Pattern.compile("(?:var\\s+"+Pattern.quote(name)+"\\s*=|window\\[['\"]"+Pattern.quote(name)+"['\"]\\]\\s*=)\\s*");Matcher match=marker.matcher(html);
        while(match.find()){int start=match.end();if(start>=html.length())break;char first=html.charAt(start);if(first=='\''||first=='\"'){StringBuilder out=new StringBuilder();for(int i=start+1;i<html.length();i++){char c=html.charAt(i);if(c==first)return new JSONObject(out.toString());if(c=='\\'){if(++i>=html.length())break;c=html.charAt(i);if(c=='x'||c=='u'){int digits=c=='x'?2:4;if(i+digits>=html.length())break;out.append((char)Integer.parseInt(html.substring(i+1,i+1+digits),16));i+=digits;}else if(c=='n')out.append('\n');else if(c=='r')out.append('\r');else if(c=='t')out.append('\t');else if(c=='b')out.append('\b');else if(c=='f')out.append('\f');else out.append(c);}else out.append(c);}throw new IOException("Incomplete page data");}
            if(first=='{'){boolean quoted=false,escaped=false;int depth=0;for(int i=start;i<html.length();i++){char c=html.charAt(i);if(quoted){if(escaped)escaped=false;else if(c=='\\')escaped=true;else if(c=='\"')quoted=false;}else if(c=='\"')quoted=true;else if(c=='{')depth++;else if(c=='}'&&--depth==0)return new JSONObject(html.substring(start,i+1));}}
        }throw new IOException("Page structure changed");
    }
    static String fetch(String url,long deadline)throws Exception{
        if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();long remaining=(deadline-System.nanoTime())/1000000;if(remaining<=0)throw new InterruptedIOException("Highlights check timed out");
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout((int)Math.min(4000,remaining));c.setReadTimeout((int)Math.min(4000,remaining));c.setInstanceFollowRedirects(false);c.setUseCaches(false);c.setRequestProperty("User-Agent","QuietTV/1.0 (TV highlights viewer)");c.setRequestProperty("Accept-Language","en-US,en;q=0.9");c.setRequestProperty("Accept-Encoding","gzip");
        try{if(c.getResponseCode()!=200)throw new IOException("Highlights source unavailable");try(InputStream in="gzip".equalsIgnoreCase(c.getContentEncoding())?new GZIPInputStream(c.getInputStream()):c.getInputStream()){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(Thread.currentThread().isInterrupted()||System.nanoTime()>deadline)throw new InterruptedIOException();if(out.size()+n>MAX_BYTES)throw new IOException("Highlights page too large");out.write(buffer,0,n);}return out.toString("UTF-8");}}finally{c.disconnect();}
    }
}
