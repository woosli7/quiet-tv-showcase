package com.quiettv.home;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import java.io.*;
import java.net.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import javax.crypto.Cipher;
import org.json.*;

/** Event-driven HTTPS control. No account key is compiled into the app. */
final class GoveeLights {
    interface Listener { void changed(String message,boolean notify); }
    static final String[] LOOK_NAMES={"Neon HQ","Arizona sunset","Deep space","Emerald terminal","Campfire"};
    static final int[][] LOOK_COLORS={{0x00d5ff,0xff208e,0x8338ff},{0xff8a20,0xff4055,0x752bba},{0x123ac7,0x6324b8,0x088ba8},{0x08c96a,0x86ed35,0x159c91},{0xff4b08,0xffa228,0xad2409}};
    static final String[] MOTION_NAMES={"Slow (smooth)","Aurora (original)","Sunset","Rainbow","Fire","Deep sea","Flow"};
    // These identifiers were returned by this H6097's official scene endpoint.
    static final int[][] MOTION_IDS={{20408,135},{111,129},{126,144},{20416,145},{130,148},{20492,230}};
    final Context context;
    final Listener listener;
    final Handler main=new Handler(Looper.getMainLooper());
    final ScheduledThreadPoolExecutor worker=new ScheduledThreadPoolExecutor(1);
    volatile boolean foreground=false,ready=false,closed=false;
    volatile int generation;
    volatile String status="Connecting lights…";
    Future<?> pending;
    JSONObject config;
    long lastRequest=-1000,autoRetryAfter;
    int appliedBrightness=-1,appliedColor=-1;
    volatile String wanted="";
    GoveeLights(Context c,Listener l){context=c.getApplicationContext();listener=l;worker.setRemoveOnCancelPolicy(true);}
    void resume(){foreground=true;worker.execute(()->{try{loadConfig();if(ready)setStatus("TV lights connected",false);else setStatus("Lights need setup",false);}catch(Exception ex){ready=false;setStatus("Lights setup unavailable",false);}});}
    void pause(){foreground=false;cancel();}
    void close(){pause();closed=true;worker.shutdownNow();main.removeCallbacksAndMessages(null);}
    void cancel(){generation++;if(pending!=null)pending.cancel(false);wanted="";}
    void setStatus(String text,boolean notify){status=text;main.post(()->{if(!closed&&foreground)listener.changed(text,notify);});}
    void loadConfig() throws Exception {
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        String alias="QuietTV-Govee";
        if(!store.containsAlias(alias)){
            KeyPairGenerator generator=KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA,"AndroidKeyStore");
            generator.initialize(new KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_DECRYPT).setKeySize(2048).setDigests(KeyProperties.DIGEST_SHA1,KeyProperties.DIGEST_SHA256).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP).build());generator.generateKeyPair();
        }
        File external=context.getExternalFilesDir(null);
        if(external==null)throw new IOException();
        File publicFile=new File(external,"govee-public.der");
        if(!publicFile.exists())write(publicFile,store.getCertificate(alias).getPublicKey().getEncoded());
        File incoming=new File(external,"govee-setup.enc"),saved=new File(context.getNoBackupFilesDir(),"govee.enc");
        File source=incoming.exists()?incoming:saved;
        if(!source.exists()){ready=false;return;}
        byte[] bytes=read(source,512);
        Cipher cipher=Cipher.getInstance("RSA/ECB/OAEPWithSHA-1AndMGF1Padding");cipher.init(Cipher.DECRYPT_MODE,(PrivateKey)store.getKey(alias,null));
        JSONObject candidate=new JSONObject(new String(cipher.doFinal(bytes),"UTF-8"));
        if(!"H6097".equals(candidate.optString("sku"))||!candidate.optString("key").matches("[A-Za-z0-9-]{20,100}")||!candidate.optString("device").matches("[A-Fa-f0-9:]{17,32}"))throw new IOException();
        if(incoming.exists()){write(saved,bytes);if(!incoming.delete())throw new IOException();}
        config=candidate;ready=true;
    }
    static byte[] read(File file,int max) throws IOException {try(InputStream in=new FileInputStream(file);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buf=new byte[256];int n;while((n=in.read(buf))!=-1){if(out.size()+n>max)throw new IOException();out.write(buf,0,n);}return out.toByteArray();}}
    static void write(File file,byte[] bytes) throws IOException {try(FileOutputStream out=new FileOutputStream(file)){out.write(bytes);out.getFD().sync();}}
    static final class Command {
        final String type,instance;final Object value;
        Command(String t,String i,Object v){type="devices.capabilities."+t;instance=i;value=v;}
    }
    void submit(String signature,String label,boolean automatic,boolean ensureOn,int brightness,ArrayList<Command> commands){
        if(!foreground||closed)return;
        if(automatic&&signature.equals(wanted))return;
        cancel();wanted=signature;int token=generation;
        pending=worker.schedule(()->{
            if(!valid(token))return;
            if(!ready){wanted="";setStatus("Lights need setup",!automatic);return;}
            if(automatic&&SystemClock.elapsedRealtime()<autoRetryAfter){wanted="";return;}
            try{
                if(ensureOn&&!automatic)send(new Command("on_off","powerSwitch",1),token);
                if(brightness>0&&(!automatic||brightness!=appliedBrightness)){send(new Command("range","brightness",brightness),token);appliedBrightness=brightness;}
                for(Command command:commands){if(!valid(token))return;if(automatic&&command.instance.equals("colorRgb")&&command.value.equals(appliedColor))continue;send(command,token);if(command.instance.equals("colorRgb"))appliedColor=(Integer)command.value;else if(!command.instance.equals("powerSwitch"))appliedColor=-1;}
                if(valid(token))setStatus(label+" • sent",!automatic);
            }catch(CancellationException ignored){}catch(NoSuchElementException ex){wanted="";if(valid(token))setStatus("Save an effect named slow in Govee Home first",true);}catch(Exception ex){appliedBrightness=-1;appliedColor=-1;wanted="";autoRetryAfter=SystemClock.elapsedRealtime()+30000;if(valid(token))setStatus("Lights unavailable — try again",!automatic);}
        },automatic?500:0,TimeUnit.MILLISECONDS);
    }
    boolean valid(int token){return !closed&&foreground&&token==generation&&!Thread.currentThread().isInterrupted();}
    void send(Command command,int token) throws Exception {
        Object value=command.value;
        if(command.instance.equals("diyScene")&&value instanceof String){
            // Resolve the saved name on demand so recreating the DIY does not leave a stale ID.
            JSONObject result=request("diy-scenes",devicePayload(),token,65536);
            JSONArray capabilities=result.getJSONObject("payload").getJSONArray("capabilities");
            Integer found=null;
            for(int i=0;i<capabilities.length();i++){
                JSONObject capability=capabilities.getJSONObject(i);if(!"diyScene".equals(capability.optString("instance")))continue;
                JSONArray options=capability.getJSONObject("parameters").getJSONArray("options");
                for(int j=0;j<options.length();j++){JSONObject option=options.getJSONObject(j);if(((String)value).equalsIgnoreCase(option.optString("name"))){if(found!=null)throw new IOException();found=option.getInt("value");}}
            }
            if(found==null)throw new NoSuchElementException();value=found;
        }
        request("control",devicePayload().put("capability",new JSONObject().put("type",command.type).put("instance",command.instance).put("value",value)),token,16384);
    }
    JSONObject devicePayload() throws JSONException {return new JSONObject().put("sku",config.getString("sku")).put("device",config.getString("device"));}
    JSONObject request(String endpoint,JSONObject payload,int token,int maxBytes) throws Exception {
        long delay=650-(SystemClock.elapsedRealtime()-lastRequest);if(delay>0)Thread.sleep(delay);
        if(!valid(token))throw new CancellationException();
        JSONObject body=new JSONObject().put("requestId",UUID.randomUUID().toString()).put("payload",payload);
        HttpURLConnection connection=(HttpURLConnection)new URL("https://openapi.api.govee.com/router/api/v1/device/"+endpoint).openConnection();
        connection.setConnectTimeout(5000);connection.setReadTimeout(5000);connection.setInstanceFollowRedirects(false);connection.setRequestMethod("POST");connection.setDoOutput(true);connection.setRequestProperty("Content-Type","application/json");connection.setRequestProperty("Govee-API-Key",config.getString("key"));
        lastRequest=SystemClock.elapsedRealtime();
        try{
            try(OutputStream out=connection.getOutputStream()){out.write(body.toString().getBytes("UTF-8"));}
            if(connection.getResponseCode()!=200)throw new IOException();
            StringBuilder reply=new StringBuilder();try(Reader reader=new InputStreamReader(connection.getInputStream(),"UTF-8")){char[] buf=new char[1024];int n;while((n=reader.read(buf))!=-1){reply.append(buf,0,n);if(reply.length()>maxBytes)throw new IOException();}}
            JSONObject result=new JSONObject(reply.toString());if(result.optInt("code")!=200)throw new IOException();return result;
        }finally{connection.disconnect();}
    }
    void solid(int rgb,int brightness,boolean automatic,String label){ArrayList<Command> commands=new ArrayList<>();commands.add(new Command("color_setting","colorRgb",rgb&0xffffff));submit(rgb+":"+brightness,label,automatic,true,brightness,commands);}
    void palette(int[] colors,int brightness,String label){
        ArrayList<Command> commands=new ArrayList<>();
        try{for(int band=0;band<colors.length;band++){JSONArray segments=new JSONArray();for(int i=band;i<=14;i+=colors.length)segments.put(i);commands.add(new Command("segment_color_setting","segmentedColorRgb",new JSONObject().put("segment",segments).put("rgb",colors[band]&0xffffff)));}}catch(JSONException ex){return;}
        submit("palette:"+label,label,false,true,brightness,commands);
    }
    void motion(int index,int brightness){ArrayList<Command> commands=new ArrayList<>();try{if(index==0)commands.add(new Command("dynamic_scene","diyScene","slow"));else commands.add(new Command("dynamic_scene","lightScene",new JSONObject().put("paramId",MOTION_IDS[index-1][0]).put("id",MOTION_IDS[index-1][1])));}catch(JSONException ex){return;}submit("scene:"+index,MOTION_NAMES[index],false,true,brightness,commands);}
    void power(boolean on){ArrayList<Command> commands=new ArrayList<>();commands.add(new Command("on_off","powerSwitch",on?1:0));submit("power:"+on,on?"Lights on":"Lights off",false,false,-1,commands);}
    void brightness(int value){submit("brightness:"+value,"Brightness "+value+"%",false,false,value,new ArrayList<>());}
}
