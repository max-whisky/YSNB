package com.YSNB.yuanshen.core.network;

import java.io.IOException;
import java.util.Map;

public interface HttpTransport {
    String execute(String method, String url, Map<String, String> headers, String body)
            throws IOException;
}
