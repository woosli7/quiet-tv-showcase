package com.quiettv.home;
import java.util.*;
import org.json.*;

/** Small, durable daily results ledger. A final result always requires a source-finished record. */
final class CsResults {
    
    static final int MAX_RESULTS=64,MAX_FEATURED=128;
    final LinkedHashMap<Long,CsMatch> finals=new LinkedHashMap<>();
    final LinkedHashMap<Long,Long> featured=new LinkedHashMap<>();
    long checkedAt;
    static long dayStart(long now){Calendar c=Calendar.getInstance(TvProfile.zone());c.setTimeInMillis(now);c.set(Calendar.HOUR_OF_DAY,0);c.set(Calendar.MINUTE,0);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);return c.getTimeInMillis();}
    static long dayEnd(long now){Calendar c=Calendar.getInstance(TvProfile.zone());c.setTimeInMillis(dayStart(now));c.add(Calendar.DATE,1);return c.getTimeInMillis();}
    static boolean today(long ended,long now){return ended>=dayStart(now)&&ended<=now;}
    static boolean finished(CsMatch m,long now){return finished(m,now,dayStart(now));}
    static boolean finished(CsMatch m,long now,long start){return "finished".equals(m.status)&&m.ended>0&&m.ended>=m.start&&m.ended>=start&&m.ended<=now;}
    void prune(long now){long start=dayStart(now);finals.values().removeIf(m->!finished(m,now,start));featured.entrySet().removeIf(e->e.getValue()>now||now-e.getValue()>48*3600000L);while(featured.size()>MAX_FEATURED)featured.remove(featured.keySet().iterator().next());}
    void remember(java.util.List<CsMatch> rows,long now){prune(now);for(CsMatch m:rows){featured.remove(m.id);featured.put(m.id,now);}prune(now);}
    Set<Long> featuredIds(long now){prune(now);return new HashSet<>(featured.keySet());}
    int count(long now){long start=dayStart(now);int count=0;for(CsMatch m:finals.values())if(finished(m,now,start))count++;return count;}
    java.util.List<CsMatch> rows(long now){long start=dayStart(now);ArrayList<CsMatch> out=new ArrayList<>();for(CsMatch m:finals.values())if(finished(m,now,start))out.add(m);out.sort((a,b)->Long.compare(b.ended,a.ended));return out;}
    void merge(java.util.List<CsMatch> incoming,long now){prune(now);for(CsMatch m:incoming)if(finished(m,now))finals.put(m.id,m);java.util.List<CsMatch> ordered=rows(now);for(int i=MAX_RESULTS;i<ordered.size();i++)finals.remove(ordered.get(i).id);}
    JSONObject cache(long now)throws JSONException{prune(now);JSONObject j=new JSONObject().put("checkedAt",checkedAt).put("zone",TvProfile.zone().getID());JSONArray a=new JSONArray(),ids=new JSONArray();for(CsMatch m:rows(now))a.put(m.cache());for(Map.Entry<Long,Long> e:featured.entrySet())ids.put(new JSONObject().put("id",e.getKey()).put("seen",e.getValue()));return j.put("results",a).put("featured",ids);}
    static CsResults restore(JSONObject j,CsPolicy policy,long now){CsResults log=new CsResults();JSONArray ids=j.optJSONArray("featured");if(ids!=null)for(int i=0;i<Math.min(MAX_FEATURED,ids.length());i++){JSONObject v=ids.optJSONObject(i);if(v!=null&&v.optLong("id",0)>0)log.featured.put(v.optLong("id"),v.optLong("seen",0));}log.prune(now);JSONArray a=j.optJSONArray("results");if(a!=null)for(int i=0;i<Math.min(MAX_RESULTS,a.length());i++)try{CsMatch m=CsMatch.read(a.getJSONObject(i));if(finished(m,now)&&(policy.accepts(m,m.eventId)||log.featured.containsKey(m.id)))log.finals.put(m.id,m);}catch(Exception ignored){}long checked=j.optLong("checkedAt",0);log.checkedAt=checked>0&&today(checked,now)&&TvProfile.zone().getID().equals(j.optString("zone",""))?checked:0;return log;}
    static final class Page {final java.util.List<CsMatch> matches=new ArrayList<>();boolean more;}
    static Page page(String body,int offset,long now,CsPolicy policy,Set<Long> featured)throws Exception{
        JSONObject root=new JSONObject(body);JSONArray rows=root.getJSONArray("results");int total=root.getJSONObject("total").getInt("count");if(total<0||rows.length()>100||rows.length()==0&&total>offset)throw new java.io.IOException("Result structure changed");
        Page page=new Page();long last=Long.MAX_VALUE,oldest=Long.MAX_VALUE,start=dayStart(now);int dated=0,valid=0;
        for(int i=0;i<rows.length();i++){
            JSONObject row=rows.optJSONObject(i);if(row==null)continue;long end=CsMatch.time(row.optString("end_date",""));if(end>0){if(end>last)throw new java.io.IOException("Result ordering changed");last=end;oldest=Math.min(oldest,end);dated++;}
            try{CsMatch m=CsMatch.read(row);if("finished".equals(m.status))valid++;if(finished(m,now,start)&&(policy.accepts(m,m.eventId)||featured.contains(m.id)))page.matches.add(m);}catch(JSONException ignored){}
        }
        if(rows.length()>0&&(dated==0||valid==0))throw new java.io.IOException("Result structure changed");
        page.more=rows.length()==100&&total>offset+rows.length()&&oldest>=dayStart(now);return page;
    }
}
