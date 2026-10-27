package com.quiettv.home;
import android.content.Context;
import android.graphics.*;
import android.os.*;
import android.util.LruCache;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.GZIPInputStream;
import org.json.*;

/** Direct public BO3 feed. Main-thread state; bounded network/JSON work on one worker. */
final class CsFeed {
    static final int LIVE_RECORDS=100,LIVE_CACHE_BYTES=262144;
    static final String API="https://api.bo3.gg/api/v1/";
    final Context context;final Runnable changed;final CsPolicy policy;final CsBroadcasts broadcasts;final CsFavorites favorites;
    java.util.List<CsMatch> liveSource,liveView=Collections.emptyList();int liveRevision=-1;
    final LinkedHashMap<String,String> youtubeAuthors=new LinkedHashMap<>();
    final ExecutorService resultWorker=Executors.newSingleThreadExecutor();final CsResults resultsLog=new CsResults();boolean resultsBusy;long resultAttempt=-600000;int resultFailures;String resultFailure="";
    java.util.List<CsMatch> schedule=Collections.emptyList();boolean scheduleBusy;long scheduleAt,scheduleAttempt=-600000;int scheduleFailures;String scheduleFailure="";
    final Handler handler=new Handler(Looper.getMainLooper());
    final ExecutorService worker=Executors.newSingleThreadExecutor(),icons=Executors.newSingleThreadExecutor();
    final LruCache<String,Bitmap> images=new LruCache<String,Bitmap>(512*1024){protected int sizeOf(String k,Bitmap b){return b.getByteCount();}};
    final HashSet<String> pending=new HashSet<>();final HashMap<String,Long> failedImages=new HashMap<>();
    volatile boolean visible,closed;boolean busy,opening;long liveAt,lastAttempt=-300000;int failures;
    String failure="";java.util.List<CsMatch> matches=Collections.emptyList();
    final Runnable expire=()->expired();
    void expired(){if(visible)changed.run();}
    interface Resolved {void done(CsMatch match,java.util.List<CsStream> streams,String error);}
    CsFeed(Context c,Runnable update){context=c;changed=update;favorites=new CsFavorites(c.getSharedPreferences("quiet",Context.MODE_PRIVATE).getString("csFavoriteTeams","[]"));CsPolicy p;
        try{int id=c.getResources().getIdentifier("cs_policy","raw",c.getPackageName());try(InputStream in=c.getResources().openRawResource(id)){p=new CsPolicy(new JSONObject(read(in,16384)));}}catch(Exception e){p=new CsPolicy(new JSONObject());}policy=p;CsBroadcasts b;try{int id=c.getResources().getIdentifier("cs_broadcasters","raw",c.getPackageName());try(InputStream in=c.getResources().openRawResource(id)){b=new CsBroadcasts(new JSONObject(read(in,16384)));}}catch(Exception e){b=new CsBroadcasts(new JSONObject());}broadcasts=b;loadResults();loadSchedule();
        worker.execute(()->{try{File file=new File(context.getFilesDir(),"cs-feed.json");if(!file.exists()||file.length()>LIVE_CACHE_BYTES)return;JSONObject j;try(InputStream in=new FileInputStream(file)){j=new JSONObject(read(in,LIVE_CACHE_BYTES));}ArrayList<CsMatch> saved=new ArrayList<>();JSONArray a=j.optJSONArray("matches");if(a!=null)for(int i=0;i<Math.min(LIVE_RECORDS,a.length());i++){CsMatch m=CsMatch.read(a.getJSONObject(i));if(policy.accepts(m,m.eventId)&&policy.live(m,System.currentTimeMillis()))saved.add(m);}post(()->{if(liveAt==0){matches=saved;liveAt=j.optLong("liveAt",0);notifyChange();}});}catch(Exception ignored){}});
    }
    java.util.List<CsMatch> results(){return resultsLog.rows(System.currentTimeMillis());}
    java.util.List<CsMatch> results(long selected){return resultsLog.rowsForDay(System.currentTimeMillis(),selected);}
    java.util.List<CsMatch> upcoming(){return upcoming(System.currentTimeMillis());}
    java.util.List<CsMatch> upcoming(long selected){long now=System.currentTimeMillis();return CsSchedule.fresh(scheduleAt,now)?CsSchedule.rowsForDay(schedule,now,policy,selected):Collections.emptyList();}
    void loadSchedule(){resultWorker.execute(()->{try{File file=new File(context.getFilesDir(),"cs-schedule.json");if(!file.exists()||file.length()>CsSchedule.MAX_CACHE_BYTES)return;JSONObject j;try(InputStream in=new FileInputStream(file)){j=new JSONObject(read(in,CsSchedule.MAX_CACHE_BYTES));}final java.util.List<CsMatch> rows=CsSchedule.restoreRows(j,System.currentTimeMillis(),policy);post(()->{if(scheduleAt==0){schedule=CsSchedule.windowRows(rows,System.currentTimeMillis(),policy);scheduleAt=CsSchedule.restoredAt(j,System.currentTimeMillis());if(scheduleAt>0&&j.optBoolean("partial",false))scheduleFailure="Saved schedule may be incomplete / Menu to refresh";changed.run();}});}catch(Exception ignored){}});}
    void saveSchedule(){final java.util.List<CsMatch> saved=new ArrayList<>(schedule);final long at=scheduleAt;final String zone=CsSchedule.zone().getID();final boolean partial=!scheduleFailure.isEmpty();resultWorker.execute(()->{try{byte[] bytes=CsSchedule.cache(saved,at,zone).put("partial",partial).toString().getBytes("UTF-8");if(bytes.length>CsSchedule.MAX_CACHE_BYTES)return;File temp=new File(context.getFilesDir(),"cs-schedule.tmp"),file=new File(context.getFilesDir(),"cs-schedule.json");try(OutputStream out=new FileOutputStream(temp)){out.write(bytes);}if(!temp.renameTo(file))temp.delete();}catch(Exception ignored){}});}
    void refreshSchedule(boolean force){
        if(closed||scheduleBusy||!visible)return;long now=System.currentTimeMillis(),elapsed=SystemClock.elapsedRealtime();long retry=Math.min(300000,60000L*(1L<<Math.min(3,scheduleFailures)));
        if(elapsed-scheduleAttempt<(force?15000:retry)||!force&&CsResults.today(scheduleAt,now)&&CsPolicy.fresh(scheduleAt,now,600000))return;
        final long observed=now,requestedDay=CsSchedule.day(now,0);scheduleBusy=true;scheduleAttempt=elapsed;changed.run();
        resultWorker.execute(()->{try{
            ArrayList<CsMatch> found=new ArrayList<>();boolean more=true;long last=0;
            for(int offset=0;more&&offset<500;offset+=100){CsSchedule.Page page=CsSchedule.windowPage(fetch(listing("upcoming",offset)),offset,observed,policy);if(page.firstStart>0&&page.firstStart<last)throw new IOException("Schedule ordering changed");last=page.lastStart;found.addAll(page.matches);more=page.more;}
            final boolean limited=more||CsSchedule.limited(found,observed,policy);
            post(()->{scheduleBusy=false;if(CsSchedule.day(System.currentTimeMillis(),0)!=requestedDay){scheduleAt=0;scheduleAttempt=-600000;refreshSchedule(false);return;}schedule=CsSchedule.windowRows(found,System.currentTimeMillis(),policy);scheduleAt=System.currentTimeMillis();scheduleFailure=limited?"Some days may be incomplete / Menu to refresh":"";scheduleFailures=limited?1:0;saveSchedule();changed.run();loadIcons();});
        }catch(Exception e){post(()->{scheduleBusy=false;scheduleFailures++;scheduleFailure="Schedule unavailable / Menu to retry";changed.run();});}});
    }

    void loadResults(){worker.execute(()->{try{File file=new File(context.getFilesDir(),"cs-results.json");if(!file.exists()||file.length()>CsResults.MAX_CACHE_BYTES)return;CsResults cached;try(InputStream in=new FileInputStream(file)){cached=CsResults.restore(new JSONObject(read(in,CsResults.MAX_CACHE_BYTES)),policy,System.currentTimeMillis());}post(()->{resultsLog.featured.putAll(cached.featured);for(CsMatch m:cached.allRows(System.currentTimeMillis()))if(!resultsLog.finals.containsKey(m.id))resultsLog.finals.put(m.id,m);resultsLog.prune(System.currentTimeMillis());if(resultsLog.checkedAt==0){resultsLog.checkedAt=cached.checkedAt;resultsLog.partial=cached.partial;if(cached.partial&&resultFailure.isEmpty())resultFailure="Some days may be incomplete / Menu to refresh";}changed.run();});}catch(Exception ignored){}});}
    void saveResults(){
        final CsResults snapshot=new CsResults();snapshot.finals.putAll(resultsLog.finals);snapshot.featured.putAll(resultsLog.featured);snapshot.checkedAt=resultsLog.checkedAt;snapshot.partial=resultsLog.partial;final String zone=TvProfile.zone().getID();
        resultWorker.execute(()->{try{String body=snapshot.cache(System.currentTimeMillis()).put("zone",zone).toString();if(body.getBytes("UTF-8").length>CsResults.MAX_CACHE_BYTES)return;File temp=new File(context.getFilesDir(),"cs-results.tmp"),file=new File(context.getFilesDir(),"cs-results.json");try(Writer w=new OutputStreamWriter(new FileOutputStream(temp),"UTF-8")){w.write(body);}if(!temp.renameTo(file))temp.delete();}catch(Exception ignored){}});
    }
    void refreshResults(boolean force){
        if(closed||resultsBusy||!visible)return;long now=System.currentTimeMillis(),elapsed=SystemClock.elapsedRealtime();long retry=Math.min(300000,60000L*(1L<<Math.min(3,resultFailures)));
        if(elapsed-resultAttempt<(force?15000:retry)||!force&&CsResults.today(resultsLog.checkedAt,now)&&CsPolicy.fresh(resultsLog.checkedAt,now,600000))return;
        final long observed=now,requestedDay=CsResults.day(now,0);final String requestedZone=CsResults.zone().getID();resultAttempt=elapsed;resultsBusy=true;changed.run();final Set<Long> featured=resultsLog.featuredIds(now);
        resultWorker.execute(()->{try{ArrayList<CsMatch> rows=new ArrayList<>();boolean more=true;long last=Long.MAX_VALUE;for(int offset=0;more&&offset<500;offset+=100){CsResults.Page page=CsResults.windowPage(fetch(resultsListing(offset)),offset,observed,policy,featured);if(page.firstEnd>last)throw new IOException("Result ordering changed");if(page.lastEnd>0)last=page.lastEnd;rows.addAll(page.matches);more=page.more;}final boolean limited=more||CsResults.limited(rows,observed);post(()->{resultsBusy=false;if(CsResults.day(System.currentTimeMillis(),0)!=requestedDay||!requestedZone.equals(CsResults.zone().getID())){resultsLog.checkedAt=0;resultAttempt=-600000;refreshResults(false);return;}resultsLog.merge(rows,System.currentTimeMillis());resultsLog.checkedAt=limited?0:System.currentTimeMillis();resultsLog.partial=limited;resultFailure=limited?"Some days may be incomplete / Menu to refresh":"";resultFailures=limited?1:0;saveResults();changed.run();});}catch(Exception e){post(()->{resultsBusy=false;resultFailures++;resultFailure="Results unavailable / saved matches kept";changed.run();});}});
    }
    static String resultsListing(int offset){return "matches?scope=widget-matches&page%5Boffset%5D="+offset+"&page%5Blimit%5D=100&sort=-end_date&filter%5Bmatches.status%5D%5Bin%5D=finished&filter%5Bmatches.discipline_id%5D%5Beq%5D=1&with=teams,tournament";}
    void post(Runnable r){handler.post(()->{if(!closed)r.run();});}
    java.util.List<CsMatch> live(){if(!CsPolicy.fresh(liveAt,System.currentTimeMillis(),policy.expiry))return Collections.emptyList();if(liveSource!=matches||liveRevision!=favorites.revision){liveView=favorites.prioritize(matches,policy.cards);liveSource=matches;liveRevision=favorites.revision;}return liveView;}
    CsMatch next(){return CsSchedule.next(schedule,scheduleAt,System.currentTimeMillis(),policy);}
    boolean verified(){return CsPolicy.fresh(liveAt,System.currentTimeMillis(),policy.expiry);}
    void resume(){visible=true;refresh(false);refreshResults(false);refreshSchedule(false);notifyChange();}
    void pause(){visible=false;handler.removeCallbacks(expire);}
    void tick(){if(visible){refresh(false);refreshResults(false);refreshSchedule(false);changed.run();}}
    void notifyChange(){handler.removeCallbacks(expire);if(!visible)return;changed.run();long until=liveAt+policy.expiry-System.currentTimeMillis();if(until>0)handler.postDelayed(expire,until+10);loadIcons();}
    void refresh(boolean force){
        if(closed||busy||!visible)return;long now=System.currentTimeMillis(),elapsed=SystemClock.elapsedRealtime();long backoff=Math.min(300000,60000L*(1L<<Math.min(3,failures)));
        if(elapsed-lastAttempt<(force?15000:backoff)||!force&&CsPolicy.fresh(liveAt,now,policy.refresh))return;
        lastAttempt=elapsed;busy=true;changed.run();
        worker.execute(()->{
            try{
                java.util.List<CsMatch> all=CsParser.list(fetch(listing("current",0)),"current",System.currentTimeMillis(),policy);
                final java.util.List<CsMatch> live=new ArrayList<>(all.subList(0,Math.min(LIVE_RECORDS,all.size())));final long observed=System.currentTimeMillis();
                post(()->{boolean lost=false;for(CsMatch old:matches){boolean found=false;for(CsMatch row:live)if(row.id==old.id)found=true;if(!found)lost=true;}matches=live;liveAt=observed;resultsLog.remember(live(),System.currentTimeMillis());saveResults();if(lost)refreshResults(true);liveAt=observed;failure="";failures=0;busy=false;notifyChange();save();});
            }catch(Exception e){post(()->{busy=false;failures++;failure=e instanceof IOException&&e.getMessage()!=null&&e.getMessage().contains("structure")?"Match source changed":"Connection unavailable";notifyChange();});}
        });
    }
    static String listing(String status,int offset){return "matches?scope=widget-matches&page%5Boffset%5D="+offset+"&page%5Blimit%5D=100&sort=start_date&filter%5Bmatches.status%5D%5Bin%5D="+status+"&filter%5Bmatches.discipline_id%5D%5Beq%5D=1&with=teams,tournament,streams";}
    void save(){try{JSONObject j=new JSONObject().put("liveAt",liveAt);JSONArray a=new JSONArray();for(CsMatch m:matches)a.put(m.cache());j.put("matches",a);final String body=j.toString();if(body.getBytes("UTF-8").length>LIVE_CACHE_BYTES)return;worker.execute(()->{try{File temp=new File(context.getFilesDir(),"cs-feed.tmp"),file=new File(context.getFilesDir(),"cs-feed.json");try(Writer w=new OutputStreamWriter(new FileOutputStream(temp),"UTF-8")){w.write(body);}if(!temp.renameTo(file))temp.delete();}catch(Exception ignored){}});}catch(Exception ignored){}}
    void resolve(CsMatch requested,boolean alternatives,Resolved callback){
        if(opening||closed)return;opening=true;changed.run();worker.execute(()->{try{JSONObject detail=new JSONObject(fetch("matches/"+requested.slug));if(!"current".equals(detail.optString("status",""))){post(()->{opening=false;ArrayList<CsMatch> kept=new ArrayList<>(matches);kept.removeIf(old->old.id==requested.id);matches=kept;refreshResults(true);notifyChange();callback.done(requested,Collections.emptyList(),"This match is no longer confirmed live");refresh(true);});return;}CsMatch m=CsMatch.read(detail);if(m.id!=requested.id||!policy.live(m,System.currentTimeMillis())||!policy.accepts(m,m.eventId)){post(()->{opening=false;ArrayList<CsMatch> kept=new ArrayList<>(matches);kept.removeIf(old->old.id==requested.id);matches=kept;refreshResults(true);notifyChange();callback.done(requested,Collections.emptyList(),"This match is no longer confirmed live");refresh(true);});return;}java.util.List<CsStream> listed=CsStream.read(detail,m.id);HashMap<String,String> authors=new HashMap<>();java.util.List<CsStream> chosen=broadcasts.select(listed,m.event,authors);if(broadcasts.known(m.event)&&(alternatives||chosen.isEmpty())){int attempts=0;for(CsStream candidate:listed){if(!CsBroadcasts.youtubeVideo(candidate.url))continue;if(attempts++>=2)break;try{authors.put(candidate.url,youtubeAuthor(candidate.url));}catch(Exception ignored){}}chosen=broadcasts.select(listed,m.event,authors);}if(chosen.isEmpty()||alternatives||!chosen.get(0).english()){
            java.util.List<CsStream> checked=CsOrganizerLive.find(m,broadcasts,alternatives);HashSet<String> seen=new HashSet<>();for(CsStream s:chosen)seen.add(s.url);for(CsStream s:checked)if(seen.add(s.url))chosen.add(s);chosen.sort((a,b)->Integer.compare(b.priority(),a.priority()));
        }final java.util.List<CsStream> streams=chosen;post(()->{opening=false;callback.done(m,streams,streams.isEmpty()?"No organizer broadcast could be verified for this match":null);});}catch(Exception e){post(()->{opening=false;callback.done(requested,Collections.emptyList(),"Could not verify this match's broadcast");});}});
    }
    CsHighlights highlightFinder;
    interface HighlightResolved {void done(CsHighlights.Result result);}
    Future<?> highlights(CsMatch match,HighlightResolved callback){return worker.submit(()->{
        if(closed||Thread.currentThread().isInterrupted())return;
        if(highlightFinder==null){JSONObject config=new JSONObject();try{int id=context.getResources().getIdentifier("cs_highlights","raw",context.getPackageName());try(InputStream in=context.getResources().openRawResource(id)){config=new JSONObject(read(in,16384));}}catch(Exception ignored){}highlightFinder=new CsHighlights(config);}
        CsHighlights.Result result=highlightFinder.find(match);if(!Thread.currentThread().isInterrupted())post(()->callback.done(result));
    });}
    String youtubeAuthor(String video) throws Exception {
        if(youtubeAuthors.containsKey(video))return youtubeAuthors.get(video);
        HttpURLConnection c=(HttpURLConnection)new URL("https://www.youtube.com/oembed?format=json&url="+URLEncoder.encode(video,"UTF-8")).openConnection();c.setConnectTimeout(3000);c.setReadTimeout(3000);c.setUseCaches(false);
        try{if(c.getResponseCode()!=200)throw new IOException("Video owner unavailable");String author;try(InputStream in=c.getInputStream()){author=new JSONObject(read(in,65536)).optString("author_url","");}if(!CsBroadcasts.identity(author).startsWith("youtube:"))throw new IOException("Video owner unavailable");if(youtubeAuthors.size()>=32)youtubeAuthors.remove(youtubeAuthors.keySet().iterator().next());youtubeAuthors.put(video,author);return author;}finally{c.disconnect();}
    }
    static String fetch(String path) throws Exception {
        if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Match requests stopped");
        HttpURLConnection c=(HttpURLConnection)new URL(API+path).openConnection();c.setConnectTimeout(7000);c.setReadTimeout(7000);c.setUseCaches(false);c.setRequestProperty("User-Agent","QuietTV/1.0 (personal Fire TV match viewer)");c.setRequestProperty("Accept","application/json");c.setRequestProperty("Accept-Encoding","gzip");c.setRequestProperty("Cache-Control","no-cache");
        try{int code=c.getResponseCode();if(code!=200)throw new IOException("Match source HTTP "+code);InputStream raw=c.getInputStream();try(InputStream in="gzip".equalsIgnoreCase(c.getContentEncoding())?new GZIPInputStream(raw):raw){return read(in,524288);}}finally{c.disconnect();}
    }
    static String read(InputStream input,int limit) throws IOException {ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int n;while((n=input.read(buffer))!=-1){if(out.size()+n>limit)throw new IOException("Response too large");out.write(buffer,0,n);}return out.toString("UTF-8");}
    void loadIcons(){java.util.List<CsMatch> rows=live();if(rows.isEmpty()&&next()!=null)rows=Collections.singletonList(next());for(CsMatch m:rows){image(m.logoEvent);image(m.logoA);image(m.logoB);}}
    void image(String url){
        if(url.isEmpty()||images.get(url)!=null||closed||!visible)return;
        synchronized(pending){if(pending.contains(url)||failedImages.containsKey(url)&&SystemClock.elapsedRealtime()-failedImages.get(url)<900000)return;pending.add(url);}
        icons.execute(()->{boolean failed=false;try{if(!visible||closed)return;HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(3500);c.setReadTimeout(3500);c.setRequestProperty("User-Agent","QuietTV/1.0");try{if(c.getResponseCode()!=200)throw new IOException();byte[] bytes;try(InputStream in=c.getInputStream()){ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] chunk=new byte[2048];int n;while((n=in.read(chunk))!=-1){if(b.size()+n>131072)throw new IOException();b.write(chunk,0,n);}bytes=b.toByteArray();}BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);if(options.outWidth<1||options.outHeight<1||options.outWidth>4096||options.outHeight>4096)throw new IOException();options.inJustDecodeBounds=false;options.inSampleSize=1;while(Math.max(options.outWidth,options.outHeight)/options.inSampleSize>128)options.inSampleSize*=2;Bitmap icon=BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);if(icon==null)throw new IOException();images.put(url,icon);post(()->{if(visible)changed.run();});}finally{c.disconnect();}}catch(Exception ignored){failed=true;}finally{synchronized(pending){pending.remove(url);if(failed){if(failedImages.size()>64)failedImages.clear();failedImages.put(url,SystemClock.elapsedRealtime());}}}});
    }
    void close(){closed=true;visible=false;handler.removeCallbacksAndMessages(null);worker.shutdownNow();resultWorker.shutdownNow();icons.shutdownNow();images.evictAll();}
}
