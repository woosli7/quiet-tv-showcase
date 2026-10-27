package com.quiettv.home;
import java.util.List;

/** A bounded viewport over the existing live list, independent of match eligibility. */
final class CsLiveWindow {
    static int selected(List<CsMatch> rows,long id,int previous){
        if(id>0)for(int i=0;i<rows.size();i++)if(rows.get(i).id==id)return i;
        return Math.max(0,Math.min(previous,rows.size()-1));
    }
    static int first(int previous,int selected,int count,int capacity){
        capacity=Math.max(1,capacity);if(count<=capacity)return 0;
        selected=Math.max(0,Math.min(selected,count-1));int start=Math.max(0,Math.min(previous,count-capacity));
        if(selected<start)start=selected;else if(selected>=start+capacity)start=selected-capacity+1;
        return Math.max(0,Math.min(start,count-capacity));
    }
}
