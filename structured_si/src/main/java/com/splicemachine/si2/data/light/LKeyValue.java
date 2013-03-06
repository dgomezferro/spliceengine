package com.splicemachine.si2.data.light;

public class LKeyValue {
    final String family;
    final String qualifier;
    final Object value;
    final Long timestamp;

    public LKeyValue(String family, String qualifier, Long timestamp, Object value) {
        this.family = family;
        this.qualifier = qualifier;
        this.value = value;
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return family + "." + qualifier + "@" + timestamp + "=" + value;
    }
}
