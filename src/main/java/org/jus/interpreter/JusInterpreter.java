package org.jus.interpreter;

import org.jus.interpreter.runtime.Function;
import org.jus.interpreter.runtime.Parameter;
import org.jus.interpreter.runtime.Value;
import org.jus.interpreter.state.StateManager;
import org.jus.interpreter.state.TransitionFunction;
import org.jus.interpreter.symbol.Symbol;
import org.jus.interpreter.symbol.SymbolTable;
import org.jus.parser.JusBaseVisitor;
import org.jus.parser.JusParser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JusInterpreter extends JusBaseVisitor<Value> {
    private SymbolTable symbolTable;
    private StateManager stateManager;
    private int indentLevel = 0;

    public JusInterpreter() {
        this.symbolTable = new SymbolTable();
        this.stateManager = new StateManager();
    }

    private void log(String message) {
        String indent = "  ".repeat(indentLevel);
        System.out.println(indent + message);
    }

    public void enterScope(String scopeName) {
        log("Entering " + scopeName);
        symbolTable.enterScope();
        indentLevel++;
    }

    public void exitScope(String scopeName) {
        indentLevel--;
        log("Exiting " + scopeName);
        symbolTable.exitScope();
    }

    @Override
    public Value visitProgram(JusParser.ProgramContext ctx) {
        log("Starting program interpretation");
        enterScope("program");
        ctx.children.forEach(this::visit);
        exitScope("program");
        log("Finished program interpretation");
        return null;
    }

    @Override
    public Value visitModule_definition(JusParser.Module_definitionContext ctx) {
        String moduleName = ctx.IDENTIFIER().getText();
        log("Processing module: " + moduleName);
        
        enterScope("module " + moduleName);
        Symbol moduleSymbol = new Symbol(moduleName, "module");
        symbolTable.define(moduleName, moduleSymbol);
        log("Defined module symbol: " + moduleName);
        
        ctx.definition().forEach(this::visit);
        
        exitScope("module " + moduleName);
        return null;
    }

    @Override
    public Value visitContract_definition(JusParser.Contract_definitionContext ctx) {
        String contractName = ctx.IDENTIFIER().getText();
        log("Processing contract: " + contractName);
        
        enterScope("contract " + contractName);
        Symbol contractSymbol = new Symbol(contractName, "contract");
        symbolTable.define(contractName, contractSymbol);
        log("Defined contract symbol: " + contractName);
        
        ctx.definition().forEach(this::visit);
        
        exitScope("contract " + contractName);
        return null;
    }

    @Override
    public Value visitType_definition(JusParser.Type_definitionContext ctx) {
        String typeName = ctx.IDENTIFIER().getText();
        log("Processing type definition: " + typeName);
        
        Value typeValue = visit(ctx.type_expression());
        Symbol typeSymbol = new Symbol(typeName, "type", typeValue);
        symbolTable.define(typeName, typeSymbol);
        log("Defined type symbol: " + typeName + " with value type: " + typeValue.getType());
        
        return null;
    }

    @Override
    public Value visitType_expression(JusParser.Type_expressionContext ctx) {
        log("Processing type expression: " + ctx.getText());
        Value result;
        
        if (ctx.IDENTIFIER() != null) {
            result = new Value(ctx.IDENTIFIER().getText(), "type");
        } else if (ctx.getChildCount() == 3 && ctx.getChild(1).getText().equals("->")) {
            Value from = visit(ctx.type_expression(0));
            Value to = visit(ctx.type_expression(1));
            result = new Value(Map.of("from", from, "to", to), "function_type");
        } else if (ctx.getChildCount() == 3 && ctx.getChild(1).getText().equals("*")) {
            Value left = visit(ctx.type_expression(0));
            Value right = visit(ctx.type_expression(1));
            result = new Value(List.of(left, right), "product_type");
        } else if (ctx.MAP() != null) {
            Value keyType = visit(ctx.type_expression(0));
            Value valueType = visit(ctx.type_expression(1));
            result = new Value(Map.of("key", keyType, "value", valueType), "map_type");
        } else {
            result = new Value(ctx.getText(), "primitive_type");
        }
        
        log("Type expression result: " + result.getType());
        return result;
    }

    @Override
    public Value visitState_definition(JusParser.State_definitionContext ctx) {
        log("Processing state definition");
        enterScope("state");
        
        List<Value> constructors = new ArrayList<>();
        for (JusParser.State_constructorContext constructor : ctx.state_constructor()) {
            Value constructorValue = visit(constructor);
            constructors.add(constructorValue);
            // Adiciona o estado ao gerenciador
            stateManager.addState(constructorValue.getValue().toString());
        }
        
        Symbol stateSymbol = new Symbol("State", "state_type", constructors);
        symbolTable.define("State", stateSymbol);
        log("Defined state type with " + constructors.size() + " constructors");
        
        // Define o estado inicial como o primeiro construtor
        if (!constructors.isEmpty()) {
            stateManager.setState(constructors.get(0).getValue().toString());
            log("Set initial state to: " + constructors.get(0).getValue());
        }
        
        exitScope("state");
        return null;
    }

    @Override
    public Value visitState_constructor(JusParser.State_constructorContext ctx) {
        String constructorName = ctx.IDENTIFIER().getText();
        log("Processing state constructor: " + constructorName);
        
        Symbol constructor = new Symbol(constructorName, "state_constructor");
        symbolTable.define(constructorName, constructor);
        log("Defined state constructor: " + constructorName);
        
        return new Value(constructorName, "state_constructor");
    }

    @Override
    public Value visitTransition_definition(JusParser.Transition_definitionContext ctx) {
        String stateName = ctx.IDENTIFIER(0).getText();
        String eventName = ctx.IDENTIFIER(1).getText();
        log("Processing transition from state '" + stateName + "' on event '" + eventName + "'");
        
        // Criar uma função de transição que avalia a expressão e retorna o novo estado
        TransitionFunction transitionFunction = (eventData) -> {
            symbolTable.enterScope();
            // Bind event data to the event parameter
            symbolTable.define(ctx.IDENTIFIER(1).getText(), 
                new Symbol(eventName, eventData.getType(), eventData.getValue()));
            
            Value result = visit(ctx.expression());
            symbolTable.exitScope();
            
            if (result == null || result.getValue() == null) {
                throw new RuntimeException("Transition must return a valid state");
            }
            
            return result.getValue().toString();
        };
        
        // Registrar a transição no gerenciador de estado
        stateManager.addTransition(stateName, eventName, transitionFunction);
        
        // Armazenar a definição da transição na tabela de símbolos
        Map<String, Object> transitionData = new HashMap<>();
        transitionData.put("state", stateName);
        transitionData.put("event", eventName);
        
        String transitionId = "transition_" + stateName + "_" + eventName;
        Symbol transition = new Symbol("transition", "transition", transitionData);
        symbolTable.define(transitionId, transition);
        log("Defined transition: " + transitionId);
        
        return null;
    }

    @Override
    public Value visitStruct_definition(JusParser.Struct_definitionContext ctx) {
        String structName = ctx.IDENTIFIER().getText();
        log("Processing struct definition: " + structName);
        
        Map<String, Value> fields = new HashMap<>();
        for (JusParser.Struct_fieldContext field : ctx.struct_field()) {
            String fieldName = field.IDENTIFIER().getText();
            Value fieldType = visit(field.type_expression());
            fields.put(fieldName, fieldType);
            log("  Field: " + fieldName + " of type: " + fieldType.getType());
        }
        
        Symbol structSymbol = new Symbol(structName, "struct", fields);
        symbolTable.define(structName, structSymbol);
        log("Defined struct: " + structName + " with " + fields.size() + " fields");
        
        return null;
    }

    @Override
    public Value visitExpression(JusParser.ExpressionContext ctx) {
        log("Processing expression: " + ctx.getText());
        Value result;
        
        if (ctx.IDENTIFIER() != null) {
            String id = ctx.IDENTIFIER().getText();
            Symbol symbol = symbolTable.resolve(id);
            if (symbol != null) {
                result = new Value(symbol.getValue(), symbol.getType());
                log("Resolved identifier: " + id + " to type: " + result.getType());
            } else {
                log("WARNING: Undefined identifier: " + id);
                result = null;
            }
            return result;
        }
        return visitChildren(ctx);
    }

    @Override
    public Value visitFunction_definition(JusParser.Function_definitionContext ctx) {
        String functionName = ctx.IDENTIFIER().getText();
        log("Processing function definition: " + functionName);

        // Get return type
        Value returnType = visit(ctx.type_expression());
        
        // Process parameters
        List<Parameter> parameters = new ArrayList<>();
        if (ctx.parameter_list() != null) {
            for (JusParser.ParameterContext param : ctx.parameter_list().parameter()) {
                String paramName = param.IDENTIFIER().getText();
                Value paramType = visit(param.type_expression());
                parameters.add(new Parameter(paramName, paramType));
                log("  Parameter: " + paramName + " of type: " + paramType.getType());
            }
        }

        // Create function object
        Function function = new Function(functionName, parameters, returnType, ctx.expression());
        Symbol functionSymbol = new Symbol(functionName, "function", function);
        symbolTable.define(functionName, functionSymbol);
        
        log("Defined function: " + functionName);
        return null;
    }

    @Override
    public Value visitFunction_call(JusParser.Function_callContext ctx) {
        String functionName = ctx.IDENTIFIER().getText();
        log("Processing function call: " + functionName);

        // Resolve function
        Symbol functionSymbol = symbolTable.resolve(functionName);
        if (functionSymbol == null || !(functionSymbol.getValue() instanceof Function)) {
            throw new RuntimeException("Function not found or symbol is not a function: " + functionName);
        }

        Function function = (Function) functionSymbol.getValue();

        // Evaluate arguments
        List<Value> arguments = new ArrayList<>();
        if (ctx.parameter_list() != null) {
            log("Evaluating function arguments");
            for (JusParser.ParameterContext param : ctx.parameter_list().parameter()) {
                Value arg = visit(param);
                arguments.add(arg);
                log("  Argument evaluated to: " + arg.getValue() + " of type: " + arg.getType());
            }
        }

        // Execute function
        log("Executing function: " + functionName);
        Value result = function.execute(this, arguments);
        log("Function returned: " + result.getValue() + " of type: " + result.getType());
        
        return result;
    }

    // Helper method to define variables (used by Function.execute)
    public void defineVariable(String name, Value value) {
        Symbol symbol = new Symbol(name, value.getType(), value.getValue());
        symbolTable.define(name, symbol);
        log("Defined variable: " + name + " with value: " + value.getValue());
    }

    // Implementar avaliação de expressões condicionais (if)
    @Override
    public Value visitIf_expression(JusParser.If_expressionContext ctx) {
        log("Evaluating if expression");
        
        Value condition = visit(ctx.expression(0));
        if (!(condition.getValue() instanceof Boolean)) {
            throw new RuntimeException("If condition must be boolean, got: " + condition.getType());
        }

        if ((Boolean) condition.getValue()) {
            log("Condition is true, evaluating 'then' branch");
            return visit(ctx.expression(1));
        } else {
            log("Condition is false, evaluating 'else' branch");
            return visit(ctx.expression(2));
        }
    }

    // Implementar comparações e operações booleanas
    private Value evaluateComparison(Value left, String operator, Value right) {
        switch (operator) {
            case ">":
                if (left.getValue() instanceof Number && right.getValue() instanceof Number) {
                    double l = ((Number) left.getValue()).doubleValue();
                    double r = ((Number) right.getValue()).doubleValue();
                    return new Value(l > r, "Bool");
                }
                break;
            case ">=":
                if (left.getValue() instanceof Number && right.getValue() instanceof Number) {
                    double l = ((Number) left.getValue()).doubleValue();
                    double r = ((Number) right.getValue()).doubleValue();
                    return new Value(l >= r, "Bool");
                }
                break;
            case "==":
                return new Value(left.getValue().equals(right.getValue()), "Bool");
            // Adicionar outros operadores conforme necessário
        }
        throw new RuntimeException("Invalid comparison between " + left.getType() + " and " + right.getType());
    }

    @Override
    public Value visitLiteral(JusParser.LiteralContext ctx) {
        log("Processing literal: " + ctx.getText());
        Value result;
        
        if (ctx.NAT_LITERAL() != null) {
            result = new Value(Integer.parseInt(ctx.NAT_LITERAL().getText()), "Nat");
        } else if (ctx.STRING_LITERAL() != null) {
            String text = ctx.STRING_LITERAL().getText();
            result = new Value(text.substring(1, text.length() - 1), "String");
        } else if (ctx.BOOL_LITERAL() != null) {
            result = new Value(Boolean.parseBoolean(ctx.BOOL_LITERAL().getText()), "Bool");
        } else {
            result = null;
        }
        
        if (result != null) {
            log("Literal value: " + result.getValue() + " of type: " + result.getType());
        }
        return result;
    }

    // Novo método para executar eventos
    public Value executeEvent(String eventName, Value eventData) {
        log("Executing event: " + eventName + " with data: " + eventData);
        String oldState = stateManager.getCurrentState();
        String newState = stateManager.handleEvent(eventName, eventData);
        log("State transition: " + oldState + " -> " + newState);
        return new Value(newState, "State");
    }

    public String getCurrentState() {
        return stateManager.getCurrentState();
    }

    public StateManager getStateManager() {
        return stateManager;
    }

    public SymbolTable getSymbolTable() {
        return symbolTable;
    }
}