package com.YSNB.yuanshen.core.model;

public final class QrLoginStatus {
    public enum State { CREATED, SCANNED, CONFIRMED, EXPIRED }

    private final State state;
    private final String rawAccount;

    public QrLoginStatus(State state, String rawAccount) {
        this.state = state;
        this.rawAccount = rawAccount;
    }

    public State getState() { return state; }
    public String getRawAccount() { return rawAccount; }
}
