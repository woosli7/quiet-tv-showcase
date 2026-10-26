package com.quiettv.home;
public class CsPastZoneChecks {
    public static void main(String[] args)throws Exception{
        TvProfile.configure("America/New_York");long spring=CsMatch.time("2026-03-08T16:00:00Z"),fall=CsMatch.time("2026-11-01T16:00:00Z");
        if(CsResults.day(spring,1)-CsResults.day(spring,0)!=23*3600000L)throw new AssertionError("spring date");
        if(CsResults.day(fall,1)-CsResults.day(fall,0)!=25*3600000L)throw new AssertionError("fall date");
        long afterSpring=CsResults.day(spring,1),afterFall=CsResults.day(fall,1);
        if(CsResults.day(afterSpring,0)-CsResults.day(afterSpring,-3)!=71*3600000L)throw new AssertionError("spring history");
        if(CsResults.day(afterFall,0)-CsResults.day(afterFall,-3)!=73*3600000L)throw new AssertionError("fall history");
        if(CsResults.browseDay(afterFall,-1,afterFall)!=CsResults.day(fall,0))throw new AssertionError("fall navigation");
        TvProfile.configure("America/Phoenix");System.out.println("COMPLETE: 5 seasonal past-date checks passed");
    }
}
