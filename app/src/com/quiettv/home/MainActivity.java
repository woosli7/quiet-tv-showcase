package com.quiettv.home;

import android.app.*;
import android.os.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import android.util.LruCache;
import android.animation.ValueAnimator;
import java.util.concurrent.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {
    static final int CS_ACCENT=0xffe9ad72;
    static final String[] DEFAULT_APP_ORDER={"com.amazon.firetv.youtube","com.hulu.plus","com.amazon.firebat","com.cbs.ott","com.disney.disneyplus","com.amazon.cloud9"};
    final ArrayList<Entry> all = new ArrayList<>(), visible = new ArrayList<>();
    android.content.SharedPreferences prefs;
    Home screen;
    boolean setupShown;int profileGeneration;
    GoveeLights lights;
    boolean csUpcomingOnlyFavorites=false,csPastOnlyFavorites=false;
    final CsRemoteInput csUpcomingInput=new CsRemoteInput(),csPastInput=new CsRemoteInput();
    CsFeed cs;boolean csFocused=false,csResultFocus=false,csUpcomingFocus=false;int csSelected=0;long csSelectedId=0;
    long csResultDate;TextView csResultsHint;ListView csResultsList;AlertDialog csResultsPage;java.util.List<CsMatch> csResultRows=Collections.emptyList();String[] csResultLabels;
    long csUpcomingDate;TextView csUpcomingHint;ListView csUpcomingList;AlertDialog csUpcomingPage;java.util.List<CsMatch> csUpcomingRows=Collections.emptyList();String[] csUpcomingLabels;
    AlertDialog lightsPage;
    String[] lightsRows;
    String lastLightAppKey=DEFAULT_APP_ORDER[0],lastLightAppLabel="YouTube";
    int selected = 0;
    boolean active=false, idle=false, weatherBusy=false;
    int wakingKey=-1, dialogs=0;
    final ArrayList<AlertDialog> settingsPages=new ArrayList<>();
    final Map<AlertDialog,int[]> settingsFocus=new IdentityHashMap<>();
    boolean closingSettings=false;
    long lastInput=SystemClock.elapsedRealtime(), lastWeatherAttempt=-1800000;
    SolarDay cachedSolarDay;int solarDate=-1;String solarValue="";
    final ExecutorService weatherWorker=Executors.newSingleThreadExecutor();
    final Handler timer = new Handler(Looper.getMainLooper());
    final Runnable tick = new Runnable() { public void run() {
        if(!active)return;
        int delay=prefs.getInt("idleMinutes",2);
        if(delay>0&&dialogs==0&&!idle&&SystemClock.elapsedRealtime()-lastInput>=delay*60000L)enterIdle();
        if(!idle&&cs!=null)cs.tick();refreshWeather(false);screen.invalidate();scheduleTick();
    } };
    void scheduleTick(){
        timer.removeCallbacks(tick);if(!active)return;
        long next=60000-System.currentTimeMillis()%60000;
        int minutes=prefs.getInt("idleMinutes",2);
        if(minutes>0&&!idle&&dialogs==0)next=Math.min(next,Math.max(100,minutes*60000L-(SystemClock.elapsedRealtime()-lastInput)));
        timer.postDelayed(tick,next);
    }
    void enterIdle(){enterIdle(-1);}
    void enterIdle(int previewMinute){idle=true;if(cs!=null)cs.pause();screen.idlePreviewMinute=previewMinute;screen.setAlpha(.55f);screen.animate().alpha(1).setDuration(180).start();screen.invalidate();syncAppLights(false);}
    void wake(){boolean wasIdle=idle;idle=false;screen.idlePreviewMinute=-1;lastInput=SystemClock.elapsedRealtime();screen.animate().cancel();screen.setAlpha(1);screen.invalidate();scheduleTick();if(wasIdle)syncAppLights(false);if(active&&cs!=null)cs.resume();}
    TvProfile.City profileCity(){return TvProfile.city(prefs.getString("city",""));}
    boolean fahrenheit(){return prefs.getBoolean("fahrenheit","US".equals(Locale.getDefault().getCountry()));}
    void configureZone(){TvProfile.City city=profileCity();TvProfile.configure(city!=null&&prefs.getBoolean("cityTimezone",true)?city.zone:"");}
    void saveLocation(TvProfile.City city,boolean cityZone){
        String raw="";try{if(city!=null)raw=city.json().toString();}catch(Exception ignored){}boolean changed=!raw.equals(prefs.getString("city",""))||cityZone!=prefs.getBoolean("cityTimezone",true);
        prefs.edit().putString("city",raw).putBoolean("cityTimezone",cityZone).apply();if(changed)resetProfileData();else{configureZone();screen.invalidate();}refreshWeather(false);
    }
    void resetProfileData(){profileGeneration++;prefs.edit().remove("weatherAt").remove("weatherTemp").remove("weatherCode").remove("weatherDay").remove("solarDays").apply();configureZone();solarDate=-1;solarValue="";cachedSolarDay=null;lastWeatherAttempt=-1800000;screen.formats.clear();if(cs!=null){cs.close();cs=null;}applyWidgets();}
    void startSetup(boolean locationOnly){if(!locationOnly)closeSettings();setupShown=true;new SetupFlow(this,locationOnly).show();}
    void applyWidgets(){
        if(prefs.getBoolean("widgetCs",false)){if(cs==null)cs=new CsFeed(this,()->{java.util.List<CsMatch> live=cs.live();if(csSelectedId!=0)for(int i=0;i<live.size();i++)if(live.get(i).id==csSelectedId)csSelected=i;csSelected=Math.min(csSelected,Math.max(0,live.size()-1));refreshCsResultsPage();refreshCsUpcomingPage();if(active&&!idle)screen.invalidate();});if(active&&!idle)cs.resume();}
        else{if(cs!=null)cs.close();cs=null;csFocused=csResultFocus=csUpcomingFocus=false;csSelected=0;csSelectedId=0;}
        if(screen!=null)screen.invalidate();
    }
    void widgetSettings(){
        final String[] names={"Greeting","Clock and date","Weather","Counter-Strike (optional)"},keys={"widgetGreeting","widgetClock","weather","widgetCs"};String[] rows=new String[names.length];for(int i=0;i<rows.length;i++)rows[i]=names[i]+(prefs.getBoolean(keys[i],i!=3)?": On":": Off");
        menu("Widgets",rows,(d,i)->{boolean on=!prefs.getBoolean(keys[i],i!=3);prefs.edit().putBoolean(keys[i],on).apply();updateMenu(d,rows,i,names[i]+(on?": On":": Off"));applyWidgets();refreshWeather(i==2&&on);});
    }
    void refreshWeather(boolean force){
        if(weatherBusy)return;
        final TvProfile.City city=profileCity();if(city==null)return;final int generation=profileGeneration;final String zone=TvProfile.zone().getID();final boolean useFahrenheit=fahrenheit();
        final boolean includeWeather=prefs.getBoolean("weather",true);boolean needsSolar=prefs.getInt("idleSky",1)==1&&solarDay()==null;
        if(!force&&!includeWeather&&!needsSolar)return;
        long age=System.currentTimeMillis()-prefs.getLong("weatherAt",0);
        if(!force&&((!needsSolar&&age>=0&&age<1800000)||SystemClock.elapsedRealtime()-lastWeatherAttempt<300000))return;
        weatherBusy=true;lastWeatherAttempt=SystemClock.elapsedRealtime();
        weatherWorker.execute(()->{
            Weather.Reading reading=null;try{reading=Weather.fetch(includeWeather,city,zone,useFahrenheit);}catch(Exception ignored){}
            final Weather.Reading result=reading;
            timer.post(()->{weatherBusy=false;if(isDestroyed())return;
                if(generation!=profileGeneration){if(active)refreshWeather(false);return;}
                if(result!=null){android.content.SharedPreferences.Editor update=prefs.edit();if(result.hasWeather)update.putInt("weatherTemp",result.degrees).putInt("weatherCode",result.code).putBoolean("weatherDay",result.day).putLong("weatherAt",System.currentTimeMillis());if(result.solarJson!=null)update.putString("solarDays",result.solarJson);update.apply();}
                if(active)screen.invalidate();
            });
        });
    }
    SolarDay solarDay(){
        long now=System.currentTimeMillis();int today=SolarDay.dateKey(now);String value=prefs.getString("solarDays","");
        if(today==solarDate&&value.equals(solarValue))return cachedSolarDay;
        solarDate=today;solarValue=value;cachedSolarDay=null;
        try{org.json.JSONArray rows=new org.json.JSONArray(value);for(int i=0;i<Math.min(16,rows.length());i++){org.json.JSONObject row=rows.getJSONObject(i);SolarDay day=SolarDay.forDate(row.optLong("rise",0),row.optLong("set",0),now);if(day!=null){cachedSolarDay=day;break;}}}catch(org.json.JSONException ignored){}
        return cachedSolarDay;
    }
    int previewSkyMinute(int choice){SolarDay day=solarDay();return day==null?new int[]{-1,390,720,1065,1320}[choice]:day.previewMinute(choice);}
    void solarInfo(){
        SolarDay day=solarDay();TvProfile.City city=profileCity();String message=(city==null?"Choose a city in Settings → City and time":city.label())+" / "+TvProfile.zone().getID()+"\n\n";
        if(day==null)message+="Daily sun times are unavailable. The sky temporarily uses preset times until it can refresh.";
        else{SimpleDateFormat clock=new SimpleDateFormat("h:mm a",Locale.US);clock.setTimeZone(TvProfile.zone());message+="Sunrise: "+clock.format(new Date(day.sunrise))+"\nSunset: "+clock.format(new Date(day.sunset))+"\n\nThe colors warm before sunrise and fade through twilight after sunset. Daily times are cached for offline use.";}
        showDialog(new AlertDialog.Builder(this).setTitle("Today's sun times").setMessage(message).setPositiveButton("Back",null).setNeutralButton("Refresh",(d,n)->refreshWeather(true)).create());
    }
    void rememberSettingsFocus(AlertDialog dialog){
        ListView list=dialog==csUpcomingPage?csUpcomingList:dialog==csResultsPage?csResultsList:dialog.getListView();if(list==null)return;
        View first=list.getChildAt(0);
        settingsFocus.put(dialog,new int[]{list.getSelectedItemPosition(),list.getFirstVisiblePosition(),first==null?0:first.getTop()});
    }
    void revealSettingsPage(AlertDialog dialog){
        dialog.show();ReadableText.dialog(dialog);
        if(dialog==csResultsPage){refreshCsResultsPage();if(!csResultRows.isEmpty())csResultsList.requestFocus();return;}
        if(dialog==csUpcomingPage){refreshCsUpcomingPage();if(!csUpcomingRows.isEmpty())csUpcomingList.requestFocus();return;}
        if(settingsPages.size()>1){
            android.widget.Button back=dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            if(back!=null&&dialog.getListView()!=null)back.setText("Back");
            android.widget.Button close=dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if(close!=null&&"Close".contentEquals(close.getText()))close.setText("Back");
        }
        ListView list=dialog==csUpcomingPage?csUpcomingList:dialog==csResultsPage?csResultsList:dialog.getListView();int[] saved=settingsFocus.get(dialog);
        if(list!=null&&saved!=null)list.post(()->{
            if(settingsPages.isEmpty()||settingsPages.get(settingsPages.size()-1)!=dialog||!dialog.isShowing())return;
            list.requestFocus();list.setSelectionFromTop(saved[1],saved[2]);
            if(saved[0]>=0)list.setSelection(saved[0]);
        });
    }
    void showDialog(AlertDialog dialog){
        if(!settingsPages.isEmpty()){
            AlertDialog parent=settingsPages.get(settingsPages.size()-1);
            rememberSettingsFocus(parent);parent.hide();
        }
        settingsPages.add(dialog);dialogs=settingsPages.size();
        dialog.setOnDismissListener(d->{
            if(closingSettings||settingsPages.isEmpty()||settingsPages.get(settingsPages.size()-1)!=dialog)return;
            settingsPages.remove(settingsPages.size()-1);settingsFocus.remove(dialog);dialogs=settingsPages.size();
            if(dialog==lightsPage){lightsPage=null;lightsRows=null;}
            if(dialog==csResultsPage){csResultsPage=null;csResultRows=Collections.emptyList();csResultLabels=null;}
            if(dialog==csUpcomingPage){csUpcomingPage=null;csUpcomingRows=Collections.emptyList();csUpcomingLabels=null;}
            if(!settingsPages.isEmpty())revealSettingsPage(settingsPages.get(settingsPages.size()-1));
            lastInput=SystemClock.elapsedRealtime();scheduleTick();
        });
        revealSettingsPage(dialog);
    }
    void closeSettings(){
        closingSettings=true;
        for(int i=settingsPages.size()-1;i>=0;i--){AlertDialog dialog=settingsPages.get(i);dialog.setOnDismissListener(null);dialog.dismiss();}
        settingsPages.clear();settingsFocus.clear();lightsPage=null;lightsRows=null;csResultsPage=null;csResultRows=Collections.emptyList();csResultLabels=null;csUpcomingPage=null;csUpcomingRows=Collections.emptyList();csUpcomingLabels=null;dialogs=0;closingSettings=false;
        lastInput=SystemClock.elapsedRealtime();scheduleTick();
    }
    void menu(String title,String[] items,DialogInterface.OnClickListener action){
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(title).setItems(items,null).setNegativeButton("Close",null).create();
        showDialog(dialog);
        // Handle menu selection ourselves so Android does not discard the parent page.
        dialog.getListView().setOnItemClickListener((parent,row,index,id)->action.onClick(dialog,index));
    }
    void updateMenu(DialogInterface source,String[] items,int index,String label){
        items[index]=label;ListView list=((AlertDialog)source).getListView();
        if(list!=null&&list.getAdapter() instanceof BaseAdapter)((BaseAdapter)list.getAdapter()).notifyDataSetChanged();
    }
    static class Entry { String label, key; Intent intent; Entry(String l,String k,Intent i){label=l;key=k;intent=i;} }
    public void onCreate(Bundle b) {
        super.onCreate(b);
        prefs=getSharedPreferences("quiet",MODE_PRIVATE);
        if(!prefs.contains("name"))prefs.edit().putString("name","").apply();configureZone();
        getWindow().getDecorView().setSystemUiVisibility(5894);
        screen=new Home(); setContentView(screen);applyWidgets();
        lights=new GoveeLights(this,(message,notify)->{refreshLightsRows();if(notify)Toast.makeText(this,message,Toast.LENGTH_SHORT).show();});
        if(HomeService.shouldRun(this))NightScreen.refresh(this);
    }
    void updateScreenHold(){
        // A foreground window flag prevents the TV screensaver without polling or a wake lock.
        if(active&&prefs.getBoolean("keepQuietScreen",true))getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    protected void onResume(){super.onResume();String oldZone=TvProfile.zone().getID();configureZone();if(!oldZone.equals(TvProfile.zone().getID()))resetProfileData();HomeService.clearTrackedApp(this);active=true;lights.resume();updateScreenHold();wake();loadApps();refreshWeather(false);scheduleTick();if(!prefs.getBoolean("setupComplete",false)&&!setupShown){setupShown=true;timer.post(()->{if(active&&!isDestroyed())new SetupFlow(this,false).show();});}}
    protected void onPause(){active=false;if(cs!=null)cs.pause();lights.pause();updateScreenHold();closeSettings();super.onPause();timer.removeCallbacks(tick);screen.animate().cancel();}
    protected void onDestroy(){if(cs!=null)cs.close();lights.close();weatherWorker.shutdownNow();timer.removeCallbacksAndMessages(null);super.onDestroy();}
    protected void onNewIntent(Intent intent){super.onNewIntent(intent);HomeService.clearTrackedApp(this);closeSettings();wake();}
    public boolean dispatchKeyEvent(KeyEvent event){
        int key=event.getKeyCode();
        if(wakingKey==key){if(event.getAction()==KeyEvent.ACTION_UP)wakingKey=-1;return true;}
        if(idle){wake();if(event.getAction()==KeyEvent.ACTION_DOWN)wakingKey=key;return true;}
        if(event.getAction()==KeyEvent.ACTION_DOWN){lastInput=SystemClock.elapsedRealtime();scheduleTick();}
        return super.dispatchKeyEvent(event);
    }
    void loadApps(){
        String selectedKey=selected<visible.size()?visible.get(selected).key:null;
        boolean settingsSelected=!visible.isEmpty()&&selected==visible.size();
        all.clear();visible.clear(); HashSet<String> seen=new HashSet<>();
        for(String cat:new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER,Intent.CATEGORY_LAUNCHER}) {
            Intent q=new Intent(Intent.ACTION_MAIN).addCategory(cat);
            for(ResolveInfo r:getPackageManager().queryIntentActivities(q,0)) {
                String pkg=r.activityInfo.packageName;
                if(pkg.equals(getPackageName())||!seen.add(pkg))continue;
                Intent launch=new Intent(Intent.ACTION_MAIN).addCategory(cat).setComponent(new ComponentName(pkg,r.activityInfo.name)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                all.add(new Entry(r.loadLabel(getPackageManager()).toString(),pkg,launch));
            }
        }
        String[][] favorites={{"com.amazon.firetv.youtube","YouTube"},{"com.hulu.plus","Hulu"},{"com.amazon.firebat","Prime Video"},{"com.cbs.ott","Paramount+"},{"com.disney.disneyplus","Disney+"},{"com.amazon.cloud9","Chrome"}};
        HashMap<String,Integer> rank=new HashMap<>();
        for(int f=0;f<favorites.length;f++){
            String pkg=favorites[f][0], label=favorites[f][1];rank.put(pkg,f);boolean found=false;
            for(Entry e:all)if(e.key.equals(pkg)){e.label=label;found=true;break;}
            if(!found){Intent launch=getPackageManager().getLeanbackLaunchIntentForPackage(pkg);if(launch==null)launch=getPackageManager().getLaunchIntentForPackage(pkg);
                if(launch!=null)all.add(new Entry(label,pkg,launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
            }
        }
        for(Entry e:all)if(!prefs.contains("hide:"+e.key))prefs.edit().putBoolean("hide:"+e.key,!rank.containsKey(e.key)).apply();
        Collections.sort(all,(a,b)->{int ar=rank.containsKey(a.key)?rank.get(a.key):100,br=rank.containsKey(b.key)?rank.get(b.key):100;return ar==br?a.label.compareToIgnoreCase(b.label):Integer.compare(ar,br);});
        HashMap<String,Integer> savedRank=new HashMap<>();
        try{org.json.JSONArray order=new org.json.JSONArray(prefs.getString("appOrder","[]"));for(int i=0;i<order.length();i++){String key=order.optString(i,"");if(!key.isEmpty()&&!savedRank.containsKey(key))savedRank.put(key,i);}}catch(org.json.JSONException ignored){}
        if(!savedRank.isEmpty())Collections.sort(all,(a,b)->Integer.compare(savedRank.containsKey(a.key)?savedRank.get(a.key):Integer.MAX_VALUE,savedRank.containsKey(b.key)?savedRank.get(b.key):Integer.MAX_VALUE));
        for(Entry e:all) if(!prefs.getBoolean("hide:"+e.key,false))visible.add(e);
        if(settingsSelected)selected=visible.size();
        else if(selectedKey!=null)for(int i=0;i<visible.size();i++)if(visible.get(i).key.equals(selectedKey)){selected=i;break;}
        selected=Math.max(0,Math.min(selected,visible.size())); screen.invalidate();syncAppLights(false);
    }
    void openSelected(){
        if(selected==visible.size()){settings();return;}
        try{Entry entry=visible.get(selected);ComponentName target=entry.intent.resolveActivity(getPackageManager());HomeService.trackApp(this,target==null?entry.key:target.getPackageName());startActivity(entry.intent);}catch(Exception ex){HomeService.clearTrackedApp(this);Toast.makeText(this,"This app could not open",Toast.LENGTH_SHORT).show();}
    }
    void settings(){
        closeSettings();
        String[] items={"Change greeting name","Choose apps to show","TV settings",prefs.getBoolean("redirect",false)?"Turn off Home / app return":"Turn on Home / app return","Appearance","Greeting","Idle clock","Weather","Rearrange apps","Lights","Night screen","Widgets","City and time","Run setup again"};
        menu("Quiet TV",items,(d,n)->{
            if(n==0){EditText input=new EditText(this);input.setSingleLine(true);input.setText(prefs.getString("name",""));input.setHint("Your name");
                showDialog(new AlertDialog.Builder(this).setTitle("Greeting name").setView(input).setPositiveButton("Save",(x,y)->{String name=input.getText().toString().trim();if(name.length()>18)name=name.substring(0,18);prefs.edit().putString("name",name).apply();screen.invalidate();}).setNegativeButton("Cancel",null).create());
            }else if(n==1){String[] labels=new String[all.size()];boolean[] checked=new boolean[all.size()];for(int i=0;i<all.size();i++){labels[i]=all.get(i).label;checked[i]=!prefs.getBoolean("hide:"+all.get(i).key,false);}
                showDialog(new AlertDialog.Builder(this).setTitle("Apps on your home screen").setMultiChoiceItems(labels,checked,(x,i,on)->checked[i]=on).setPositiveButton("Save",(x,y)->{android.content.SharedPreferences.Editor edit=prefs.edit();for(int i=0;i<all.size();i++)edit.putBoolean("hide:"+all.get(i).key,!checked[i]);edit.apply();loadApps();}).setNegativeButton("Cancel",null).create());
            }else if(n==2){closeSettings();try{startActivity(new Intent("android.settings.SETTINGS"));}catch(Exception ex){Toast.makeText(this,"Use the remote to open TV settings",0).show();}}
            else if(n==3){boolean enabled=!prefs.getBoolean("redirect",false);prefs.edit().putBoolean("redirect",enabled).apply();if(!enabled)HomeService.clearTrackedApp(this);if(HomeService.shouldRun(this))NightScreen.refresh(this);else stopService(new Intent(this,HomeService.class));updateMenu(d,items,3,enabled?"Turn off Home / app return":"Turn on Home / app return");}
            else if(n==4)appearance();else if(n==5)greetingSettings();else if(n==6)idleSettings();else if(n==7)weatherSettings();else if(n==8)rearrangeApps();else if(n==9)lightSettings();else if(n==10)nightSettings();else if(n==11)widgetSettings();else startSetup(n==12);
        });
    }
    int defaultRank(String key){for(int i=0;i<DEFAULT_APP_ORDER.length;i++)if(DEFAULT_APP_ORDER[i].equals(key))return i;return 100;}
    void saveAppOrder(ArrayList<Entry> draft){
        org.json.JSONArray order=new org.json.JSONArray();HashSet<String> saved=new HashSet<>();
        for(Entry entry:draft){order.put(entry.key);saved.add(entry.key);}
        for(Entry entry:all)if(saved.add(entry.key))order.put(entry.key);
        prefs.edit().putString("appOrder",order.toString()).apply();loadApps();
        Toast.makeText(this,"App order saved",Toast.LENGTH_SHORT).show();
    }
    void rearrangeApps(){
        if(visible.isEmpty()){Toast.makeText(this,"Choose some apps to show first",Toast.LENGTH_SHORT).show();return;}
        ArrayList<Entry> draft=new ArrayList<>(visible);String[] labels=new String[draft.size()];int[] moving={-1,-1};
        Runnable labelRows=()->{for(int i=0;i<draft.size();i++)labels[i]=String.format(Locale.US,"%02d  %s%s",i+1,i==moving[0]?"↕  ":"",draft.get(i).label);};
        labelRows.run();
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Rearrange apps • OK to pick up").setItems(labels,null)
            .setPositiveButton("Save order",(d,n)->saveAppOrder(draft)).setNeutralButton("Reset",null).setNegativeButton("Cancel",null).create();
        showDialog(dialog);dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setText("Cancel");
        ListView list=dialog.getListView();
        Runnable refresh=()->{labelRows.run();((BaseAdapter)list.getAdapter()).notifyDataSetChanged();dialog.setTitle(moving[0]<0?"Rearrange apps • OK to pick up":"Up / Down to move • OK to drop");};
        list.setOnItemClickListener((parent,row,index,id)->{moving[0]=index;moving[1]=index;refresh.run();list.setSelection(index);});
        dialog.setOnKeyListener((d,key,event)->{
            if(moving[0]<0)return false;
            if(key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_DOWN){
                if(event.getAction()==KeyEvent.ACTION_DOWN){int next=Math.max(0,Math.min(draft.size()-1,moving[0]+(key==KeyEvent.KEYCODE_DPAD_UP?-1:1)));if(next!=moving[0]){Collections.swap(draft,moving[0],next);moving[0]=next;refresh.run();list.setSelection(next);}}
                return true;
            }
            if(key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER){if(event.getAction()==KeyEvent.ACTION_UP){int row=moving[0];moving[0]=-1;refresh.run();list.setSelection(row);}return true;}
            if(key==KeyEvent.KEYCODE_BACK){if(event.getAction()==KeyEvent.ACTION_UP){Entry entry=draft.remove(moving[0]);draft.add(moving[1],entry);int row=moving[1];moving[0]=-1;refresh.run();list.setSelection(row);}return true;}
            return false;
        });
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->{
            Collections.sort(draft,(a,b)->{int ar=defaultRank(a.key),br=defaultRank(b.key);return ar==br?a.label.compareToIgnoreCase(b.label):Integer.compare(ar,br);});
            moving[0]=-1;refresh.run();list.requestFocus();list.setSelection(0);
        });
    }
    void choice(String title,String key,String[] options,int[] values,int defaultValue){
        int value=prefs.getInt(key,defaultValue),checked=0;for(int i=0;i<values.length;i++)if(value==values[i])checked=i;
        showDialog(new AlertDialog.Builder(this).setTitle(title).setSingleChoiceItems(options,checked,(d,n)->{
            prefs.edit().putInt(key,values[n]).apply();screen.invalidate();d.dismiss();scheduleTick();
        }).setNegativeButton("Cancel",null).create());
    }
    void appearance(){
        menu("Appearance",new String[]{"Color theme","Background glow","Highlight brightness","Type style"},(d,n)->{
            if(n==0)choice("Color theme","theme",new String[]{"App brand colors","Amber terminal","Icy blue"},new int[]{0,1,2},0);
            else if(n==1)choice("Background glow","glow",new String[]{"Off","Subtle","Medium","Bright"},new int[]{0,1,2,3},1);
            else if(n==2)choice("Highlight brightness","brightness",new String[]{"65%","85%","100%"},new int[]{65,85,100},100);
            else choice("Type style","typeStyle",new String[]{"Clean terminal","Original dot matrix"},new int[]{1,0},1);
        });
    }
    void greetingSettings(){
        menu("Greeting",new String[]{"Simple hello","Change with the time of day","Write a custom phrase"},(d,n)->{
            if(n<2){prefs.edit().putInt("greetingMode",n).apply();screen.invalidate();return;}
            EditText input=new EditText(this);input.setSingleLine(true);input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(60)});
            input.setText(prefs.getString("greetingText","WELCOME HOME, {name}"));input.setHint("Use {name} to include your name");
            showDialog(new AlertDialog.Builder(this).setTitle("Custom greeting").setView(input).setPositiveButton("Save",(x,y)->{
                String phrase=input.getText().toString().trim();if(!phrase.isEmpty())prefs.edit().putString("greetingText",phrase).putInt("greetingMode",2).apply();screen.invalidate();
            }).setNegativeButton("Cancel",null).create());
        });
    }
    void idleSettings(){
        String[] items={"Choose idle delay","Preview idle screen",prefs.getBoolean("keepQuietScreen",true)?"Keep Quiet TV on screen: On":"Keep Quiet TV on screen: Off","Idle background","Today's sun times"};
        menu("Idle clock",items,(d,n)->{
            if(n==0)choice("Show idle clock after","idleMinutes",new String[]{"Never","2 minutes","5 minutes","10 minutes"},new int[]{0,2,5,10},2);
            else if(n==1)showDialog(new AlertDialog.Builder(this).setTitle("Preview idle sky").setItems(new String[]{"Current time","Sunrise","Daylight","Sunset","Night"},(page,index)->{closeSettings();enterIdle(previewSkyMinute(index));}).setNegativeButton("Back",null).create());
            else if(n==2){boolean enabled=!prefs.getBoolean("keepQuietScreen",true);prefs.edit().putBoolean("keepQuietScreen",enabled).apply();updateScreenHold();updateMenu(d,items,2,enabled?"Keep Quiet TV on screen: On":"Keep Quiet TV on screen: Off");}
            else if(n==3)choice("Idle background","idleSky",new String[]{"Time-of-day sky","Classic plain background"},new int[]{1,0},1);else solarInfo();
        });
    }
    void weatherSettings(){
        String[] items={prefs.getBoolean("weather",true)?"Hide weather":"Show weather","Refresh now","City and time","Temperature: "+(fahrenheit()?"Fahrenheit":"Celsius"),"Weather source"};
        menu("Weather",items,(d,n)->{
            if(n==0){boolean enabled=!prefs.getBoolean("weather",true);prefs.edit().putBoolean("weather",enabled).apply();if(enabled)refreshWeather(true);screen.invalidate();updateMenu(d,items,0,enabled?"Hide weather":"Show weather");}
            else if(n==1){refreshWeather(true);Toast.makeText(this,profileCity()==null?"Choose a city first":"Refreshing weather",Toast.LENGTH_SHORT).show();}
            else if(n==2)startSetup(true);
            else if(n==3){boolean useF=!fahrenheit();prefs.edit().putBoolean("fahrenheit",useF).apply();resetProfileData();refreshWeather(true);updateMenu(d,items,3,"Temperature: "+(useF?"Fahrenheit":"Celsius"));}
            else showDialog(new AlertDialog.Builder(this).setTitle("Weather by Open-Meteo").setMessage("Weather data: Open-Meteo.com (CC BY 4.0). City search uses GeoNames through Open-Meteo.\n\nWeather refreshes every 30 minutes while visible. Only your selected city's coordinates are sent; device location is not requested. Solar times may still refresh when the weather widget is hidden.").setPositiveButton("Close",null).create());
        });
    }
    static int appBrandColor(String key){
        if(key.equals("com.amazon.firetv.youtube"))return 0xffff3434;
        if(key.equals("com.hulu.plus"))return 0xff1ce783;
        if(key.equals("com.spotify.tv.android")||key.equals("com.spotify.music"))return 0xff1ed760;
        if(key.equals("com.amazon.firebat"))return 0xff00a8e1;
        if(key.equals("com.cbs.ott"))return 0xff348bff;
        if(key.equals("com.disney.disneyplus"))return 0xff7bdded;
        if(key.equals("com.amazon.cloud9"))return 0xff4285f4;
        return 0xfff05243;
    }
    int lightBrightness(){return prefs.getInt("lightBrightness",45);}
    void nightSettings(){
        String[] strengths={"Gentle","Balanced","Strong"};
        String[] rows={NightScreen.enabled(this)?"Night screen: On":"Night screen: Off","Strength: "+strengths[Math.max(0,Math.min(2,prefs.getInt("nightStrength",1)))],"Screen filter permission","About the DIRECTV shortcut"};
        menu("Night screen",rows,(parent,n)->{
            if(n==0){boolean on=!NightScreen.enabled(this);if(NightScreen.set(this,on)){NightScreen.refresh(this);updateMenu(parent,rows,0,on?"Night screen: On":"Night screen: Off");}else Toast.makeText(this,"Allow the screen filter permission first",Toast.LENGTH_LONG).show();}
            else if(n==1)showDialog(new AlertDialog.Builder(this).setTitle("Warmth and dimming").setSingleChoiceItems(strengths,prefs.getInt("nightStrength",1),(d,i)->{prefs.edit().putInt("nightStrength",i).apply();if(HomeService.shouldRun(this))NightScreen.refresh(this);updateMenu(parent,rows,1,"Strength: "+strengths[i]);d.dismiss();}).setNegativeButton("Cancel",null).create());
            else if(n==2){if(NightScreen.allowed(this)){Toast.makeText(this,"Screen filter is allowed",Toast.LENGTH_SHORT).show();return;}try{closeSettings();startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,android.net.Uri.parse("package:"+getPackageName())));}catch(Exception ex){Toast.makeText(this,"Enable Quiet TV's display-over-apps permission during setup",Toast.LENGTH_LONG).show();}}
            else showDialog(new AlertDialog.Builder(this).setTitle("A warmer screen for the evening").setMessage("Press DIRECTV once to toggle the warm, dim screen filter. Press it again to restore normal colors. You can also toggle it here.\n\nGentle, Balanced, and Strong change warmth and dimming together. The filter does not capture the screen or intercept remote input. No continuous animation is used.\n\nThe shortcut uses the Quiet TV Night button helper. To use the official DIRECTV app later, remove that helper first.\n\nThis adjusts displayed colors and brightness; sleep benefits are not guaranteed.").setPositiveButton("Back",null).create());
        });
    }
    void syncAppLights(boolean manual){
        if(lights==null||!active||!prefs.getBoolean("lightAppSync",false))return;
        if(idle){if(prefs.getBoolean("lightIdleDim",true))lights.solid(0x303a70,10,!manual,"Idle blue");return;}
        if(csFocused&&cs!=null){lights.solid(CS_ACCENT,lightBrightness(),!manual,"Counter-Strike lights");return;}
        // Settings is neutral; entering it does not change the current app's lighting.
        if(selected<visible.size()){lastLightAppKey=visible.get(selected).key;lastLightAppLabel=visible.get(selected).label;}
        lights.solid(appBrandColor(lastLightAppKey),lightBrightness(),!manual,lastLightAppLabel+" lights");
    }
    void stopAppLights(){prefs.edit().putBoolean("lightAppSync",false).apply();lights.cancel();refreshLightsRows();}
    void refreshLightsRows(){
        if(lightsRows==null||lightsPage==null)return;
        updateMenu(lightsPage,lightsRows,0,prefs.getBoolean("lightAppSync",false)?"App colors: On":"App colors: Off");
        updateMenu(lightsPage,lightsRows,4,"Brightness: "+lightBrightness()+"%");
        updateMenu(lightsPage,lightsRows,5,prefs.getBoolean("lightIdleDim",true)?"Dim with idle clock: On":"Dim with idle clock: Off");
        updateMenu(lightsPage,lightsRows,9,lights.status);
    }
    void lightSettings(){
        lightsRows=new String[]{prefs.getBoolean("lightAppSync",false)?"App colors: On":"App colors: Off","Ambient looks","Moving effects","Mix your own colors","Brightness: "+lightBrightness()+"%",prefs.getBoolean("lightIdleDim",true)?"Dim with idle clock: On":"Dim with idle clock: Off","Lights on","Lights off","Camera matching / help",lights.status};
        menu("Quiet TV • Lights",lightsRows,(d,n)->{
            if(n==0){boolean on=!prefs.getBoolean("lightAppSync",false);prefs.edit().putBoolean("lightAppSync",on).apply();lights.cancel();refreshLightsRows();if(on)syncAppLights(true);else Toast.makeText(this,"App colors stopped • current color stays",Toast.LENGTH_SHORT).show();}
            else if(n==1)menu("Ambient looks",GoveeLights.LOOK_NAMES,(x,i)->{stopAppLights();lights.palette(GoveeLights.LOOK_COLORS[i],lightBrightness(),GoveeLights.LOOK_NAMES[i]);});
            else if(n==2)menu("Moving effects • run on the lights",GoveeLights.MOTION_NAMES,(x,i)->{stopAppLights();lights.motion(i,lightBrightness());});
            else if(n==3)customLights();
            else if(n==4){String[] levels={"10%","25%","45%","65%","85%","100%"};int[] values={10,25,45,65,85,100};int checked=2;for(int i=0;i<values.length;i++)if(values[i]==lightBrightness())checked=i;
                showDialog(new AlertDialog.Builder(this).setTitle("Light brightness").setSingleChoiceItems(levels,checked,(x,i)->{prefs.edit().putInt("lightBrightness",values[i]).apply();refreshLightsRows();if(prefs.getBoolean("lightAppSync",false))syncAppLights(true);else lights.brightness(values[i]);x.dismiss();}).setNegativeButton("Cancel",null).create());
            }else if(n==5){prefs.edit().putBoolean("lightIdleDim",!prefs.getBoolean("lightIdleDim",true)).apply();refreshLightsRows();}
            else if(n==6)lights.power(true);
            else if(n==7){stopAppLights();lights.power(false);}
            else showDialog(new AlertDialog.Builder(this).setTitle("Your lights, your atmosphere").setMessage("App colors follow your selection while Quiet TV is open. Idle dimming uses blue at 10%. When you open another app, the last light color stays.\n\nAmbient looks mix colors across the strip. Moving effects run on the lights, without a TV animation loop.\n\nCustom colors replace camera matching. To return to camera sync: stop App colors here, then open Govee Home → your device → Video. Camera mode cannot be restored through this model's API.\n\nChanges need internet and may take a few seconds.\n\n"+lights.status).setPositiveButton("Back",null).setNeutralButton("Stop app colors",(x,i)->stopAppLights()).create());
        });
        lightsPage=settingsPages.get(settingsPages.size()-1);
    }
    static final String[] LIGHT_COLOR_NAMES={"Cyan","Neon pink","Violet","Electric blue","Sunset orange","Mint","Warm white","Red"};
    static final int[] LIGHT_COLORS={0x00d5ff,0xff208e,0x8338ff,0x245bff,0xff8a20,0x1ce783,0xffc786,0xff3434};
    int customLightIndex(String key,int fallback){return Math.max(0,Math.min(LIGHT_COLORS.length-1,prefs.getInt(key,fallback)));}
    void customLights(){
        String[] rows={"First color: "+LIGHT_COLOR_NAMES[customLightIndex("lightColorA",0)],"Second color: "+LIGHT_COLOR_NAMES[customLightIndex("lightColorB",1)],"Apply my mix"};
        menu("Make your own look",rows,(parent,n)->{
            if(n==2){stopAppLights();lights.palette(new int[]{LIGHT_COLORS[customLightIndex("lightColorA",0)],LIGHT_COLORS[customLightIndex("lightColorB",1)]},lightBrightness(),"Your color mix");return;}
            String key=n==0?"lightColorA":"lightColorB";
            showDialog(new AlertDialog.Builder(this).setTitle(n==0?"First color":"Second color").setSingleChoiceItems(LIGHT_COLOR_NAMES,customLightIndex(key,n),(d,i)->{prefs.edit().putInt(key,i).apply();updateMenu(parent,rows,n,(n==0?"First color: ":"Second color: ")+LIGHT_COLOR_NAMES[i]);d.dismiss();}).setNegativeButton("Cancel",null).create());
        });
    }
    String csTime(long when){SimpleDateFormat f=new SimpleDateFormat("EEE h:mm a",Locale.US);f.setTimeZone(TvProfile.zone());return f.format(new Date(when));}
    CsMatch csSelectedMatch(){if(csResultFocus||csUpcomingFocus)return null;java.util.List<CsMatch> rows=cs.live();return csSelected<rows.size()?rows.get(csSelected):null;}
    String csResultDay(){SimpleDateFormat f=new SimpleDateFormat("EEE MMM d",Locale.US);f.setTimeZone(TvProfile.zone());return f.format(new Date());}
    CharSequence csStarText(String text){if(text.indexOf('★')<0)return text;android.text.SpannableString value=new android.text.SpannableString(text);for(int i=0;i<text.length();i++)if(text.charAt(i)=='★')value.setSpan(new android.text.style.ForegroundColorSpan(CS_ACCENT),i,i+1,android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);return value;}
    String csTeamName(long id,String name){return cs!=null&&cs.favorites.contains(id,name)?"★ "+name:name;}
    String csMatchNames(CsMatch m){return csTeamName(m.teamIdA,m.a)+" vs "+csTeamName(m.teamIdB,m.b);}
    boolean csMyTeams(boolean upcoming){return upcoming?csUpcomingOnlyFavorites:csPastOnlyFavorites;}
    java.util.List<CsMatch> csFiltered(java.util.List<CsMatch> rows,boolean upcoming){return cs.favorites.filter(rows,csMyTeams(upcoming));}
    void csSetFavorite(CsFavorites.Team team,boolean on){
        CsMatch selectedMatch=csSelectedMatch();if(selectedMatch!=null)csSelectedId=selectedMatch.id;
        if(!cs.favorites.set(team.id,team.name,on)){Toast.makeText(this,CsFavorites.valid(team.name)?"You can follow up to 32 teams":"Choose a named team to follow",Toast.LENGTH_LONG).show();return;}
        prefs.edit().putString("csFavoriteTeams",cs.favorites.save()).apply();csUpcomingLabels=null;csResultLabels=null;cs.notifyChange();Toast.makeText(this,(on?"Following ":"Unfollowed ")+team.name,Toast.LENGTH_SHORT).show();
    }
    String csFavoriteRow(CsFavorites.Team t){return (cs.favorites.contains(t.id,t.name)?"★ ":"☆ ")+t.name+(cs.favorites.contains(t.id,t.name)?" / Following":" / Follow");}
    void csTeamFavorites(CsMatch match){
        if(cs==null)return;final AlertDialog parent=settingsPages.isEmpty()?null:settingsPages.get(settingsPages.size()-1);java.util.List<CsFavorites.Team> teams;
        if(match!=null)teams=Arrays.asList(new CsFavorites.Team(match.teamIdA,match.a),new CsFavorites.Team(match.teamIdB,match.b));
        else{ArrayList<CsMatch> known=new ArrayList<>(cs.matches);known.addAll(cs.schedule);known.addAll(cs.resultsLog.allRows(System.currentTimeMillis()));teams=cs.favorites.available(known);}
        if(teams.isEmpty()){showDialog(new AlertDialog.Builder(this).setTitle("Team favorites").setMessage("Teams will appear as match data loads. You can also follow teams from any match's details.").setNegativeButton("Back",null).create());return;}
        String[] labels=new String[teams.size()];for(int i=0;i<labels.length;i++)labels[i]=csFavoriteRow(teams.get(i));
        menu("Team favorites / OK to follow or unfollow",labels,(dialog,n)->{CsFavorites.Team t=teams.get(n);csSetFavorite(t,!cs.favorites.contains(t.id,t.name));for(int i=0;i<labels.length;i++)labels[i]=csFavoriteRow(teams.get(i));((BaseAdapter)((AlertDialog)dialog).getListView().getAdapter()).notifyDataSetChanged();if(match!=null&&parent!=null&&parent.getListView()==null&&(match.status.equals("finished")||match.status.equals("upcoming")))parent.setTitle(match.status.equals("finished")?csFinalLine(match):csMatchNames(match));});
    }
    View csFavoriteControls(CsMatch match){int pad=Math.round(getResources().getDisplayMetrics().density*16);LinearLayout box=new LinearLayout(this);box.setPadding(pad,0,pad,0);Button button=new Button(this);button.setText("Team favorites");button.setOnClickListener(v->csTeamFavorites(match));box.addView(button,new LinearLayout.LayoutParams(-2,-2));return box;}
    boolean csDateKeys(AlertDialog page,ListView list,CsRemoteInput input,boolean upcoming,int key,KeyEvent event){
        Button[] buttons={page.getButton(AlertDialog.BUTTON_POSITIVE),page.getButton(AlertDialog.BUTTON_NEUTRAL),page.getButton(AlertDialog.BUTTON_NEGATIVE)};
        View focus=page.getCurrentFocus();int selected=-1,requested=0;for(int i=0;i<buttons.length;i++)if(focus==buttons[i])selected=i;
        if(key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT){int direction=key==KeyEvent.KEYCODE_DPAD_RIGHT?1:-1;requested=selected>=0?direction*2:list.hasFocus()?direction:0;}
        else if(key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER)requested=selected==0?-1:selected==1?1:0;
        if(requested==0&&!input.owns(key))return false;
        int command=input.release(key,event.getAction(),event.getRepeatCount(),event.getDownTime(),event.isCanceled(),requested);
        if(command==1||command==-1){if(upcoming)csChangeUpcomingDay(command);else csChangeResultDay(command);}
        else if((command==2||command==-2)&&input.step(command,SystemClock.uptimeMillis())){
            int direction=command>0?1:-1;for(int i=selected+direction;i>=0&&i<buttons.length;i+=direction)if(buttons[i].isEnabled()){buttons[i].requestFocus();break;}
        }
        return true;
    }
    void csAddFilter(LinearLayout heading,TextView hint,boolean upcoming){
        int height=Math.round(getResources().getDisplayMetrics().density*42);LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);bar.addView(hint,new LinearLayout.LayoutParams(0,-2,1));Button filter=new Button(this);filter.setTextSize(13);ReadableText.wrap(filter);filter.setText(csMyTeams(upcoming)?"My teams ★":"All matches");bar.addView(filter,new LinearLayout.LayoutParams(-2,-2));bar.setMinimumHeight(height);heading.addView(bar);
        filter.setOnClickListener(v->{if(upcoming)csUpcomingOnlyFavorites=!csUpcomingOnlyFavorites;else csPastOnlyFavorites=!csPastOnlyFavorites;filter.setText(csMyTeams(upcoming)?"My teams ★":"All matches");if(upcoming){csUpcomingLabels=null;refreshCsUpcomingPage();}else{csResultLabels=null;refreshCsResultsPage();}filter.requestFocus();});
    }

    String csFinalLine(CsMatch m){return csTeamName(m.teamIdA,m.a)+(m.finalScore()?"  "+m.one+" - "+m.two+"  ":" vs ")+csTeamName(m.teamIdB,m.b);}
    String csUpcomingDayLabel(){long now=System.currentTimeMillis();SimpleDateFormat f=new SimpleDateFormat("EEE, MMM d",Locale.US);f.setTimeZone(CsSchedule.zone());String date=f.format(new Date(csUpcomingDate));return (csUpcomingDate==CsSchedule.day(now,0)?"Today · ":csUpcomingDate==CsSchedule.day(now,1)?"Tomorrow · ":"")+date;}
    String csUpcomingTime(long when){SimpleDateFormat f=new SimpleDateFormat("EEE MMM d, h:mm a",Locale.US);f.setTimeZone(CsSchedule.zone());return f.format(new Date(when));}
    void csUpcomingDetails(CsMatch m){showDialog(new AlertDialog.Builder(this).setTitle(csMatchNames(m)).setMessage(m.event+"\n\nScheduled "+csUpcomingTime(m.start)+" / local time"+(m.bo>0?"\nBest of "+m.bo:"")+"\n\nScheduled match / not confirmed live. Start times may change.\nSource: BO3.gg").setView(csFavoriteControls(m)).setPositiveButton("Open match page",(d,n)->{closeSettings();CsWatch.open(this,m.page(),true);}).setNegativeButton("Back",null).create());}
    void csChangeUpcomingDay(int delta){long next=CsSchedule.browseDay(csUpcomingDate,delta,System.currentTimeMillis());if(next==csUpcomingDate)return;if(!csUpcomingInput.step(delta,SystemClock.uptimeMillis()))return;View focused=csUpcomingPage.getCurrentFocus();boolean fromList=csUpcomingList.hasFocus();csUpcomingDate=next;csUpcomingLabels=null;csUpcomingRows=Collections.emptyList();refreshCsUpcomingPage();if(fromList)csUpcomingList.requestFocus();else if(focused!=null&&focused.isEnabled())focused.requestFocus();else csUpcomingPage.getButton(AlertDialog.BUTTON_NEGATIVE).requestFocus();cs.refreshSchedule(false);}
    void csShowUpcoming(){
        if(csUpcomingPage!=null&&csUpcomingPage.isShowing())return;csUpcomingInput.reset();csUpcomingOnlyFavorites=false;csUpcomingDate=CsSchedule.day(System.currentTimeMillis(),0);csUpcomingLabels=null;
        int pad=Math.round(getResources().getDisplayMetrics().density*16);LinearLayout heading=new LinearLayout(this);heading.setOrientation(LinearLayout.VERTICAL);heading.setPadding(0,0,0,0);
        csUpcomingHint=new TextView(this);csUpcomingHint.setTag("quiet-date");csUpcomingHint.setPadding(0,pad/2,pad/2,pad/2);csAddFilter(heading,csUpcomingHint,true);csUpcomingList=new ListView(this);csUpcomingList.setAdapter(csRowsAdapter(new String[]{"Checking schedule..."},Collections.emptyList()));heading.addView(csUpcomingList,new LinearLayout.LayoutParams(-1,pad*7));
        csUpcomingPage=new AlertDialog.Builder(this).setTitle("Upcoming matches").setView(heading).setNeutralButton("Next day",null).setPositiveButton("Previous day",null).setNegativeButton("Back",null).create();showDialog(csUpcomingPage);
        csUpcomingList.setOnItemClickListener((parent,row,index,id)->{if(index<csUpcomingRows.size())csUpcomingDetails(csUpcomingRows.get(index));});
        csUpcomingPage.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->csChangeUpcomingDay(-1));csUpcomingPage.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->csChangeUpcomingDay(1));
        csUpcomingPage.setOnKeyListener((dialog,key,event)->{if(csDateKeys(csUpcomingPage,csUpcomingList,csUpcomingInput,true,key,event))return true;if(key==KeyEvent.KEYCODE_MENU){if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0)cs.refreshSchedule(true);return true;}return false;});
        refreshCsUpcomingPage();if(!csUpcomingRows.isEmpty())csUpcomingList.requestFocus();cs.refreshSchedule(false);
    }
    void refreshCsUpcomingPage(){
        if(cs==null||csUpcomingPage==null||!csUpcomingPage.isShowing())return;long now=System.currentTimeMillis(),selectedDay=CsSchedule.browseDay(csUpcomingDate,0,now);if(csUpcomingDate!=selectedDay){csUpcomingDate=selectedDay;csUpcomingLabels=null;}
        java.util.List<CsMatch> rows=csFiltered(cs.upcoming(csUpcomingDate),true);String[] labels;boolean today=csUpcomingDate==CsSchedule.day(now,0);
        if(rows.isEmpty()){String status=csMyTeams(true)&&cs.favorites.empty()?"No teams followed / Switch to All matches":cs.scheduleBusy?"Checking the schedule...":!cs.scheduleFailure.isEmpty()?cs.scheduleFailure:CsSchedule.fresh(cs.scheduleAt,now)?csMyTeams(true)?cs.favorites.empty()?"No teams followed / Switch to All matches":"No matches for your teams this day":today?"No more featured matches scheduled today":"No featured matches scheduled this day":"Schedule needs a refresh / Press Menu";labels=new String[]{status};}
        else{boolean saved=!cs.scheduleFailure.isEmpty();labels=new String[rows.size()+(saved?1:0)];for(int i=0;i<rows.size();i++){CsMatch m=rows.get(i);labels[i]=csMatchNames(m)+"\n"+m.event+"\n"+csTime(m.start)+" / SCHEDULED";}if(saved)labels[rows.size()]="Saved schedule may be incomplete / Menu to refresh";}
        csUpcomingPage.setTitle("Upcoming matches");csUpcomingHint.setText(csUpcomingDayLabel());
        csUpcomingPage.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(csUpcomingDate>CsSchedule.day(now,0));csUpcomingPage.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(csUpcomingDate<CsSchedule.day(now,CsSchedule.DAYS-1));
        if(Arrays.equals(csUpcomingLabels,labels)){csUpcomingRows=rows;return;}
        ListView list=csUpcomingList;int selected=list.getSelectedItemPosition();long selectedId=selected>=0&&selected<csUpcomingRows.size()?csUpcomingRows.get(selected).id:0;csUpcomingRows=rows;csUpcomingLabels=labels;list.setAdapter(csRowsAdapter(labels,rows));csResizeList(list);int focus=0;for(int i=0;i<rows.size();i++)if(rows.get(i).id==selectedId)focus=i;list.setSelection(focus);
    }

    void csResultDetails(CsMatch m){
        String summary=m.finalScore()?m.winner()+" won the series "+Math.max(m.one,m.two)+" - "+Math.min(m.one,m.two):"Finished / final series score unavailable";
        AlertDialog detail=new AlertDialog.Builder(this).setTitle(csFinalLine(m)).setMessage(m.event+"\n\n"+summary+"\nFinished "+csTime(m.ended)+" / local time\n\nSource: BO3.gg").setView(csFavoriteControls(m)).setNeutralButton("Highlights",null).setPositiveButton("Open match page",(d,n)->{closeSettings();CsWatch.open(this,m.page(),true);}).setNegativeButton("Back",null).create();showDialog(detail);detail.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->csHighlights(m));
    }
    void csHighlights(CsMatch match){
        if(cs==null)return;ProgressDialog progress=new ProgressDialog(this);progress.setMessage("Checking official highlights...");progress.setCancelable(true);showDialog(progress);
        Future<?> task=cs.highlights(match,result->{boolean showing=progress.isShowing();progress.dismiss();if(!showing||!active||isDestroyed())return;
            if(result.videos.size()==1){closeSettings();if(!CsWatch.open(this,result.videos.get(0).url(),false))Toast.makeText(this,"Highlights could not open",Toast.LENGTH_LONG).show();return;}
            if(!result.videos.isEmpty()){String[] labels=new String[result.videos.size()];for(int i=0;i<labels.length;i++)labels[i]=result.videos.get(i).title;showDialog(new AlertDialog.Builder(this).setTitle("Official highlights / "+match.a+" vs "+match.b).setItems(labels,(d,n)->{closeSettings();if(!CsWatch.open(this,result.videos.get(n).url(),false))Toast.makeText(this,"Highlights could not open",Toast.LENGTH_LONG).show();}).setNegativeButton("Back",null).create());return;}
            AlertDialog.Builder page=new AlertDialog.Builder(this).setTitle("Match highlights").setMessage(result.message+(result.source==null?"":"\n\n"+result.source.name+"\n"+match.a+" vs "+match.b+"\n\nChannel search is a fallback, not a verified match video.")).setNegativeButton("Back",null);
            if(result.source!=null)page.setPositiveButton("Search official channel",(d,n)->{closeSettings();if(!CsWatch.open(this,result.source.search(match),true))Toast.makeText(this,"Channel search could not open",Toast.LENGTH_LONG).show();});else page.setPositiveButton("Open match page",(d,n)->{closeSettings();CsWatch.open(this,match.page(),true);});showDialog(page.create());
        });progress.setOnCancelListener(d->task.cancel(true));
    }
    String csPastDayLabel(){long now=System.currentTimeMillis();SimpleDateFormat f=new SimpleDateFormat("EEE, MMM d",Locale.US);f.setTimeZone(CsResults.zone());return (csResultDate==CsResults.day(now,0)?"Today · ":csResultDate==CsResults.day(now,-1)?"Yesterday · ":"")+f.format(new Date(csResultDate));}
    void csChangeResultDay(int delta){long next=CsResults.browseDay(csResultDate,delta,System.currentTimeMillis());if(next==csResultDate)return;if(!csPastInput.step(delta,SystemClock.uptimeMillis()))return;View focused=csResultsPage.getCurrentFocus();boolean fromList=csResultsList.hasFocus();csResultDate=next;csResultLabels=null;csResultRows=Collections.emptyList();refreshCsResultsPage();if(fromList)csResultsList.requestFocus();else if(focused!=null&&focused.isEnabled())focused.requestFocus();else csResultsPage.getButton(AlertDialog.BUTTON_NEGATIVE).requestFocus();cs.refreshResults(false);}
    void csShowResults(){
        if(csResultsPage!=null&&csResultsPage.isShowing())return;csPastInput.reset();csPastOnlyFavorites=false;csResultDate=CsResults.day(System.currentTimeMillis(),0);csResultLabels=null;
        int pad=Math.round(getResources().getDisplayMetrics().density*16);LinearLayout heading=new LinearLayout(this);heading.setOrientation(LinearLayout.VERTICAL);heading.setPadding(0,0,0,0);
        csResultsHint=new TextView(this);csResultsHint.setTag("quiet-date");csResultsHint.setPadding(0,pad/2,pad/2,pad/2);csAddFilter(heading,csResultsHint,false);csResultsList=new ListView(this);csResultsList.setAdapter(csRowsAdapter(new String[]{"Checking past matches..."},Collections.emptyList()));heading.addView(csResultsList,new LinearLayout.LayoutParams(-1,pad*7));
        csResultsPage=new AlertDialog.Builder(this).setTitle("Past matches").setView(heading).setPositiveButton("Previous day",null).setNeutralButton("Next day",null).setNegativeButton("Back",null).create();showDialog(csResultsPage);
        csResultsList.setOnItemClickListener((parent,row,index,id)->{if(index<csResultRows.size())csResultDetails(csResultRows.get(index));});
        csResultsPage.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->csChangeResultDay(-1));csResultsPage.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->csChangeResultDay(1));
        csResultsPage.setOnKeyListener((dialog,key,event)->{if(csDateKeys(csResultsPage,csResultsList,csPastInput,false,key,event))return true;if(key==KeyEvent.KEYCODE_MENU){if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0)cs.refreshResults(true);return true;}return false;});
        refreshCsResultsPage();if(!csResultRows.isEmpty())csResultsList.requestFocus();cs.refreshResults(false);
    }
    void refreshCsResultsPage(){
        if(cs==null||csResultsPage==null||!csResultsPage.isShowing())return;long now=System.currentTimeMillis(),selectedDay=CsResults.browseDay(csResultDate,0,now);if(selectedDay!=csResultDate){csResultDate=selectedDay;csResultLabels=null;}
        java.util.List<CsMatch> rows=csFiltered(cs.results(csResultDate),false);String[] labels;boolean current=csResultDate==CsResults.day(now,0),fresh=CsResults.fresh(cs.resultsLog.checkedAt,now);
        if(rows.isEmpty()){String status=csMyTeams(false)&&cs.favorites.empty()?"No teams followed / Switch to All matches":cs.resultsBusy?"Checking past matches...":!cs.resultFailure.isEmpty()?cs.resultFailure:fresh?csMyTeams(false)?cs.favorites.empty()?"No teams followed / Switch to All matches":"No finished matches for your teams this day":current?"No featured matches have finished today":"No featured matches finished this day":"Past matches need a refresh / Press Menu";labels=new String[]{status};}
        else{boolean saved=!cs.resultFailure.isEmpty()||!fresh&&!cs.resultsBusy;labels=new String[rows.size()+(saved?1:0)];for(int i=0;i<rows.size();i++){CsMatch m=rows.get(i);labels[i]=csFinalLine(m)+"\n"+m.event+"\nFINAL / "+csTime(m.ended);}if(saved)labels[rows.size()]="Saved matches / Menu to check for more";}
        csResultsPage.setTitle("Past matches");csResultsHint.setText(csPastDayLabel());
        csResultsPage.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(csResultDate>CsResults.day(now,1-CsResults.DAYS));csResultsPage.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(csResultDate<CsResults.day(now,0));
        if(Arrays.equals(csResultLabels,labels)){csResultRows=rows;return;}
        ListView list=csResultsList;int selected=list.getSelectedItemPosition();long selectedId=selected>=0&&selected<csResultRows.size()?csResultRows.get(selected).id:0;csResultRows=rows;csResultLabels=labels;list.setAdapter(csRowsAdapter(labels,rows));csResizeList(list);int focus=0;for(int i=0;i<rows.size();i++)if(rows.get(i).id==selectedId)focus=i;list.setSelection(focus);
    }

    void csResizeList(ListView list){
        // Measure wrapped rows at the actual width, stopping as soon as the viewport is full.
        list.post(()->{if(list.getWindowToken()==null||list.getWidth()==0)return;
            int cap=Math.round(getResources().getDisplayMetrics().density*260),minimum=Math.round(getResources().getDisplayMetrics().density*72);
            int width=list.getWidth()-list.getPaddingLeft()-list.getPaddingRight(),height=list.getPaddingTop()+list.getPaddingBottom();
            ListAdapter adapter=list.getAdapter();View row=null;
            for(int i=0;i<adapter.getCount()&&height<cap;i++){row=adapter.getView(i,row,list);row.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));height+=row.getMeasuredHeight()+(i>0?list.getDividerHeight():0);}
            ViewGroup.LayoutParams params=list.getLayoutParams();int target=Math.min(cap,Math.max(minimum,height));if(params.height!=target){params.height=target;list.setLayoutParams(params);}
        });
    }
    ArrayAdapter<String> csRowsAdapter(String[] labels,java.util.List<CsMatch> rows){
        return new ArrayAdapter<String>(this,android.R.layout.select_dialog_item,labels){
            public boolean areAllItemsEnabled(){return false;}
            public boolean isEnabled(int position){return position<rows.size();}
            public View getView(int position,View reused,ViewGroup parent){
                LinearLayout row;if(reused instanceof LinearLayout)row=(LinearLayout)reused;else{
                    row=new LinearLayout(MainActivity.this);row.setOrientation(LinearLayout.VERTICAL);int pad=Math.round(getResources().getDisplayMetrics().density*16);row.setPadding(pad,pad*3/4,pad,pad*3/4);row.setMinimumHeight(pad*4);
                    TextView title=new TextView(MainActivity.this),subtitle=new TextView(MainActivity.this);Typeface face=Typeface.create("monospace",Typeface.NORMAL);title.setTypeface(face);subtitle.setTypeface(face);title.setTextSize(22);subtitle.setTextSize(14);title.setTextColor(0xffe5ebf5);subtitle.setTextColor(0xff9aa9bd);ReadableText.wrap(title);ReadableText.wrap(subtitle);row.addView(title);row.addView(subtitle);MenuStyle.row(row);
                }
                String value=getItem(position);int newline=value.indexOf('\n');((TextView)row.getChildAt(0)).setText(csStarText(newline<0?value:value.substring(0,newline)));TextView subtitle=(TextView)row.getChildAt(1);subtitle.setText(newline<0?"":value.substring(newline+1));subtitle.setVisibility(newline<0?View.GONE:View.VISIBLE);return row;
            }
        };
    }
    void csOptions(){
        menu("Live Counter-Strike",new String[]{"Upcoming matches","Past matches","Team favorites","Refresh live matches","Open CS match listings","About this feed"},(d,n)->{
            if(n==0)csShowUpcoming();
            else if(n==1)csShowResults();
            else if(n==2)csTeamFavorites(null);
            else if(n==3){cs.refresh(true);cs.refreshResults(true);cs.refreshSchedule(true);closeSettings();Toast.makeText(this,"Checking live matches",Toast.LENGTH_SHORT).show();}
            else if(n==4){closeSettings();CsWatch.open(this,"https://bo3.gg/matches/current",true);}
            else showDialog(new AlertDialog.Builder(this).setTitle("Live CS / BO3").setMessage("Match and broadcast data: BO3.gg. HLTV blocked direct access during setup.\n\nOnly current matches at prominent events are shown. LIVE labels expire after three minutes without a successful check.\n\nRight opens this section; Left returns to your apps. OK watches; Menu offers verified organizer streams.").setPositiveButton("Back",null).create());
        });
    }
    void csNext(){CsMatch m=cs.next();if(m==null){cs.refreshSchedule(true);csOptions();return;}showDialog(new AlertDialog.Builder(this).setTitle(m.event).setMessage(m.a+" vs "+m.b+"\n"+csTime(m.start)+" / local time\n\nScheduled today / not confirmed live.").setView(csFavoriteControls(m)).setPositiveButton("Open match page",(d,n)->{closeSettings();CsWatch.open(this,m.page(),true);}).setNegativeButton("Back",null).create());}
    void csResolve(boolean alternatives){
        if(csUpcomingFocus){csShowUpcoming();return;}
        if(csResultFocus){csShowResults();return;}
        CsMatch requested=csSelectedMatch();if(requested==null){if(alternatives)csOptions();else csNext();return;}
        if(cs.opening)return;
        ProgressDialog progress=new ProgressDialog(this);progress.setMessage("Finding match broadcast...");progress.setCancelable(true);showDialog(progress);
        cs.resolve(requested,alternatives,(match,streams,error)->{
            boolean showing=progress.isShowing();progress.dismiss();if(!showing||!active||isDestroyed())return;
            if(error!=null||streams.isEmpty()){
                showDialog(new AlertDialog.Builder(this).setTitle(error==null?"Stream unavailable":"Live match check").setMessage((error==null?"No match broadcast could be resolved.":error)+"\n\nYou can open the match page to check its broadcast links.").setView(csFavoriteControls(match)).setPositiveButton("Open match page",(d,n)->{closeSettings();CsWatch.open(this,match.page(),true);}).setNegativeButton("Back",null).create());return;
            }
            if(!alternatives){closeSettings();if(!CsWatch.open(this,streams.get(0).url,false))Toast.makeText(this,"The broadcast could not open",Toast.LENGTH_LONG).show();return;}
            String[] labels=new String[streams.size()+2];for(int i=0;i<streams.size();i++)labels[i]=streams.get(i).label();labels[streams.size()]="Team favorites";labels[streams.size()+1]="Open match page / browser";
            menu("Match broadcasts",labels,(d,n)->{if(n==streams.size()){csTeamFavorites(match);return;}closeSettings();if(n==streams.size()+1)CsWatch.open(this,match.page(),true);else CsWatch.open(this,streams.get(n).url,false);});
        });
    }
    public boolean onKeyDown(int key,KeyEvent ev){
        if(key==KeyEvent.KEYCODE_DPAD_RIGHT){if(cs!=null&&!csFocused){csFocused=true;syncAppLights(false);}screen.invalidate();return true;}
        if(csFocused){int liveSlots=Math.max(1,cs.live().size()),count=liveSlots+2;if(key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_BACK){csFocused=false;screen.invalidate();syncAppLights(false);return true;}if(key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_DOWN){int index=csResultFocus?liveSlots+1:csUpcomingFocus?liveSlots:Math.min(csSelected,liveSlots-1);index=(index+(key==KeyEvent.KEYCODE_DPAD_DOWN?1:count-1))%count;csUpcomingFocus=index==liveSlots;csResultFocus=index==liveSlots+1;if(!csResultFocus&&!csUpcomingFocus)csSelected=index;CsMatch m=csSelectedMatch();csSelectedId=m==null?0:m.id;screen.invalidate();return true;}if(key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER){if(ev.getRepeatCount()==0)csResolve(false);return true;}if(key==KeyEvent.KEYCODE_MENU){csResolve(true);return true;}}
        if(key==KeyEvent.KEYCODE_DPAD_DOWN){selected=(selected+1)%(visible.size()+1);screen.invalidate();syncAppLights(false);return true;}
        if(key==KeyEvent.KEYCODE_DPAD_UP){selected=(selected+visible.size())%(visible.size()+1);screen.invalidate();syncAppLights(false);return true;}
        if(key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER){if(ev.getRepeatCount()==0)openSelected();return true;}
        if(key==KeyEvent.KEYCODE_MENU){settings();return true;}
        if(key==KeyEvent.KEYCODE_BACK)return true;
        return super.onKeyDown(key,ev);
    }
    class Home extends View {
        final Paint p=new Paint(3);
        final Map<String,BrandBadge> brandBadges=BrandBadge.createAll();
        boolean isSpotify(String key){return key.equals("com.spotify.tv.android")||key.equals("com.spotify.music");}
        final Paint font=new Paint(3), bitmapPaint=new Paint(3);
        final Paint cleanFont=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.SUBPIXEL_TEXT_FLAG);boolean cleanType=true;
        final Paint tintedText=new Paint(3);
        final android.util.SparseArray<PorterDuffColorFilter> colorFilters=new android.util.SparseArray<>();
        final Map<String,SimpleDateFormat> formats=new HashMap<>();
        RadialGradient glowShader;int glowKey=-1;
        final IdleSky idleSky=new IdleSky();int idlePreviewMinute=-1;
        String localTime(String pattern){SimpleDateFormat format=formats.get(pattern);if(format==null){format=new SimpleDateFormat(pattern,Locale.US);format.setTimeZone(TvProfile.zone());formats.put(pattern,format);}return format.format(new Date());}
        int highlight(int color){float strength=prefs.getInt("brightness",100)/100f;return Color.rgb(Math.round(Color.red(color)*strength),Math.round(Color.green(color)*strength),Math.round(Color.blue(color)*strength));}
        int themeColor(){return prefs.getInt("theme",0)==1?0xffffbd69:0xff91d9ff;}
        String greeting(){
            String name=prefs.getString("name","");int mode=prefs.getInt("greetingMode",1);
            if(mode==2)return prefs.getString("greetingText","WELCOME HOME, {name}").replace("{name}",name);
            String phrase="HELLO";
            if(mode==1){int hour=Integer.parseInt(localTime("H"));phrase=hour>=5&&hour<12?"GOOD MORNING":hour<17&&hour>=12?"GOOD AFTERNOON":hour>=17&&hour<22?"GOOD EVENING":"GOOD NIGHT";}
            return name.isEmpty()?phrase:phrase+", "+name;
        }
        int brandColor(String key){
            if(prefs.getInt("theme",0)!=0)return themeColor();
            return appBrandColor(key);
        }
        float textWidth(String text,float height){
            if(cleanType){cleanFont.setTextSize(height);return cleanFont.measureText(text.toUpperCase(Locale.ROOT));}
            return font.measureText(text.toUpperCase(Locale.ROOT))*height/13f;
        }
        void dotCurve(Canvas c,float x,float y,float width,float rise,int color){
            p.setColor(color);p.setShader(null);
            int steps=Math.max(12,(int)(width/3));
            for(int n=0;n<=steps;n++){float t=n/(float)steps;c.drawCircle(x+t*width,y+4*rise*t*(1-t),.9f,p);}
        }
        void brandedLabel(Canvas c,Entry app,float x,float y,float height){
            int color=highlight(brandColor(app.key));boolean branded=prefs.getInt("theme",0)==0;
            if(app.key.equals("com.amazon.firetv.youtube")&&branded){
                dots(c,app.label,x,y,height,color,false);
                c.save();c.clipRect(x+textWidth("YOU",height)-.5f,y-10,1000,y+height+10);
                dots(c,app.label,x,y,height,highlight(0xfff8f8ff),false);c.restore();
            }else if(app.key.equals("com.amazon.cloud9")&&branded){
                int[] colors={0xff4285f4,0xffea4335,0xfffbbc05,0xff4285f4,0xff34a853,0xffea4335};
                for(int n=0;n<app.label.length();n++){
                    float left=x+textWidth(app.label.substring(0,n),height),right=x+textWidth(app.label.substring(0,n+1),height);
                    c.save();c.clipRect(left-.5f,y-10,right-.5f,y+height+10);dots(c,app.label,x,y,height,highlight(colors[n%colors.length]),false);c.restore();
                }
            }else if(app.key.equals("com.disney.disneyplus")){
                dots(c,app.label,x,y,height,branded?highlight(0xffeffbff):color,false);
                float wordWidth=textWidth("DISNEY",height);
                dotCurve(c,x+3,y-3,wordWidth-5,-12,color);
                c.save();c.clipRect(x+wordWidth-.5f,y-10,1000,y+height+10);
                dots(c,app.label,x,y,height,color,false);c.restore();
            }else{
                dots(c,app.label,x,y,height,color,false);
                if(app.key.equals("com.amazon.firebat")){
                    float width=textWidth("PRIME",height);
                    dotCurve(c,x+5,y+height-3,width-10,7,color);
                }
            }
        }
        class DotText { Bitmap bitmap;float width,step,density; DotText(Bitmap b,float w,float s,float d){bitmap=b;width=w;step=s;density=d;} }
        final LruCache<String,DotText> textCache=new LruCache<String,DotText>(2*1024*1024){protected int sizeOf(String key,DotText value){return value.bitmap.getByteCount();}};
        Home(){super(MainActivity.this);font.setTypeface(Typeface.create("monospace",Typeface.NORMAL));font.setTextSize(13);font.setColor(Color.WHITE);cleanFont.setTypeface(font.getTypeface());setFocusable(true);setFocusableInTouchMode(true);requestFocus();}
        void dots(Canvas c,String text,float x,float y,float height,int color,boolean right){
            text=text.toUpperCase(Locale.ROOT);
            if(cleanType){cleanFont.setTextSize(height);cleanFont.setColor(color);float left=right?x-cleanFont.measureText(text):x;c.drawText(text,left,y+height*10f/13f,cleanFont);return;}
            float density=Math.max(1,getWidth()/1280f);
            String cacheKey=text+"|"+height+"|"+density;DotText rendered=textCache.get(cacheKey);
            if(rendered==null){
                Bitmap glyph=Bitmap.createBitmap(Math.max(1,(int)Math.ceil(font.measureText(text))+2),17,Bitmap.Config.ARGB_8888);
                new Canvas(glyph).drawText(text,0,13,font);float step=height/13f;
                Bitmap bitmap=Bitmap.createBitmap(Math.max(1,(int)Math.ceil((glyph.getWidth()+2)*step*density)),Math.max(1,(int)Math.ceil(19*step*density)),Bitmap.Config.ARGB_8888);
                Canvas cached=new Canvas(bitmap);cached.scale(density,density);bitmapPaint.setColor(Color.WHITE);
                int[] pixels=new int[glyph.getWidth()*glyph.getHeight()];glyph.getPixels(pixels,0,glyph.getWidth(),0,0,glyph.getWidth(),glyph.getHeight());
                for(int yy=1;yy<16;yy++)for(int xx=0;xx<glyph.getWidth();xx++)if(Color.alpha(pixels[yy*glyph.getWidth()+xx])>90)cached.drawCircle((xx+1)*step,(yy+1)*step,step*.34f,bitmapPaint);
                rendered=new DotText(bitmap,glyph.getWidth()*step,step,density);glyph.recycle();textCache.put(cacheKey,rendered);
            }
            if(right)x-=rendered.width;
            PorterDuffColorFilter tint=colorFilters.get(color);if(tint==null){if(colorFilters.size()>=64)colorFilters.clear();tint=new PorterDuffColorFilter(color,PorterDuff.Mode.SRC_IN);colorFilters.put(color,tint);}tintedText.setColorFilter(tint);
            c.save();c.translate(x-rendered.step,y-4*rendered.step);c.scale(1/rendered.density,1/rendered.density);c.drawBitmap(rendered.bitmap,0,0,tintedText);c.restore();
        }
        String conditions(int code){if(code==0)return "CLEAR";if(code<=3)return "CLOUDY";if(code<=48)return "FOG";if(code<=57)return "DRIZZLE";if(code<=67)return "RAIN";if(code<=77)return "SNOW";if(code<=82)return "SHOWERS";if(code<=86)return "SNOW";return "STORMS";}
        void weather(Canvas c,float right,float y,boolean dim){
            if(!prefs.getBoolean("weather",true))return;TvProfile.City city=profileCity();
            long fetched=prefs.getLong("weatherAt",0);boolean sky=dim&&prefs.getInt("idleSky",1)==1;int color=dim?(sky?0xffbac6d5:0xff737d8a):0xffc6ced8;
            if(city==null){dots(c,"ADD A CITY IN SETTINGS",right,y,12,0xff737582,true);return;}
            if(fetched==0){label(c,city.name+" / WEATHER UNAVAILABLE",right,y,12,0xff737582,true,500,2,28);return;}
            boolean stale=System.currentTimeMillis()-fetched>7200000L;
            int code=prefs.getInt("weatherCode",0);String text=conditions(code)+" / "+prefs.getInt("weatherTemp",0)+(fahrenheit()?"°F":"°C");
            dots(c,text,right,y,dim?21:24,color,true);
            label(c,city.name+(stale?" / SAVED":""),right,y+32,10,sky?0xff929fb0:0xff737582,true,500,2,24);
            float iconX=right-textWidth(text,dim?21:24)-25,iconY=y+8;
            p.setShader(null);p.setColor(color);p.setStrokeWidth(1.5f);p.setStyle(Paint.Style.STROKE);
            if(code==0){
                if(prefs.getBoolean("weatherDay",true)){c.drawCircle(iconX,iconY,6,p);for(int i=0;i<8;i++){double a=i*Math.PI/4;c.drawLine(iconX+(float)Math.cos(a)*10,iconY+(float)Math.sin(a)*10,iconX+(float)Math.cos(a)*13,iconY+(float)Math.sin(a)*13,p);}}
                else{c.drawArc(iconX-9,iconY-9,iconX+9,iconY+9,55,255,false,p);}
            }else{c.drawArc(iconX-12,iconY-3,iconX,iconY+9,150,210,false,p);c.drawArc(iconX-5,iconY-9,iconX+10,iconY+7,170,200,false,p);c.drawLine(iconX-11,iconY+8,iconX+12,iconY+8,p);if(code>=51){c.drawLine(iconX-4,iconY+12,iconX-6,iconY+17,p);c.drawLine(iconX+5,iconY+12,iconX+3,iconY+17,p);}}
            p.setStyle(Paint.Style.FILL);
        }
        void idleScreen(Canvas c){
            if(prefs.getInt("idleSky",1)==1){int minute=idlePreviewMinute>=0?idlePreviewMinute:Integer.parseInt(localTime("H"))*60+Integer.parseInt(localTime("m"));SolarDay day=solarDay();idleSky.draw(c,minute,day==null?360:day.riseMinute,day==null?1110:day.setMinute,day!=null);}
            int tint=prefs.getInt("theme",0)==0?0xffb3bfcc:themeColor();
            float drift=((SystemClock.elapsedRealtime()/300000)%3-1)*5;
            String welcome=greeting();float size=Math.min(28,900f/Math.max(1,welcome.length())/.61f);
            float center=640+drift;
            if(prefs.getBoolean("widgetGreeting",true))dots(c,welcome,center+textWidth(welcome,size)/2,218,size,highlight(tint),true);
            if(prefs.getBoolean("widgetClock",true)){String clock=localTime("h:mm a");dots(c,clock,center+textWidth(clock,64)/2,290,64,prefs.getInt("idleSky",1)==1?0xffe5ebf5:0xffbec6d1,true);
            String date=localTime("EEE MMM d");dots(c,date,center+textWidth(date,20)/2,384,20,prefs.getInt("idleSky",1)==1?0xffa7b4c5:0xff6d7785,true);}
            weather(c,center+140,443,true);
            dots(c,"PRESS ANY BUTTON TO RETURN",center+textWidth("PRESS ANY BUTTON TO RETURN",10)/2,600,10,prefs.getInt("idleSky",1)==1?0xff778597:0xff47515e,true);
        }
        final LruCache<String,ReadableText.Label> labelCache=new LruCache<>(128);
        void label(Canvas c,String value,float x,float y,float size,int color,boolean right,float width,int lines,float height){
            String key=cleanType+"|"+width+"|"+size+"|"+lines+"|"+height+"|"+value;
            ReadableText.Label layout=labelCache.get(key);if(layout==null){layout=ReadableText.fit(value,width-3,size,lines,height,(text,scale)->textWidth(text,scale));labelCache.put(key,layout);}
            for(int i=0;i<layout.lines.length;i++)dots(c,layout.lines[i],x,y+i*layout.size*1.16f,layout.size,color,right);
        }
        void csLogo(Canvas c,String url,String fallback,float x,float y,float size){
            Bitmap bitmap=cs.images.get(url);if(bitmap!=null){p.setColor(0xffffffff);p.setAlpha(235);float ratio=Math.min(size/bitmap.getWidth(),size/bitmap.getHeight());float w=bitmap.getWidth()*ratio,h=bitmap.getHeight()*ratio;c.drawBitmap(bitmap,null,new RectF(x+(size-w)/2,y+(size-h)/2,x+(size+w)/2,y+(size+h)/2),p);p.setAlpha(255);}
            else{p.setColor(0xff202735);c.drawRoundRect(x,y,x+size,y+size,4,4,p);String initial=fallback.isEmpty()?"?":fallback.substring(0,1);dots(c,initial,x+size*.28f,y+size*.28f,size*.55f,0xffb2bfcd,false);}
        }
        void csCard(Canvas c,float y,boolean focus){p.setShader(null);p.setStyle(Paint.Style.FILL);p.setColor(focus?0xff1d2432:0xff10151f);c.drawRoundRect(660,y,1190,y+94,7,7,p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(focus?1.8f:1);p.setColor(focus?(prefs.getInt("theme",0)==0?CS_ACCENT:themeColor()):0xff252c38);c.drawRoundRect(660,y,1190,y+94,7,7,p);p.setStyle(Paint.Style.FILL);}
        long pastRangeDay=Long.MIN_VALUE;String pastRangeZone="",pastRangeLabel="";
        String pastMatchRange(){long now=System.currentTimeMillis(),day=CsResults.day(now,0);String zone=CsResults.zone().getID();if(day!=pastRangeDay||!zone.equals(pastRangeZone)){SimpleDateFormat f=new SimpleDateFormat("MMM d",Locale.US);f.setTimeZone(CsResults.zone());pastRangeLabel=f.format(new Date(CsResults.day(now,1-CsResults.DAYS)))+" – "+f.format(new Date(day));pastRangeDay=day;pastRangeZone=zone;}return pastRangeLabel;}
        void csLiveBadge(Canvas c,float y){
            p.setStyle(Paint.Style.FILL);p.setColor(0xff72343b);c.drawRoundRect(1110,y+11,1175,y+31,4,4,p);
            // A static broadcast mark: crisp at TV size, with no animation or new assets.
            p.setColor(0xffffb2ad);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.1f);
            c.drawArc(1113,y+15,1125,y+27,-50,100,false,p);c.drawArc(1113,y+15,1125,y+27,130,100,false,p);
            c.drawArc(1116,y+18,1122,y+24,-50,100,false,p);c.drawArc(1116,y+18,1122,y+24,130,100,false,p);
            p.setStyle(Paint.Style.FILL);c.drawCircle(1119,y+21,1.4f,p);dots(c,"LIVE",1134,y+17,10,0xffffb2ad,false);
        }
        void drawCs(Canvas c){
            if(cs==null)return;java.util.List<CsMatch> rows=cs.live();
            dots(c,"LIVE COUNTER-STRIKE",660,352,15,csFocused?0xffd8e2ed:0xff919baa,false);
            if(rows.isEmpty()){
                csCard(c,383,csFocused&&!csResultFocus&&!csUpcomingFocus);boolean verified=cs.verified();String title=verified?"NO MAJOR CS MATCHES LIVE":cs.busy?"CHECKING LIVE MATCHES":"LIVE STATUS UNAVAILABLE";
                dots(c,title,677,398,14,verified?0xffb0bdcc:0xffb8a68d,false);CsMatch next=cs.next();
                if(next!=null){csLogo(c,next.logoEvent,next.event,677,425,24);label(c,"NEXT TODAY: "+csMatchNames(next),710,424,13,0xffa9b8c9,false,453,2,26);label(c,next.event+" / "+csTime(next.start),710,455,10,0xff748297,false,453,1,12);}
                else label(c,verified?"CHECK BACK LATER":cs.failure.isEmpty()?(cs.busy?"LOADING MATCH DATA":cs.liveAt>0?"LAST CHECK EXPIRED / MENU TO RETRY":"MENU TO RETRY"):cs.failure.toUpperCase(Locale.US)+" / MENU TO RETRY",677,435,11,0xff768599,false,498,2,28);
            }else for(int i=0;i<rows.size();i++){
                CsMatch m=rows.get(i);float y=383+i*106;boolean focus=csFocused&&!csResultFocus&&!csUpcomingFocus&&csSelected==i;csCard(c,y,focus);csLogo(c,m.logoEvent,m.event,677,y+13,21);
                label(c,m.event,710,y+17,13,0xffa7b4c5,false,390,1,16);csLiveBadge(c,y);
                csLogo(c,m.logoA,m.a,677,y+40,22);label(c,csTeamName(m.teamIdA,m.a),710,y+43,18,cs.favorites.contains(m.teamIdA,m.a)?CS_ACCENT:0xffdbe2ec,false,170,2,29);dots(c,"VS",895,y+48,10,0xff657387,false);csLogo(c,m.logoB,m.b,926,y+40,22);label(c,csTeamName(m.teamIdB,m.b),959,y+43,18,cs.favorites.contains(m.teamIdB,m.b)?CS_ACCENT:0xffdbe2ec,false,210,2,29);
                String score=m.one>=0&&m.two>=0?"MAPS "+m.one+" - "+m.two:"SERIES SCORE UNAVAILABLE";dots(c,score+(m.bo>0?" / BO"+m.bo:""),710,y+76,10,0xff8d9eb2,false);if(focus)dots(c,"OK WATCH",1175,y+76,10,0xffe0b68b,true);
            }
            int upcoming=cs.upcoming().size();String resultRange=pastMatchRange();String upcomingCount=upcoming>0?upcoming+" TODAY":cs.scheduleBusy?"CHECKING":cs.scheduleFailure.isEmpty()?"VIEW":"RETRY";
            csListButton(c,594,"UPCOMING MATCHES",upcomingCount,csFocused&&csUpcomingFocus);csListButton(c,632,"PAST MATCHES",resultRange,csFocused&&csResultFocus);
            dots(c,csFocused?(csResultFocus?"OK RESULTS / LEFT APPS":csUpcomingFocus?"OK SCHEDULE / LEFT APPS":rows.isEmpty()?"OK NEXT / DOWN SCHEDULE / MENU OPTIONS":"UP/DOWN SELECT / OK WATCH / MENU STREAMS"):"RIGHT TO EXPLORE",660,674,10,0xff64748a,false);
            long age=System.currentTimeMillis()-cs.liveAt;String stamp=cs.verified()?"BO3 / CHECKED "+(age<60000?"JUST NOW":age/60000+"M AGO")+(cs.failure.isEmpty()?"":" / RETRYING"):"BO3 / "+(cs.busy?"CHECKING":"STATUS NOT VERIFIED");dots(c,stamp,660,696,9,0xff4e5c70,false);
        }
        void csListButton(Canvas c,float y,String label,String count,boolean focus){p.setShader(null);p.setStyle(Paint.Style.FILL);p.setColor(focus?0xff1d2432:0xff10151f);c.drawRoundRect(660,y,1190,y+30,6,6,p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(focus?1.8f:1);p.setColor(focus?CS_ACCENT:0xff252c38);c.drawRoundRect(660,y,1190,y+30,6,6,p);p.setStyle(Paint.Style.FILL);dots(c,label,677,y+9,13,focus?0xffe6d1b8:0xffa7b4c5,false);dots(c,count,1174,y+10,11,0xff8d9eb2,true);}
        protected void onDraw(Canvas real){
            super.onDraw(real);cleanType=prefs.getInt("typeStyle",1)==1;real.save();real.scale(getWidth()/1280f,getHeight()/720f);
            real.drawColor(Color.rgb(9,10,16));
            if(idle){idleScreen(real);real.restore();return;}
            int accent=csFocused?(prefs.getInt("theme",0)==0?CS_ACCENT:themeColor()):selected<visible.size()?brandColor(visible.get(selected).key):0xfff05243;
            int glow=prefs.getInt("glow",1),shaderKey=accent*31+glow+(csFocused?7:0);
            if(glow>0){if(shaderKey!=glowKey){int alpha=csFocused?(glow==1?28:glow==2?44:60):(glow==1?18:glow==2?34:54);glowShader=new RadialGradient(csFocused?930:80,csFocused?490:390,csFocused?610:570,new int[]{(accent&0x00ffffff)|(alpha<<24),0x00090a10},null,Shader.TileMode.CLAMP);glowKey=shaderKey;}p.setShader(glowShader);p.setAlpha(255);real.drawRect(0,0,1280,720,p);p.setShader(null);}
            String welcome=greeting();float greetingSize=Math.min(40,1000f/Math.max(1,welcome.length())/.61f);
            if(prefs.getBoolean("widgetGreeting",true))dots(real,welcome,1190,100,greetingSize,0xffeeeef4,true);
            if(prefs.getBoolean("widgetClock",true)){dots(real,localTime("h:mm a"),1190,175,48,0xffeeeef4,true);
            dots(real,localTime("EEE MMM d"),1190,240,22,0xff999ba8,true);}
            weather(real,1190,286,false);
            int count=visible.size()+1, start=Math.max(0,Math.min(selected-2,Math.max(0,count-5)));
            for(int j=0;j<Math.min(5,count-start);j++){
                int i=start+j;boolean active=i==selected&&!csFocused;float y=260+j*66;
                String label=i==visible.size()?"SETTINGS":visible.get(i).label;
                if(active){
                    p.setColor(highlight(accent));
                    BrandBadge badge=brandBadges.get(i<visible.size()?visible.get(i).key:"@settings");
                    if(badge!=null){real.save();real.translate(80,y+12);real.drawPath(badge.color,p);if(badge.layers!=null)for(int layer=0;layer<badge.layers.length;layer++){p.setColor(highlight(prefs.getInt("theme",0)==0?badge.layerColors[layer]:accent));real.drawPath(badge.layers[layer],p);}if(badge.white!=null){p.setColor(highlight(prefs.getInt("theme",0)==0?0xfff8f8ff:accent));real.drawPath(badge.white,p);}real.restore();}
                    else real.drawCircle(80,y+12,5,p);
                    if(i<visible.size()&&textWidth(label,33)<=490)brandedLabel(real,visible.get(i),112,y,33);
                    else if(i<visible.size())label(real,label,112,y,33,highlight(accent),false,490,2,53);
                    else dots(real,label,112,y,33,0xfff1f0f6,false);
                }else label(real,label,112,y,29,0xff737582,false,490,2,53);
            }
            drawCs(real);
            dots(real,cs==null?"UP / DOWN SELECT   OK OPEN   MENU SETTINGS":"UP / DOWN SELECT   OK OPEN   RIGHT CS   MENU SETTINGS",112,657,11,0xff4e505f,false);
            real.restore();
        }
    }
}
