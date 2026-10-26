package com.quiettv.home;
import java.util.*;
import java.nio.file.*;
import org.json.*;

/** Rolling history, cache coverage, and source-state regressions. */
public class CsPastChecks {
    static int count;
    static void ok(boolean value,String label){if(!value)throw new AssertionError(label);count++;System.out.println("PASS "+label);}
    static CsMatch match(long id,long end)throws Exception{return CsMatch.read(new JSONObject().put("id",id).put("slug","test-"+id).put("discipline_id",1).put("status","finished").put("start_date",CsMatch.stamp(end-3600000)).put("end_date",CsMatch.stamp(end)).put("bo_type",3).put("team1_score",2).put("team2_score",0).put("tournament",new JSONObject().put("id",1).put("name","PGL Test 2026").put("tier_rank",1)).put("team1",new JSONObject().put("name","A")).put("team2",new JSONObject().put("name","B")));}
    static JSONObject page(List<CsMatch> rows,int total)throws Exception{JSONArray a=new JSONArray();for(CsMatch m:rows)a.put(m.cache());return new JSONObject().put("results",a).put("total",new JSONObject().put("count",total));}
    public static void main(String[] args)throws Exception{
        TimeZone.setDefault(TimeZone.getTimeZone("America/Phoenix"));
        long now=CsMatch.time("2026-10-26T03:00:00Z"),today=CsResults.day(now,0),floor=CsResults.day(now,-3);CsPolicy policy=new CsPolicy(new JSONObject());
        CsMatch a=match(1,now-1000),b=match(2,today-1000),c=match(3,CsResults.day(now,-1)-1000),d=match(4,floor),old=match(5,floor-1);CsResults log=new CsResults();log.merge(Arrays.asList(a,b,c,d,old,a),now);
        ok(CsResults.DAYS==4,"today plus three previous dates");
        ok(log.totalCount(now)==4,"window keeps four days and deduplicates");
        ok(log.rows(now).size()==1&&log.rows(now).get(0).id==1,"today is isolated");
        ok(log.rowsForDay(now,CsResults.day(now,-1)).get(0).id==2,"yesterday can be selected");
        ok(log.rowsForDay(now,CsResults.day(now,-2)).get(0).id==3,"two days back can be selected");
        ok(log.rowsForDay(now,floor).get(0).id==4,"oldest local midnight is inclusive");
        ok(log.rowsForDay(now,CsResults.day(now,-4)).isEmpty(),"outside dates cannot leak into a day");
        ok(!CsResults.inWindow(old,now),"instant before oldest date excluded");
        ok(CsResults.browseDay(today,1,now)==today,"cannot navigate into future");
        ok(CsResults.browseDay(floor,-1,now)==floor,"cannot navigate beyond retained history");
        ok(CsResults.browseDay(today,-1,now)==CsResults.day(now,-1),"left browses previous calendar day");
        ok(CsResults.browseDay(floor,1,now)==CsResults.day(now,-2),"right browses next calendar day");
        CsMatch live=match(6,now-1);live.status="current";CsMatch future=match(7,now+1000),missing=match(8,now-1);missing.ended=0;log.merge(Arrays.asList(live,future,missing),now);ok(log.totalCount(now)==4,"live future and missing finish excluded");
        CsMatch overnight=match(9,today+1000);CsResults overnightLog=new CsResults();overnightLog.merge(Collections.singletonList(overnight),now);ok(overnightLog.rows(now).size()==1&&overnightLog.rowsForDay(now,today-1).isEmpty(),"overnight result belongs to finish day");
        log.checkedAt=now;JSONObject saved=log.cache(now);CsResults restored=CsResults.restore(saved,policy,now);ok(restored.totalCount(now)==4&&restored.checkedAt==now,"whole history and coverage survive restart");
        JSONObject legacy=new JSONObject(saved.toString());legacy.remove("version");ok(CsResults.restore(legacy,policy,now).checkedAt==0&&CsResults.restore(legacy,policy,now).totalCount(now)==4,"legacy cache keeps finals but forces full history check");
        JSONObject zone=new JSONObject(saved.toString()).put("zone","Different/Zone");ok(CsResults.restore(zone,policy,now).checkedAt==0,"timezone change invalidates checked coverage");
        JSONObject partial=new JSONObject(saved.toString()).put("partial",true);CsResults partialLog=CsResults.restore(partial,policy,now);ok(partialLog.partial&&partialLog.checkedAt==0&&partialLog.totalCount(now)==4,"incomplete coverage survives restart honestly");
        ok(CsResults.fresh(now,now)&&!CsResults.fresh(now-1800000,now),"checked coverage expires at thirty minutes");
        ok(!CsResults.fresh(now+1,now),"clock rollback invalidates coverage");
        long next=CsResults.day(now,1);ok(!CsResults.fresh(now,next),"midnight invalidates old coverage");
        ok(log.rows(next).isEmpty()&&log.rowsForDay(next,today).size()==1,"midnight moves today into yesterday without loss");
        log.prune(next);ok(log.totalCount(next)==3,"oldest day rolls out at midnight");
        log.merge(Collections.emptyList(),next);ok(log.totalCount(next)==3,"empty refresh or failure cannot erase confirmed finals");
        CsResults many=new CsResults();ArrayList<CsMatch> batch=new ArrayList<>();for(int day=0;day<4;day++)for(int n=0;n<70;n++)batch.add(match(100+day*100+n,CsResults.day(now,-day)+3600000+n*1000));ok(CsResults.limited(batch,now),"per-day overflow marks coverage incomplete");many.merge(batch,now);ok(many.totalCount(now)==256&&many.rows(now).size()==64,"retention bounded per day and across window");
        ok(many.cache(now).toString().getBytes("UTF-8").length<CsResults.MAX_CACHE_BYTES,"maximum normalized history fits cache bound");
        List<CsMatch> ordered=Arrays.asList(a,b,c,d,old);CsResults.Page parsed=CsResults.windowPage(page(ordered,5).toString(),0,now,policy,Collections.emptySet());ok(parsed.matches.size()==4&&!parsed.more,"page groups all retained days and stops beyond boundary");
        CsMatch low=match(10,now-1000);low.tier=4;ok(CsResults.windowPage(page(Arrays.asList(low),1).toString(),0,now,policy,Collections.emptySet()).matches.isEmpty(),"obscure events remain excluded");
        ok(CsResults.windowPage(page(Arrays.asList(low),1).toString(),0,now,policy,Collections.singleton(10L)).matches.size()==1,"previously featured identity remains eligible");
        boolean failed=false;try{CsResults.windowPage(page(Arrays.asList(b,a),2).toString(),0,now,policy,Collections.emptySet());}catch(Exception e){failed=true;}ok(failed,"changed result order fails closed");
        failed=false;try{CsResults.windowPage("{\"results\":[],\"total\":{\"count\":1}}",0,now,policy,Collections.emptySet());}catch(Exception e){failed=true;}ok(failed,"unexpected empty page cannot imply no matches");
        ArrayList<CsMatch> hundred=new ArrayList<>();for(int n=0;n<100;n++)hundred.add(match(500+n,now-1000-n));parsed=CsResults.windowPage(page(hundred,200).toString(),0,now,policy,Collections.emptySet());ok(parsed.more,"pagination continues within history window");
        ok(parsed.firstEnd>=parsed.lastEnd,"page exposes order boundaries for cross-page validation");
        long year=CsMatch.time("2027-01-01T12:00:00Z");ok(CsResults.day(year,-1)==CsMatch.time("2026-12-31T07:00:00Z"),"year boundary uses local calendar");
        long month=CsMatch.time("2026-11-01T12:00:00Z");ok(CsResults.day(month,-1)==CsMatch.time("2026-10-31T07:00:00Z"),"month boundary uses local calendar");
        if(args.length>0){String body=new String(Files.readAllBytes(Paths.get(args[0])),"UTF-8");parsed=CsResults.windowPage(body,0,now,policy,Collections.emptySet());HashSet<Long> dates=new HashSet<>();for(CsMatch m:parsed.matches)dates.add(CsResults.day(m.ended,0));ok(dates.size()>=2,"real provider page backfills more than one date");}
        System.out.println("COMPLETE: "+count+" past-match checks passed");
    }
}
