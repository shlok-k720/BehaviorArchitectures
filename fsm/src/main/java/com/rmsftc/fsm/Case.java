package com.rmsftc.fsm;

public abstract class Case {
    public Case() {

    }

    abstract void execute();

    protected void transition(Case other) {

    }
}
