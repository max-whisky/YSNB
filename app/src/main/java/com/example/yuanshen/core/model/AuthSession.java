package com.YSNB.yuanshen.core.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

public final class AuthSession {
    private final String accountId;
    private final String stoken;
    private final Map<String, String> cookies;

    public AuthSession(String accountId, String stoken, Map<String, String> cookies) {
        this.accountId = Objects.requireNonNull(accountId, "accountId");
        this.stoken = Objects.requireNonNull(stoken, "stoken");
        this.cookies = Collections.unmodifiableMap(new LinkedHashMap<>(cookies));
    }

    public String getAccountId() {
        return accountId;
    }

    public String getStoken() {
        return stoken;
    }

    public Map<String, String> getCookies() {
        return cookies;
    }

    public boolean hasStoken() {
        return cookies.containsKey("stoken") || cookies.containsKey("stoken_v2");
    }

    public String toCookieHeader() {
        StringJoiner joiner = new StringJoiner("; ");
        cookies.forEach((name, value) -> joiner.add(name + "=" + value));
        return joiner.toString();
    }
}
