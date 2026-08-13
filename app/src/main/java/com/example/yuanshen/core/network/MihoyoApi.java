package com.YSNB.yuanshen.core.network;

import com.YSNB.yuanshen.core.model.AuthSession;
import com.YSNB.yuanshen.core.model.GachaPage;
import com.YSNB.yuanshen.core.model.GachaRecord;
import com.YSNB.yuanshen.core.model.GameRole;
import com.YSNB.yuanshen.core.model.QrLogin;
import com.YSNB.yuanshen.core.model.QrLoginStatus;
import java.io.IOException;
import java.net.URI;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class MihoyoApi {
    private static final String QR_APP_ID = "9";
    private static final String QR_FETCH =
            "https://hk4e-sdk.mihoyo.com/hk4e_cn/combo/panda/qrcode/fetch";
    private static final String QR_QUERY =
            "https://hk4e-sdk.mihoyo.com/hk4e_cn/combo/panda/qrcode/query";
    private static final String TOKEN_EXCHANGE =
            "https://api-takumi.mihoyo.com/account/ma-cn-session/app/getTokenByGameToken";
    private static final String ROLES =
            "https://api-takumi.miyoushe.com/binding/api/getUserGameRolesByStoken";
    private static final String ROLES_BY_COOKIE =
            "https://api-takumi.mihoyo.com/binding/api/getUserGameRolesByCookie?game_biz=hk4e_cn";
    private static final String AUTH_KEY_BY_STOKEN =
            "https://api-takumi.miyoushe.com/binding/api/genAuthKey";
    private static final String AUTH_KEY_BY_COOKIE_TOKEN =
            "https://passport-api.mihoyo.com/account/binding/api/genAuthKeyByCookieToken";
    private static final String GACHA =
            "https://public-operation-hk4e.mihoyo.com/gacha_info/api/getGachaLog";

    private final HttpTransport transport;
    private final DsSigner signer;
    private final String qrDeviceId;

    public MihoyoApi(HttpTransport transport, DsSigner signer, String qrDeviceId) {
        this.transport = transport;
        this.signer = signer;
        this.qrDeviceId = qrDeviceId;
    }

    public QrLogin createQrLogin() throws IOException {
        JSONObject body = jsonObject("app_id", QR_APP_ID, "device", qrDeviceId);
        JSONObject data = data(post(QR_FETCH, basicJsonHeaders(), body.toString()));
        String url = requiredString(data, "url");
        String ticket = queryParameter(url, "ticket");
        if (ticket == null || ticket.isBlank()) throw new IOException("登录二维码缺少必要标识");
        return new QrLogin(url, ticket, qrDeviceId);
    }

    public QrLoginStatus queryQrLogin(QrLogin login) throws IOException {
        JSONObject body = jsonObject(
                "app_id", QR_APP_ID,
                "device", login.getDeviceId(),
                "ticket", login.getTicket()
        );
        JSONObject root = post(QR_QUERY, basicJsonHeaders(), body.toString());
        if (root.optInt("retcode") == -106) {
            return new QrLoginStatus(QrLoginStatus.State.EXPIRED, null);
        }
        JSONObject data = data(root);
        String status = data.optString("stat");
        if ("Scanned".equals(status)) return new QrLoginStatus(QrLoginStatus.State.SCANNED, null);
        if ("Confirmed".equals(status)) {
            JSONObject payload = data.optJSONObject("payload");
            if (payload == null) throw new IOException("扫码登录数据不完整");
            String raw = payload.optString("raw");
            return new QrLoginStatus(QrLoginStatus.State.CONFIRMED, raw);
        }
        return new QrLoginStatus(QrLoginStatus.State.CREATED, null);
    }

    public AuthSession exchangeGameToken(String rawAccount) throws IOException {
        try {
            JSONObject raw = new JSONObject(rawAccount);
            String accountId = requiredString(raw, "uid");
            String gameToken = requiredString(raw, "token");
            JSONObject body = jsonObject("account_id", accountId, "game_token", gameToken);
            Map<String, String> headers = basicJsonHeaders();
            headers.put("x-rpc-app_id", MihoyoApiConfig.APP_ID);
            JSONObject data = data(post(TOKEN_EXCHANGE, headers, body.toString()));
            String stoken = requiredString(data.getJSONObject("token"), "token");
            Map<String, String> cookies = new LinkedHashMap<>();
            cookies.put("stuid", accountId);
            cookies.put("stoken", stoken);
            return new AuthSession(accountId, stoken, cookies);
        } catch (JSONException error) {
            throw new IOException("扫码登录数据格式异常", error);
        }
    }

    public List<GameRole> getRoles(AuthSession session) throws IOException {
        Map<String, String> headers;
        String url;
        if (session.hasStoken()) {
            headers = signedHeaders(session, "2", MihoyoApiConfig.K2_SALT);
            url = ROLES;
        } else {
            headers = cookieSignedHeaders(session, "game_biz=hk4e_cn");
            url = ROLES_BY_COOKIE;
        }
        JSONObject data = data(get(url, headers));
        JSONArray array = data.optJSONArray("list");
        List<GameRole> roles = new ArrayList<>();
        if (array == null) return roles;
        for (int index = 0; index < array.length(); index++) {
            JSONObject item = array.optJSONObject(index);
            if (item == null) continue;
            if (!"hk4e_cn".equals(item.optString("game_biz"))) continue;
            roles.add(new GameRole(
                    item.optString("game_biz"),
                    item.optString("region"),
                    item.optString("game_uid"),
                    item.optString("nickname"),
                    item.optInt("level"),
                    item.optString("region_name")
            ));
        }
        return roles;
    }

    public String generateAuthKey(AuthSession session, GameRole role) throws IOException {
        JSONObject body = jsonObject(
                "game_biz", "hk4e_cn",
                "game_uid", role.getUid(),
                "region", role.getRegion(),
                "auth_appid", "webview_gacha"
        );
        String url;
        Map<String, String> headers;
        if (session.hasStoken()) {
            url = AUTH_KEY_BY_STOKEN;
            headers = signedHeaders(session, "5", MihoyoApiConfig.LK2_SALT);
        } else {
            url = AUTH_KEY_BY_COOKIE_TOKEN;
            headers = passportCookieHeaders(session);
        }
        JSONObject data = data(post(url, headers, body.toString()));
        return requiredString(data, "authkey");
    }

    public GachaPage getGachaPage(
            String authKey,
            GameRole role,
            String gachaType,
            int page,
            String endId
    ) throws IOException {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("authkey_ver", "1");
        params.put("sign_type", "2");
        params.put("auth_appid", "webview_gacha");
        params.put("authkey", authKey);
        params.put("lang", "zh-cn");
        params.put("game_biz", "hk4e_cn");
        params.put("region", role.getRegion());
        params.put("size", "20");
        params.put("gacha_type", gachaType);
        params.put("page", String.valueOf(page));
        params.put("end_id", endId);
        JSONObject data = data(get(GACHA + "?" + encodeQuery(params), Map.of(
                "User-Agent", "Mozilla/5.0",
                "Accept", "application/json"
        )));
        JSONArray list = data.optJSONArray("list");
        List<GachaRecord> records = new ArrayList<>();
        if (list != null) {
            for (int index = 0; index < list.length(); index++) {
                JSONObject item = list.optJSONObject(index);
                if (item == null) continue;
                String id = item.optString("id");
                String name = item.optString("name");
                String itemType = item.optString("item_type");
                String time = item.optString("time");
                String rank = item.optString("rank_type");
                if (id.isBlank() || name.isBlank() || itemType.isBlank() || time.isBlank()) continue;
                int rankType;
                try {
                    rankType = Integer.parseInt(rank);
                } catch (NumberFormatException ignored) {
                    continue;
                }
                records.add(new GachaRecord(
                        id,
                        role.getUid(),
                        item.optString("gacha_type", gachaType),
                        name,
                        itemType,
                        rankType,
                        time
                ));
            }
        }
        return new GachaPage(records);
    }

    private Map<String, String> signedHeaders(AuthSession session, String clientType, String salt) {
        Map<String, String> headers = basicJsonHeaders();
        headers.put("Cookie", session.toCookieHeader());
        headers.put("DS", signer.signWithoutBody(salt));
        headers.put("x-rpc-app_version", MihoyoApiConfig.APP_VERSION);
        headers.put("x-rpc-client_type", clientType);
        headers.put("x-rpc-device_id", UUID.nameUUIDFromBytes(
                session.getAccountId().getBytes(StandardCharsets.UTF_8)).toString());
        headers.put("User-Agent", "Mozilla/5.0 miHoYoBBS/" + MihoyoApiConfig.APP_VERSION);
        headers.put("Referer", "https://app.mihoyo.com");
        return headers;
    }

    private Map<String, String> cookieSignedHeaders(AuthSession session, String query) {
        Map<String, String> headers = basicJsonHeaders();
        headers.put("Cookie", session.toCookieHeader());
        headers.put("DS", signer.signWithQuery(MihoyoApiConfig.FOUR_X_SALT, query));
        headers.put("x-rpc-app_version", MihoyoApiConfig.APP_VERSION);
        headers.put("x-rpc-client_type", "5");
        headers.put("x-rpc-device_id", UUID.nameUUIDFromBytes(
                session.getAccountId().getBytes(StandardCharsets.UTF_8)).toString());
        headers.put("X-Requested-With", "com.mihoyo.hyperion");
        headers.put("User-Agent", "Mozilla/5.0 miHoYoBBS/" + MihoyoApiConfig.APP_VERSION);
        headers.put("Origin", "https://webstatic.mihoyo.com");
        headers.put("Referer", "https://webstatic.mihoyo.com/");
        return headers;
    }

    private Map<String, String> passportCookieHeaders(AuthSession session) {
        Map<String, String> headers = basicJsonHeaders();
        headers.put("Cookie", session.toCookieHeader());
        headers.put("x-rpc-app_id", MihoyoApiConfig.APP_ID);
        headers.put("x-rpc-app_version", MihoyoApiConfig.APP_VERSION);
        headers.put("x-rpc-game_biz", "hk4e_cn");
        headers.put("x-rpc-client_type", "5");
        headers.put("x-rpc-device_id", UUID.nameUUIDFromBytes(
                session.getAccountId().getBytes(StandardCharsets.UTF_8)).toString());
        String deviceFp = session.getCookies().get("DEVICEFP");
        if (deviceFp != null && !deviceFp.isBlank()) headers.put("x-rpc-device_fp", deviceFp);
        headers.put("x-rpc-mi_referrer", MihoyoApiConfig.LOGIN_PAGE);
        headers.put("Origin", "https://user.mihoyo.com");
        headers.put("Referer", "https://user.mihoyo.com/");
        headers.put("User-Agent", "Mozilla/5.0");
        return headers;
    }

    private static Map<String, String> basicJsonHeaders() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json; charset=UTF-8");
        headers.put("Accept", "application/json");
        return headers;
    }

    private JSONObject get(String url, Map<String, String> headers) throws IOException {
        return parse(transport.execute("GET", url, headers, null));
    }

    private JSONObject post(String url, Map<String, String> headers, String body) throws IOException {
        return parse(transport.execute("POST", url, headers, body));
    }

    private static JSONObject parse(String json) throws IOException {
        try {
            return new JSONObject(json);
        } catch (JSONException error) {
            throw new IOException("接口返回了无法识别的数据", error);
        }
    }

    private static JSONObject jsonObject(Object... keyValues) throws IOException {
        try {
            JSONObject object = new JSONObject();
            for (int index = 0; index < keyValues.length; index += 2) {
                object.put((String) keyValues[index], keyValues[index + 1]);
            }
            return object;
        } catch (JSONException error) {
            throw new IOException("无法构造接口请求", error);
        }
    }

    private static JSONObject data(JSONObject root) throws IOException {
        int code = root.optInt("retcode", Integer.MIN_VALUE);
        if (code != 0) {
            String message = root.optString("message");
            if (code == -100 || code == 10001) message = "登录已失效，请重新登录";
            if (code == -1034 || code == 1034) message = "请求触发了米游社安全验证，请稍后重试";
            throw new ApiException(code, message);
        }
        JSONObject data = root.optJSONObject("data");
        if (data == null) throw new IOException("接口返回数据不完整");
        return data;
    }

    private static String requiredString(JSONObject object, String name) throws IOException {
        String value = object.optString(name);
        if (value.isBlank()) throw new IOException("接口返回缺少字段：" + name);
        return value;
    }

    private static String encodeQuery(Map<String, String> params) {
        StringBuilder result = new StringBuilder();
        params.forEach((name, value) -> {
            if (result.length() > 0) result.append('&');
            result.append(urlEncode(name));
            result.append('=');
            result.append(urlEncode(value));
        });
        return result.toString();
    }

    private static String queryParameter(String url, String name) {
        String query = URI.create(url).getRawQuery();
        if (query == null) return null;
        for (String part : query.split("&")) {
            int equals = part.indexOf('=');
            if (equals > 0 && name.equals(part.substring(0, equals))) {
                return urlDecode(part.substring(equals + 1));
            }
        }
        return null;
    }

    private static String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException impossible) {
            throw new IllegalStateException("当前设备不支持通用文本编码", impossible);
        }
    }

    private static String urlDecode(String value) {
        try {
            return java.net.URLDecoder.decode(value, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException impossible) {
            throw new IllegalStateException("当前设备不支持通用文本编码", impossible);
        }
    }
}
