package com.quiettv.home;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;

/** Static native-view styling; keeps Android's remote navigation and dialog lifecycle. */
final class MenuStyle {
    static final int PANEL=0xff10151f,EDGE=0xff303a49,TEXT=0xffdde5ef,MUTED=0xff8e9db0,AMBER=0xffe9ad72;
    static int dp(View view,float value){return Math.round(view.getResources().getDisplayMetrics().density*value);}
    static GradientDrawable shape(View view,int fill,int stroke,float radius){
        GradientDrawable drawable=new GradientDrawable();drawable.setColor(fill);drawable.setCornerRadius(dp(view,radius));
        if(stroke!=0)drawable.setStroke(dp(view,1),stroke);return drawable;
    }
    static StateListDrawable focus(View view){
        StateListDrawable states=new StateListDrawable();
        states.addState(new int[]{-android.R.attr.state_enabled},shape(view,0xff121923,0xff242d3a,6));
        states.addState(new int[]{android.R.attr.state_pressed},shape(view,0xff3b3029,AMBER,6));
        states.addState(new int[]{android.R.attr.state_focused},shape(view,0xff2b2828,AMBER,6));
        states.addState(new int[]{android.R.attr.state_selected},shape(view,0xff2b2828,AMBER,6));
        states.addState(new int[]{},shape(view,0xff171f2c,EDGE,6));return states;
    }
    static ColorStateList colors(){return new ColorStateList(new int[][]{{-android.R.attr.state_enabled},{android.R.attr.state_focused},{android.R.attr.state_selected},{}},new int[]{0xff526071,0xffffd1a4,0xffffd1a4,TEXT});}
    static void text(TextView text){
        text.setTypeface(Typeface.create("monospace",Typeface.NORMAL));text.setTextSize("quiet-caption".equals(text.getTag())?12:16);text.setTextColor("quiet-caption".equals(text.getTag())?MUTED:TEXT);text.setIncludeFontPadding(false);text.setLineSpacing(dp(text,2),1);
    }
    static void button(Button button){
        text(button);button.setTextSize(13);button.setAllCaps(false);button.setTextColor(colors());button.setBackground(focus(button));
        button.setMinHeight(dp(button,40));button.setMinimumHeight(dp(button,40));button.setMinWidth(dp(button,76));button.setMinimumWidth(dp(button,76));
        button.setPadding(dp(button,14),dp(button,10),dp(button,14),dp(button,10));button.setStateListAnimator(null);
    }
    static void row(View row){
        if(row instanceof TextView){TextView text=(TextView)row;text(text);text.setTextColor(colors());text.setMinHeight(dp(row,46));text.setMinimumHeight(dp(row,46));text.setGravity(Gravity.CENTER_VERTICAL);text.setPadding(dp(row,14),dp(row,12),dp(row,14),dp(row,12));if(text instanceof CheckedTextView)((CheckedTextView)text).setCheckMarkTintList(new ColorStateList(new int[][]{{android.R.attr.state_checked},{}},new int[]{AMBER,MUTED}));}
        else if(row instanceof LinearLayout){
            LinearLayout group=(LinearLayout)row;row.setMinimumHeight(dp(row,58));row.setPadding(dp(row,14),dp(row,11),dp(row,14),dp(row,11));
            for(int i=0;i<group.getChildCount();i++)if(group.getChildAt(i) instanceof TextView){TextView text=(TextView)group.getChildAt(i);text(text);text.setTextSize(i==0?18:12);text.setTextColor(i==0?colors():ColorStateList.valueOf(MUTED));if(i>0)text.setPadding(0,dp(text,5),0,0);}
        }
    }
    static void list(ListView list){
        list.setSelector(focus(list));list.setDrawSelectorOnTop(false);list.setDivider(new ColorDrawable(Color.TRANSPARENT));list.setDividerHeight(dp(list,4));list.setPadding(0,dp(list,3),0,dp(list,3));list.setClipToPadding(false);
        for(int i=0;i<list.getChildCount();i++)row(list.getChildAt(i));
    }
    static void view(View view){
        view.setAlpha(1f);
        if(view instanceof ListView){list((ListView)view);return;}
        if(view instanceof CompoundButton){CompoundButton box=(CompoundButton)view;text(box);box.setButtonTintList(new ColorStateList(new int[][]{{android.R.attr.state_checked},{}},new int[]{AMBER,MUTED}));}
        else if(view instanceof Button)button((Button)view);
        else if(view instanceof EditText){TextView text=(TextView)view;text(text);text.setHintTextColor(MUTED);text.setBackgroundTintList(ColorStateList.valueOf(AMBER));}
        else if(view instanceof TextView){TextView label=(TextView)view;text(label);if(label.getId()==android.R.id.message){label.setGravity(Gravity.START);label.setPadding(0,dp(label,12),0,dp(label,10));}}
        else if(view instanceof ProgressBar)((ProgressBar)view).setIndeterminateTintList(ColorStateList.valueOf(AMBER));
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)view(group.getChildAt(i));}
    }
    static View named(AlertDialog dialog,String name){int id=dialog.getContext().getResources().getIdentifier(name,"id","android");return id==0?null:dialog.findViewById(id);}
    static void footer(AlertDialog dialog){
        for(int id:new int[]{AlertDialog.BUTTON_POSITIVE,AlertDialog.BUTTON_NEUTRAL,AlertDialog.BUTTON_NEGATIVE}){
            Button button=dialog.getButton(id);if(button==null)continue;button(button);
            if(button.getLayoutParams() instanceof LinearLayout.LayoutParams){LinearLayout.LayoutParams size=(LinearLayout.LayoutParams)button.getLayoutParams();size.width=ViewGroup.LayoutParams.WRAP_CONTENT;size.height=ViewGroup.LayoutParams.WRAP_CONTENT;size.weight=0;size.gravity=Gravity.CENTER_VERTICAL;size.setMargins(dp(button,4),0,dp(button,4),0);button.setLayoutParams(size);}
            if(button.getParent() instanceof LinearLayout){LinearLayout group=(LinearLayout)button.getParent();group.setOrientation(LinearLayout.HORIZONTAL);group.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);group.setPadding(0,0,0,0);group.setMinimumHeight(0);}
        }
        View panel=named(dialog,"buttonPanel");if(panel!=null){panel.setPadding(0,dp(panel,10),0,0);panel.setMinimumHeight(0);}
    }
    static void dialog(AlertDialog dialog){
        Window window=dialog.getWindow();View decor=window.getDecorView();window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);window.setDimAmount(.68f);WindowManager.LayoutParams attributes=window.getAttributes();attributes.alpha=1;attributes.windowAnimations=0;window.setAttributes(attributes);
        View panel=named(dialog,"parentPanel");if(panel!=null){
            panel.setBackground(shape(panel,PANEL,EDGE,12));panel.setPadding(dp(panel,24),dp(panel,20),dp(panel,24),dp(panel,20));
            ViewGroup.LayoutParams size=panel.getLayoutParams();size.width=Math.min(dp(panel,640),Math.round(panel.getResources().getDisplayMetrics().widthPixels*.82f));panel.setLayoutParams(size);
        }
        view(decor);
        View title=named(dialog,"alertTitle");if(title instanceof TextView){TextView label=(TextView)title;label.setTextSize(20);label.setTextColor(TEXT);label.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);label.setPadding(0,0,0,dp(label,4));}
        View divider=named(dialog,"titleDivider");if(divider!=null){divider.setBackground(new ColorDrawable(EDGE));ViewGroup.LayoutParams size=divider.getLayoutParams();size.height=dp(divider,1);divider.setLayoutParams(size);}
        footer(dialog);
    }
}
