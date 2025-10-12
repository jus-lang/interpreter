package org.jus.interpreter.runtime;

import org.jus.interpreter.JusInterpreter;
import org.jus.parser.JusParser;
import java.util.List;

public class Function {
    private final String name;
    private final List<Parameter> parameters;
    private final Value returnType;
    private final JusParser.ExpressionContext body;

    public Function(String name, List<Parameter> parameters, Value returnType, JusParser.ExpressionContext body) {
        this.name = name;
        this.parameters = parameters;
        this.returnType = returnType;
        this.body = body;
    }

    public Value execute(JusInterpreter interpreter, List<Value> arguments) {
        // Validate number of arguments
        if (arguments.size() != parameters.size()) {
            throw new RuntimeException("Function " + name + " expects " + parameters.size() + 
                                    " arguments, but got " + arguments.size());
        }

        // Create new scope for function execution
        interpreter.enterScope("function " + name);

        try {
            // Bind parameters to arguments
            for (int i = 0; i < parameters.size(); i++) {
                Parameter param = parameters.get(i);
                Value arg = arguments.get(i);
                interpreter.defineVariable(param.getName(), arg);
            }

            // Execute function body
            Value result = interpreter.visit(body);

            // Type check return value
            if (!isTypeCompatible(returnType, result)) {
                throw new RuntimeException("Function " + name + " returned incompatible type. " +
                                        "Expected: " + returnType.getType() + 
                                        ", got: " + result.getType());
            }

            return result;
        } finally {
            // Always exit scope, even if there's an error
            interpreter.exitScope("function " + name);
        }
    }

    private boolean isTypeCompatible(Value expected, Value actual) {
        // Basic type compatibility check
        return expected.getType().equals(actual.getType());
    }
}