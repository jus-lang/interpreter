package org.jus.interpreter.symbol;

public class Symbol {
    private String name;
    private String type;
    private Object value;

    public Symbol(String name, String type) {
        this.name = name;
        this.type = type;
    }

    public Symbol(String name, String type, Object value) {
        this.name = name;
        this.type = type;
        this.value = value;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }
}