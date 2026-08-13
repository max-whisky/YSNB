package com.YSNB.yuanshen.core.network;

import com.YSNB.yuanshen.core.model.AuthSession;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class MihoyoCookieParser {
    private static final List<String> ATTRIBUTES = List.of(
            "path", "domain", "expires", "max-age", "samesite"
    );
    private static final List<String> REQUIRED_COOKIES = List.of(
            "stoken", "stoken_v2", "stuid", "mid",
            "cookie_token_v2", "ltoken_v2", "ltuid_v2", "ltmid_v2",
            "account_id", "account_id_v2", "account_mid_v2", "DEVICEFP"
    );

    private MihoyoCookieParser() {
    }

    public static Map<String, String> merge(List<String> cookieHeaders) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String header : cookieHeaders) {
            if (header == null || header.isBlank()) continue;
            for (String segment : header.split(";")) {
                String trimmed = segment.trim();
                int equals = trimmed.indexOf('=');
                if (equals <= 0) continue;
                String name = trimmed.substring(0, equals).trim();
                String lowerName = name.toLowerCase(Locale.ROOT);
                if (ATTRIBUTES.contains(lowerName)) continue;
                result.put(name, trimmed.substring(equals + 1).trim());
            }
        }
        return result;
    }

    public static AuthSession extractSession(Map<String, String> cookies) {
        String sessionToken = firstNonBlank(
                cookies.get("stoken"),
                cookies.get("stoken_v2"),
                cookies.get("cookie_token_v2")
        );
        String accountId = firstNonBlank(
                cookies.get("stuid"),
                cookies.get("account_id"),
                cookies.get("ltuid_v2"),
                cookies.get("account_id_v2")
        );
        if (sessionToken == null || accountId == null) return null;

        Map<String, String> normalized = new LinkedHashMap<>();
        for (String name : REQUIRED_COOKIES) {
            String value = cookies.get(name);
            if (value != null && !value.isBlank()) normalized.put(name, value);
        }
        if (cookies.containsKey("stoken") || cookies.containsKey("stoken_v2")) {
            normalized.put("stoken", sessionToken);
            normalized.put("stuid", accountId);
        }
        return new AuthSession(accountId, sessionToken, normalized);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }
}
