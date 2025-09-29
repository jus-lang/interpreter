package org.jus.interpreter;

import org.antlr.v4.runtime.*;
import org.jus.parser.JusLexer;
import org.jus.parser.JusParser;

import java.io.IOException;

public class Main {
    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Uso: java -jar jus-interpreter.jar <arquivo.jus>");
            System.exit(1);
        }

        try {
            CharStream input = CharStreams.fromFileName(args[0]);
            JusLexer lexer = new JusLexer(input);
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            JusParser parser = new JusParser(tokens);
            JusParser.ProgramContext tree = parser.program();

            // TODO: Implementar um visitor ou listener para percorrer a árvore e interpretar o código
            System.out.println("Arquivo JUS analisado com sucesso!");

        } catch (IOException e) {
            System.err.println("Erro ao ler o arquivo: " + e.getMessage());
            System.exit(1);
        }
    }
}

