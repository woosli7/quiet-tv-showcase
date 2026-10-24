package com.quiettv.home;
import java.io.*;
import java.net.*;
import java.util.*;

final class CitySearch {
    static java.util.List<TvProfile.City> search(String query)throws Exception{
        query=query.trim();if(query.length()<2||query.length()>100)return Collections.emptyList();
        HttpURLConnection c=(HttpURLConnection)new URL("https://geocoding-api.open-meteo.com/v1/search?name="+URLEncoder.encode(query,"UTF-8")+"&count=5&language=en&format=json").openConnection();c.setConnectTimeout(5000);c.setReadTimeout(5000);c.setInstanceFollowRedirects(false);
        try{if(c.getResponseCode()!=200)throw new IOException("City search unavailable");try(InputStream in=c.getInputStream()){return TvProfile.cities(CsFeed.read(in,65536));}}finally{c.disconnect();}
    }
}
