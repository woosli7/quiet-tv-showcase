package com.quiettv.home;
import java.text.Normalizer;
import java.util.*;
import org.json.*;

/** Local team preferences, independent of match eligibility, status, and network polling. */
final class CsFavorites {
    static final int MAX_TEAMS=32,MAX_BYTES=16384;
    static final class Team {
        final long id;final String name;
        Team(long id,String name){this.id=Math.max(0,id);this.name=CsMatch.clean(name);}
        String key(){return id>0?"id:"+id:"name:"+canonical(name);}
    }
    final LinkedHashMap<String,Team> teams=new LinkedHashMap<>();
    final HashSet<Long> ids=new HashSet<>();final HashSet<String> legacyNames=new HashSet<>(),names=new HashSet<>();int revision;
    CsFavorites(String raw){if(raw==null||raw.length()>MAX_BYTES||raw.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>MAX_BYTES)return;try{JSONArray a=new JSONArray(raw);for(int i=0;i<Math.min(MAX_TEAMS,a.length());i++){JSONObject j=a.optJSONObject(i);if(j==null)continue;long id=j.optLong("id",0);String name=j.optString("name","");if(id<0||!valid(name)||contains(id,name))continue;Team t=new Team(id,name);teams.put(t.key(),t);index();}}catch(Exception ignored){teams.clear();index();}}
    static String canonical(String name){String s=Normalizer.normalize(name,Normalizer.Form.NFKD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+"," ").trim().replaceAll("\\b(team|esports|gaming|clan)\\b"," ").replaceAll("\\s+","");if(s.equals("navi"))return "natusvincere";if(s.equals("themongolz"))return "mongolz";if(s.equals("mousesports"))return "mouz";return s;}
    static boolean valid(String name){if(name==null||name.length()>100)return false;String key=canonical(name);return !key.isEmpty()&&!key.equals("tbd")&&!key.equals("tba")&&!key.equals("tobedetermined");}
    void index(){ids.clear();legacyNames.clear();names.clear();for(Team t:teams.values()){if(t.id>0)ids.add(t.id);else legacyNames.add(canonical(t.name));names.add(canonical(t.name));}}
    boolean contains(long id,String name){if(id>0)return ids.contains(id)||!legacyNames.isEmpty()&&legacyNames.contains(canonical(name));return names.contains(canonical(name));}
    boolean match(CsMatch m){return contains(m.teamIdA,m.a)||contains(m.teamIdB,m.b);}
    boolean empty(){return teams.isEmpty();}
    boolean set(long id,String name,boolean on){
        if(id<0||!valid(name))return false;boolean present=contains(id,name);if(on&&present)return true;if(on&&teams.size()>=MAX_TEAMS)return false;
        if(on){Team t=new Team(id,name);teams.put(t.key(),t);}else{String key=canonical(name);teams.values().removeIf(t->id>0&&t.id>0?t.id==id:canonical(t.name).equals(key));}
        if(present!=on){revision++;index();}return true;
    }
    String save(){JSONArray a=new JSONArray();for(Team t:teams.values())try{a.put(new JSONObject().put("id",t.id).put("name",t.name));}catch(JSONException ignored){}return a.toString();}
    List<CsMatch> filter(List<CsMatch> rows,boolean only){if(!only)return rows;ArrayList<CsMatch> out=new ArrayList<>();for(CsMatch m:rows)if(match(m))out.add(m);return out;}
    List<CsMatch> prioritize(List<CsMatch> rows,int limit){ArrayList<CsMatch> out=new ArrayList<>(rows);if(!empty())out.sort((a,b)->Boolean.compare(match(b),match(a)));return new ArrayList<>(out.subList(0,Math.min(Math.max(0,limit),out.size())));}
    List<Team> available(Collection<CsMatch> rows){LinkedHashMap<String,Team> out=new LinkedHashMap<>(teams);for(CsMatch m:rows)for(Team t:new Team[]{new Team(m.teamIdA,m.a),new Team(m.teamIdB,m.b)}){if(!valid(t.name))continue;String name=canonical(t.name);if(t.id>0)out.remove("name:"+name);else{boolean identified=false;for(Team other:out.values())if(other.id>0&&canonical(other.name).equals(name)){identified=true;break;}if(identified)continue;}if(out.size()<96||out.containsKey(t.key()))out.put(t.key(),t);}ArrayList<Team> list=new ArrayList<>(out.values());list.sort((a,b)->{int favorite=Boolean.compare(contains(b.id,b.name),contains(a.id,a.name));return favorite!=0?favorite:a.name.compareToIgnoreCase(b.name);});return list;}
}
