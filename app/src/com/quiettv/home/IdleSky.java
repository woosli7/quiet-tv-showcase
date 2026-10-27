package com.quiettv.home;
import android.graphics.*;

/** Cached procedural sky. No animation loop or downloaded assets. */
final class IdleSky {
    // Night, predawn, sunrise, morning, solar noon, afternoon, sunset, twilight, night.
    // SolarDay supplies dated anchors so the looks move with the seasons.
    private static final int[][] COLORS={
        {0x030817,0x0b1935,0x1c3552,0xc5e1ff},{0x0e2144,0x3e6683,0xb8a3a5,0xffe0b1},
        {0x183d64,0x638c9e,0xf3b88a,0xffe9b5},{0x23566e,0x679d9c,0xd5dcb3,0xfff2ce},
        {0x174b78,0x388caf,0xa0ced7,0xfff5dd},{0x243f6b,0x597f99,0xd6b18c,0xffe0a3},
        {0x2e153f,0x8c3d5c,0xf6874c,0xffc47c},{0x111333,0x39315c,0x9a5775,0xe3d4ff},
        {0x030817,0x0b1935,0x1c3552,0xc5e1ff},{0x030817,0x0b1935,0x1c3552,0xc5e1ff}
    };
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path far=new Path(),middle=new Path(),near=new Path(),moon=new Path(),cacti=new Path();
    private final Path cloudLeft=new Path(),cloudRight=new Path(),cloudLow=new Path();
    private final Path sun=new Path(),sunsetBands=new Path();
    private final float[] starPoints=new float[64*3];
    private final Shader centerShade=new RadialGradient(640,345,460,new int[]{0xa3071122,0x55071122,0x00071122},new float[]{0,.56f,1},Shader.TileMode.CLAMP);
    private final Shader moonHalo=new RadialGradient(1060,145,155,new int[]{0x456b9ed0,0x166b9ed0,0x006b9ed0},new float[]{0,.35f,1},Shader.TileMode.CLAMP);
    private final Shader nebula=new RadialGradient(930,90,590,new int[]{0x222c527f,0x0a2c527f,0x002c527f},new float[]{0,.65f,1},Shader.TileMode.CLAMP);
    private Shader sky,halo,horizonGlow,land,cloudColor,orb,mist;
    private int cachedMinute=-1,cachedRise=-1,cachedSet=-1,sunColor,farColor,middleColor;
    private float sunX,sunY,daylight,stars,sunOpacity,warmth,dusk,morningMist,sunRadius;
    IdleSky(){
        ridge(far,new float[]{0,564,95,549,180,565,270,524,355,544,448,500,540,533,635,514,735,552,835,515,915,542,1000,510,1100,548,1185,527,1280,552});
        ridge(middle,new float[]{0,622,95,596,175,611,262,576,350,599,445,565,555,594,675,578,778,608,887,565,974,586,1075,567,1160,605,1280,583});
        ridge(near,new float[]{0,660,130,647,250,668,390,638,520,657,650,644,800,671,940,644,1080,663,1170,638,1280,649});
        moon.addCircle(0,0,43,Path.Direction.CW);Path cut=new Path();cut.addCircle(17,-11,42,Path.Direction.CW);moon.op(cut,Path.Op.DIFFERENCE);
        cloudLeft.moveTo(0,153);cloudLeft.cubicTo(135,132,261,152,451,119);cloudLeft.cubicTo(326,158,174,159,0,174);cloudLeft.close();
        cloudRight.moveTo(822,245);cloudRight.cubicTo(1003,215,1135,241,1280,201);cloudRight.lineTo(1280,224);cloudRight.cubicTo(1111,253,1008,239,822,245);cloudRight.close();
        cloudLow.moveTo(0,427);cloudLow.cubicTo(125,400,249,424,370,395);cloudLow.cubicTo(243,438,130,422,0,450);cloudLow.close();
        sunsetBands.moveTo(775,393);sunsetBands.cubicTo(939,368,1124,383,1280,350);sunsetBands.lineTo(1280,361);sunsetBands.cubicTo(1110,395,936,381,775,393);sunsetBands.close();
        sunsetBands.moveTo(892,452);sunsetBands.cubicTo(1038,431,1182,449,1280,423);sunsetBands.lineTo(1280,432);sunsetBands.cubicTo(1154,460,1020,441,892,452);sunsetBands.close();
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
        if(!seasonal){rise=360;set=1110;}
        if(minute==cachedMinute&&rise==cachedRise&&set==cachedSet)return;
        cachedMinute=minute;cachedRise=rise;cachedSet=set;int[] times=SolarDay.anchors(rise,set);int i=0;while(i<times.length-2&&minute>=times[i+1])i++;
        float blend=smooth((minute-times[i])/(float)(times[i+1]-times[i]));
        int top=mix(COLORS[i][0],COLORS[i+1][0],blend),mid=mix(COLORS[i][1],COLORS[i+1][1],blend),horizon=mix(COLORS[i][2],COLORS[i+1][2],blend);
        sunColor=mix(COLORS[i][3],COLORS[i+1][3],blend);sky=new LinearGradient(0,0,0,625,new int[]{top,mid,horizon},new float[]{0,.60f,1},Shader.TileMode.CLAMP);
        daylight=smooth((minute-(rise-40))/55f)*(1-smooth((minute-(set-15))/60f));stars=1-daylight;
        sunOpacity=smooth((minute-rise)/12f)*(1-smooth((minute-(set-12))/12f));
        float progress=clamp((minute-rise)/(float)(set-rise));
        warmth=1-smooth(Math.min(Math.abs(minute-rise),Math.abs(minute-set))/105f);
        dusk=smooth((progress-.5f)/.35f)*warmth;
        morningMist=(1-smooth(progress/.42f))*daylight;
        sunX=210+860*progress;sunY=562-451*(float)Math.sin(Math.PI*progress);sunRadius=34+8*warmth+26*dusk;
        halo=new RadialGradient(sunX,sunY,260+80*warmth,new int[]{alpha(sunColor,Math.round((35+85*warmth)*sunOpacity)),alpha(sunColor,Math.round(22*sunOpacity)),alpha(sunColor,0)},new float[]{0,.4f,1},Shader.TileMode.CLAMP);
        int glow=mix(0xffffd39f,0xffff793f,smooth(progress));
        horizonGlow=new RadialGradient(sunX,570,500,new int[]{alpha(glow,Math.round((55+45*dusk)*warmth)),alpha(glow,Math.round(24*warmth)),alpha(glow,0)},new float[]{0,.4f,1},Shader.TileMode.CLAMP);
        mist=new LinearGradient(0,380,0,570,new int[]{0x00c4e2ea,alpha(0xffc4e2ea,Math.round(40*morningMist)),0x00c4e2ea},new float[]{0,.72f,1},Shader.TileMode.CLAMP);
        cloudColor=new LinearGradient(0,0,1280,0,new int[]{alpha(sunColor,Math.round(52*daylight+7*stars)),alpha(sunColor,5),alpha(sunColor,Math.round(58*daylight+7*stars))},new float[]{0,.5f,1},Shader.TileMode.CLAMP);
        orb=new LinearGradient(0,sunY-sunRadius,0,sunY+sunRadius,0xfffff6dc,mix(sunColor,0xffff925b,dusk*.8f),Shader.TileMode.CLAMP);
        sun.reset();sun.addCircle(sunX,sunY,sunRadius,Path.Direction.CW);
        // The larger banded western sun belongs to dusk; dawn stays clear and soft.
        if(dusk>.01f){Path gaps=new Path();for(int stripe=0;stripe<5;stripe++){float y=sunY+2+stripe*11;gaps.addRect(sunX-70,y,sunX+70,y+(2+stripe*.8f)*dusk,Path.Direction.CW);}sun.op(gaps,Path.Op.DIFFERENCE);}
        farColor=mix(0xff17283e,horizon,.27f);middleColor=mix(0xff101c30,horizon,.10f);land=new LinearGradient(0,635,0,720,0xff0a1425,0xff040a15,Shader.TileMode.CLAMP);
    }
    void draw(Canvas canvas,int minute,int rise,int set,boolean seasonal){
        minute=((minute%1440)+1440)%1440;prepare(minute,rise,set,seasonal);paint.setStyle(Paint.Style.FILL);paint.setAlpha(255);paint.setShader(sky);canvas.drawRect(0,0,1280,720,paint);
        paint.setShader(nebula);paint.setAlpha(Math.round(255*stars));canvas.drawRect(0,0,1280,720,paint);paint.setAlpha(255);
        paint.setShader(horizonGlow);canvas.drawRect(0,0,1280,720,paint);paint.setShader(mist);canvas.drawRect(0,380,1280,570,paint);
        paint.setShader(cloudColor);canvas.drawPath(cloudLeft,paint);canvas.drawPath(cloudRight,paint);canvas.drawPath(cloudLow,paint);paint.setShader(null);
        paint.setColor(alpha(0xffffba91,Math.round(94*dusk)));canvas.drawPath(sunsetBands,paint);
        // Shade the text's sky backdrop without turning the sun or moon grey.
        paint.setAlpha(255);paint.setShader(centerShade);canvas.drawRect(0,0,1280,720,paint);paint.setShader(null);
        if(stars>.01f)for(int j=0;j<64;j++){float x=starPoints[j*3],y=starPoints[j*3+1],r=starPoints[j*3+2];paint.setColor(alpha(0xffd5e7ff,Math.round((100+60*r)*stars)));canvas.drawCircle(x,y,r,paint);if(r>1.6f){paint.setStrokeWidth(.65f);paint.setAlpha(Math.round(100*stars));canvas.drawLine(x-4,y,x+4,y,paint);canvas.drawLine(x,y-4,x,y+4,paint);}}
        paint.setShader(moonHalo);paint.setAlpha(Math.round(255*stars));canvas.drawRect(900,0,1220,320,paint);paint.setShader(null);paint.setColor(alpha(0xffd9eaff,Math.round(235*stars)));canvas.save();canvas.translate(1060,145);canvas.drawPath(moon,paint);canvas.restore();
        paint.setAlpha(255);paint.setShader(halo);canvas.drawRect(0,0,1280,625,paint);paint.setShader(orb);paint.setAlpha(Math.round(240*sunOpacity));canvas.drawPath(sun,paint);paint.setShader(null);
        paint.setColor(farColor);canvas.drawPath(far,paint);paint.setColor(middleColor);canvas.drawPath(middle,paint);paint.setAlpha(255);paint.setShader(land);canvas.drawPath(near,paint);paint.setShader(null);paint.setColor(0xff070e1a);canvas.drawPath(cacti,paint);
    }
}


