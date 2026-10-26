package com.quiettv.home;
public final class CsRemoteInputChecks {
    static int count;
    static void ok(boolean value,String label){if(!value)throw new AssertionError(label);count++;}
    public static void main(String[] args){
        CsRemoteInput n=new CsRemoteInput();int left=21,right=22,okKey=23;
        ok(n.release(left,0,0,100,false,-1)==0,"Wait for release");
        ok(n.release(left,0,1,100,false,-1)==0,"Ignore held-key repeat");
        ok(n.release(left,0,2,100,false,-1)==0,"Ignore further repeats");
        ok(n.release(left,1,0,100,false,-1)==-1,"Held key changes exactly one day");
        ok(n.release(left,1,0,100,false,-1)==0,"Duplicate release cannot change twice");
        ok(n.step(-1,300),"First step");ok(!n.step(-1,360),"Reject duplicate click burst");
        ok(n.step(1,380),"Allow quick correction in opposite direction");
        ok(n.step(1,660),"Allow normal next press at boundary");
        ok(!n.step(1,700),"Guard programmatic and footer-click paths too");
        n.release(right,0,0,800,false,1);ok(n.owns(right),"Own the complete key stream");
        ok(n.release(right,1,0,800,false,2)==1,"Focus change during press cannot reinterpret day as footer move");
        n.release(okKey,0,0,900,false,-1);ok(n.release(okKey,1,0,900,false,-1)==-1,"OK on previous-day button activates once");
        ok(n.release(okKey,1,0,900,false,-1)==0,"Ignore duplicated native button release");
        n.release(left,0,0,1000,false,-1);ok(n.release(left,1,0,1000,true,-1)==0,"Canceled press does not change date");
        ok(n.release(left,1,0,1100,false,-1)==0,"Release without down does not activate");
        n.release(right,0,0,1200,false,2);ok(n.release(right,1,0,1200,false,2)==2,"Footer arrow stays a focus action");
        n.release(left,0,0,1300,false,-1);ok(n.release(left,1,0,1301,false,-1)==0,"Mismatched stream does not activate");
        n.reset();ok(!n.owns(left),"New dialog has no stuck key");ok(n.step(-1,1),"New dialog accepts first press immediately");
        ok(!n.step(-1,0),"Out-of-order timestamp rejected");
        n.release(left,0,0,1400,false,-1);n.release(left,0,0,1400,false,-1);ok(n.release(left,1,0,1400,false,-1)==-1,"Duplicated initial down still activates only once");
        System.out.println(count+" remote date-input checks passed.");
    }
}
