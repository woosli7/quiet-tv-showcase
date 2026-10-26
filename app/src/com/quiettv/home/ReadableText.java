package com.quiettv.home;
import android.app.AlertDialog;
import android.view.*;
import android.widget.*;

/** Full labels, without marquees or per-frame layout work. */
final class ReadableText {
    static void wrap(TextView text){
        if(text instanceof EditText)return;
        text.setSingleLine(false);text.setMaxLines(Integer.MAX_VALUE);
        text.setEllipsize(null);text.setHorizontallyScrolling(false);
        ViewGroup.LayoutParams params=text.getLayoutParams();
        if(params!=null&&params.height>0){params.height=ViewGroup.LayoutParams.WRAP_CONTENT;text.setLayoutParams(params);}
    }
    static void view(View view){
        if(view instanceof ListView){rows((ListView)view);return;}
        if(view instanceof TextView)wrap((TextView)view);
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)view(group.getChildAt(i));}
    }
    static void rows(ListView list){
        list.setOnHierarchyChangeListener(new ViewGroup.OnHierarchyChangeListener(){
            public void onChildViewAdded(View parent,View child){view(child);}
            public void onChildViewRemoved(View parent,View child){}
        });
        for(int i=0;i<list.getChildCount();i++)view(list.getChildAt(i));
    }
    static void dialog(AlertDialog dialog){
        View decor=dialog.getWindow().getDecorView();view(decor);
        int id=dialog.getContext().getResources().getIdentifier("alertTitle","id","android");
        TextView title=id==0?null:decor.findViewById(id);if(title!=null)title.setTextSize(22);
        decor.post(()->{if(!dialog.isShowing()||dialog.getListView()==null)return;
            android.util.DisplayMetrics metrics=dialog.getContext().getResources().getDisplayMetrics();
            ListView list=dialog.getListView();int cap=Math.min(Math.round(metrics.density*300),Math.round(metrics.heightPixels*.58f));
            if(list.getHeight()>cap){ViewGroup.LayoutParams params=list.getLayoutParams();params.height=cap;list.setLayoutParams(params);}
        });
    }
    interface Width {float measure(String text,float size);}
    static final class Label {
        final String[] lines;final float size;
        Label(String[] value,float height){lines=value;size=height;}
    }
    static Label fit(String text,float width,float size,int lines,float height,Width measure){
        float full=measure.measure(text,size);
        if(full<=width)return new Label(new String[]{text},size);
        float single=size*width/Math.max(1,full),best=0;int split=-1;
        if(lines>1){
            // Prefer word boundaries; a single long word can break between code points.
            boolean words=false;for(int i=1;i<text.length()-1;i++)if(Character.isWhitespace(text.charAt(i))){words=true;break;}
            for(int i=Character.charCount(text.codePointAt(0));i<text.length();i+=Character.charCount(text.codePointAt(i))){
                if(words&&!Character.isWhitespace(text.charAt(i)))continue;
                String left=text.substring(0,i).trim(),right=text.substring(i).trim();if(left.isEmpty()||right.isEmpty())continue;
                float widest=Math.max(measure.measure(left,size),measure.measure(right,size));
                float candidate=Math.min(size,Math.min(size*width/Math.max(1,widest),height/2.32f));
                if(candidate>best){best=candidate;split=i;}
            }
        }
        if(split>=0&&best>single*1.1f)return new Label(new String[]{text.substring(0,split).trim(),text.substring(split).trim()},best);
        return new Label(new String[]{text},single);
    }
}
