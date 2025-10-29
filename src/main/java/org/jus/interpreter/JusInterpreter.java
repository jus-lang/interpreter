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
        // A expressão agora delega para comparison_expression
        return visit(ctx.comparison_expression());
    }

    // Método para processar expressões de comparação
    @Override
    public Value visitComparison_expression(JusParser.Comparison_expressionContext ctx) {
        log("Processing comparison expression: " + ctx.getText());
        
        // Se é apenas uma expressão de acesso a membro, delega para ela
        if (ctx.member_access_expression() != null) {
            return visit(ctx.member_access_expression());
        }
        
        // Caso contrário, é uma operação de comparação
        Value left = visit(ctx.comparison_expression(0));
        Value right = visit(ctx.comparison_expression(1));
        
        log("Comparing: " + left.getValue() + " with " + right.getValue());
        
        // Verificar se os tipos são compatíveis
        if (!areTypesComparable(left.getType(), right.getType())) {
            throw new RuntimeException("Cannot compare incompatible types: " + 
                                     left.getType() + " and " + right.getType());
        }
        
        // Determinar qual operador foi usado
        String operator = ctx.getChild(1).getText();
        boolean result = performComparison(left, right, operator);
        
        log("Comparison result: " + result);
        return new Value(result, "Bool");
    }

    // Novo método para processar acesso a membros de estruturas
    @Override
    public Value visitMember_access_expression(JusParser.Member_access_expressionContext ctx) {
        log("Processing member access expression: " + ctx.getText());
        
        // Se é apenas uma expressão primária, delega para ela
        if (ctx.primary_expression() != null) {
            return visit(ctx.primary_expression());
        }
        
        // Caso contrário, é um acesso a membro (obj.field)
        Value object = visit(ctx.member_access_expression());
        String fieldName = ctx.IDENTIFIER().getText();
        
        log("Accessing field '" + fieldName + "' of object with type: " + object.getType());
        
        // Verificar se o objeto é uma estrutura ou tem campos
        if (object.getValue() instanceof Map) {
            Map<String, Object> structInstance = (Map<String, Object>) object.getValue();
            if (structInstance.containsKey(fieldName)) {
                Object fieldValue = structInstance.get(fieldName);
                // Inferir o tipo do campo baseado no valor
                String fieldType = inferType(fieldValue);
                log("Found field '" + fieldName + "' with value: " + fieldValue + " of type: " + fieldType);
                return new Value(fieldValue, fieldType);
            } else {
                throw new RuntimeException("Field '" + fieldName + "' not found in struct");
            }
        } else {
            throw new RuntimeException("Cannot access field '" + fieldName + "' on non-struct type: " + object.getType());
        }
    }

    // Método para processar expressões primárias
    @Override
    public Value visitPrimary_expression(JusParser.Primary_expressionContext ctx) {
        log("Processing primary expression: " + ctx.getText());
        
        if (ctx.IDENTIFIER() != null) {
            String id = ctx.IDENTIFIER().getText();
            Symbol symbol = symbolTable.resolve(id);
            if (symbol != null) {
                Value result = new Value(symbol.getValue(), symbol.getType());
                log("Resolved identifier: " + id + " to type: " + result.getType());
                return result;
            } else {
                log("WARNING: Undefined identifier: " + id);
                return null;
            }
        } else if (ctx.literal() != null) {
            return visit(ctx.literal());
        } else if (ctx.function_call() != null) {
            return visit(ctx.function_call());
        } else if (ctx.match_expression() != null) {
            return visit(ctx.match_expression());
        } else if (ctx.if_expression() != null) {
            return visit(ctx.if_expression());
        } else if (ctx.expression() != null) {
            // Expressão entre parênteses
            return visit(ctx.expression());
        }
        
        return null;
    }

    // Método auxiliar para inferir tipo baseado no valor
    private String inferType(Object value) {
        if (value instanceof Integer) {
            return "Nat";
        } else if (value instanceof String) {
            return "String";
        } else if (value instanceof Boolean) {
            return "Bool";
        } else if (value instanceof Map) {
            return "struct";
        } else {
            return "unknown";
        }
    }

    // Método auxiliar para verificar se dois tipos podem ser comparados
    private boolean areTypesComparable(String type1, String type2) {
        // Tipos iguais sempre podem ser comparados
        if (type1.equals(type2)) {
            return true;
        }
        
        // Tipos numéricos podem ser comparados entre si
        if ((type1.equals("Nat") || type1.equals("Int")) && 
            (type2.equals("Nat") || type2.equals("Int"))) {
            return true;
        }
        
        return false;
    }

    // Método auxiliar para realizar a comparação
    private boolean performComparison(Value left, Value right, String operator) {
        Object leftVal = left.getValue();
        Object rightVal = right.getValue();
        
        switch (operator) {
            case ">":
                return compareNumbers(leftVal, rightVal) > 0;
            case "<":
                return compareNumbers(leftVal, rightVal) < 0;
            case ">=":
                return compareNumbers(leftVal, rightVal) >= 0;
            case "<=":
                return compareNumbers(leftVal, rightVal) <= 0;
            case "==":
                return leftVal.equals(rightVal);
            case "!=":
                return !leftVal.equals(rightVal);
            default:
                throw new RuntimeException("Unknown comparison operator: " + operator);
        }
    }

    // Método auxiliar para comparar números
    private int compareNumbers(Object left, Object right) {
        if (left instanceof Integer && right instanceof Integer) {
            return Integer.compare((Integer) left, (Integer) right);
        } else if (left instanceof Double && right instanceof Double) {
            return Double.compare((Double) left, (Double) right);
        } else if (left instanceof Integer && right instanceof Double) {
            return Double.compare(((Integer) left).doubleValue(), (Double) right);
        } else if (left instanceof Double && right instanceof Integer) {
            return Double.compare((Double) left, ((Integer) right).doubleValue());
        } else {
            throw new RuntimeException("Cannot compare non-numeric values: " + 
                                     left.getClass() + " and " + right.getClass());
        }
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
        if (ctx.argument_list() != null) {
            log("Evaluating function arguments");
            for (JusParser.ExpressionContext expr : ctx.argument_list().expression()) {
                Value arg = visit(expr);
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

    @Override
    public Value visitEvent_definition(JusParser.Event_definitionContext ctx) {
        log("Processing event definition");
        enterScope("event");
        
        List<Value> constructors = new ArrayList<>();
        for (JusParser.Event_constructorContext constructor : ctx.event_constructor()) {
            Value constructorValue = visit(constructor);
            constructors.add(constructorValue);
        }
        
        Symbol eventSymbol = new Symbol("Event", "event_type", constructors);
        symbolTable.define("Event", eventSymbol);
        log("Defined event type with " + constructors.size() + " constructors");
        
        exitScope("event");
        return null;
    }

    @Override
    public Value visitEvent_constructor(JusParser.Event_constructorContext ctx) {
        String constructorName = ctx.IDENTIFIER().getText();
        log("Processing event constructor: " + constructorName);
        
        Symbol constructor = new Symbol(constructorName, "event_constructor");
        symbolTable.define(constructorName, constructor);
        log("Defined event constructor: " + constructorName);
        
        return new Value(constructorName, "event_constructor");
    }

    @Override
    public Value visitMatch_expression(JusParser.Match_expressionContext ctx) {
        log("Processing match expression");
        
        // Avaliar todas as expressões do match
        List<Value> matchValues = new ArrayList<>();
        for (JusParser.ExpressionContext exprCtx : ctx.expression()) {
            Value value = visit(exprCtx);
            matchValues.add(value);
            log("Match value: " + value.getValue() + " of type: " + value.getType());
        }
        
        // Tentar cada caso
        for (JusParser.Match_caseContext caseCtx : ctx.match_case()) {
            log("Trying match case: " + caseCtx.pattern().getText());
            
            if (tryMatchPattern(caseCtx.pattern(), matchValues)) {
                log("Pattern matched, evaluating expression");
                return visit(caseCtx.expression());
            }
        }
        
        throw new RuntimeException("No matching pattern found in match expression");
    }

    private boolean tryMatchPattern(JusParser.PatternContext pattern, List<Value> values) {
        symbolTable.enterScope();
        boolean matched = matchPattern(pattern, values);
        if (!matched) {
            symbolTable.exitScope();
        }
        return matched;
    }

    private boolean matchPattern(JusParser.PatternContext pattern, List<Value> values) {
        // Pattern wildcard (_) sempre faz match
        if (pattern.UNDERSCORE() != null) {
            log("Wildcard pattern matched");
            return true;
        }
        
        // Pattern de tupla: (p1, p2, ...)
        if (pattern.pattern() != null && !pattern.pattern().isEmpty()) {
            List<JusParser.PatternContext> subPatterns = pattern.pattern();
            
            if (subPatterns.size() != values.size()) {
                log("Pattern tuple size mismatch: expected " + subPatterns.size() + ", got " + values.size());
                return false;
            }
            
            // Verificar cada sub-padrão
            for (int i = 0; i < subPatterns.size(); i++) {
                List<Value> singleValue = new ArrayList<>();
                singleValue.add(values.get(i));
                
                if (!matchPattern(subPatterns.get(i), singleValue)) {
                    return false;
                }
            }
            
            log("Tuple pattern matched");
            return true;
        }
        
        // Pattern de identificador: bind variável ou match construtor
        if (pattern.IDENTIFIER() != null) {
            String id = pattern.IDENTIFIER().getText();
            
            // Se temos apenas um valor
            if (values.size() == 1) {
                Value value = values.get(0);
                
                // Tentar resolver como construtor
                Symbol symbol = symbolTable.resolve(id);
                if (symbol != null && 
                    (symbol.getType().equals("state_constructor") || 
                     symbol.getType().equals("event_constructor"))) {
                    // É um construtor, verificar se o valor corresponde
                    boolean matches = value.getValue().equals(id);
                    log("Constructor pattern '" + id + "' " + (matches ? "matched" : "did not match"));
                    return matches;
                }
                
                // Caso contrário, é uma variável - fazer bind
                symbolTable.define(id, new Symbol(id, value.getType(), value.getValue()));
                log("Bound variable '" + id + "' to value: " + value.getValue());
                return true;
            }
        }
        
        log("Pattern did not match");
        return false;
    }
}