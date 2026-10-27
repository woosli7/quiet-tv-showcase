package com.quiettv.home;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Pattern;
import org.json.*;

/** Small normalized records; no betting data or raw provider responses are persisted. */
final class CsMatch {
    long id,start,prize,eventId,teamIdA,teamIdB;int tier,stars,one=-1,two=-1,bo;
    String slug="",event="",a="",b="",status="",logoA="",logoB="",logoEvent=""; long ended;
    String page(){return "https://bo3.gg/matches/"+slug;}
    static long time(String text){try{String value=text.replaceFirst("\\.\\d+(?=[Z+-])","").replaceFirst("Z$","+0000").replaceFirst("([+-]\\d{2}):(\\d{2})$","$1$2");SimpleDateFormat f=new SimpleDateFormat("yyyy-MM-dd\u0027T\u0027HH:mm:ssZ",Locale.US);f.setLenient(false);java.text.ParsePosition p=new java.text.ParsePosition(0);Date date=f.parse(value,p);return date!=null&&p.getIndex()==value.length()?date.getTime():0;}catch(Exception ignored){return 0;}}
    static CsMatch read(JSONObject j) throws JSONException {
        CsMatch m=new CsMatch();m.id=j.optLong("id",0);m.slug=j.optString("slug","");m.status=j.optString("status","");m.start=time(j.optString("start_date",""));m.ended=time(j.optString("end_date",""));
        if(m.id<=0||!m.slug.matches("[a-zA-Z0-9_-]{1,180}")||j.optInt("discipline_id",0)!=1||!(m.status.equals("current")||m.status.equals("upcoming")||m.status.equals("finished")))throw new JSONException("Match structure changed");
        JSONObject t=j.getJSONObject("tournament"),a=j.getJSONObject("team1"),b=j.getJSONObject("team2");
        m.teamIdA=Math.max(0,a.optLong("id",0));m.teamIdB=Math.max(0,b.optLong("id",0));m.eventId=t.optLong("id",0);m.event=clean(t.optString("name",""));m.a=clean(a.optString("name",""));m.b=clean(b.optString("name",""));
        if(m.event.isEmpty()||m.a.isEmpty()||m.b.isEmpty()||m.start<=0)throw new JSONException("Match details incomplete");
        m.tier=t.optInt("tier_rank",0);m.prize=t.optLong("prize",0);m.stars=j.optInt("stars",0);m.bo=j.optInt("bo_type",0);
        if(!j.isNull("team1_score")&&!j.isNull("team2_score")){int x=j.optInt("team1_score",-1),y=j.optInt("team2_score",-1);int max=m.bo>=1&&m.bo<=7?(m.bo+1)/2:0;if(x>=0&&y>=0&&x<=max&&y<=max&&max>0){m.one=x;m.two=y;}}
        m.logoA=image(a.optString("image_url",""));m.logoB=image(b.optString("image_url",""));m.logoEvent=image(t.optString("image_url",""));return m;
    }
    boolean finalScore(){int wins=bo>=1&&bo<=7?(bo+1)/2:0;return wins>0&&(one==wins&&two>=0&&two<wins||two==wins&&one>=0&&one<wins);}
    String winner(){return finalScore()?(one>two?a:b):"";}
    static String clean(String text){String clean=text.replaceAll("[\\p{Cntrl}]"," ").trim();return clean.substring(0,Math.min(100,clean.length()));}
    static String image(String s){try{URI u=URI.create(s);return "https".equals(u.getScheme())&&"files.bo3.gg".equalsIgnoreCase(u.getHost())&&u.getUserInfo()==null?s:"";}catch(Exception ignored){return "";}}
    static String stamp(long time){SimpleDateFormat f=new SimpleDateFormat("yyyy-MM-dd\u0027T\u0027HH:mm:ss\u0027Z\u0027",Locale.US);f.setTimeZone(TimeZone.getTimeZone("UTC"));return f.format(new Date(time));}
    JSONObject cache() throws JSONException{return new JSONObject().put("id",id).put("slug",slug).put("status",status).put("start_date",stamp(start)).put("end_date",ended>0?stamp(ended):JSONObject.NULL).put("discipline_id",1).put("bo_type",bo).put("stars",stars).put("team1_score",one).put("team2_score",two).put("tournament",new JSONObject().put("id",eventId).put("name",event).put("tier_rank",tier).put("prize",prize).put("image_url",logoEvent)).put("team1",new JSONObject().put("id",teamIdA).put("name",a).put("image_url",logoA)).put("team2",new JSONObject().put("id",teamIdB).put("name",b).put("image_url",logoB));}
}
final class CsPolicy {
    final int cards,topTier,secondTier;final long refresh,expiry,schedule,maxDuration,secondPrize,unknownPrize;final Pattern known,excluded;final Set<Long> include=new HashSet<>(),exclude=new HashSet<>(),includeTeams=new HashSet<>();
    CsPolicy(JSONObject j){cards=Math.max(1,Math.min(2,j.optInt("max_cards",2)));refresh=Math.max(60000,j.optLong("refresh_seconds",120)*1000);expiry=Math.max(refresh+30000,Math.min(300000,j.optLong("live_expiry_seconds",180)*1000));schedule=Math.max(600000,j.optLong("schedule_cache_seconds",1800)*1000);maxDuration=j.optLong("max_live_duration_hours",12)*3600000;topTier=j.optInt("top_tier_rank",1);secondTier=j.optInt("secondary_tier_rank",2);secondPrize=j.optLong("secondary_min_prize",100000);unknownPrize=j.optLong("unknown_tier_min_prize",200000);known=Pattern.compile(j.optString("prominent_names","major|iem|esl|blast|pgl"),Pattern.CASE_INSENSITIVE);excluded=Pattern.compile(j.optString("exclude_names","qualifier|academy"),Pattern.CASE_INSENSITIVE);ids(j.optJSONArray("include_event_ids"),include);ids(j.optJSONArray("exclude_event_ids"),exclude);JSONArray teamIds=j.optJSONArray("include_team_ids");if(teamIds!=null)for(int i=0;i<Math.min(32,teamIds.length());i++){long id=teamIds.optLong(i,0);if(id>0)includeTeams.add(id);}}
    static void ids(JSONArray a,Set<Long> into){if(a!=null)for(int i=0;i<a.length();i++)into.add(a.optLong(i,0));}
    boolean accepts(CsMatch m,long eventId){if(exclude.contains(eventId))return false;if(teamIncluded(m)||include.contains(eventId))return true;if(excluded.matcher(m.event).find())return false;return m.tier>0&&(m.tier<=topTier||m.tier<=secondTier&&m.prize>=secondPrize)||m.tier==0&&m.prize>=unknownPrize&&known.matcher(m.event).find();}
    boolean teamIncluded(CsMatch m){return m.teamIdA>0&&includeTeams.contains(m.teamIdA)||m.teamIdB>0&&includeTeams.contains(m.teamIdB);}
    long rank(CsMatch m){return (m.tier==1?10000000:m.tier==2?7000000:4000000)+Math.min(2000000,m.prize)+m.stars*1000+(known.matcher(m.event).find()?500:0);}
    boolean live(CsMatch m,long now){int wins=m.bo>=1&&m.bo<=7?(m.bo+1)/2:0;return m.status.equals("current")&&m.ended==0&&now>=m.start-900000&&now-m.start<maxDuration&&(wins==0||m.one<wins&&m.two<wins);}
    static boolean fresh(long observed,long now,long ttl){return observed>0&&now>=observed&&now-observed<ttl;}
}
final class CsParser {
    static java.util.List<CsMatch> list(String body,String status,long now,CsPolicy p) throws Exception {
        JSONObject root=new JSONObject(body);JSONArray rows=root.getJSONArray("results");if(status.equals("current")&&root.getJSONObject("total").optInt("count",rows.length())>100)throw new java.io.IOException("Live list exceeds safe limit");
        ArrayList<CsMatch> out=new ArrayList<>();HashSet<Long> seen=new HashSet<>();int valid=0;
        for(int i=0;i<Math.min(100,rows.length());i++){try{JSONObject row=rows.getJSONObject(i);CsMatch m=CsMatch.read(row);if(!m.status.equals(status))continue;valid++;long event=row.getJSONObject("tournament").optLong("id",0);if(!p.accepts(m,event)||!seen.add(m.id))continue;if(status.equals("current")&&!p.live(m,now)||status.equals("upcoming")&&(m.start<=now||m.start-now>7*86400000L))continue;out.add(m);}catch(JSONException ignored){}}
        if(rows.length()>0&&valid==0)throw new java.io.IOException("Match structure changed");
        out.sort((a,b)->status.equals("current")?Long.compare(p.rank(b),p.rank(a)):a.start==b.start?Long.compare(p.rank(b),p.rank(a)):Long.compare(a.start,b.start));return out;
    }
}
final class CsStream {
    String name,url,language;boolean official,organizer;int viewers;
    static String url(String value){try{URI u=new URI(value);String host=u.getHost();if(!"https".equalsIgnoreCase(u.getScheme())||host==null||u.getUserInfo()!=null||u.getPort()!=-1)return null;host=host.toLowerCase(Locale.US);if(!(host.equals("twitch.tv")||host.equals("www.twitch.tv")||host.equals("m.twitch.tv")||host.equals("youtube.com")||host.equals("www.youtube.com")||host.equals("m.youtube.com")||host.equals("youtu.be")||host.equals("kick.com")||host.equals("www.kick.com")))return null;String path=u.getPath()==null?"":u.getPath();if(host.endsWith("twitch.tv")&&!path.matches("/[A-Za-z0-9_]{3,25}/?"))return null;if(host.endsWith("twitch.tv")&&path.matches("/(directory|videos|downloads|settings|login|search|turbo)/?"))return null;if(host.endsWith("kick.com")&&!path.matches("/[A-Za-z0-9_-]{1,60}/?"))return null;if(host.equals("youtu.be")&&!path.matches("/[A-Za-z0-9_-]{11}/?"))return null;if(host.endsWith("youtube.com")&&!(path.equals("/watch")&&u.getQuery()!=null&&u.getQuery().matches(".*(?:^|&)v=[A-Za-z0-9_-]{11}(?:&.*)?")||path.matches("/(live|embed)/[A-Za-z0-9_-]{11}/?")||path.matches("/(@[A-Za-z0-9_.-]+|channel/[A-Za-z0-9_-]+)/live/?")))return null;if(host.endsWith("youtube.com")&&path.startsWith("/embed/"))return "https://www.youtube.com/watch?v="+path.substring(7).replace("/","");return u.toASCIIString();}catch(Exception ignored){return null;}}
    int priority(){return (organizer?100000:0)+(english()?10000:0)+(preferredPlatform()?1000:0)+Math.min(500,Math.max(0,viewers));}
    boolean preferredPlatform(){String host=URI.create(url).getHost().toLowerCase(Locale.US);return host.equals("youtu.be")||host.endsWith("youtube.com")||host.endsWith("twitch.tv");}
    boolean english(){return language.equals("en")||language.startsWith("en-");}
    String platform(){String host=URI.create(url).getHost();return host.contains("twitch")?"TWITCH":host.contains("kick")?"KICK":"YOUTUBE";}
    String label(){return (english()?"EN":language.isEmpty()?"LANGUAGE ?":language.toUpperCase(Locale.US))+(organizer?" / ORGANIZER":" / OTHER LISTED")+" / "+platform()+" / "+name;}
    static java.util.List<CsStream> read(JSONObject detail,long matchId) throws Exception {ArrayList<CsStream> out=new ArrayList<>();HashSet<String> seen=new HashSet<>();JSONArray rows=detail.optJSONArray("streams");if(rows==null)return out;for(int i=0;i<Math.min(40,rows.length());i++){JSONObject row=rows.optJSONObject(i);if(row==null||row.optLong("match_id",0)!=matchId||row.optBoolean("blocked",false))continue;String url=url(row.optString("raw_url",""));if(url==null||!seen.add(url))continue;CsStream s=new CsStream();s.url=url;s.name=CsMatch.clean(row.optString("name","Stream"));s.language=row.optString("language","").toLowerCase(Locale.US);s.official=row.optBoolean("official",false);s.viewers=row.optInt("viewers_number",0);out.add(s);}out.sort((a,b)->Integer.compare(b.priority(),a.priority()));return out;}
}
