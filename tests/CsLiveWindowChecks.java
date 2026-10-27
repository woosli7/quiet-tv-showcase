package com.quiettv.home;
import java.util.*;
public final class CsLiveWindowChecks {
    static int count;static void ok(boolean value,String label){if(!value)throw new AssertionError(label);count++;}
    static List<CsMatch> rows(int count){ArrayList<CsMatch> rows=new ArrayList<>();for(int i=0;i<count;i++){CsMatch m=new CsMatch();m.id=i+1;rows.add(m);}return rows;}
    public static void main(String[] args){
        ok(CsLiveWindow.first(3,2,0,2)==0,"empty live list resets viewport");
        ok(CsLiveWindow.first(3,0,1,2)==0,"one match needs no scrolling");
        ok(CsLiveWindow.first(3,1,2,2)==0,"two matches retain original layout");
        ok(CsLiveWindow.first(0,1,5,2)==0,"focus second card without moving viewport");
        ok(CsLiveWindow.first(0,2,5,2)==1,"third match scrolls into second slot");
        ok(CsLiveWindow.first(1,3,5,2)==2,"fourth match advances one row");
        ok(CsLiveWindow.first(2,4,5,2)==3,"fifth match is reachable without leaving home");
        ok(CsLiveWindow.first(3,3,5,2)==3,"moving within visible pair keeps viewport stable");
        ok(CsLiveWindow.first(3,2,5,2)==2,"scroll upward reveals earlier match");
        ok(CsLiveWindow.first(3,0,5,2)==0,"wrapping to first match resets viewport");
        ok(CsLiveWindow.first(0,4,5,1)==4,"one-card configuration also scrolls");
        ok(CsLiveWindow.selected(rows(5),4,0)==3,"refresh preserves selected identity");
        List<CsMatch> reordered=rows(5);Collections.reverse(reordered);ok(CsLiveWindow.selected(reordered,4,3)==1,"reorder follows the selected match rather than its old slot");
        ok(CsLiveWindow.selected(rows(3),5,4)==2,"finished selected match falls back to adjacent valid row");
        ok(CsLiveWindow.selected(Collections.emptyList(),5,4)==0,"expired list resets selection safely");
        ok(CsLiveWindow.selected(rows(3),0,-1)==0,"invalid previous selection clamps");
        for(int total:new int[]{1,2,3,5,20,100})for(int capacity:new int[]{1,2})for(int selected=0;selected<total;selected++)for(int previous:new int[]{-4,0,1,total-1,total+4}){
            int first=CsLiveWindow.first(previous,selected,total,capacity),end=Math.min(total,first+capacity);
            ok(first>=0&&first<=Math.max(0,total-capacity),"viewport remains within bounds");
            ok(first<=selected&&selected<end,"every live match remains visible when selected");
            ok(end-first<=capacity,"only configured visible-card count is drawn");
        }
        ok(new CsFavorites("[]").prioritize(rows(5),100).size()==5,"normal ordering can retain more than two live matches");
        System.out.println("COMPLETE: "+count+" live-window checks passed");
    }
}
