package com.quiettv.home;
import java.util.*;
import java.nio.file.*;
import org.json.*;

/** Matching and trust checks; optional saved public-page fixtures never enter the release. */
public class CsHighlightsChecks {
    static int count;
    static void ok(boolean value,String label){if(!value)throw new AssertionError(label);count++;System.out.println("PASS "+label);}
    static final String CHANNEL="UC3y-TwdfrUfm7Iindvvxsjg",ID="U3sLkzL3ggY",TITLE="FUT vs Team Liquid - HIGHLIGHTS - PGL Masters Bucharest 2026 | CS2";
    static CsMatch match(){CsMatch m=new CsMatch();m.id=1;m.a="FUT";m.b="Liquid";m.event="PGL Masters Bucharest 2026";m.status="finished";m.start=CsMatch.time("2026-10-25T08:00:00Z");m.ended=CsMatch.time("2026-10-25T09:45:00Z");return m;}
    static JSONObject player()throws Exception{return new JSONObject().put("playabilityStatus",new JSONObject().put("status","OK")).put("videoDetails",new JSONObject().put("videoId",ID).put("channelId",CHANNEL).put("title",TITLE).put("lengthSeconds","2992").put("isLiveContent",false)).put("microformat",new JSONObject().put("playerMicroformatRenderer",new JSONObject().put("externalChannelId",CHANNEL).put("publishDate","2026-10-25T03:00:38-07:00")));}
    static JSONObject renderer(String owner)throws Exception{return new JSONObject().put("videoId",ID).put("title",new JSONObject().put("simpleText",TITLE)).put("shortBylineText",new JSONObject().put("runs",new JSONArray().put(new JSONObject().put("text","PGL Counter-Strike Highlights").put("navigationEndpoint",new JSONObject().put("browseEndpoint",new JSONObject().put("browseId",owner))))));}
    static JSONObject search(JSONObject row)throws Exception{return new JSONObject().put("metadata",new JSONObject().put("channelMetadataRenderer",new JSONObject().put("externalId",CHANNEL))).put("contents",new JSONObject().put("compactVideoRenderer",row));}
    static boolean rejects(CsHighlights h,JSONObject p,CsHighlights.Source s,CsMatch m,long now){return h.verify(p,s,m,ID,now)==null;}
    public static void main(String[] args)throws Exception{
        CsHighlights h=new CsHighlights(new JSONObject(new String(Files.readAllBytes(Paths.get(args[0])),"UTF-8")));CsMatch m=match();CsHighlights.Source s=h.source(m);long now=CsMatch.time("2026-10-25T20:00:00Z");
        ok(s!=null&&s.channel.equals(CHANNEL),"PGL routes to reviewed highlights channel");
        java.net.URI nativeSearch=new java.net.URI(s.appSearch(m));String query=java.net.URLDecoder.decode(nativeSearch.getRawQuery(),"UTF-8");
        ok(nativeSearch.getHost().equals("www.youtube.com")&&nativeSearch.getPath().equals("/tv")&&query.startsWith("launch=search&q="),"TV app receives actual native search link");
        ok(query.contains(s.name)&&query.contains(m.a)&&query.contains(m.b)&&query.contains(m.event),"Native search includes organizer, both teams and exact event");
        ok(s.search(m).contains("/channel/"+CHANNEL+"/search?query="),"Browser fallback remains scoped to reviewed channel");
        ok(s.search(m).startsWith("https://www.youtube.com/channel/"+CHANNEL+"/search?query="),"fallback stays within reviewed channel");
        ok(h.title(m,TITLE),"both teams and full event match");
        ok(h.title(m,"Liquid vs FUT / PGL Masters Bucharest 2026 HIGHLIGHTS"),"reversed team order works");
        ok(!h.title(m,TITLE.replace("Liquid","MOUZ")),"wrong opponent rejected");
        ok(!h.title(m,TITLE.replace("FUT","FUTURE")),"team substring rejected");
        ok(!h.title(m,TITLE.replace("2026","2025")),"old event year rejected");
        ok(!h.title(m,TITLE.replace("Bucharest","Cluj Napoca")),"other tournament rejected");
        ok(!h.title(m,TITLE.replace("HIGHLIGHTS","FULL MATCH")),"full broadcast not called highlights");
        CsMatch alias=match();alias.a="Natus Vincere";ok(h.title(alias,TITLE.replace("FUT","NAVI")),"configured team alias works");
        CsMatch bb=match();bb.a="BetBoom";bb.b="Natus Vincere";bb.start=CsMatch.time("2026-10-26T17:00:00Z");bb.ended=CsMatch.time("2026-10-26T19:01:01Z");
        String bbTitle="WINNER TO PLAYOFFS! BB Team vs NAVI - HIGHLIGHTS - PGL Masters Bucharest 2026 | CS2";
        ok(h.title(bb,bbTitle),"real PGL BB Team and NAVI title aliases match feed names");
        ok(!h.title(bb,bbTitle.replace("BB Team","BB")),"ambiguous bare BB is not a configured alias");
        ok(!h.title(bb,bbTitle.replace("NAVI","Nemiga")),"BB Team alias still requires exact opponent");
        JSONObject bbPlayer=player();bbPlayer.getJSONObject("videoDetails").put("videoId","Pgophr8oqXM").put("title",bbTitle).put("lengthSeconds","3044");bbPlayer.getJSONObject("microformat").getJSONObject("playerMicroformatRenderer").put("publishDate","2026-10-26T12:19:34-07:00");
        ok(h.verify(bbPlayer,s,bb,"Pgophr8oqXM",CsMatch.time("2026-10-26T21:30:00Z"))!=null,"real BetBoom NAVI upload passes owner event and finish-time checks");
        CsMatch esl=match();esl.event="ESL Pro League Season 24";ok(h.source(esl)!=null&&h.title(esl,"FUT vs Liquid HIGHLIGHTS EPL S24"),"ESL event abbreviation works");
        CsMatch blast=match();blast.event="BLAST Premier Rivals 2026 Season 2";ok(h.source(blast)!=null,"BLAST has reviewed source");
        CsMatch unknown=match();unknown.event="Unreviewed Open";ok(h.source(unknown)==null,"unknown organizer never guessed");
        ok(h.search(search(renderer(CHANNEL)),s,m).size()==1,"reviewed video renderer accepted");
        ok(h.search(search(renderer("UCaaaaaaaaaaaaaaaaaaaaaa")),s,m).isEmpty(),"same display name cannot impersonate owner");
        JSONObject bad=search(renderer(CHANNEL));bad.getJSONObject("metadata").getJSONObject("channelMetadataRenderer").put("externalId","other");boolean failed=false;try{h.search(bad,s,m);}catch(Exception e){failed=true;}ok(failed,"unexpected channel fails closed");
        ok(h.search(search(renderer(CHANNEL).put("upcomingEventData",new JSONObject())),s,m).isEmpty(),"premiere excluded");
        ok(h.verify(player(),s,m,ID,now)!=null,"published official match highlights accepted");
        JSONObject p=player();p.getJSONObject("videoDetails").put("channelId","other");ok(rejects(h,p,s,m,now),"player owner must match");
        p=player();p.getJSONObject("microformat").getJSONObject("playerMicroformatRenderer").put("externalChannelId","other");ok(rejects(h,p,s,m,now),"microformat owner must agree");
        p=player();p.getJSONObject("videoDetails").put("isLiveContent",true);ok(rejects(h,p,s,m,now),"live or archived livestream excluded");
        p=player();p.getJSONObject("videoDetails").remove("isLiveContent");ok(rejects(h,p,s,m,now),"missing live state fails closed");
        p=player();p.getJSONObject("videoDetails").put("videoId","another1234");ok(rejects(h,p,s,m,now),"video ID must agree");
        p=player();p.getJSONObject("playabilityStatus").put("status","LOGIN_REQUIRED");ok(rejects(h,p,s,m,now),"unavailable video not auto launched");
        p=player();p.getJSONObject("videoDetails").put("lengthSeconds","35");ok(rejects(h,p,s,m,now),"short clip excluded");
        p=player();p.getJSONObject("microformat").getJSONObject("playerMicroformatRenderer").put("publishDate","2026-10-24T10:00:00Z");ok(rejects(h,p,s,m,now),"previous match upload rejected");
        p=player();p.getJSONObject("microformat").getJSONObject("playerMicroformatRenderer").put("publishDate","2026-10-25");ok(rejects(h,p,s,m,now),"date without exact upload time not trusted");
        p=player();p.getJSONObject("microformat").getJSONObject("playerMicroformatRenderer").put("publishDate","2026-10-25T21:00:00Z");ok(rejects(h,p,s,m,now),"future publication excluded");
        p=player();CsMatch upcoming=match();upcoming.status="upcoming";ok(rejects(h,p,s,upcoming,now),"unfinished match excluded");
        ok(CsHighlights.initial("var ytInitialData = {\"x\":\"brace } inside\"};","ytInitialData").getString("x").equals("brace } inside"),"balanced JSON handles quoted braces");
        ok(CsHighlights.initial("var ytInitialData = '\\x7b\\x22x\\x22:1\\x7d';","ytInitialData").getInt("x")==1,"escaped page data safely decoded");
        ok(CsHighlights.initial("var ytInitialPlayerResponse = null;var ytInitialPlayerResponse = {\"ok\":true};","ytInitialPlayerResponse").getBoolean("ok"),"null mobile placeholder skipped");
        failed=false;try{CsHighlights.initial("<html>Consent required</html>","ytInitialData");}catch(Exception e){failed=true;}ok(failed,"changed or blocked page fails closed");
        if(args.length>2){JSONObject real=CsHighlights.initial(new String(Files.readAllBytes(Paths.get(args[1])),"UTF-8"),"ytInitialData");List<CsHighlights.Video> videos=h.search(real,s,m);ok(!videos.isEmpty()&&videos.get(0).id.equals(ID),"real public PGL search finds expected match");JSONObject watch=CsHighlights.initial(new String(Files.readAllBytes(Paths.get(args[2])),"UTF-8"),"ytInitialPlayerResponse");ok(h.verify(watch,s,m,ID,now)!=null,"real public PGL player confirms title owner and upload date");}
        System.out.println("COMPLETE: "+count+" highlights checks passed");
    }
}
