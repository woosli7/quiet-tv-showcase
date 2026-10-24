package com.quiettv.home;
import java.util.*;
import org.json.*;

/** Deterministic upcoming-day, cache-migration, and today-card regressions. */
public class CsScheduleDaysChecks {
    static int count;
    static void ok(boolean yes,String label){if(!yes)throw new AssertionError(label);count++;System.out.println("PASS "+label);}
    static CsMatch row(long id,long start,String status)throws Exception{return CsMatch.read(json(id,start,status));}
    static JSONObject json(long id,long start,String status)throws Exception{return new JSONObject().put("id",id).put("slug","schedule-"+id).put("discipline_id",1).put("status",status).put("start_date",CsMatch.stamp(start)).put("tournament",new JSONObject().put("id",1).put("name","PGL Test").put("tier_rank",1)).put("team1",new JSONObject().put("name","A")).put("team2",new JSONObject().put("name","B"));}
    static String page(JSONArray a,int total)throws Exception{return new JSONObject().put("results",a).put("total",new JSONObject().put("count",total)).toString();}
    public static void main(String[] args)throws Exception{
        long now=CsMatch.time("2026-10-24T20:14:00Z"),today=CsSchedule.day(now,0);CsPolicy p=new CsPolicy(new JSONObject());
        CsMatch a=row(1,now+1000,"upcoming"),b=row(2,CsSchedule.day(now,1)+12*3600000L,"upcoming"),tuesday=row(3,CsSchedule.day(now,3)+12*3600000L,"upcoming"),last=row(4,CsSchedule.day(now,7)-1,"upcoming"),outside=row(5,CsSchedule.day(now,7),"upcoming");
        java.util.List<CsMatch> all=Arrays.asList(tuesday,b,a,last,outside);
        ok(CsSchedule.rows(all,now,p).size()==1&&CsSchedule.rows(all,now,p).get(0).id==1,"home/today list excludes following days");
        ok(CsSchedule.rowsForDay(all,now,p,CsSchedule.day(now,1)).get(0).id==2,"tomorrow has its own match list");
        ok(CsSchedule.rowsForDay(all,now,p,CsSchedule.day(now,3)).get(0).id==3,"Tuesday can be browsed from Saturday");
        ok(CsSchedule.rowsForDay(all,now,p,CsSchedule.day(now,2)).isEmpty(),"empty day stays empty rather than borrowing another day");
        ok(CsSchedule.windowRows(all,now,p).size()==4,"seven-day window excludes the next week's boundary");
        ok(CsSchedule.inWindow(last,now)&&!CsSchedule.inWindow(outside,now),"window end is exclusive");
        ok(!CsSchedule.inWindow(row(6,now,"upcoming"),now),"scheduled start that has arrived leaves upcoming");
        ok(!CsSchedule.inWindow(row(7,now+1000,"current"),now)&&!CsSchedule.inWindow(row(8,now+1000,"finished"),now),"live and final records never become future schedule cards");
        CsMatch ended=row(9,now+1000,"upcoming");ended.ended=now;ok(!CsSchedule.inWindow(ended,now),"ended record is excluded even if source status lags");
        ok(CsSchedule.browseDay(today,-1,now)==today,"left arrow stops at today");
        ok(CsSchedule.browseDay(today,1,now)==CsSchedule.day(now,1),"right arrow selects tomorrow");
        ok(CsSchedule.browseDay(CsSchedule.day(now,6),1,now)==CsSchedule.day(now,6),"right arrow stops at final supported day");
        ok(CsSchedule.browseDay(CsSchedule.day(now,3),-1,now)==CsSchedule.day(now,2),"left arrow returns to preceding date");
        ok(CsSchedule.browseDay(today,0,CsSchedule.day(now,1)+1)==CsSchedule.day(now,1),"midnight clamps an open past page to the new today");
        ok(CsSchedule.next(Arrays.asList(tuesday),now,now,p)==null,"browsable Tuesday never leaks into Next today");
        ok(CsSchedule.next(all,now,now,p).id==1,"Next today still uses earliest eligible current-day match");
        ok(CsSchedule.fresh(now,now+1799999)&&!CsSchedule.fresh(now,now+1800000),"cached week expires at thirty minutes");
        ok(!CsSchedule.fresh(now+1,now)&&!CsSchedule.fresh(0,now),"future or unchecked cache timestamps fail closed");
        long midnight=CsSchedule.day(now,1);ok(!CsSchedule.fresh(midnight-1,midnight),"a previous-day window cannot claim coverage of the new final day");
        JSONObject cache=CsSchedule.cache(CsSchedule.windowRows(all,now,p),now,CsSchedule.zone().getID());
        ok(CsSchedule.restoredAt(cache,now)==now,"versioned week cache restores its check time");
        ok(CsSchedule.restoreRows(cache,now,p).size()==4,"future dates survive cache round trip");
        ok(CsSchedule.restoredAt(new JSONObject(cache.toString()).put("version",1),now)==0,"old today-only cache requires a new weekly fetch");
        ok(CsSchedule.restoredAt(new JSONObject(cache.toString()).put("days",1),now)==0,"one-day coverage cannot masquerade as a checked week");
        ok(CsSchedule.restoredAt(new JSONObject(cache.toString()).put("zone","Invalid/Zone"),now)==0,"timezone change invalidates checked coverage");
        ok(CsSchedule.restoredAt(cache,now+1800000)==0,"expired persisted schedule cannot claim checked empty days");
        ArrayList<CsMatch> many=new ArrayList<>();for(int day=0;day<7;day++)for(int i=0;i<70;i++)many.add(row(100+day*70+i,CsSchedule.day(now,day)+18*3600000L+i*1000,"upcoming"));
        java.util.List<CsMatch> bounded=CsSchedule.windowRows(many,now,p);ok(bounded.size()<=256&&CsSchedule.limited(many,now,p),"weekly retention is bounded and truncation is reported");
        ok(CsSchedule.rowsForDay(bounded,now,p,today).size()<=64,"each day's list is bounded to sixty-four matches");
        ok(!CsSchedule.limited(Arrays.asList(a,a,b),now,p),"duplicates alone do not mark a complete schedule as partial");
        CsMatch low=row(999,now+2000,"upcoming");low.event="Academy qualifier";low.tier=4;ok(CsSchedule.windowRows(Arrays.asList(low,tuesday),now,p).size()==1,"later dates retain the tournament prominence rules");
        JSONArray future=new JSONArray().put(json(1,now+1000,"upcoming")).put(json(3,tuesday.start,"upcoming"));
        ok(CsSchedule.windowPage(page(future,2),0,now,p).matches.size()==2,"window parser retains future days from source-upcoming records");
        ok(CsSchedule.page(page(future,2),0,now,p).matches.size()==1,"single-day parser remains compatible");
        JSONArray full=new JSONArray();for(int i=0;i<100;i++)full.put(json(1000+i,now+1000+i*1000,"upcoming"));full.put(99,json(1099,b.start,"upcoming"));
        ok(CsSchedule.windowPage(page(full,200),0,now,p).more,"weekly scan continues after reaching tomorrow");
        full.put(99,json(1099,outside.start,"upcoming"));ok(!CsSchedule.windowPage(page(full,200),0,now,p).more,"weekly scan stops after reaching its calendar horizon");
        boolean rejected=false;try{CsSchedule.windowPage("<html>blocked</html>",0,now,p);}catch(Exception e){rejected=true;}ok(rejected,"changed or blocked response cannot become a false empty schedule");
        long year=CsMatch.time("2026-12-31T20:00:00Z");Calendar c=Calendar.getInstance(CsSchedule.zone());c.setTimeInMillis(CsSchedule.day(year,1));ok(c.get(Calendar.YEAR)==2027&&c.get(Calendar.MONTH)==Calendar.JANUARY&&c.get(Calendar.DAY_OF_MONTH)==1,"day navigation crosses month/year boundaries");
        System.out.println("COMPLETE: "+count+" upcoming-day checks passed");
    }
}
