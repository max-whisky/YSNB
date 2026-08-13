package com.YSNB.yuanshen.core.model;

public final class QrLogin {
    private final String url;
    private final String ticket;
    private final String deviceId;

    public QrLogin(String url, String ticket, String deviceId) {
        this.url = url;
        this.ticket = ticket;
        this.deviceId = deviceId;
    }

    public String getUrl() { return url; }
    public String getTicket() { return ticket; }
    public String getDeviceId() { return deviceId; }
}
