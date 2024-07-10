package com.unulearner.backend.storage.miscellaneous;

public class Holder<T> {
    private T value;

    public Holder() {
        this.value = null;
    }

    public Holder(T value) {
        this.value = value;
    }

    public T getValue() {
        return this.value;
    }

    public void setValue(T value) {
        this.value = value;
    }
}
