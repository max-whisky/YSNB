package com.YSNB.yuanshen.ui;

public final class Event<T> {
    private final T value;
    private boolean handled;

    public Event(T value) {
        this.value = value;
    }

    public T consume() {
        if (handled) return null;
        handled = true;
        return value;
    }
}
