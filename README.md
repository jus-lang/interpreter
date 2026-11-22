[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

# Interpretador JUS

Este repositório contém o protótipo de um interpretador para a linguagem de programação funcional JUS, projetada para o desenvolvimento de contratos inteligentes seguros e verificáveis na plataforma Tezos.

## Estrutura do Projeto

O projeto utiliza o **Apache Maven** para gerenciamento de dependências e automação do build. A estrutura de diretórios segue as convenções padrão do Maven:

- `src/main/java`: Contém o código-fonte Java do interpretador.
- `src/main/antlr4`: Contém o arquivo de gramática ANTLR v4 (`Jus.g4`) para a linguagem JUS.
- `src/test/java`: Contém os testes unitários do projeto.
- `pom.xml`: O arquivo de configuração do Maven, que define as dependências e o processo de build.

## Compatibilidade com IDEs

O uso do Maven garante que o projeto seja compatível com a maioria dos ambientes de desenvolvimento Java. A configuração foi pensada para funcionar perfeitamente com:

- **IntelliJ IDEA**: A IDE tem suporte nativo para projetos Maven. Para importar, basta abrir o arquivo `pom.xml` como um projeto.
- **NetBeans**: O NetBeans também oferece excelente suporte a Maven. Vá em `File > Open Project` e selecione o diretório `jus-interpreter`.
- **Emacs**: Para usuários do Emacs, o projeto pode ser gerenciado através do terminal com comandos Maven, ou utilizando plugins como o `emacs-maven-mode` para uma integração mais profunda.

## Build do Projeto

Para construir o projeto, você precisará ter o **Apache Maven** e o **Java Development Kit (JDK)** (versão 11 ou superior) instalados.

1. **Navegue até o diretório raiz do projeto**:
   ```sh
   cd jus-interpreter
   ```

2. **Compile o projeto e gere as classes do ANTLR**:
   ```sh
   mvn compile
   ```
   Este comando irá invocar o plugin do ANTLR para gerar o lexer, o parser e as classes de visitor/listener a partir da gramática `Jus.g4` no diretório `target/generated-sources/antlr4`.

3. **Empacote o projeto em um arquivo JAR**:
   ```sh
   mvn package
   ```
   O arquivo JAR final, `jus-interpreter-1.0-SNAPSHOT.jar`, será criado no diretório `target`.

## Executando o Interpretador

Após empacotar o projeto, você pode executar o interpretador a partir da linha de comando:

```sh
java -jar target/jus-interpreter-1.0-SNAPSHOT.jar <caminho_para_seu_arquivo.jus>
```

## Próximos Passos

O ponto de entrada atual (`Main.java`) apenas analisa o arquivo de entrada e imprime uma mensagem de sucesso. Os próximos passos no desenvolvimento do interpretador incluem:

1. **Implementar um Visitor ou Listener**: Criar uma classe que estenda `JusBaseVisitor` ou `JusBaseListener` para percorrer a árvore sintática abstrata (AST) gerada pelo ANTLR.
2. **Construir a Tabela de Símbolos**: Gerenciar escopos, variáveis e funções.
3. **Avaliar as Expressões**: Implementar a lógica para interpretar as diferentes construções da linguagem JUS.
4. **Gerenciar o Estado**: Implementar a máquina de estados finitos que é central para o design da linguagem JUS.

## Licença

Este projeto está licenciado sob a licença Apache 2.0 - veja o arquivo [LICENSE](LICENSE) para mais detalhes.
