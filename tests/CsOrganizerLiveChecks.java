package com.quiettv.home;
import org.json.*;
import java.nio.file.*;
public final class CsOrganizerLiveChecks {
    static int count;
    static void ok(boolean value,String label){if(!value)throw new AssertionError(label);count++;}
    static CsStream stream(String channel){CsStream s=new CsStream();s.url="https://www.twitch.tv/"+channel;s.name=channel;s.language="en";return s;}
    static JSONObject data(CsMatch m,long now,String channel,String title)throws Exception{
        return new JSONObject().put("@graph",new JSONArray()
          .put(new JSONObject().put("@type","ProfilePage").put("mainEntity",new JSONObject().put("url","https://www.twitch.tv/"+channel)))
          .put(new JSONObject().put("@type","VideoObject").put("description",title).put("embedUrl","https://player.twitch.tv/?channel="+channel)
          .put("publication",new JSONObject().put("@type","BroadcastEvent").put("isLiveBroadcast",true).put("startDate",CsMatch.stamp(now-3600000)))));
    }
    static String html(JSONObject root){return "<html><script type='application/ld+json'>"+root+"</script></html>";}
    static JSONObject video(JSONObject root)throws Exception{return root.getJSONArray("@graph").getJSONObject(1);}
    static boolean verify(CsMatch m,JSONObject data,long now){return CsOrganizerLive.verify(html(data),m,stream("pgl"),now)!=null;}
    public static void main(String[] args)throws Exception{
        long now=System.currentTimeMillis();CsMatch m=new CsMatch();m.status="current";m.start=now-3600000;m.a="The MongolZ";m.b="MOUZ";m.event="PGL Masters Bucharest 2026";m.bo=3;m.one=0;m.two=0;
        String title="[A] The MongolZ vs. MOUZ - PGL Masters Bucharest 2026 - Group Stage: Round 3";
        ok(verify(m,data(m,now,"pgl",title),now),"Official live exact teams/event accepted");
        ok(CsOrganizerLive.title(m,"MOUZ vs. MongolZ - PGL Masters Bucharest 2026"),"Team order and reviewed alias supported");
        ok(!CsOrganizerLive.title(m,"The MongolZ vs. MOUZ NXT - PGL Masters Bucharest 2026"),"Academy roster cannot match parent team");
        ok(!CsOrganizerLive.title(m,"The MongolZ vs. MOUZ - PGL Wallachia 2026"),"Other game/event rejected");
        ok(!CsOrganizerLive.title(m,"The MongolZ vs. MOUZ - PGL Masters Bucharest 2025"),"Different event year rejected");
        ok(!CsOrganizerLive.title(m,"Nemiga Gaming vs. Aurora Gaming - PGL Masters Bucharest 2026"),"Parallel official broadcast rejected");
        ok(!CsOrganizerLive.title(m,"PGL Masters Bucharest 2026 | MOUZ and The MongolZ highlights"),"Mentioning teams without pairing is insufficient");
        JSONObject j=data(m,now,"pgl",title);video(j).getJSONObject("publication").put("isLiveBroadcast",false);ok(!verify(m,j,now),"Offline broadcast rejected");
        video(j).getJSONObject("publication").put("isLiveBroadcast","true");ok(!verify(m,j,now),"Malformed live flag rejected");
        j=data(m,now,"pgl",title);video(j).remove("publication");ok(!verify(m,j,now),"Missing live evidence rejected");
        j=data(m,now,"pgl",title);video(j).getJSONObject("publication").put("startDate",CsMatch.stamp(now+600000));ok(!verify(m,j,now),"Scheduled future stream rejected");
        j=data(m,now,"pgl",title);video(j).getJSONObject("publication").put("startDate",CsMatch.stamp(now-19*3600000));ok(!verify(m,j,now),"Old stream metadata rejected");
        j=data(m,now,"pgl_cs2",title);ok(!verify(m,j,now),"Wrong channel identity rejected");
        j=data(m,now,"pgl",title);video(j).put("embedUrl","https://player.twitch.tv/?channel=pgl_cs2");ok(!verify(m,j,now),"Embed channel must match verified owner");
        video(j).put("embedUrl","https://player.twitch.tv.evil/?channel=pgl");ok(!verify(m,j,now),"Spoofed player host rejected");
        video(j).put("embedUrl","https://player.twitch.tv/?channel=pgl&channel=other");ok(!verify(m,j,now),"Duplicate channel query rejected");
        j=data(m,now,"pgl",title);j.getJSONArray("@graph").remove(0);ok(!verify(m,j,now),"Profile identity evidence required");
        ok(CsOrganizerLive.verify("<script type='application/ld+json'>{broken</script>",m,stream("pgl"),now)==null,"Changed or malformed page fails closed");
        m.status="finished";ok(!verify(m,data(m,now,"pgl",title),now),"Final match cannot open a live fallback");m.status="current";m.one=2;ok(!verify(m,data(m,now,"pgl",title),now),"Completed score rejected");m.one=0;
        JSONObject config=new JSONObject().put("profiles",new JSONArray().put(new JSONObject().put("event_pattern","pgl").put("twitch_language","en").put("twitch",new JSONArray().put("pgl_cs2").put("pgl"))));CsBroadcasts registry=new CsBroadcasts(config);
        ok(registry.twitchCandidates(m.event,3).size()==2,"Only reviewed event channels probed");ok(registry.twitchCandidates(m.event,1).get(0).url.endsWith("/pgl"),"Deterministic primary-first bounded lookup");
        ok(registry.twitchCandidates("BLAST Rivals",3).isEmpty(),"Another organizer's channels never guessed");
        ok(registry.twitchCandidates(m.event,0).isEmpty(),"Zero limit honored");
        if(args.length==3){
            CsMatch actual=CsMatch.read(new JSONObject(new String(Files.readAllBytes(Paths.get(args[0])),"UTF-8").replace("\ufeff","")));
            CsBroadcasts liveRegistry=new CsBroadcasts(new JSONObject(new String(Files.readAllBytes(Paths.get(args[1])),"UTF-8").replace("\ufeff","")));
            ok(!liveRegistry.twitchCandidates(actual.event,3).isEmpty(),"Real organizer registry supplies reviewed candidates");
            String page=new String(Files.readAllBytes(Paths.get(args[2])),"UTF-8");
            ok(CsOrganizerLive.verify(page,actual,stream("pgl"),System.currentTimeMillis())!=null,"Captured real MOUZ organizer page matches");

        }
        System.out.println(count+" organizer-live checks passed");
    }
}
