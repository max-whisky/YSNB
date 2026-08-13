package com.YSNB.yuanshen.core.network;

public final class MihoyoApiConfig {
    public static final String APP_ID = "bll8iq97cem8";
    public static final String APP_VERSION = "2.102.1";
    public static final String K2_SALT = "lX8m5VO5at5JG7hR8hzqFwzyL5aB1tYo";
    public static final String LK2_SALT = "yBh10ikxtLPoIhgwgPZSv5dmfaOTSJ6a";
    public static final String FOUR_X_SALT = "xV8v4Qu54lUKrEYFZkJhB8cuOh9Asafs";
    public static final String LOGIN_PAGE =
            "https://user.mihoyo.com/login-platform/index.html"
                    + "?app_id=" + APP_ID
                    + "&app_version=" + APP_VERSION
                    + "&client_type=5"
                    + "&game_biz=hk4e_cn"
                    + "&lang=zh-cn"
                    + "&token_type=1"
                    + "#/login/captcha";

    private MihoyoApiConfig() {
    }
}
