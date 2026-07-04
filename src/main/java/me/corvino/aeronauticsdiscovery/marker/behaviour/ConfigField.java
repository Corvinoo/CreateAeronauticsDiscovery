package me.corvino.aeronauticsdiscovery.marker.behaviour;

public record ConfigField(String key, String label, FieldType type, Object defaultValue) {
    public enum FieldType {
        FLOAT, DOUBLE, INTEGER, STRING, RESOURCE_LOCATION
    }
}
