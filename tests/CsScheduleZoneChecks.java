package com.quiettv.home;
public class CsScheduleZoneChecks {
    public static void main(String[] args)throws Exception{
        TvProfile.configure("America/New_York");long spring=CsMatch.time("2026-03-08T12:00:00Z"),fall=CsMatch.time("2026-11-01T12:00:00Z");
        if(CsSchedule.day(spring,1)-CsSchedule.day(spring,0)!=23*3600000L)throw new AssertionError("spring day");
        if(CsSchedule.day(fall,1)-CsSchedule.day(fall,0)!=25*3600000L)throw new AssertionError("fall day");
        if(CsSchedule.day(spring,7)-CsSchedule.day(spring,0)!=167*3600000L)throw new AssertionError("spring week");
        if(CsSchedule.day(fall,7)-CsSchedule.day(fall,0)!=169*3600000L)throw new AssertionError("fall week");
        if(CsSchedule.browseDay(CsSchedule.day(spring,0),1,spring)!=CsSchedule.day(spring,1))throw new AssertionError("spring navigation");
        TvProfile.configure("America/Phoenix");System.out.println("COMPLETE: 5 daylight-saving schedule checks passed");
    }
}
