package com.YSNB.yuanshen.core.network;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.Map;
import org.junit.Test;

public final class MihoyoApiTest {
    @Test
    public void getCommunityNicknameReadsMiyousheNickname() throws Exception {
        CapturingTransport transport = new CapturingTransport();
        MihoyoApi api = new MihoyoApi(transport, new DsSigner(), "test-device");

        String nickname = api.getCommunityNickname("123456789");

        assertEquals("米游社昵称", nickname);
        assertEquals("GET", transport.method);
        assertEquals(
                "https://bbs-api.miyoushe.com/user/wapi/getUserFullInfo?uid=123456789",
                transport.url
        );
        assertEquals(null, transport.body);
        assertEquals("https://www.miyoushe.com/", transport.headers.get("Referer"));
    }

    @Test(expected = IOException.class)
    public void getCommunityNicknameRejectsMissingNickname() throws Exception {
        HttpTransport transport = (method, url, headers, body) ->
                "{\"retcode\":0,\"message\":\"OK\",\"data\":{\"user_info\":{}}}";
        MihoyoApi api = new MihoyoApi(transport, new DsSigner(), "test-device");

        api.getCommunityNickname("123456789");
    }

    private static final class CapturingTransport implements HttpTransport {
        private String method;
        private String url;
        private Map<String, String> headers;
        private String body;

        @Override
        public String execute(String method, String url, Map<String, String> headers, String body)
                throws IOException {
            this.method = method;
            this.url = url;
            this.headers = headers;
            this.body = body;
            return "{\"retcode\":0,\"message\":\"OK\",\"data\":{\"user_info\":"
                    + "{\"nickname\":\"米游社昵称\"}}}";
        }
    }
}
