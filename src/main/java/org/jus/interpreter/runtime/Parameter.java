package org.jus.interpreter.runtime;

public class Parameter {
    private final String name;
    private final Value type;

    public Parameter(String name, Value type) {
        this.name = name;
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public Value getType() {
        return type;
    }
}