package com.YSNB.yuanshen.core.network;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

public final class DsSigner {
    private static final char[] RANDOM_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789".toCharArray();
    private final SecureRandom random = new SecureRandom();

    public String signWithoutBody(String salt) {
        long timestamp = System.currentTimeMillis() / 1000L;
        String randomText = randomText(6);
        String source = "salt=" + salt + "&t=" + timestamp + "&r=" + randomText;
        return timestamp + "," + randomText + "," + md5(source);
    }

    public String signWithQuery(String salt, String query) {
        long timestamp = System.currentTimeMillis() / 1000L;
        int randomNumber = random.nextInt(100_000) + 100_001;
        String source = "salt=" + salt + "&t=" + timestamp + "&r=" + randomNumber
                + "&b=&q=" + query;
        return timestamp + "," + randomNumber + "," + md5(source);
    }

    private String randomText(int length) {
        StringBuilder builder = new StringBuilder(length);
        while (builder.length() < length) {
            char candidate = RANDOM_CHARS[random.nextInt(RANDOM_CHARS.length)];
            if (builder.indexOf(String.valueOf(candidate)) < 0) builder.append(candidate);
        }
        return builder.toString();
    }

    private static String md5(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte item : digest) hex.append(String.format("%02x", item & 0xff));
            return hex.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("当前系统不支持所需的摘要算法", impossible);
        }
    }
}
