package com.quiettv.home;
import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Four remote-friendly steps. Draft choices are committed only by Finish. */
final class SetupFlow {
    final MainActivity host;final boolean locationOnly;final ArrayList<MainActivity.Entry> apps;
    AlertDialog dialog;LinearLayout body;EditText input;ListView list;TextView status;CheckBox zoneChoice;
    TvProfile.City city;String name,query="";boolean cityZone;boolean[] enabled;boolean greeting,clock,weather;
    int step,generation;long lastSearch=-5000;
    SetupFlow(MainActivity h,boolean location){host=h;locationOnly=location;step=location?1:0;apps=new ArrayList<>(h.all);enabled=new boolean[apps.size()];for(int i=0;i<apps.size();i++)enabled[i]=!h.prefs.getBoolean("hide:"+apps.get(i).key,false);name=h.prefs.getString("name","");city=h.profileCity();cityZone=h.prefs.getBoolean("cityTimezone",true);greeting=h.prefs.getBoolean("widgetGreeting",true);clock=h.prefs.getBoolean("widgetClock",true);weather=h.prefs.getBoolean("weather",true);}
    int dp(int value){return Math.round(value*host.getResources().getDisplayMetrics().density);}
    TextView text(String value){TextView v=new TextView(host);v.setText(value);v.setTextSize(18);v.setTextColor(0xffc3cedd);v.setPadding(0,dp(6),0,dp(10));return v;}
    void show(){
        body=new LinearLayout(host);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(24),dp(8),dp(24),dp(8));
        dialog=new AlertDialog.Builder(host).setTitle("Welcome to Quiet TV").setView(body).setPositiveButton("Continue",null).setNegativeButton("Later",null).setNeutralButton("Skip city",null).create();host.showDialog(dialog);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->next());dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v->back());dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->{city=null;cityZone=false;if(locationOnly){host.saveLocation(null,false);dialog.dismiss();}else{weather=false;step=2;render();}});
        dialog.setOnKeyListener((d,key,event)->{if(key!=KeyEvent.KEYCODE_BACK)return false;if(event.getAction()==KeyEvent.ACTION_DOWN)back();return true;});render();
    }
    void back(){if(step==1&&zoneChoice!=null)cityZone=city!=null&&zoneChoice.isChecked();if(locationOnly||step==0){dialog.dismiss();return;}step--;render();}
    void next(){
        if(step==0){name=input.getText().toString().trim();step=1;render();return;}
        if(step==1){cityZone=city!=null&&zoneChoice.isChecked();if(locationOnly){host.saveLocation(city,cityZone);dialog.dismiss();return;}step=2;render();return;}
        if(step==2){step=3;render();return;}
        SharedPreferences.Editor e=host.prefs.edit().putString("name",name).putBoolean("widgetGreeting",greeting).putBoolean("widgetClock",clock).putBoolean("weather",weather).putBoolean("setupComplete",true);
        for(int i=0;i<apps.size();i++)e.putBoolean("hide:"+apps.get(i).key,!enabled[i]);e.apply();host.saveLocation(city,cityZone);dialog.dismiss();host.loadApps();host.applyWidgets();host.refreshWeather(true);Toast.makeText(host,"Your home screen is ready",Toast.LENGTH_SHORT).show();
    }
    void render(){renderBody();MenuStyle.view(body);MenuStyle.footer(dialog);}
    void renderBody(){
        generation++;body.removeAllViews();input=null;list=null;dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setText(locationOnly?"Cancel":step==0?"Later":"Back");dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText(locationOnly?"Save":step==3?"Finish":"Continue");dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setVisibility(step==1?View.VISIBLE:View.GONE);dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setText(locationOnly?"No city":"Skip city");
        if(step==0){dialog.setTitle("Make it yours / 1 of 4");body.addView(text("What should Quiet TV call you? Leave this blank for a simple greeting."));input=new EditText(host);input.setSingleLine(true);input.setText(name);input.setHint("Your name");input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(18)});body.addView(input);return;}
        if(step==1){
            dialog.setTitle(locationOnly?"City and time":"Your city / 2 of 4");body.addView(text("Choose a city for weather and seasonal skies. Search sends the city name to Open-Meteo; no device location is requested. You can skip this."));
            LinearLayout search=new LinearLayout(host);input=new EditText(host);input.setSingleLine(true);input.setHint("City or postal code");input.setText(query);input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(100)});search.addView(input,new LinearLayout.LayoutParams(0,dp(52),1));Button button=new Button(host);button.setText("Search");search.addView(button);body.addView(search);status=text(city==null?"No city selected. Clock uses the TV's timezone.":"Selected: "+city.label());body.addView(status);
            zoneChoice=new CheckBox(host);zoneChoice.setText("Use the selected city's timezone");zoneChoice.setChecked(city!=null&&cityZone);zoneChoice.setEnabled(city!=null);body.addView(zoneChoice);button.setOnClickListener(v->search());input.setOnEditorActionListener((v,action,event)->{search();return true;});return;
        }
        if(step==2){dialog.setTitle("Your apps / 3 of 4");body.addView(text(apps.isEmpty()?"No launchable apps were found. You can choose apps later in Settings.":"Choose the apps to show. You can rearrange them later in Settings."));String[] labels=new String[apps.size()];for(int i=0;i<labels.length;i++)labels[i]=apps.get(i).label;list=choices(labels,enabled,(index,on)->enabled[index]=on);return;}
        dialog.setTitle("Your home screen / 4 of 4");body.addView(text("Choose what appears on your home and idle screens. You can change these choices in Settings → Widgets."));boolean[] selected={greeting,clock,weather};choices(new String[]{"Greeting","Clock and date","Weather"},selected,(index,on)->{if(index==0)greeting=on;else if(index==1)clock=on;else weather=on;});body.addView(text(city==null?"Weather and seasonal sun times need a city. You can add one later.":"City: "+city.label()+"\nTime: "+(cityZone?city.zone:TimeZone.getDefault().getID())));
    }
    interface Checked {void changed(int index,boolean value);}
    ListView choices(String[] labels,boolean[] selected,Checked action){ListView v=new ListView(host);ReadableText.rows(v);v.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);v.setAdapter(new ArrayAdapter<String>(host,android.R.layout.simple_list_item_multiple_choice,labels));for(int i=0;i<labels.length;i++)v.setItemChecked(i,selected[i]);v.setOnItemClickListener((p,row,i,id)->action.changed(i,v.isItemChecked(i)));body.addView(v,new LinearLayout.LayoutParams(-1,dp(Math.min(260,Math.max(64,labels.length*52)))));return v;}
    void search(){
        if(SystemClock.elapsedRealtime()-lastSearch<2000)return;query=input.getText().toString().trim();if(query.length()<2){status.setText("Enter at least two characters.");return;}lastSearch=SystemClock.elapsedRealtime();final int token=++generation;final String term=query;status.setText("Finding cities...");
        host.weatherWorker.execute(()->{java.util.List<TvProfile.City> found=null;try{found=CitySearch.search(term);}catch(Exception error){if((host.getApplicationInfo().flags&android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0)android.util.Log.w("QuietTV-Setup","City lookup failed",error);}final java.util.List<TvProfile.City> result=found;host.timer.post(()->{if(!dialog.isShowing()||step!=1||token!=generation||host.isDestroyed())return;status.setText(result==null?"City search unavailable. Try again, or skip city.":result.isEmpty()?"No cities found. Try a nearby city or add a country.":"Choose your city:");if(result==null||result.isEmpty())return;String[] names=new String[result.size()];for(int i=0;i<names.length;i++)names[i]=result.get(i).label();AlertDialog choices=new AlertDialog.Builder(host).setTitle("Choose your city").setItems(names,(picker,i)->{city=result.get(i);cityZone=true;zoneChoice.setChecked(true);zoneChoice.setEnabled(true);status.setText("Selected: "+city.label()+"\n"+city.zone);dialog.getButton(AlertDialog.BUTTON_POSITIVE).requestFocus();}).setNegativeButton("Back",null).create();host.showDialog(choices);});});
    }
}
