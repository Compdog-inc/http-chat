package com.compdog.httpchat;

public class ClientSettings {

    public enum ClientTier {
        None("Free", 0), Tier1("Tier 1", 1), Tier2("Tier 2", 2);

        private final int level;
        private final String badge;

        ClientTier(String badge, int level){
            this.badge = badge;
            this.level = level;
        }

        public static ClientTier Parse(String str) {
            if (str.equalsIgnoreCase("1") || str.equalsIgnoreCase("tier 1")) {
                return ClientTier.Tier1;
            } else if (str.equalsIgnoreCase("2") || str.equalsIgnoreCase("tier 2")) {
                return ClientTier.Tier2;
            } else {
                return ClientTier.None;
            }
        }

        public String toBadge(){
            return badge;
        }

        public boolean atLeast(ClientTier tier){
            return level >= tier.level;
        }
    }

    public Long mutedUntil;
    public ClientTier tier;
    public String badge;

    public ClientSettings(){
        mutedUntil = 0L;
        tier = ClientTier.None;
        badge = "";
    }

    public void mute(long until){
        mutedUntil = until;
    }

    public void unmute(){
        mutedUntil = 0L;
    }

    public boolean isMuted() {
        return mutedUntil > System.currentTimeMillis();
    }

}
