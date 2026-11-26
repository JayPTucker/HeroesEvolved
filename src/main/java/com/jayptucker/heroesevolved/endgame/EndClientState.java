package com.jayptucker.heroesevolved.endgame;

/** Common-side data only; native phase mixins can read this without loading any Minecraft client classes on a server. */
public final class EndClientState {
    private static EndView view=EndView.EMPTY;
    private static int elapsed;
    public static EndView view(){return view;}
    public static float age(float partial){return view.stageAge()+elapsed+partial;}
    /** Ticks since the last view arrived, for poses that run on between packets. */
    public static float since(float partial){return elapsed+partial;}
    public static void accept(EndView value){view=value;elapsed=0;}
    public static void tick(){if(++elapsed>100)clear();}
    public static void clear(){view=EndView.EMPTY;elapsed=0;}
    private EndClientState(){}
}
