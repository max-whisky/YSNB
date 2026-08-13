package com.YSNB.yuanshen.core.network;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class UrlConnectionTransport implements HttpTransport {
    private static final int TIMEOUT_MILLIS = 15_000;

    @Override
    public String execute(String method, String url, Map<String, String> headers, String body)
            throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(TIMEOUT_MILLIS);
        connection.setReadTimeout(TIMEOUT_MILLIS);
        connection.setUseCaches(false);
        headers.forEach(connection::setRequestProperty);
        try {
            if (body != null) {
                connection.setDoOutput(true);
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                connection.setFixedLengthStreamingMode(bytes.length);
                connection.getOutputStream().write(bytes);
            }
            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            String response = stream == null
                    ? ""
                    : readUtf8(stream);
            if (status < 200 || status >= 300) {
                throw new IOException("网络请求失败（状态码 " + status + "）");
            }
            return response;
        } finally {
            connection.disconnect();
        }
    }

    private static String readUtf8(InputStream input) throws IOException {
        try (InputStream stream = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8_192];
            int count;
            while ((count = stream.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }
}
