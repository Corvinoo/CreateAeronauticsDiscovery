package me.corvino.aeronauticsdiscovery.pin.behaviour;

public record ConfigField(String key, String label, FieldType type, Object defaultValue) {
    public enum FieldType {
        FLOAT, DOUBLE, INTEGER, BOOLEAN, STRING, RESOURCE_LOCATION
    }
}
