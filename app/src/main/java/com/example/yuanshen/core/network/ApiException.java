package com.YSNB.yuanshen.core.network;

import java.io.IOException;

public final class ApiException extends IOException {
    private final int code;

    public ApiException(int code, String message) {
        super(message == null || message.isBlank() ? "米游社接口请求失败（" + code + "）" : message);
        this.code = code;
    }

    public int getCode() { return code; }
}
