package com.quiettv.home;
import android.graphics.*;

/** Cached procedural sky. No animation loop or downloaded assets. */
final class IdleSky {
    private static final int[] TIMES={0,300,390,540,900,1065,1170,1260,1440};
    private static final int[][] COLORS={
        {0x050919,0x141b40,0x35385d,0xc3daf5},{0x10122f,0x4c305c,0xa95778,0xffc7a5},
        {0x1d3056,0x9b5c7b,0xffb177,0xffe5b0},{0x102f50,0x226b86,0x77b9bb,0xffefbf},
        {0x1b3b58,0x4c7c89,0xc6a477,0xffdda0},{0x241745,0xa94270,0xff994c,0xffd58b},
        {0x101234,0x473569,0x9e5a86,0xd2ccff},{0x050919,0x141b40,0x35385d,0xc3daf5},
        {0x050919,0x141b40,0x35385d,0xc3daf5}
    };
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path far=new Path(),middle=new Path(),near=new Path(),moon=new Path(),cacti=new Path();
    private final Path cloudLeft=new Path(),cloudRight=new Path(),cloudLow=new Path();
    private final Path sun=new Path();
    private final float[] starPoints=new float[64*3];
    private final Shader centerShade=new RadialGradient(640,345,540,new int[]{0xb0040a19,0x40040a19,0x00040a19},new float[]{0,.62f,1},Shader.TileMode.CLAMP);
    private final Shader moonHalo=new RadialGradient(1060,150,130,new int[]{0x34677eaf,0x14677eaf,0x00677eaf},new float[]{0,.4f,1},Shader.TileMode.CLAMP);
    private final Shader nebula=new RadialGradient(930,90,590,new int[]{0x35694c9e,0x0c694c9e,0x00694c9e},new float[]{0,.65f,1},Shader.TileMode.CLAMP);
    private Shader sky,halo,horizonGlow,land,cloudColor,orb;
    private int cachedMinute=-1,cachedRise=-1,cachedSet=-1,sunColor,farColor,middleColor;
    private float sunX,sunY,daylight,stars,sunOpacity,warmth;
    IdleSky(){
        ridge(far,new float[]{0,564,95,549,180,565,270,524,355,544,448,500,540,533,635,514,735,552,835,515,915,542,1000,510,1100,548,1185,527,1280,552});
        ridge(middle,new float[]{0,622,95,596,175,611,262,576,350,599,445,565,555,594,675,578,778,608,887,565,974,586,1075,567,1160,605,1280,583});
        ridge(near,new float[]{0,660,130,647,250,668,390,638,520,657,650,644,800,671,940,644,1080,663,1170,638,1280,649});
        moon.addCircle(0,0,32,Path.Direction.CW);Path cut=new Path();cut.addCircle(14,-9,31,Path.Direction.CW);moon.op(cut,Path.Op.DIFFERENCE);
        cloudLeft.moveTo(0,153);cloudLeft.cubicTo(135,132,261,152,451,119);cloudLeft.cubicTo(326,158,174,159,0,174);cloudLeft.close();
        cloudRight.moveTo(822,245);cloudRight.cubicTo(1003,215,1135,241,1280,201);cloudRight.lineTo(1280,224);cloudRight.cubicTo(1111,253,1008,239,822,245);cloudRight.close();
        cloudLow.moveTo(0,427);cloudLow.cubicTo(125,400,249,424,370,395);cloudLow.cubicTo(243,438,130,422,0,450);cloudLow.close();
        cactus(117,667,1);cactus(1185,666,.72f);
        java.util.Random random=new java.util.Random(418);
        for(int i=0;i<64;i++){float x,y;do{x=55+random.nextFloat()*1170;y=45+random.nextFloat()*465;}while(x>365&&x<900&&y>180&&y<505);starPoints[i*3]=x;starPoints[i*3+1]=y;starPoints[i*3+2]=.65f+random.nextFloat()*1.05f;}
    }
    private void cactus(float x,float ground,float scale){
        Path shape=new Path();shape.addRoundRect(-5,-84,5,2,5,5,Path.Direction.CW);
        shape.moveTo(-1,-35);shape.lineTo(-18,-35);shape.quadTo(-26,-35,-26,-44);shape.lineTo(-26,-64);shape.quadTo(-22,-70,-18,-64);shape.lineTo(-18,-45);shape.lineTo(-1,-45);shape.close();
        shape.moveTo(1,-51);shape.lineTo(18,-51);shape.lineTo(18,-76);shape.quadTo(22,-82,26,-76);shape.lineTo(26,-50);shape.quadTo(26,-42,18,-42);shape.lineTo(1,-42);shape.close();
        Matrix matrix=new Matrix();matrix.setScale(scale,scale);matrix.postTranslate(x,ground);shape.transform(matrix);cacti.addPath(shape);
    }
    private static void ridge(Path path,float[] points){path.moveTo(points[0],points[1]);for(int i=2;i<points.length;i+=2)path.lineTo(points[i],points[i+1]);path.lineTo(1280,720);path.lineTo(0,720);path.close();}
    private static float clamp(float n){return Math.max(0,Math.min(1,n));}
    private static float smooth(float n){n=clamp(n);return n*n*(3-2*n);}
    private static int mix(int a,int b,float t){return Color.rgb(Math.round(Color.red(a)+(Color.red(b)-Color.red(a))*t),Math.round(Color.green(a)+(Color.green(b)-Color.green(a))*t),Math.round(Color.blue(a)+(Color.blue(b)-Color.blue(a))*t));}
    private static int alpha(int color,int a){return (color&0xffffff)|(Math.max(0,Math.min(255,a))<<24);}
    private void prepare(int minute,int rise,int set,boolean seasonal){
        int keyRise=seasonal?rise:-1,keySet=seasonal?set:-1;if(minute==cachedMinute&&keyRise==cachedRise&&keySet==cachedSet)return;
        cachedMinute=minute;cachedRise=keyRise;cachedSet=keySet;int[] times=seasonal?SolarDay.anchors(rise,set):TIMES;int i=0;while(i<times.length-2&&minute>=times[i+1])i++;
        float blend=smooth((minute-times[i])/(float)(times[i+1]-times[i]));
        int top=mix(COLORS[i][0],COLORS[i+1][0],blend),mid=mix(COLORS[i][1],COLORS[i+1][1],blend),horizon=mix(COLORS[i][2],COLORS[i+1][2],blend);
        sunColor=mix(COLORS[i][3],COLORS[i+1][3],blend);sky=new LinearGradient(0,0,0,625,new int[]{top,mid,horizon},new float[]{0,.60f,1},Shader.TileMode.CLAMP);
        daylight=seasonal?smooth((minute-(rise-40))/55f)*(1-smooth((minute-(set-15))/60f)):smooth((minute-315)/105f)*(1-smooth((minute-1080)/90f));stars=1-daylight;
        sunOpacity=seasonal?smooth((minute-rise)/12f)*(1-smooth((minute-(set-12))/12f)):daylight;
        float progress=seasonal?clamp((minute-rise)/(float)(set-rise)):clamp((minute-360)/750f);
        warmth=1-smooth(Math.min(Math.abs(minute-rise),Math.abs(minute-set))/115f);
        sunX=210+860*progress;sunY=565-440*(float)Math.sin(Math.PI*progress);
        halo=new RadialGradient(sunX,sunY,320,new int[]{alpha(sunColor,Math.round((90+70*warmth)*daylight)),alpha(sunColor,Math.round(35*daylight)),alpha(sunColor,0)},new float[]{0,.4f,1},Shader.TileMode.CLAMP);
        horizonGlow=new RadialGradient(sunX,585,560,new int[]{alpha(0xffffa568,Math.round(85*warmth)),alpha(0xffff725d,Math.round(35*warmth)),0x00ff725d},new float[]{0,.4f,1},Shader.TileMode.CLAMP);
        cloudColor=new LinearGradient(0,0,1280,0,new int[]{alpha(sunColor,Math.round(42*daylight+14*stars)),alpha(sunColor,8),alpha(sunColor,Math.round(50*daylight+12*stars))},new float[]{0,.5f,1},Shader.TileMode.CLAMP);
        orb=new LinearGradient(0,sunY-46,0,sunY+46,0xfffff1cb,mix(0xffffa15f,sunColor,1-warmth),Shader.TileMode.CLAMP);
        sun.reset();sun.addCircle(sunX,sunY,46,Path.Direction.CW);
        if(warmth>.01f){Path gaps=new Path();for(int stripe=0;stripe<4;stripe++){float y=sunY+8+stripe*10;gaps.addRect(sunX-50,y,sunX+50,y+(2+stripe*.7f)*warmth,Path.Direction.CW);}sun.op(gaps,Path.Op.DIFFERENCE);}
        farColor=mix(0xff18243b,horizon,.22f);middleColor=mix(0xff10182c,horizon,.07f);land=new LinearGradient(0,635,0,720,0xff0b1222,0xff050a13,Shader.TileMode.CLAMP);
    }
    void draw(Canvas canvas,int minute,int rise,int set,boolean seasonal){
        minute=((minute%1440)+1440)%1440;prepare(minute,rise,set,seasonal);paint.setStyle(Paint.Style.FILL);paint.setAlpha(255);paint.setShader(sky);canvas.drawRect(0,0,1280,720,paint);
        paint.setShader(nebula);paint.setAlpha(Math.round(255*stars));canvas.drawRect(0,0,1280,720,paint);paint.setAlpha(255);
        paint.setShader(horizonGlow);canvas.drawRect(0,0,1280,720,paint);paint.setShader(cloudColor);canvas.drawPath(cloudLeft,paint);canvas.drawPath(cloudRight,paint);canvas.drawPath(cloudLow,paint);paint.setShader(null);
        if(stars>.01f)for(int j=0;j<64;j++){float x=starPoints[j*3],y=starPoints[j*3+1],r=starPoints[j*3+2];paint.setColor(alpha(0xffd5e7ff,Math.round((100+60*r)*stars)));canvas.drawCircle(x,y,r,paint);if(r>1.6f){paint.setStrokeWidth(.65f);paint.setAlpha(Math.round(100*stars));canvas.drawLine(x-4,y,x+4,y,paint);canvas.drawLine(x,y-4,x,y+4,paint);}}
        paint.setShader(moonHalo);paint.setAlpha(Math.round(255*stars));canvas.drawRect(900,0,1220,320,paint);paint.setShader(null);paint.setColor(alpha(0xffd2e2f7,Math.round(205*stars)));canvas.save();canvas.translate(1060,150);canvas.drawPath(moon,paint);canvas.restore();
        paint.setAlpha(255);paint.setShader(halo);canvas.drawRect(0,0,1280,625,paint);paint.setShader(orb);paint.setAlpha(Math.round(240*sunOpacity));canvas.drawPath(sun,paint);paint.setShader(null);
        paint.setColor(farColor);canvas.drawPath(far,paint);paint.setColor(middleColor);canvas.drawPath(middle,paint);paint.setAlpha(255);paint.setShader(land);canvas.drawPath(near,paint);paint.setShader(null);paint.setColor(0xff070e1a);canvas.drawPath(cacti,paint);
        paint.setAlpha(255);paint.setShader(centerShade);canvas.drawRect(0,0,1280,720,paint);paint.setShader(null);
    }
}
