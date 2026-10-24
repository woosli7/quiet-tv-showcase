package com.quiettv.home;
import java.util.*;
import org.json.*;

/** Regression for the home card sharing today's bounded upcoming schedule. */
public class CsNextChecks {
    static int count;
    static void ok(boolean value,String label){if(!value)throw new AssertionError(label);count++;System.out.println("PASS "+label);}
    static CsMatch match(long id,String date,String status)throws Exception{return CsMatch.read(new JSONObject().put("id",id).put("slug","test-"+id).put("discipline_id",1).put("status",status).put("start_date",date).put("tournament",new JSONObject().put("id",1).put("name","PGL Test").put("tier_rank",1)).put("team1",new JSONObject().put("name","A")).put("team2",new JSONObject().put("name","B")));}
    public static void main(String[] args)throws Exception{
        long now=CsMatch.time("2026-10-24T20:14:00Z");CsPolicy policy=new CsPolicy(new JSONObject());
        CsMatch today=match(1,"2026-10-24T23:00:00Z","upcoming"),earlier=match(2,"2026-10-24T22:00:00Z","upcoming"),tuesday=match(3,"2026-10-27T11:00:00Z","upcoming");
        ok(CsSchedule.next(Arrays.asList(tuesday),now,now,policy)==null,"Tuesday match cannot be Saturday's next card");
        ok(CsSchedule.next(Arrays.asList(tuesday,today,earlier),now,now,policy).id==2,"next card chooses earliest eligible match today");
        ok(CsSchedule.next(Arrays.asList(today),now-1799999,now,policy)!=null,"fresh today schedule remains available");
        ok(CsSchedule.next(Arrays.asList(today),now-1800000,now,policy)==null,"schedule expires at thirty minutes");
        ok(CsSchedule.next(Arrays.asList(today),now+1,now,policy)==null,"clock rollback cannot revive cache");
        ok(CsSchedule.next(Arrays.asList(today),0,now,policy)==null,"unchecked cache cannot supply next card");
        ok(CsSchedule.next(Collections.emptyList(),now,now,policy)==null,"empty today schedule has no next card");
        ok(CsSchedule.next(Arrays.asList(today),now, today.start,policy)==null,"match disappears from next at scheduled start");
        CsMatch live=match(4,"2026-10-24T23:00:00Z","current"),finished=match(5,"2026-10-24T23:00:00Z","finished");
        ok(CsSchedule.next(Arrays.asList(live,finished),now,now,policy)==null,"only source-upcoming records qualify");
        CsMatch low=match(6,"2026-10-24T21:00:00Z","upcoming");low.event="Random Academy Qualifier";low.tier=4;
        ok(CsSchedule.next(Arrays.asList(low,today),now,now,policy).id==1,"home card keeps prominence filtering");
        long midnight=CsResults.dayStart(now)+86400000L;CsMatch boundary=match(7,CsMatch.stamp(midnight),"upcoming"),last=match(8,CsMatch.stamp(midnight-1),"upcoming");
        ok(CsSchedule.next(Arrays.asList(boundary),now,now,policy)==null,"next local midnight is excluded");
        ok(CsSchedule.next(Arrays.asList(last),now,now,policy)!=null,"last instant of today remains eligible");
        long later=midnight+1;ok(CsSchedule.next(Arrays.asList(last),later,later,policy)==null,"new day cannot resurrect previous day's card");
        ok(CsSchedule.next(Arrays.asList(today,earlier,today),now,now,policy).id==CsSchedule.rows(Arrays.asList(today,earlier,today),now,policy).get(0).id,"home and Upcoming Matches agree");
        System.out.println("COMPLETE: "+count+" next-today checks passed");
    }
}
