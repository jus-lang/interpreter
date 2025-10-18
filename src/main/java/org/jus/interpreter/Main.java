package org.jus.interpreter;

import org.antlr.v4.runtime.*;
import org.jus.interpreter.runtime.Value;
import org.jus.parser.JusLexer;
import org.jus.parser.JusParser;
import org.jus.interpreter.symbol.Symbol;

import java.io.IOException;

public class Main {
    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: java -jar jus-interpreter.jar <file.jus>");
            System.exit(1);
        }

        try {
            CharStream input = CharStreams.fromFileName(args[0]);
            JusLexer lexer = new JusLexer(input);
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            JusParser parser = new JusParser(tokens);
            JusParser.ProgramContext tree = parser.program();

            // Create and run the interpreter
            JusInterpreter interpreter = new JusInterpreter();
            
            System.out.println("=== Parsing and analyzing program ===");
            interpreter.visit(tree);
        } catch (IOException e) {
            System.err.println("Error reading file: " + e.getMessage());
            System.exit(1);
        }
    }
}

