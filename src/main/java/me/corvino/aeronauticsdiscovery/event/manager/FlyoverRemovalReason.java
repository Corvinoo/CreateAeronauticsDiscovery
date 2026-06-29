package me.corvino.aeronauticsdiscovery.event.manager;


public enum FlyoverRemovalReason {

    LIFETIME_EXPIRED("max lifetime reached"),

    DRIFTED_TOO_FAR("drifted too far from all players"),

    EXTERNAL_REQUEST("external request");

    private final String description;

    FlyoverRemovalReason(String description) {
        this.description = description;
    }

    public String describe() {
        return description;
    }
}