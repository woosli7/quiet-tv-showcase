package com.quiettv.home;
import java.util.*;
import org.json.*;

/** Bounded upcoming window, grouped by local calendar day. */
final class CsSchedule {
    static final int DAYS=7,MAX_ROWS=64,MAX_WINDOW_ROWS=256,MAX_CACHE_BYTES=524288,CACHE_VERSION=2;
    static TimeZone zone(){return TvProfile.zone();}
    static long day(long instant,int offset){Calendar c=Calendar.getInstance(zone());c.setTimeInMillis(instant);c.set(Calendar.HOUR_OF_DAY,0);c.set(Calendar.MINUTE,0);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);c.add(Calendar.DATE,offset);return c.getTimeInMillis();}
    static long browseDay(long selected,int delta,long now){return Math.max(day(now,0),Math.min(day(now,DAYS-1),day(selected,delta)));}
    static boolean fresh(long at,long now){return CsPolicy.fresh(at,now,1800000)&&day(at,0)==day(now,0);}
    static boolean onDay(CsMatch m,long now,long selected){long start=day(selected,0);return "upcoming".equals(m.status)&&m.ended==0&&m.start>now&&m.start>=start&&m.start<day(start,1);}
    static boolean today(CsMatch m,long now){return onDay(m,now,now);}
    static boolean inWindow(CsMatch m,long now){return "upcoming".equals(m.status)&&m.ended==0&&m.start>now&&m.start>=day(now,0)&&m.start<day(now,DAYS);}
    static CsMatch next(Collection<CsMatch> source,long checkedAt,long now,CsPolicy policy){if(!fresh(checkedAt,now))return null;java.util.List<CsMatch> today=rows(source,now,policy);return today.isEmpty()?null:today.get(0);}
    static java.util.List<CsMatch> ordered(Collection<CsMatch> source,CsPolicy policy){ArrayList<CsMatch> out=new ArrayList<>(source);out.sort((a,b)->a.start==b.start?Long.compare(policy.rank(b),policy.rank(a)):Long.compare(a.start,b.start));return out;}
    static java.util.List<CsMatch> rows(Collection<CsMatch> source,long now,CsPolicy policy){return rowsForDay(source,now,policy,now);}
    static java.util.List<CsMatch> rowsForDay(Collection<CsMatch> source,long now,CsPolicy policy,long selected){
        LinkedHashMap<Long,CsMatch> unique=new LinkedHashMap<>();for(CsMatch m:source)if(inWindow(m,now)&&onDay(m,now,selected)&&policy.accepts(m,m.eventId))unique.put(m.id,m);
        java.util.List<CsMatch> out=ordered(unique.values(),policy);return new ArrayList<>(out.subList(0,Math.min(MAX_ROWS,out.size())));
    }
    static java.util.List<CsMatch> windowRows(Collection<CsMatch> source,long now,CsPolicy policy){
        LinkedHashMap<Long,CsMatch> unique=new LinkedHashMap<>();for(CsMatch m:source)if(inWindow(m,now)&&policy.accepts(m,m.eventId))unique.put(m.id,m);
        ArrayList<CsMatch> out=new ArrayList<>();HashMap<Long,Integer> counts=new HashMap<>();for(CsMatch m:ordered(unique.values(),policy)){long date=day(m.start,0);int count=counts.containsKey(date)?counts.get(date):0;if(count>=MAX_ROWS)continue;counts.put(date,count+1);out.add(m);if(out.size()==MAX_WINDOW_ROWS)break;}return out;
    }
    static boolean limited(Collection<CsMatch> source,long now,CsPolicy policy){HashSet<Long> ids=new HashSet<>();HashMap<Long,Integer> counts=new HashMap<>();for(CsMatch m:source)if(inWindow(m,now)&&policy.accepts(m,m.eventId)&&ids.add(m.id)){long date=day(m.start,0);int count=(counts.containsKey(date)?counts.get(date):0)+1;counts.put(date,count);if(count>MAX_ROWS||ids.size()>MAX_WINDOW_ROWS)return true;}return false;}
    static JSONObject cache(Collection<CsMatch> source,long at,String zone)throws JSONException{JSONArray rows=new JSONArray();int n=0;for(CsMatch m:source){if(n++==MAX_WINDOW_ROWS)break;rows.put(m.cache());}return new JSONObject().put("version",CACHE_VERSION).put("days",DAYS).put("zone",zone).put("checkedAt",at).put("matches",rows);}
    static long restoredAt(JSONObject j,long now){long at=j.optLong("checkedAt",0);return j.optInt("version",0)==CACHE_VERSION&&j.optInt("days",0)==DAYS&&zone().getID().equals(j.optString("zone",""))&&fresh(at,now)?at:0;}
    static java.util.List<CsMatch> restoreRows(JSONObject j,long now,CsPolicy policy)throws JSONException{JSONArray a=j.getJSONArray("matches");ArrayList<CsMatch> rows=new ArrayList<>();for(int i=0;i<Math.min(MAX_WINDOW_ROWS,a.length());i++)try{rows.add(CsMatch.read(a.getJSONObject(i)));}catch(JSONException ignored){}return windowRows(rows,now,policy);}
    static final class Page {final java.util.List<CsMatch> matches=new ArrayList<>();boolean more;long firstStart,lastStart;}
    static Page page(String body,int offset,long now,CsPolicy policy)throws Exception{return parsePage(body,offset,now,policy,1);}
    static Page windowPage(String body,int offset,long now,CsPolicy policy)throws Exception{return parsePage(body,offset,now,policy,DAYS);}
    static Page parsePage(String body,int offset,long now,CsPolicy policy,int days)throws Exception{
        JSONObject root=new JSONObject(body);JSONArray rows=root.getJSONArray("results");int total=root.getJSONObject("total").getInt("count");if(total<0||rows.length()>100||rows.length()==0&&total>offset)throw new java.io.IOException("Schedule structure changed");
        Page page=new Page();long last=0,latest=0,end=day(now,days);int dated=0,valid=0;
        for(int i=0;i<rows.length();i++){
            JSONObject row=rows.optJSONObject(i);if(row==null)continue;long start=CsMatch.time(row.optString("start_date",""));if(start>0){if(start<last)throw new java.io.IOException("Schedule ordering changed");if(page.firstStart==0)page.firstStart=start;last=start;latest=start;dated++;}
            try{CsMatch m=CsMatch.read(row);if("upcoming".equals(m.status))valid++;if(inWindow(m,now)&&m.start<end&&policy.accepts(m,m.eventId))page.matches.add(m);}catch(JSONException ignored){}
        }
        if(rows.length()>0&&(dated==0||valid==0))throw new java.io.IOException("Schedule structure changed");
        page.lastStart=last;page.more=rows.length()==100&&total>offset+rows.length()&&latest<end;return page;
    }
}
