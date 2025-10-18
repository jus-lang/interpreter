package org.jus.interpreter.runtime;

public class Value {
    private Object value;
    private String type;

    public Value(Object value, String type) {
        this.value = value;
        this.type = type;
    }

    public Object getValue() {
        return value;
    }

    public String getType() {
        return type;
    }

    @Override
    public String toString() {
        return value.toString();
    }
}