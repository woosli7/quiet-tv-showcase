package com.quiettv.home;

/** One command per completed remote press, with a short same-command bounce guard. */
final class CsRemoteInput {
    private int key=-1,command,lastCommand;
    private long down=-1,lastStep=-1;
    boolean owns(int value){return key==value;}
    void reset(){key=-1;command=0;down=-1;lastStep=-1;lastCommand=0;}
    int release(int value,int action,int repeats,long started,boolean canceled,int requested){
        if(action==0){
            if(repeats==0&&requested!=0){key=value;down=started;command=requested;}
            return 0;
        }
        if(action!=1||key!=value||down!=started)return 0;
        int result=command;key=-1;down=-1;command=0;
        return canceled?0:result;
    }
    boolean step(int value,long now){
        if(lastStep>=0&&(now<lastStep||(lastCommand==value&&now-lastStep<280)))return false;
        lastCommand=value;lastStep=now;return true;
    }
}
