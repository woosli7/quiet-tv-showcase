package com.quiettv.home;
import org.json.*;
import java.util.*;

/** Run on Android's app_process runtime; does not depend on network or personal data. */
public class ProfileChecks {
    static int count;
    static void ok(boolean value,String label){if(!value)throw new AssertionError(label);count++;System.out.println("PASS "+label);}
    static JSONObject city()throws Exception{return new JSONObject().put("name","Paris").put("admin1","Ile-de-France").put("country","France").put("latitude",48.85).put("longitude",2.35).put("timezone","Europe/Paris");}
    static boolean invalid(JSONObject j){try{TvProfile.City.read(j);return false;}catch(Exception e){return true;}}
    static CsMatch scheduled(long start){CsMatch m=new CsMatch();m.status="upcoming";m.start=start;return m;}
    public static void main(String[] args)throws Exception{
        ok(TvProfile.validZone("Europe/Paris")&&!TvProfile.validZone("Unrecognized/City"),"timezone IDs validated instead of silently falling back to GMT");
        TvProfile.City paris=TvProfile.City.read(city());ok(paris.label().contains("France")&&paris.zone.equals("Europe/Paris"),"city identity includes region/country and timezone");
        ok(TvProfile.city(paris.json().toString()).latitude==paris.latitude,"selected city survives normalized persistence");
        ok(TvProfile.city("")==null&&TvProfile.city("not json")==null,"missing or corrupt city safely yields no location");
        ok(invalid(city().put("latitude",91)),"out-of-range latitude rejected");ok(invalid(city().put("longitude",181)),"out-of-range longitude rejected");ok(invalid(city().put("timezone","Unknown/Zone")),"invalid city timezone rejected");ok(invalid(city().put("name","")),"empty city name rejected");
        ok(TvProfile.cities("{}").isEmpty(),"empty geocoding result supports skip/retry");JSONArray a=new JSONArray();a.put(city().put("latitude",100));a.put(city());ok(TvProfile.cities(new JSONObject().put("results",a).toString()).size()==1,"malformed geocoder entry cannot hide a later valid city");
        a=new JSONArray();for(int i=0;i<20;i++)a.put(city());ok(TvProfile.cities(new JSONObject().put("results",a).toString()).size()==5,"geocoding list bounded to five choices");
        TvProfile.configure("Europe/Paris");long instant=CsMatch.time("2026-10-24T12:00:00Z");ok(SolarDay.minute(instant)==840,"solar display follows selected city's wall time");
        TvProfile.configure("America/Phoenix");ok(CsResults.dayStart(instant)==CsMatch.time("2026-10-24T07:00:00Z"),"Phoenix day behavior remains correct");
        TvProfile.configure("America/New_York");long spring=CsMatch.time("2026-03-08T06:00:00Z");ok(CsResults.dayEnd(spring)-CsResults.dayStart(spring)==23*3600000L,"spring-forward day has 23 hours");
        ok(CsSchedule.today(scheduled(CsMatch.time("2026-03-09T03:59:59Z")),spring),"last second of short local day remains included");ok(!CsSchedule.today(scheduled(CsMatch.time("2026-03-09T04:00:00Z")),spring),"short local day ends at actual midnight");
        long autumn=CsMatch.time("2026-11-01T05:00:00Z");ok(CsResults.dayEnd(autumn)-CsResults.dayStart(autumn)==25*3600000L,"fall-back day has 25 hours");ok(CsSchedule.today(scheduled(CsMatch.time("2026-11-02T04:30:00Z")),autumn),"extra hour of long local day not lost from schedule");
        ok(!CsSchedule.today(scheduled(CsResults.dayEnd(autumn)),autumn),"next midnight excluded on long day");
        long parisSet=CsMatch.time("2026-10-24T16:45:00Z"),parisRise=CsMatch.time("2026-10-24T06:25:00Z");TvProfile.configure("Europe/Paris");SolarDay day=SolarDay.forDate(parisRise,parisSet,instant);ok(day!=null&&day.riseMinute==505&&day.setMinute==1125,"solar events validated in selected timezone");
        CsResults ledger=new CsResults();ledger.checkedAt=instant;JSONObject cached=ledger.cache(instant);CsPolicy policy=new CsPolicy(new JSONObject());ok(CsResults.restore(cached,policy,instant).checkedAt==instant,"same-zone results retain their checked time");TvProfile.configure("America/New_York");ok(CsResults.restore(cached,policy,instant).checkedAt==0,"changed timezone requires a new daily-results check");
        TvProfile.configure("Unrecognized/Zone");ok(TvProfile.zone().getID().equals(TimeZone.getDefault().getID()),"no city uses TV timezone");
        System.out.println("COMPLETE: "+count+" profile checks passed");
    }
}
