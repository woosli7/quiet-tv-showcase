package com.quiettv.home;
import java.util.*;
import org.json.*;

/** Bounded, durable finished-match window grouped by local completion date. */
final class CsResults {

    static TimeZone zone(){return TvProfile.zone();}
    static final int DAYS=4,MAX_RESULTS=64,MAX_WINDOW_RESULTS=256,MAX_FEATURED=128,MAX_CACHE_BYTES=524288,CACHE_VERSION=2;
    final LinkedHashMap<Long,CsMatch> finals=new LinkedHashMap<>();
    final LinkedHashMap<Long,Long> featured=new LinkedHashMap<>();
    long checkedAt;boolean partial;
    static long day(long now,int offset){Calendar c=Calendar.getInstance(zone());c.setTimeInMillis(now);c.set(Calendar.HOUR_OF_DAY,0);c.set(Calendar.MINUTE,0);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);c.add(Calendar.DATE,offset);return c.getTimeInMillis();}
    static long dayStart(long now){return day(now,0);}
    static long dayEnd(long now){return day(now,1);}
    static long browseDay(long selected,int delta,long now){return Math.max(day(now,1-DAYS),Math.min(day(now,0),day(selected,delta)));}
    static boolean fresh(long checked,long now){return CsPolicy.fresh(checked,now,1800000)&&day(checked,0)==day(now,0);}
    static boolean today(long ended,long now){return ended>=dayStart(now)&&ended<=now;}
    static boolean finished(CsMatch m,long now){return finished(m,now,dayStart(now));}
    static boolean finished(CsMatch m,long now,long start){return "finished".equals(m.status)&&m.ended>0&&m.ended>=m.start&&m.ended>=start&&m.ended<=now;}
    static boolean inWindow(CsMatch m,long now){return finished(m,now,day(now,1-DAYS));}
    static ArrayList<CsMatch> ordered(Collection<CsMatch> source){ArrayList<CsMatch> out=new ArrayList<>(source);out.sort((a,b)->a.ended==b.ended?Long.compare(a.id,b.id):Long.compare(b.ended,a.ended));return out;}
    void prune(long now){long start=day(now,1-DAYS),seenStart=day(now,-DAYS);finals.values().removeIf(m->!finished(m,now,start));featured.entrySet().removeIf(e->e.getValue()>now||e.getValue()<seenStart);while(featured.size()>MAX_FEATURED)featured.remove(featured.keySet().iterator().next());HashMap<Long,Integer> counts=new HashMap<>();int total=0;for(CsMatch m:ordered(finals.values())){long date=day(m.ended,0);int count=counts.containsKey(date)?counts.get(date):0;if(count>=MAX_RESULTS||total>=MAX_WINDOW_RESULTS){finals.remove(m.id);continue;}counts.put(date,count+1);total++;}}
    void remember(java.util.List<CsMatch> rows,long now){prune(now);for(CsMatch m:rows){featured.remove(m.id);featured.put(m.id,now);}prune(now);}
    Set<Long> featuredIds(long now){prune(now);return new HashSet<>(featured.keySet());}
    int count(long now){return rows(now).size();}
    int totalCount(long now){return allRows(now).size();}
    java.util.List<CsMatch> rows(long now){return rowsForDay(now,now);}
    java.util.List<CsMatch> rowsForDay(long now,long selected){long start=day(selected,0),end=day(selected,1),window=day(now,1-DAYS);ArrayList<CsMatch> out=new ArrayList<>();if(start<window||start>day(now,0))return out;for(CsMatch m:finals.values())if(finished(m,now,start)&&m.ended<end)out.add(m);return ordered(out);}
    java.util.List<CsMatch> allRows(long now){long start=day(now,1-DAYS);ArrayList<CsMatch> out=new ArrayList<>();for(CsMatch m:finals.values())if(finished(m,now,start))out.add(m);return ordered(out);}
    static boolean limited(Collection<CsMatch> source,long now){HashSet<Long> ids=new HashSet<>();HashMap<Long,Integer> counts=new HashMap<>();for(CsMatch m:source)if(inWindow(m,now)&&ids.add(m.id)){long date=day(m.ended,0);int count=(counts.containsKey(date)?counts.get(date):0)+1;counts.put(date,count);if(count>MAX_RESULTS||ids.size()>MAX_WINDOW_RESULTS)return true;}return false;}
    void merge(java.util.List<CsMatch> incoming,long now){prune(now);for(CsMatch m:incoming)if(inWindow(m,now))finals.put(m.id,m);prune(now);}
    JSONObject cache(long now)throws JSONException{prune(now);JSONObject j=new JSONObject().put("version",CACHE_VERSION).put("days",DAYS).put("zone",zone().getID()).put("checkedAt",checkedAt).put("partial",partial);JSONArray a=new JSONArray(),ids=new JSONArray();for(CsMatch m:allRows(now))a.put(m.cache());for(Map.Entry<Long,Long> e:featured.entrySet())ids.put(new JSONObject().put("id",e.getKey()).put("seen",e.getValue()));return j.put("results",a).put("featured",ids);}
    static CsResults restore(JSONObject j,CsPolicy policy,long now){CsResults log=new CsResults();JSONArray ids=j.optJSONArray("featured");if(ids!=null)for(int i=0;i<Math.min(MAX_FEATURED,ids.length());i++){JSONObject v=ids.optJSONObject(i);if(v!=null&&v.optLong("id",0)>0)log.featured.put(v.optLong("id"),v.optLong("seen",0));}log.prune(now);JSONArray a=j.optJSONArray("results");if(a!=null)for(int i=0;i<Math.min(MAX_WINDOW_RESULTS,a.length());i++)try{CsMatch m=CsMatch.read(a.getJSONObject(i));if(inWindow(m,now)&&(policy.accepts(m,m.eventId)||log.featured.containsKey(m.id)))log.finals.put(m.id,m);}catch(JSONException ignored){}log.prune(now);long checked=j.optLong("checkedAt",0);boolean version=j.optInt("version",0)==CACHE_VERSION&&j.optInt("days",0)==DAYS&&zone().getID().equals(j.optString("zone",""));log.partial=j.optBoolean("partial",false);log.checkedAt=version&&fresh(checked,now)&&!log.partial?checked:0;return log;}
    static final class Page {final java.util.List<CsMatch> matches=new ArrayList<>();boolean more;long firstEnd,lastEnd;}
    static Page page(String body,int offset,long now,CsPolicy policy,Set<Long> featured)throws Exception{
        return parsePage(body,offset,now,policy,featured,1);
    }
    static Page windowPage(String body,int offset,long now,CsPolicy policy,Set<Long> featured)throws Exception{return parsePage(body,offset,now,policy,featured,DAYS);}
    static Page parsePage(String body,int offset,long now,CsPolicy policy,Set<Long> featured,int days)throws Exception{
        JSONObject root=new JSONObject(body);JSONArray rows=root.getJSONArray("results");int total=root.getJSONObject("total").getInt("count");if(total<0||rows.length()>100||rows.length()==0&&total>offset)throw new java.io.IOException("Result structure changed");
        Page page=new Page();long last=Long.MAX_VALUE,oldest=Long.MAX_VALUE,start=day(now,1-days);int dated=0,valid=0;
        for(int i=0;i<rows.length();i++){
            JSONObject row=rows.optJSONObject(i);if(row==null)continue;long end=CsMatch.time(row.optString("end_date",""));if(end>0){if(end>last)throw new java.io.IOException("Result ordering changed");if(page.firstEnd==0)page.firstEnd=end;last=end;oldest=Math.min(oldest,end);dated++;}
            try{CsMatch m=CsMatch.read(row);if("finished".equals(m.status))valid++;if(finished(m,now,start)&&(policy.accepts(m,m.eventId)||featured.contains(m.id)))page.matches.add(m);}catch(JSONException ignored){}
        }
        if(rows.length()>0&&(dated==0||valid==0))throw new java.io.IOException("Result structure changed");
        page.lastEnd=dated==0?0:last;page.more=rows.length()==100&&total>offset+rows.length()&&oldest>=start;return page;
    }
}

