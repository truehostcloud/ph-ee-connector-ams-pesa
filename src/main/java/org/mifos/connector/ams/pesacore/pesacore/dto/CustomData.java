package org.mifos.connector.ams.pesacore.pesacore.dto;

public class CustomData {
    public String key;
    public Object value;

    public String getKey() {
        return this.key;
    }

    public Object getValue() {
        return this.value;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public String toString() {
        return "CustomData(key=" + this.getKey() + ", value=" + this.getValue() + ")";
    }

    public CustomData() {
    }

    public CustomData(String key, Object value) {
        this.key = key;
        this.value = value;
    }
}
