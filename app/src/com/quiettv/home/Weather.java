package com.quiettv.home;
import java.io.*;
import java.net.*;
import org.json.*;

/** Selected city coordinates; daily solar times share the weather request. */
final class Weather {
    static final class Reading {
        final int degrees,code;final boolean day,hasWeather;final String solarJson;
        Reading(int t,int c,boolean d,boolean weather,String solar){degrees=t;code=c;day=d;hasWeather=weather;solarJson=solar;}
    }
    static Reading fetch(boolean includeWeather,TvProfile.City city,String zone,boolean fahrenheit) throws Exception {
        if(city==null||!TvProfile.validZone(zone))throw new IOException("Choose a city first");
        String current=includeWeather?"&current=temperature_2m,weather_code,is_day&temperature_unit="+(fahrenheit?"fahrenheit":"celsius"):"";
        URL url=new URL("https://api.open-meteo.com/v1/forecast?latitude="+city.latitude+"&longitude="+city.longitude+current+"&daily=sunrise,sunset&timeformat=unixtime&timezone="+URLEncoder.encode(zone,"UTF-8")+"&forecast_days=16");
        HttpURLConnection connection=(HttpURLConnection)url.openConnection();connection.setConnectTimeout(8000);connection.setReadTimeout(8000);
        try{
            if(connection.getResponseCode()!=200)throw new IOException("Weather unavailable");StringBuilder body=new StringBuilder();
            try(Reader reader=new InputStreamReader(connection.getInputStream(),"UTF-8")){char[] chunk=new char[2048];int length;while((length=reader.read(chunk))!=-1){body.append(chunk,0,length);if(body.length()>32768)throw new IOException("Response too large");}}
            JSONObject root=new JSONObject(body.toString());int temperature=0,code=0;boolean day=false;
            if(includeWeather){JSONObject currentData=root.getJSONObject("current");double degrees=currentData.getDouble("temperature_2m");if(!Double.isFinite(degrees)||degrees < -150||degrees>170)throw new IOException("Invalid temperature");temperature=(int)Math.round(degrees);code=currentData.getInt("weather_code");day=currentData.getInt("is_day")==1;}
            JSONArray saved=new JSONArray();JSONObject daily=root.optJSONObject("daily");
            if(daily!=null){JSONArray rises=daily.optJSONArray("sunrise"),sets=daily.optJSONArray("sunset");if(rises!=null&&sets!=null){int count=Math.min(16,Math.min(rises.length(),sets.length()));for(int i=0;i<count;i++){long rise=rises.optLong(i,0)*1000L,set=sets.optLong(i,0)*1000L;if(SolarDay.forDate(rise,set,rise)!=null)saved.put(new JSONObject().put("rise",rise).put("set",set));}}}
            return new Reading(temperature,code,day,includeWeather,saved.length()>0?saved.toString():null);
        }finally{connection.disconnect();}
    }
}
