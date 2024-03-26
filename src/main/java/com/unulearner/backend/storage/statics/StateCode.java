package com.unulearner.backend.storage.statics;

public enum StateCode{
    ERROR(-99),
    CANCELLED(-1),
    RUNNING(0),
    COMPLETED(1),
    EXCEPTION(99);

    private final int value;
    StateCode(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}