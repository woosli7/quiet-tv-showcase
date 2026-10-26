package com.quiettv.home;
import java.util.*;
import org.json.*;

public class CsFavoriteChecks {
    static int count;
    static void ok(boolean b,String label){if(!b)throw new AssertionError(label);count++;System.out.println("PASS "+label);}
    static CsMatch row(long id,long team,String a,String b){CsMatch m=new CsMatch();m.id=id;m.teamIdA=team;m.teamIdB=100+id;m.a=a;m.b=b;m.event="PGL Test";m.tier=1;return m;}
    public static void main(String[] args)throws Exception{
        CsFavorites f=new CsFavorites("[]");ok(f.empty(),"no teams followed by default");
        ok(f.set(10,"Natus Vincere",true),"follow known team");ok(f.contains(10,"NAVI"),"stable team ID survives rename");
        ok(!f.contains(11,"Natus Vincere"),"different known IDs cannot collide by display name");
        ok(f.contains(0,"NAVI"),"legacy ID-less records recognize reviewed alias");
        ok(!f.contains(0,"NAVI Junior"),"academy name cannot match by substring");
        ok(!f.contains(0,"NAVI Javelins"),"separate roster cannot match by prefix");
        ok(!f.contains(0,"NaViGators"),"unrelated substring is excluded");
        CsFavorites legacy=new CsFavorites("[{\"name\":\"NAVI\"}]");ok(legacy.contains(10,"Natus Vincere"),"saved name-only favorite works with fresh ID-bearing data");
        ok(f.set(10,"NAVI",true)&&f.teams.size()==1,"following again does not duplicate");
        ok(new CsFavorites(f.save()).contains(10,"Renamed"),"favorite survives serialization");
        int revision=f.revision;ok(f.set(10,"NAVI",false)&&f.empty()&&f.revision>revision,"unfollow updates indexes and revision");
        ok(f.set(10,"NAVI",false)&&f.empty(),"unfollow is idempotent");
        ok(!f.set(0,"TBD",true)&&!f.set(0,"",true)&&!f.set(-1,"NAVI",true),"placeholder empty and invalid identities rejected");
        ok(new CsFavorites("not json").empty(),"corrupt settings fall back to no favorites");
        ok(new CsFavorites("[{},null,{\"id\":-3,\"name\":\"bad\"}]").empty(),"malformed entries ignored");
        char[] huge=new char[CsFavorites.MAX_BYTES+1];Arrays.fill(huge,'x');ok(new CsFavorites(new String(huge)).empty(),"oversized preference rejected");
        f.set(10,"NAVI",true);CsMatch a=row(1,20,"Vitality","Spirit"),b=row(2,30,"G2","Aurora"),c=row(3,10,"Natus Vincere","MOUZ"),d=row(4,40,"FUT","Liquid");List<CsMatch> rows=Arrays.asList(a,b,c,d);
        ok(f.match(c)&&!f.match(a),"favorite membership covers either side of match");
        CsMatch otherSide=row(5,50,"MOUZ","NAVI");otherSide.teamIdB=10;ok(f.match(otherSide),"opponent-side identity recognized");
        List<CsMatch> live=f.prioritize(rows,2);ok(live.size()==2&&live.get(0).id==3&&live.get(1).id==1,"favorite outside original two cards is promoted before cap");
        ok(rows.get(0).id==1&&rows.get(2).id==3,"ranking does not mutate source order");
        f.set(40,"FUT",true);live=f.prioritize(rows,2);ok(live.get(0).id==3&&live.get(1).id==4,"two favorites preserve original tournament ranking");
        ok(f.prioritize(rows,0).isEmpty(),"zero card budget remains empty");
        ok(new CsFavorites("[]").prioritize(rows,2).get(0).id==1,"no favorites preserves original live priority");
        ok(f.filter(rows,false)==rows,"All matches preserves chronological list without copy");
        List<CsMatch> mine=f.filter(rows,true);ok(mine.size()==2&&mine.get(0).id==3&&mine.get(1).id==4,"My teams filters without changing date order");
        ok(new CsFavorites("[]").filter(rows,true).isEmpty(),"My teams with no favorites is empty");
        ok(f.filter(Collections.emptyList(),true).isEmpty(),"empty date stays empty");
        List<CsFavorites.Team> catalog=f.available(rows);ok(catalog.stream().anyMatch(t->t.id==10)&&catalog.size()<=96,"manager includes current teams and saved favorites");
        ok(legacy.available(Collections.singletonList(row(1,10,"Natus Vincere","MOUZ"))).size()==2,"fresh IDs do not duplicate legacy favorite in manager");
        CsFavorites capped=new CsFavorites("[]");for(int i=1;i<=32;i++)ok(capped.set(i,"Club "+i,true),"bounded follow "+i);
        ok(!capped.set(33,"Extra",true)&&capped.teams.size()==32,"favorite count cap enforced");
        ok(capped.save().getBytes("UTF-8").length<=CsFavorites.MAX_BYTES,"bounded favorites fit preference size");
        CsMatch original=CsMatch.read(new JSONObject().put("id",99).put("slug","test").put("discipline_id",1).put("status","upcoming").put("start_date","2026-10-27T12:00:00Z").put("team1",new JSONObject().put("id",10).put("name","NAVI")).put("team2",new JSONObject().put("id",20).put("name","MOUZ")).put("tournament",new JSONObject().put("id",1).put("name","PGL Test").put("tier_rank",1)));
        CsMatch restored=CsMatch.read(original.cache());ok(restored.teamIdA==10&&restored.teamIdB==20,"team IDs survive normalized match cache");
        JSONObject old=original.cache();old.getJSONObject("team1").remove("id");old.getJSONObject("team2").remove("id");CsMatch oldMatch=CsMatch.read(old);ok(oldMatch.teamIdA==0&&f.match(oldMatch),"old match caches remain usable with name fallback");
        CsPolicy policy=new CsPolicy(new JSONObject());original.event="Academy Qualifier";original.tier=4;ok(!policy.accepts(original,original.eventId),"following never relaxes tournament eligibility");
        original.status="upcoming";ok(!policy.live(original,original.start),"following never makes upcoming LIVE");
        ok(!CsPolicy.fresh(1,180001,180000),"following cannot extend LIVE expiry");
        System.out.println("COMPLETE: "+count+" favorite-team checks passed");
    }
}
