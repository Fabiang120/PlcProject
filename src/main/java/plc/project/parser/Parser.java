package plc.project.parser;

import com.google.common.base.Preconditions;
import plc.project.lexer.Token;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * This style of parser is called <em>recursive descent</em>. Each rule in our
 * grammar has dedicated function, and references to other rules correspond to
 * calling that function. Recursive rules are therefore supported by actual
 * recursive calls, while operator precedence is encoded via the grammar.
 *
 * <p>The parser has a similar architecture to the lexer, just with
 * {@link Token}s instead of characters. As before, {@link TokenStream#peek} and
 * {@link TokenStream#match} help with traversing the token stream. Instead of
 * emitting tokens, you will instead need to extract the literal value via
 * {@link TokenStream#get} to be added to the relevant AST.
 */
public final class Parser {

    private final TokenStream tokens;

    public Parser(List<Token> tokens) {
        this.tokens = new TokenStream(tokens);
    }

    public Ast parse(String rule) throws ParseException {
        var ast = switch (rule) {
            case "source" -> parseSource();
            case "stmt" -> parseStmt();
            case "expr" -> parseExpr();
            default -> throw new AssertionError(rule);
        };
        if (tokens.has(0)) {
            throw new ParseException("Expected end of input.", tokens.getNext());
        }
        return ast;
    }

    private Ast.Source parseSource() throws ParseException {
        var statements = new ArrayList<Ast.Stmt>();
        while (tokens.has(0)) {
            statements.add(parseStmt());
        }
        return new Ast.Source(statements);
    }

    private Ast.Stmt parseStmt() throws ParseException {
        if (tokens.peek("LET")){
            return parseLetStmt();
        }
        else if (tokens.peek("DEF")) {
            return parseDefStmt();
        }
        else if (tokens.peek("IF")) {
            return parseIfStmt();
        }
        else if (tokens.peek("FOR")) {
            return parseForStmt();
        }
        else if (tokens.peek("RETURN")) {
            return parseReturnStmt();
        }
        else {
            return parseExpressionOrAssignmentStmt();
        }
    }

    private Ast.Stmt parseLetStmt() throws ParseException {
        tokens.match("LET");

        if(!tokens.match(Token.Type.IDENTIFIER)){
            throw new ParseException("Expected 'IDENTIFER' after LET", tokens.getNext());
        }
        String name = tokens.get(-1).literal();

        Optional<Ast.Expr> expr1 = Optional.empty();

        if (tokens.match("=")) {
            expr1 = Optional.of(parseExpr());
        }

        if(!tokens.match(";")){
            throw new ParseException("Expected 'SEMICOLON' at end of LET", tokens.getNext());
        }
        return new Ast.Stmt.Let(name,expr1);
    }

    private Ast.Stmt parseDefStmt() throws ParseException {
        tokens.match("DEF");


        if(!tokens.match(Token.Type.IDENTIFIER)){
            throw new ParseException("Expected 'IDENTIFER' after DEF", tokens.getNext());
        }
        String name = tokens.get(-1).literal();

        if(!tokens.match("(")){
            throw new ParseException("Expected '(' after DEF", tokens.getNext());
        }


        List<String> statements1 = new ArrayList<>();
        if (tokens.match(Token.Type.IDENTIFIER)) {
            statements1.add(tokens.get(-1).literal());
            while (tokens.has(0) && tokens.match(",")) {
                if (!tokens.match(Token.Type.IDENTIFIER)) {
                    throw new ParseException("Expected identifier after ',' in parameter list", tokens.getNext());
                }
                statements1.add(tokens.get(-1).literal());
            }
        }


        if(!tokens.match(")")){
            throw new ParseException("Expected ')' after Identifer", tokens.getNext());
        }

        if(!tokens.match("DO")){
            throw new  ParseException("Expected 'DO' after )", tokens.getNext());
        }

        List<Ast.Stmt> statements2 = new ArrayList<>();
        while (tokens.has(0) && !tokens.peek("END")) {
            statements2.add(parseStmt());
        }

        if(!tokens.match("END")){
            throw new ParseException("Expected 'END' after DO", tokens.getNext());
        }
        return new Ast.Stmt.Def(name, statements1, statements2);
    }

    private Ast.Stmt parseIfStmt() throws ParseException {
        tokens.match("IF");
        Ast.Expr expr1 = parseExpr();

        if (!tokens.match("DO")) {
            throw new ParseException("Expected 'DO' after expression", tokens.getNext());
        }

        List<Ast.Stmt> statements = new ArrayList<>();
        while (tokens.has(0) && !tokens.peek("ELSE") && !tokens.peek("END")) {
            statements.add(parseStmt());
        }

        List<Ast.Stmt> statements2 = new ArrayList<>();
        if (tokens.match("ELSE")) {
            while (tokens.has(0) && !tokens.peek("END")) {
                statements2.add(parseStmt());
            }
        }

        if (!tokens.match("END")) {
            throw new ParseException("Expected 'END' to close IF statement", tokens.getNext());
        }

        return new Ast.Stmt.If(expr1, statements, statements2);
    }

    private Ast.Stmt parseForStmt() throws ParseException {
        tokens.match("FOR");
        if (!tokens.match(Token.Type.IDENTIFIER)) {
            throw new ParseException("Expected identifier after 'FOR'", tokens.getNext());
        }

        String name = tokens.get(-1).literal();
        if (!tokens.match("IN")) {
            throw new ParseException("Expected 'IN' after identifier", tokens.getNext());
        }

        Ast.Expr expr1 = parseExpr();
        if (!tokens.match("DO")) {
            throw new ParseException("Expected 'DO' after expression", tokens.getNext());
        }

        List<Ast.Stmt> statements = new ArrayList<>();
        while (tokens.has(0) && !tokens.peek("END") ) {
            statements.add(parseStmt());
        }

        if (!tokens.match("END")) {
            throw new ParseException("Expected 'END' to close for-statement", tokens.getNext());
        }

        return new Ast.Stmt.For(name, expr1, statements);
    }


    private Ast.Stmt parseReturnStmt() throws ParseException {
        tokens.match("RETURN");
        Optional<Ast.Expr> value = Optional.empty();
        Optional<Ast.Expr> cond = Optional.empty();

        if (!tokens.peek("IF") && !tokens.peek(";")) {
            value = Optional.of(parseExpr());
        }
        if (tokens.match("IF")) {
            cond = Optional.of(parseExpr());
        }
        if (!tokens.match(";")) {
            throw new ParseException("Expected ';' after RETURN", tokens.getNext());
        }
        if (cond.isEmpty()) {
            return new Ast.Stmt.Return(value);
        }
        return new Ast.Stmt.If(
            cond.get(),
            List.of(new Ast.Stmt.Return(value)),
            List.of()
        );
    }


    private Ast.Stmt parseExpressionOrAssignmentStmt() throws ParseException {
        Ast.Expr expr1 =parseExpr();
        if(tokens.match("=")){
            Ast.Expr expr2 =parseExpr();
            if (!tokens.match(";")) {
                throw new ParseException("Expected ';' after assignment statement", tokens.getNext());
            }
            return new Ast.Stmt.Assignment(expr1, expr2);
        }


        if (!tokens.match(";")) {
            throw new ParseException("Expected ';' after expression statement", tokens.getNext());
        }
        return new Ast.Stmt.Expression(expr1);
    }

    private Ast.Expr parseExpr() throws ParseException {
        return parseLogicalExpr();
    }

    private Ast.Expr parseLogicalExpr() throws ParseException {
        Ast.Expr expr = parseComparisonExpr();
        while (tokens.match("AND") || tokens.match("OR")) {
            String oper = tokens.get(-1).literal();
            Ast.Expr right = parseComparisonExpr();
            expr = new Ast.Expr.Binary(oper, expr, right);
        }
        return expr;
    }

    private Ast.Expr parseComparisonExpr() throws ParseException {
        Ast.Expr expr = parseAdditiveExpr();
        while (tokens.match("<") || tokens.match("<=") ||
            tokens.match(">") || tokens.match(">=") ||
            tokens.match("==") || tokens.match("!=")) {
            String oper = tokens.get(-1).literal();
            Ast.Expr right = parseAdditiveExpr();
            expr = new Ast.Expr.Binary(oper, expr, right);
        }
        return expr;
    }

    private Ast.Expr parseAdditiveExpr() throws ParseException {
        Ast.Expr expr = parseMultiplicativeExpr();
        while (tokens.match("+") || tokens.match("-")) {
            String oper = tokens.get(-1).literal();
            Ast.Expr right = parseMultiplicativeExpr();
            expr = new Ast.Expr.Binary(oper, expr, right);
        }
        return expr;
    }

    private Ast.Expr parseMultiplicativeExpr() throws ParseException {
        Ast.Expr expr = parseSecondaryExpr();
        while (tokens.match("*") || tokens.match("/")) {
            String oper = tokens.get(-1).literal();
            Ast.Expr right = parseSecondaryExpr();
            expr = new Ast.Expr.Binary(oper, expr, right);
        }
        return expr;
    }

    private Ast.Expr parseSecondaryExpr() throws ParseException {
        Ast.Expr expr = parsePrimaryExpr();
        while (tokens.peek(".")) {
            expr = parsePropertyOrMethod(expr);
        }
        return expr;
    }

    private Ast.Expr parsePropertyOrMethod(Ast.Expr receiver) throws ParseException {
        tokens.match(".");
        if (!tokens.match(Token.Type.IDENTIFIER)) {
            throw new ParseException("Expected identifier after '.'", tokens.getNext());
        }
        String name = tokens.get(-1).literal();
        if (tokens.match("(")) {
            List<Ast.Expr> arguments = new ArrayList<>();
            if (!tokens.peek(")")) {
                do {
                    arguments.add(parseExpr());
                } while (tokens.match(","));
            }
            if (!tokens.match(")")) {
                throw new ParseException("Expected ')'", tokens.getNext());
            }
            return new Ast.Expr.Method(receiver, name, arguments);
        }
        return new Ast.Expr.Property(receiver, name);
    }

    private Ast.Expr parsePrimaryExpr() throws ParseException {
        if (tokens.peek("NIL") || tokens.peek("TRUE") || tokens.peek("FALSE") ||
            tokens.peek(Token.Type.INTEGER) || tokens.peek(Token.Type.DECIMAL) ||
            tokens.peek(Token.Type.CHARACTER) || tokens.peek(Token.Type.STRING)) {
            return parseLiteralExpr();
        }
        else if (tokens.peek("(")) {
            return parseGroupExpr();
        }
        else if (tokens.peek("OBJECT")) {
            return parseObjectExpr();
        }
        else if (tokens.peek(Token.Type.IDENTIFIER)) {
            return parseVariableOrFunctionExpr();
        }
        throw new ParseException("Expected primary expression", tokens.getNext());
    }

    private Ast.Expr parseLiteralExpr() throws ParseException {
        if (tokens.match("NIL")) {
            return new Ast.Expr.Literal(null);
        }

        if (tokens.match("TRUE")) {
            return new Ast.Expr.Literal(Boolean.TRUE);
        }

        if (tokens.match("FALSE")) {
            return new Ast.Expr.Literal(Boolean.FALSE);
        }

        if (tokens.match(Token.Type.INTEGER)) {
            return new Ast.Expr.Literal(new java.math.BigInteger(tokens.get(-1).literal()));
        }

        if (tokens.match(Token.Type.DECIMAL)) {
            return new Ast.Expr.Literal(new java.math.BigDecimal(tokens.get(-1).literal()));
        }

        if (tokens.match(Token.Type.CHARACTER)) {
            String raw = tokens.get(-1).literal();
            String inner = raw.substring(1, raw.length() - 1);
            char value;
            switch (inner) {
                case "\\n" -> value = '\n';
                case "\\t" -> value = '\t';
                case "\\r" -> value = '\r';
                case "\\'" -> value = '\'';
                case "\\\"" -> value = '"';
                case "\\\\" -> value = '\\';
                default -> value = inner.charAt(0);
            }
            return new Ast.Expr.Literal(value);
        }

        if (tokens.match(Token.Type.STRING)) {
            String raw = tokens.get(-1).literal();
            String inner = raw.substring(1, raw.length() - 1);

            StringBuilder stringbuild = new StringBuilder();

            for (int i = 0; i < inner.length(); i++) {
                char c = inner.charAt(i);

                if (c == '\\') {
                    if (i + 1 >= inner.length()) {
                        throw new ParseException("Invalid escape", tokens.getNext());
                    }
                    char e = inner.charAt(++i);

                    switch (e) {
                        case 'n' -> stringbuild.append('\n');
                        case 't' -> stringbuild.append('\t');
                        case 'r' -> stringbuild.append('\r');
                        case 'b' -> stringbuild.append('\b');
                        case 'f' -> stringbuild.append('\f');
                        case '\\' -> stringbuild.append('\\');
                        case '\'' -> stringbuild.append('\'');
                        case '"' -> stringbuild.append('"');
                        default -> {
                            stringbuild.append('\\');
                            stringbuild.append(e);
                        }
                    }
                } else {
                    stringbuild.append(c);
                }
            }

            return new Ast.Expr.Literal(stringbuild.toString());
        }
        throw new ParseException("Expected literal", tokens.getNext());
    }


    private Ast.Expr parseGroupExpr() throws ParseException {
        if (!tokens.match("(")) {
            throw new ParseException("Expected '('", tokens.getNext());
        }
        Ast.Expr innerpart = parseExpr();
        if (!tokens.match(")")) {
            throw new ParseException("Expected ')'", tokens.getNext());
        }
        return new Ast.Expr.Group(innerpart);
    }

    private Ast.Expr parseObjectExpr() throws ParseException {
        tokens.match("OBJECT");

        Optional<String> name = Optional.empty();
        if (!tokens.peek("DO") && tokens.match(Token.Type.IDENTIFIER)) {
            name = Optional.of(tokens.get(-1).literal());
        }

        if (!tokens.match("DO")) {
            throw new ParseException("Expected 'DO'", tokens.getNext());
        }

        List<Ast.Stmt.Let> fields = new ArrayList<>();
        List<Ast.Stmt.Def> methods = new ArrayList<>();

        boolean sawMethod = false;
        while (tokens.has(0) && !tokens.peek("END")) {
            if (tokens.peek("LET")) {
                if (sawMethod) {
                    throw new ParseException("Fields cannot appear after methods", tokens.getNext());
                }
                fields.add((Ast.Stmt.Let) parseLetStmt());
            } else if (tokens.peek("DEF")) {
                sawMethod = true;
                methods.add((Ast.Stmt.Def) parseDefStmt());
            } else {
                throw new ParseException("Unexpected statement in object literal", tokens.getNext());
            }
        }

        if (!tokens.match("END")) {
            throw new ParseException("Expected 'END'", tokens.getNext());
        }

        return new Ast.Expr.ObjectExpr(name, fields, methods);
    }

    private Ast.Expr parseVariableOrFunctionExpr() throws ParseException {
        if (!tokens.match(Token.Type.IDENTIFIER)) {
            throw new ParseException("Expected identifier", tokens.getNext());
        }
        String name = tokens.get(-1).literal();

        if (tokens.match("(")) {
            List<Ast.Expr> arguments = new ArrayList<>();

            if (!tokens.peek(")")) {
                arguments.add(parseExpr());
                while (tokens.match(",")) {
                    arguments.add(parseExpr());
                }
            }
            if (!tokens.match(")")) {
                throw new ParseException("Expected ')'", tokens.getNext());
            }

            return new Ast.Expr.Function(name, arguments);
        }

        return new Ast.Expr.Variable(name);
    }


    private static final class TokenStream {

        private final List<Token> tokens;
        private int index = 0;

        private TokenStream(List<Token> tokens) {
            this.tokens = tokens;
        }

        /**
         * Returns true if there is a token at (index + offset).
         */
        public boolean has(int offset) {
            return index + offset < tokens.size();
        }

        /**
         * Returns the token at (index + offset).
         */
        public Token get(int offset) {
            Preconditions.checkState(has(offset));
            return tokens.get(index + offset);
        }

        /**
         * Returns the next token, if present.
         */
        public Optional<Token> getNext() {
            return index < tokens.size() ? Optional.of(tokens.get(index)) : Optional.empty();
        }

        /**
         * Returns true if the next characters match their corresponding
         * pattern. Each pattern is either a {@link Token.Type}, matching tokens
         * of that type, or a {@link String}, matching tokens with that literal.
         * In effect, {@code new Token(Token.Type.IDENTIFIER, "literal")} is
         * matched by both {@code peek(Token.Type.IDENTIFIER)} and
         * {@code peek("literal")}.
         */
        public boolean peek(Object... patterns) {
            if (!has(patterns.length - 1)) {
                return false;
            }
            for (int offset = 0; offset < patterns.length; offset++) {
                var token = tokens.get(index + offset);
                var pattern = patterns[offset];
                Preconditions.checkState(pattern instanceof Token.Type || pattern instanceof String, pattern);
                if (!token.type().equals(pattern) && !token.literal().equals(pattern)) {
                    return false;
                }
            }
            return true;
        }

        /**
         * Equivalent to peek, but also advances the token stream.
         */
        public boolean match(Object... patterns) {
            var peek = peek(patterns);
            if (peek) {
                index += patterns.length;
            }
            return peek;
        }

    }

}
