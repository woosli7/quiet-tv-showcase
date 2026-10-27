package com.quiettv.home;
import java.util.Calendar;
import java.util.TimeZone;

/** Validates dated solar events and converts their UTC instants to the selected wall time. */
final class SolarDay {
    
    final long sunrise,sunset;
    final int riseMinute,setMinute,date;
    private SolarDay(long rise,long set){sunrise=rise;sunset=set;riseMinute=minute(rise);setMinute=minute(set);date=dateKey(rise);}
    static int dateKey(long time){Calendar c=Calendar.getInstance(TvProfile.zone());c.setTimeInMillis(time);return c.get(Calendar.YEAR)*1000+c.get(Calendar.DAY_OF_YEAR);}
    static int minute(long time){Calendar c=Calendar.getInstance(TvProfile.zone());c.setTimeInMillis(time);return c.get(Calendar.HOUR_OF_DAY)*60+c.get(Calendar.MINUTE);}
    static SolarDay forDate(long rise,long set,long date){
        if(rise<=0||set<=rise||set-rise<6*3600000L||set-rise>18*3600000L)return null;
        if(dateKey(rise)!=dateKey(set)||dateKey(rise)!=dateKey(date))return null;
        int r=minute(rise),s=minute(set);if(r<120||s>1320||s<=r)return null;
        return new SolarDay(rise,set);
    }
    int previewMinute(int choice){int noon=(riseMinute+setMinute)/2;if(choice==1)return riseMinute+12;if(choice==2)return riseMinute+(noon-riseMinute)/2;if(choice==3)return noon;if(choice==4)return setMinute-12;if(choice==5)return Math.min(1439,setMinute+120);return -1;}
    static int[] anchors(int rise,int set){int noon=(rise+set)/2;return new int[]{0,rise-60,rise+12,rise+(noon-rise)/2,noon,noon+(set-noon)/2,set-12,set+35,set+90,1440};}
}
