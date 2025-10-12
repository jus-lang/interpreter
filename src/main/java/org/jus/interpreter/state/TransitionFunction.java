package org.jus.interpreter.state;

import org.jus.interpreter.runtime.Value;

@FunctionalInterface
public interface TransitionFunction {
    String execute(Value eventData);
}