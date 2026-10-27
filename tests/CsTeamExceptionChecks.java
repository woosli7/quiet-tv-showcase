package com.quiettv.home;
import java.util.*;
import org.json.*;

/** Team-only eligibility must never become event-wide eligibility. */
public final class CsTeamExceptionChecks {
    static int count;
    static void ok(boolean value,String label){if(!value)throw new AssertionError(label);count++;System.out.println("PASS "+label);}
    static CsMatch match(long id,long a,long b,String status,long now){CsMatch m=new CsMatch();m.id=id;m.slug="test-"+id;m.a=a==791?"FaZe":"Other A";m.b=b==791?"FaZe":"Other B";m.teamIdA=a;m.teamIdB=b;m.event="Tesla Cup 2026";m.eventId=6218;m.tier=3;m.prize=50000;m.status=status;m.start=now+3600000;m.bo=3;return m;}
    public static void main(String[] args)throws Exception{
        long now=CsMatch.time("2026-10-26T20:00:00Z");JSONObject config=new JSONObject().put("include_team_ids",new JSONArray().put(791));CsPolicy policy=new CsPolicy(config),normal=new CsPolicy(new JSONObject());
        CsMatch a=match(1,791,8221,"upcoming",now),b=match(2,8221,791,"upcoming",now),other=match(3,100,200,"upcoming",now);
        ok(policy.accepts(a,6218)&&policy.accepts(b,6218),"main FaZe roster included on either side in smaller event");
        ok(!policy.accepts(other,6218),"other matches in the same Tesla Cup stay excluded");
        ok(!normal.accepts(a,6218),"generic shared defaults do not enable a personal team exception");
        a.event="Small Open Qualifier";ok(policy.accepts(a,6218),"main roster exception applies even to qualifiers");
        a.teamIdA=999;a.a="FaZe Academy";ok(!policy.accepts(a,6218),"academy with a different ID cannot inherit the exception");
        a.a="FaZe";ok(!policy.accepts(a,6218),"same display name with different ID is not trusted");
        a.teamIdA=0;ok(!policy.accepts(a,6218),"missing identity never bypasses the event filter");
        a.teamIdA=791;a.a="FaZe Clan";ok(policy.accepts(a,6218),"verified ID survives main-roster display-name changes");
        CsPolicy blocked=new CsPolicy(new JSONObject(config.toString()).put("exclude_event_ids",new JSONArray().put(6218)));ok(!blocked.accepts(a,6218),"explicit event block still wins over team exception");
        CsPolicy invalid=new CsPolicy(new JSONObject().put("include_team_ids",new JSONArray().put(0).put(-1).put("invalid")));ok(!invalid.accepts(b,6218),"invalid team IDs cannot grant eligibility");
        other.event="PGL Major 2026";other.tier=1;ok(policy.accepts(other,6218),"normal top-event eligibility still works");
        other.event="Academy qualifier";ok(!policy.accepts(other,6218),"unrelated excluded events remain excluded");
        a=match(1,791,8221,"upcoming",now);other=match(3,100,200,"upcoming",now);
        List<CsMatch> schedule=CsSchedule.rows(Arrays.asList(other,a,b),now,policy);ok(schedule.size()==2,"upcoming schedule admits only FaZe fixtures from smaller event");
        CsMatch live=match(4,791,8221,"current",now);live.start=now-60000;ok(policy.live(live,now),"only explicit source-current FaZe fixture qualifies live");ok(!policy.live(a,now),"upcoming FaZe match is never promoted to LIVE");
        live.ended=now;ok(!policy.live(live,now),"finished FaZe match cannot stay LIVE");
        a.status="finished";a.start=now-7200000;a.ended=now-3600000;a.one=2;a.two=0;
        other.status="finished";other.start=a.start;other.ended=a.ended;
        JSONObject results=new JSONObject().put("results",new JSONArray().put(a.cache()).put(other.cache())).put("total",new JSONObject().put("count",2));
        CsResults.Page page=CsResults.windowPage(results.toString(),0,now,policy,Collections.emptySet());ok(page.matches.size()==1&&page.matches.get(0).id==a.id,"past matches also use the same team-only rule");
        CsResults log=new CsResults();log.merge(page.matches,now);ok(CsResults.restore(log.cache(now),policy,now).count(now)==1,"FaZe results survive normal bounded cache restore");
        CsMatch major1=match(10,100,200,"current",now),major2=match(11,300,400,"current",now);CsFavorites favorites=new CsFavorites("[]");favorites.set(100,"Other A",true);favorites.set(300,"Other A",true);
        List<CsMatch> rows=Arrays.asList(major1,major2,b);List<CsMatch> featured=favorites.prioritize(rows,policy.cards);
        ok(featured.size()==2&&featured.get(0)==major1&&featured.get(1)==major2,"FaZe exception grants no bonus over normal favorites or two-card limit");
        ok(policy.rank(b)==normal.rank(b),"FaZe has the same tournament ranking with or without eligibility exception");
        ok(rows.get(0)==major1,"normal selection does not mutate source records");
        ok(new CsFavorites("[]").prioritize(Arrays.asList(b,major1),2).get(0)==b,"FaZe retains its ordinary position without forced promotion or demotion");
        if(args.length>1){JSONObject registry=new JSONObject(new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(args[0])),"UTF-8"));JSONObject detail=new JSONObject(new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(args[1])),"UTF-8"));CsMatch actual=CsMatch.read(detail);ok(actual.teamIdB==791&&policy.accepts(actual,actual.eventId),"real Alliance FaZe fixture is accepted by verified provider ID");List<CsStream> streams=new CsBroadcasts(registry).select(CsStream.read(detail,actual.id),actual.event,Collections.emptyMap());ok(streams.size()==1&&streams.get(0).url.equals("https://kick.com/rlgcs")&&streams.get(0).english(),"actual match broadcast resolves reviewed English RLG channel");ok(!policy.live(actual,System.currentTimeMillis()),"real scheduled fixture remains upcoming");}
        System.out.println("COMPLETE: "+count+" team exception checks passed");
    }
}
