package com.compdog.httpchat;

public class Message {
    String user;
    String badge;
    ClientSettings.ClientTier tier;
    String message;
    boolean isMuted;

    public Message(Client user, ClientSettings settings, String message) {
        this.user = user.getName();
        this.badge = settings.badge;
        this.tier = settings.tier;
        this.message = message;
        isMuted = user.isMuted();
    }
}
