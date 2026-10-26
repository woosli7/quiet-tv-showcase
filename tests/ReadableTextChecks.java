package com.quiettv.home;

/** Run on Android with app_process; no provider requests or preference changes. */
public class ReadableTextChecks {
    static int count;
    static void check(boolean value,String name){if(!value)throw new AssertionError(name);count++;}
    static final ReadableText.Width WIDTH=(text,size)->text.codePointCount(0,text.length())*.61f*size;
    static void label(String value,float width,float size,int lines,float height){
        ReadableText.Label fit=ReadableText.fit(value,width,size,lines,height,WIDTH);
        check(fit.lines.length>=1&&fit.lines.length<=lines,"line budget");
        check(fit.size>0&&fit.size<=size,"valid font size");
        check(String.join(" ",fit.lines).replaceAll("\\s+","").equals(value.replaceAll("\\s+","")),"all characters retained");
        for(String line:fit.lines){check(WIDTH.measure(line,fit.size)<=width+.01f,"line fits width");
            check(line.isEmpty()||!Character.isLowSurrogate(line.charAt(0))&&!Character.isHighSurrogate(line.charAt(line.length()-1)),"code point intact");}
        check(fit.lines.length==1||fit.size*2.32f<=height+.01f,"two lines fit height");
    }
    public static void main(String[] args){
        label("YouTube",490,33,2,53);
        label("An installed application with a very long display name",490,33,2,53);
        label("AReallyLongApplicationNameWithoutWordBoundaries",490,33,2,53);
        label("★ Natus Vincere",170,18,2,29);
        label("An unusually long Counter Strike team name",170,18,2,29);
        label("ESL Challenger League Season 100 European Division Finals",390,13,1,16);
        label("NEXT TODAY: ★ Natus Vincere vs The MongolZ",453,13,2,26);
        label("A very long selected city / WEATHER UNAVAILABLE",500,12,2,28);
        label("Reconnect to the internet and press Menu to retry the latest live match update",498,11,2,28);
        label("Emoji😀Team😀Name😀With😀No😀Spaces",90,18,2,29);
        label("",170,18,2,29);
        ReadableText.Label shortLabel=ReadableText.fit("HULU",490,33,2,53,WIDTH);
        check(shortLabel.size==33&&shortLabel.lines.length==1,"short label unchanged");
        ReadableText.Label longLabel=ReadableText.fit("A long application name that should use two lines",490,33,2,53,WIDTH);
        check(longLabel.lines.length==2,"long app uses available second line");
        System.out.println("PASS "+count+" readable-label checks");
    }
}
