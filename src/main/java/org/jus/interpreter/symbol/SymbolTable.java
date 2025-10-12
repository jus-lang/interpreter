package org.jus.interpreter.symbol;

import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

public class SymbolTable {
    private Stack<Map<String, Symbol>> scopes;

    public SymbolTable() {
        this.scopes = new Stack<>();
        enterScope(); // Global scope
    }

    public void enterScope() {
        scopes.push(new HashMap<>());
    }

    public void exitScope() {
        if (!scopes.isEmpty()) {
            scopes.pop();
        }
    }

    public void define(String name, Symbol symbol) {
        if (!scopes.isEmpty()) {
            scopes.peek().put(name, symbol);
        }
    }

    public Symbol resolve(String name) {
        for (int i = scopes.size() - 1; i >= 0; i--) {
            Symbol symbol = scopes.get(i).get(name);
            if (symbol != null) {
                return symbol;
            }
        }
        return null;
    }
}