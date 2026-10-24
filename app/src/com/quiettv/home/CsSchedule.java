package com.quiettv.home;
import java.util.*;
import org.json.*;

/** Bounded, source-confirmed schedule for the current local day. */
final class CsSchedule {
    static final int MAX_ROWS=64;
    static CsMatch next(Collection<CsMatch> source,long checkedAt,long now,CsPolicy policy){if(!CsPolicy.fresh(checkedAt,now,1800000))return null;java.util.List<CsMatch> today=rows(source,now,policy);return today.isEmpty()?null:today.get(0);}
    static boolean today(CsMatch m,long now){return "upcoming".equals(m.status)&&m.ended==0&&m.start>now&&m.start<CsResults.dayEnd(now);}
    static java.util.List<CsMatch> rows(Collection<CsMatch> source,long now,CsPolicy policy){
        LinkedHashMap<Long,CsMatch> unique=new LinkedHashMap<>();for(CsMatch m:source)if(today(m,now)&&policy.accepts(m,m.eventId))unique.put(m.id,m);
        ArrayList<CsMatch> out=new ArrayList<>(unique.values());out.sort((a,b)->a.start==b.start?Long.compare(policy.rank(b),policy.rank(a)):Long.compare(a.start,b.start));return new ArrayList<>(out.subList(0,Math.min(MAX_ROWS,out.size())));
    }
    static final class Page {final java.util.List<CsMatch> matches=new ArrayList<>();boolean more;}
    static Page page(String body,int offset,long now,CsPolicy policy)throws Exception{
        JSONObject root=new JSONObject(body);JSONArray rows=root.getJSONArray("results");int total=root.getJSONObject("total").getInt("count");if(total<0||rows.length()>100||rows.length()==0&&total>offset)throw new java.io.IOException("Schedule structure changed");
        Page page=new Page();long last=0,latest=0;int dated=0,valid=0;
        for(int i=0;i<rows.length();i++){
            JSONObject row=rows.optJSONObject(i);if(row==null)continue;long start=CsMatch.time(row.optString("start_date",""));if(start>0){if(start<last)throw new java.io.IOException("Schedule ordering changed");last=start;latest=start;dated++;}
            try{CsMatch m=CsMatch.read(row);if("upcoming".equals(m.status))valid++;if(today(m,now)&&policy.accepts(m,m.eventId))page.matches.add(m);}catch(JSONException ignored){}
        }
        if(rows.length()>0&&(dated==0||valid==0))throw new java.io.IOException("Schedule structure changed");
        page.more=rows.length()==100&&total>offset+rows.length()&&latest<CsResults.dayEnd(now);return page;
    }
}
