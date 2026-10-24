package com.quiettv.home;
import java.util.*;
import org.json.*;

/** Small location model and shared wall-clock zone; no location permission is used. */
final class TvProfile {
    private static final Set<String> ZONES=new HashSet<>(Arrays.asList(TimeZone.getAvailableIDs()));
    private static volatile TimeZone zone=TimeZone.getDefault();
    static boolean validZone(String id){return id!=null&&ZONES.contains(id);}
    static void configure(String id){zone=validZone(id)?TimeZone.getTimeZone(id):TimeZone.getDefault();}
    static TimeZone zone(){return zone;}
    static final class City {
        final String name,region,country,zone;final double latitude,longitude;
        City(String n,String r,String c,String z,double lat,double lon){name=n;region=r;country=c;zone=z;latitude=lat;longitude=lon;}
        String label(){return name+(region.isEmpty()?"":", "+region)+(country.isEmpty()?"":", "+country);}
        JSONObject json()throws JSONException{return new JSONObject().put("name",name).put("admin1",region).put("country",country).put("timezone",zone).put("latitude",latitude).put("longitude",longitude);}
        static City read(JSONObject j)throws JSONException{
            String name=CsMatch.clean(j.optString("name","")),z=j.optString("timezone","");double lat=j.optDouble("latitude",Double.NaN),lon=j.optDouble("longitude",Double.NaN);
            if(name.isEmpty()||name.length()>80||!validZone(z)||!Double.isFinite(lat)||!Double.isFinite(lon)||Math.abs(lat)>90||Math.abs(lon)>180)throw new JSONException("Invalid city");
            return new City(name,CsMatch.clean(j.optString("admin1","")),CsMatch.clean(j.optString("country","")),z,lat,lon);
        }
    }
    static City city(String raw){try{return raw.isEmpty()?null:City.read(new JSONObject(raw));}catch(Exception e){return null;}}
    static java.util.List<City> cities(String raw)throws JSONException{
        JSONObject root=new JSONObject(raw);if(root.optBoolean("error",false))throw new JSONException("City search unavailable");JSONArray rows=root.optJSONArray("results");ArrayList<City> out=new ArrayList<>();if(rows==null)return out;
        for(int i=0;i<Math.min(5,rows.length());i++)try{out.add(City.read(rows.getJSONObject(i)));}catch(JSONException ignored){}return out;
    }
}
