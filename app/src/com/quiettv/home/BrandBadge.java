package com.quiettv.home;

import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.RectF;
import java.util.HashMap;
import java.util.Map;

/** Small brand-inspired marks, converted to dot geometry once when the view is created. */
final class BrandBadge {
    final Path color, white;
    final Path[] layers;final int[] layerColors;
    BrandBadge(Path color,Path white){this(color,white,null,null);}
    BrandBadge(Path color,Path white,Path[] layers,int[] colors){this.color=color;this.white=white;this.layers=layers;this.layerColors=colors;}
    static Path dotted(Path source){
        Path result=new Path();PathMeasure measure=new PathMeasure(source,false);float[] point=new float[2];
        do{float length=measure.getLength();int count=Math.max(1,(int)Math.ceil(length/1.7f));
            for(int n=0;n<count;n++){measure.getPosTan(length*n/count,point,null);result.addCircle(point[0],point[1],.75f,Path.Direction.CW);}
        }while(measure.nextContour());
        return result;
    }
    static void stroke(Path p,float... xy){p.moveTo(xy[0],xy[1]);for(int i=2;i<xy.length;i+=2)p.lineTo(xy[i],xy[i+1]);}
    static Map<String,BrandBadge> createAll(){
        Map<String,BrandBadge> badges=new HashMap<>();Path a=new Path(),b=new Path();
        // YouTube: rounded video screen and contrasting play triangle.
        a.addRoundRect(new RectF(-15,-10.5f,15,10.5f),4,4,Path.Direction.CW);
        stroke(b,-4,-6,7,0,-4,6,-4,-6);
        badges.put("com.amazon.firetv.youtube",new BrandBadge(dotted(a),dotted(b)));
        // Hulu: compact h monogram inside its app tile.
        a=new Path();a.addRoundRect(new RectF(-13,-13,13,13),4,4,Path.Direction.CW);
        stroke(a,-5,-8,-5,8);a.moveTo(-5,0);a.cubicTo(-2,-5,5,-5,5,0);a.lineTo(5,8);
        badges.put("com.hulu.plus",new BrandBadge(dotted(a),null));
        // Prime Video: play symbol above the Amazon smile and arrow.
        a=new Path();a.moveTo(-11,5);a.quadTo(0,13,11,3);stroke(a,6,2,12,3,10,8);
        b=new Path();stroke(b,-5,-10,6,-4,-5,2,-5,-10);
        badges.put("com.amazon.firebat",new BrandBadge(dotted(a),dotted(b)));
        // Paramount+: mountain peak beneath a crown of stars.
        a=new Path();stroke(a,-10,9,-1,-6,10,9,-10,9);stroke(a,-1,-6,2,2,-1,0,-4,4);
        for(int n=0;n<7;n++){double angle=Math.toRadians(200+n*140/6f);float x=(float)Math.cos(angle)*14,y=(float)Math.sin(angle)*14;
            for(int k=0;k<10;k++){double direction=-Math.PI/2+k*Math.PI/5;float radius=k%2==0?1.8f:.8f;float sx=x+(float)Math.cos(direction)*radius,sy=y+(float)Math.sin(direction)*radius;if(k==0)a.moveTo(sx,sy);else a.lineTo(sx,sy);}a.close();
        }
        badges.put("com.cbs.ott",new BrandBadge(dotted(a),null));
        // Disney+: a D monogram with the familiar overhead arc and plus.
        a=new Path();a.moveTo(-13,-5);a.quadTo(0,-19,13,-4);stroke(a,7,8,15,8);stroke(a,11,4,11,12);
        b=new Path();b.moveTo(-7,10);b.lineTo(-7,-7);b.cubicTo(13,-9,14,11,-7,10);
        badges.put("com.disney.disneyplus",new BrandBadge(dotted(a),dotted(b)));
        // Chrome-inspired three-blade mark; keep launching the installed Silk browser.
        Path[] chrome=new Path[4];RectF rim=new RectF(-14,-14,14,14),hub=new RectF(-7,-7,7,7);
        for(int n=0;n<3;n++){
            float outerStart=210+n*120,innerStart=270+n*120;
            Path blade=new Path();blade.arcTo(rim,outerStart,120,false);
            blade.lineTo((float)Math.cos(Math.toRadians(innerStart))*7,(float)Math.sin(Math.toRadians(innerStart))*7);
            blade.arcTo(hub,innerStart,-120,false);blade.close();chrome[n]=dotted(blade);
        }
        chrome[3]=new Path();for(float y=-5;y<=5;y+=1.7f)for(float x=-5;x<=5;x+=1.7f)if(x*x+y*y<29)chrome[3].addCircle(x,y,.75f,Path.Direction.CW);
        b=new Path();b.addCircle(0,0,6.3f,Path.Direction.CW);
        badges.put("com.amazon.cloud9",new BrandBadge(new Path(),dotted(b),chrome,new int[]{0xffea4335,0xfffbbc05,0xff34a853,0xff4285f4}));
        // Preserve the Spotify mark already used in the original design.
        a=new Path();
        for(int angle=0;angle<360;angle+=10){double r=Math.toRadians(angle);a.addCircle((float)Math.cos(r)*14,(float)Math.sin(r)*14,.7f,Path.Direction.CW);}
        for(int row=0;row<3;row++){float width=19-row*2.5f;int steps=(int)(width/1.6f);
            for(int dot=0;dot<=steps;dot++){float t=dot/(float)steps;a.addCircle(-width/2+t*width,-4.5f+row*4.6f-10*t*(1-t)+2*t,.8f,Path.Direction.CW);}}
        BrandBadge spotify=new BrandBadge(a,null);badges.put("com.spotify.tv.android",spotify);badges.put("com.spotify.music",spotify);
        // Settings gets a matching gear rather than a brand mark.
        a=new Path();for(int n=0;n<32;n++){double angle=n*Math.PI/16;float radius=(n%4==0||n%4==3)?14:11;float x=(float)Math.cos(angle)*radius,y=(float)Math.sin(angle)*radius;if(n==0)a.moveTo(x,y);else a.lineTo(x,y);}a.close();a.addCircle(0,0,4.5f,Path.Direction.CW);
        badges.put("@settings",new BrandBadge(dotted(a),null));
        return badges;
    }
}
