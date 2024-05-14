package com.unulearner.backend.storage.statics;

public enum StateCode{
    CANCELLED(-99),
    EXCEPTION(-1),
    RUNNING(0),
    COMPLETED(1);

    private final int value;
    StateCode(int v) {
        this.value = v;
    }

    public int getValue() {
        return this.value;
    }
}