package plc.project.analyzer;

import plc.project.evaluator.EvaluateException;
import plc.project.evaluator.Evaluator;
import plc.project.evaluator.RuntimeValue;
import plc.project.parser.Ast;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class Analyzer implements Ast.Visitor<Ir, AnalyzeException> {

    private Scope scope;

    public Analyzer(Scope scope) {
        this.scope = scope;
    }

    public Scope getScope() {
        return scope;
    }

    @Override
    public Ir.Source visit(Ast.Source ast) throws AnalyzeException {
        var statements = new ArrayList<Ir.Stmt>();
        for (var statement : ast.statements()) {
            statements.add(visit(statement));
        }
        return new Ir.Source(statements);
    }

    private Ir.Stmt visit(Ast.Stmt ast) throws AnalyzeException {
        return (Ir.Stmt) visit((Ast) ast); //helper to cast visit(Ast.Stmt) to Ir.Stmt
    }

    @Override
    public Ir.Stmt.Let visit(Ast.Stmt.Let ast) throws AnalyzeException {
        if (scope.resolve(ast.name(), false).isPresent()) {
            throw new AnalyzeException(
                "Variable '" + ast.name() + "' is already defined in this scope.",
                Optional.of(ast)
            );
        }

        Type declaredType = null;

        if (ast.type().isPresent()) {
            declaredType = resolveTypeName(ast.type().get());
        };

        Optional<Ir.Expr> irValue = Optional.empty();
        Type valueType = null;

        if (ast.value().isPresent()) {
            Ir.Expr val = visit(ast.value().get());
            irValue = Optional.of(val);
            valueType = val.type();
        }

        Type variableType =
            declaredType != null ? declaredType :
                valueType != null ? valueType :
                    Type.DYNAMIC;

        if (declaredType != null && valueType != null) {
            if (!valueType.isSubtypeOf(declaredType)) {
                throw new AnalyzeException(
                    "Value type does not match declared type.",
                    Optional.of(ast.value().get())
                );
            }
        }

        scope.define(ast.name(), variableType);

        return new Ir.Stmt.Let(ast.name(), variableType, irValue);
    }
    private static Type resolveTypeName(String name) throws AnalyzeException {
        Type t = Environment.TYPES.get(name);
        if (t == null) {
            throw new AnalyzeException("Unknown type: " + name, Optional.empty());
        }
        return t;
    }


    @Override
    public Ir.Stmt.Def visit(Ast.Stmt.Def ast) throws AnalyzeException {
        if (scope.resolve(ast.name(), false).isPresent()) {
            throw new AnalyzeException("Function already defined.", Optional.of(ast));
        }

        List<Type> paramTypes = new ArrayList<>();
        for (int i = 0; i < ast.parameters().size(); i++) {
            if (ast.parameterTypes().get(i).isPresent()) {
                paramTypes.add(resolveTypeName(ast.parameterTypes().get(i).get()));
            } else {
                paramTypes.add(Type.DYNAMIC);
            }
        }

        Type returnType = ast.returnType().isPresent()
            ? resolveTypeName(ast.returnType().get())
            : Type.DYNAMIC;

        List<Ir.Stmt.Def.Parameter> irParams = new ArrayList<>();
        for (int i = 0; i < ast.parameters().size(); i++) {
            irParams.add(new Ir.Stmt.Def.Parameter(
                ast.parameters().get(i),
                paramTypes.get(i)
            ));
        }

        scope.define(ast.name(), new Type.Function(paramTypes, returnType));

        Scope previous = scope;
        scope = new Scope(previous);

        for (int i = 0; i < ast.parameters().size(); i++) {
            scope.define(ast.parameters().get(i), paramTypes.get(i));
        }

        scope.define("$RETURN", returnType);

        List<Ir.Stmt> bodyIr = new ArrayList<>();
        for (var stmt : ast.body()) {
            bodyIr.add(visit(stmt));
        }

        scope = previous;

        return new Ir.Stmt.Def(ast.name(), irParams, returnType, bodyIr);
    }

    @Override
    public Ir.Stmt.If visit(Ast.Stmt.If ast) throws AnalyzeException {
        // Okay so if has  Expr condition,
        //            List<Stmt> thenBody,
        //            List<Stmt> elseBody
        // We gotta visit condition and get the type
        Ir.Expr condition = visit(ast.condition());
        Type conditionType = condition.type();
        if(!conditionType.isSubtypeOf(Type.BOOLEAN)){
            throw new AnalyzeException("Condition is not a Boolean.", Optional.of(ast.condition()));
        }
        // Go to every visit ever stmt in then body and else body
        List<Ir.Stmt> thenIRlist = new ArrayList<>();
        List<Ir.Stmt> elseIRlist = new ArrayList<>();

        plc.project.analyzer.Scope previous = scope;
        scope = new plc.project.analyzer.Scope(previous);
        try {
            for (var stmt : ast.thenBody()) {
                Ir.Stmt thenstmt = visit(stmt);
                thenIRlist.add(thenstmt);
            }
        } finally {
            scope = previous;
        }

        plc.project.analyzer.Scope previous2 = scope;
        scope = new plc.project.analyzer.Scope(previous2);
        try {
            for (var stmt : ast.elseBody()) {
                Ir.Stmt elsestmt = visit(stmt);
                elseIRlist.add(elsestmt);
            }
        } finally {
            scope = previous2;
        }
        return  new Ir.Stmt.If(condition,thenIRlist,elseIRlist);
    }

    @Override
    public Ir.Stmt.For visit(Ast.Stmt.For ast) throws AnalyzeException {
        Ir.Expr iterableValue = visit(ast.expression());
        Type iterableType = iterableValue.type();
        if (iterableValue.type().equals(Type.NIL)) {
            throw new AnalyzeException("Expression must be iterable", Optional.of(ast.expression()));
        }
        Scope previous = scope;
        scope = new Scope(previous);
        scope.define(ast.name(), Type.INTEGER);
        List<Ir.Stmt> bodyIr = new ArrayList<>();
        for(var stmt: ast.body()){
            bodyIr.add(visit(stmt));
        }
        scope = previous;
        return new Ir.Stmt.For(ast.name(),Type.INTEGER,iterableValue,bodyIr);


    }

    @Override
    public Ir.Stmt.Return visit(Ast.Stmt.Return ast) throws AnalyzeException {
        Type inside = scope.resolve("$RETURN", true)
            .orElseThrow(() -> new AnalyzeException("RETURN outside function", Optional.of(ast)));

        Optional<Ir.Expr> valueIr = ast.value().isPresent()
            ? Optional.of(visit(ast.value().get()))
            : Optional.empty();

        Type actual = valueIr.isPresent() ? valueIr.get().type() : Type.NIL;

        if (!actual.isSubtypeOf(inside)) {
            if (ast.value().isPresent()) {
                throw new AnalyzeException("Return type mismatch",
                    Optional.of(ast.value().get()));
            }
            throw new AnalyzeException("Return type mismatch",
                Optional.of(ast));
        }

        return new Ir.Stmt.Return(valueIr);
    }

    @Override
    public Ir.Stmt.Expression visit(Ast.Stmt.Expression ast) throws AnalyzeException {
        var expression = visit(ast.expression());
        return new Ir.Stmt.Expression(expression);
    }

    @Override
    public Ir.Stmt.Assignment visit(Ast.Stmt.Assignment ast) throws AnalyzeException {
        if (ast.expression() instanceof Ast.Expr.Variable variable) {
            Optional<Type> existing = scope.resolve(variable.name(), false);
            if (existing.isEmpty()) {
                throw new AnalyzeException("Variable doesnt exist",
                    Optional.of(variable));
            }
            Ir.Expr right = visit(ast.value());
            if (!right.type().isSubtypeOf(existing.get())) {
                throw new AnalyzeException("Type mismatch in assignment", Optional.of(ast.value()));
            }
            Ir.Expr.Variable left = new Ir.Expr.Variable(
                variable.name(),
                existing.get()
            );
            return new Ir.Stmt.Assignment.Variable(left, right);
        } else if (ast.expression() instanceof Ast.Expr.Property property) {
            Ir.Expr receiver = visit(property.receiver());
            Type type = receiver.type();

            if (!(type instanceof Type.ObjectType
                || type == Type.DYNAMIC)) {
                throw new AnalyzeException("Invalid property receiver", Optional.of(property.receiver()));
            }

            Type actualtype = Type.DYNAMIC;
            if (type instanceof Type.ObjectType objectType) {
                Optional<Type> resolved = objectType.scope().resolve(property.name(), true);
                if (resolved.isEmpty()) {
                    throw new AnalyzeException("Invalid property receiver", Optional.of(property));
                }
                actualtype = resolved.get();
            }


            Ir.Expr right = visit(ast.value());
            if (!right.type().isSubtypeOf(actualtype)) {
                throw new AnalyzeException("Type mismatch in assignment", Optional.of(ast));
            }
            Ir.Expr.Property left = new Ir.Expr.Property(
                receiver,
                property.name(),
                actualtype
            );

            return new Ir.Stmt.Assignment.Property(left, right);
        } else {
            throw new AnalyzeException("Invalid expression", Optional.of(ast));
        }
    }

    private Ir.Expr visit(Ast.Expr ast) throws AnalyzeException {
        return (Ir.Expr) visit((Ast) ast); //helper to cast visit(Ast.Expr) to Ir.Expr
    }

    @Override
    public Ir.Expr.Literal visit(Ast.Expr.Literal ast) throws AnalyzeException {
        var type = switch (ast.value()) {
            case null -> Type.NIL;
            case Boolean _ -> Type.BOOLEAN;
            case BigInteger _ -> Type.INTEGER;
            case BigDecimal _ -> Type.DECIMAL;
            case Character _ -> Type.CHARACTER;
            case String _ -> Type.STRING;
            default -> throw new AssertionError(ast.value().getClass());
        };
        return new Ir.Expr.Literal(ast.value(), type);
    }

    @Override
    public Ir.Expr.Group visit(Ast.Expr.Group ast) throws AnalyzeException {
        Ir.Expr expr = visit(ast.expression());
        return new Ir.Expr.Group(expr);
    }

    @Override
    public Ir.Expr.Binary visit(Ast.Expr.Binary ast) throws AnalyzeException {
        Ir.Expr leftExpr = visit(ast.left());
        Ir.Expr rightExpr = visit(ast.right());
        Type lefttype = leftExpr.type();
        Type righttype = rightExpr.type();

        Type result;

        switch (ast.operator()) {
            case "+": case "-": case "*": case "/":
                result = analyzeMath(lefttype, righttype, ast);
                break;

            case "==": case "!=":
                result = analyzeEquality(lefttype, righttype, ast);
                break;

            case "<": case "<=": case ">": case ">=":
                result = analyzeComparison(lefttype, righttype, ast);
                break;

            case "AND": case "OR":
                result = analyzeBoolean(lefttype, righttype, ast);
                break;

            default:
                throw new AnalyzeException("Unknown operator: " + ast.operator(), Optional.of(ast));
        }

        return new Ir.Expr.Binary(ast.operator(), leftExpr, rightExpr, result);
    }

    private Type analyzeMath(Type left, Type right, Ast.Expr.Binary ast) throws AnalyzeException {
        if(left.equals(Type.DYNAMIC) && right.equals(Type.DYNAMIC)) {
            return Type.DYNAMIC;
        }

        if (ast.operator().equals("+") &&
            (left.equals(Type.STRING) || right.equals(Type.STRING))) {
            return Type.STRING;
        }

        Type leftanalyzed  = left.equals(Type.DYNAMIC)  ? right : left;
        Type rightanalyzed = right.equals(Type.DYNAMIC) ? left  : right;

        boolean leftNumeric  = leftanalyzed.equals(Type.INTEGER) || leftanalyzed.equals(Type.DECIMAL);
        boolean rightNumeric = rightanalyzed.equals(Type.INTEGER) || rightanalyzed.equals(Type.DECIMAL);

        if (!leftNumeric) {
            throw new AnalyzeException("Left operand must be numeric", Optional.of(ast.left()));
        }
        if (!rightNumeric) {
            throw new AnalyzeException("Right operand must be numeric", Optional.of(ast.right()));
        }

        if (!leftanalyzed.equals(rightanalyzed)) {
            throw new AnalyzeException("Numeric operands must match type", Optional.of(ast));
        }

        return leftanalyzed;
    }

    private Type analyzeEquality(Type left, Type right, Ast.Expr.Binary ast) throws AnalyzeException {
        if (!Environment.isSubtypeOf(left, right) && !Environment.isSubtypeOf(right, left)) {
            throw new AnalyzeException("Operands cannot be compared for equality", Optional.of(ast));
        }
        return Type.BOOLEAN;
    }

    private Type analyzeComparison(Type left, Type right, Ast.Expr.Binary ast) throws AnalyzeException {
        if (!Environment.isSubtypeOf(left, Type.COMPARABLE)
            || !Environment.isSubtypeOf(right, Type.COMPARABLE)) {
            throw new AnalyzeException("Operands must be comparable", Optional.of(ast));
        }

        if (!Environment.isSubtypeOf(left, right) && !Environment.isSubtypeOf(right, left)) {
            throw new AnalyzeException("Operands must be compatible", Optional.of(ast));
        }

        return Type.BOOLEAN;
    }

    private Type analyzeBoolean(Type left, Type right, Ast.Expr.Binary ast) throws AnalyzeException {
        if(!left.equals(Type.BOOLEAN)) {
            throw new AnalyzeException("Operands must be boolean", Optional.of(ast.left()));
        }
        if(!right.equals(Type.BOOLEAN)) {
            throw new AnalyzeException("Operands must be boolean", Optional.of(ast.right()));
        }
        return Type.BOOLEAN;
    }


    @Override
    public Ir.Expr.Variable visit(Ast.Expr.Variable ast) throws AnalyzeException {
        var type = scope.resolve(ast.name(), false)
            .orElseThrow(() -> new AnalyzeException("Variable undefined", Optional.of(ast)));
        return new Ir.Expr.Variable(ast.name(), type);
    }

    @Override
    public Ir.Expr.Property visit(Ast.Expr.Property ast) throws AnalyzeException {
        Ir.Expr receiver = visit(ast.receiver());
        Type receiverType = receiver.type();

        Type propType;

        if (receiverType instanceof Type.ObjectType obj) {
            var resolved = obj.scope().resolve(ast.name(), true);
            if (resolved.isEmpty()) {
                throw new AnalyzeException("Property '" + ast.name() + "' not defined on object (direct properties only)", Optional.of(ast));
            }
            propType = resolved.get();

        } else if (receiverType.equals(Type.DYNAMIC)) {
            propType = Type.DYNAMIC;

        } else {
            throw new AnalyzeException("Property access is not right type", Optional.of(ast));
        }

        return new Ir.Expr.Property(receiver, ast.name(), propType);
    }

    @Override
    public Ir.Expr.Function visit(Ast.Expr.Function ast) throws AnalyzeException {
        var functionType = scope.resolve(ast.name(), false)
            .orElseThrow(() ->
                new AnalyzeException("Function " + ast.name() + " is not defined.", Optional.of(ast))
            );
        if(!(functionType instanceof Type.Function FinalFuncType)) {
            throw new AnalyzeException("Not a function: " + ast.name(), Optional.of(ast));
        }
        if((FinalFuncType.parameters().size() != ast.arguments().size())){
            throw new AnalyzeException("Arguments don't match" + ast.name(), Optional.of(ast));
        }
        List<Ir.Expr> analyzedArgs = new ArrayList<>();
        for (int i = 0; i < ast.arguments().size(); i++) {
            Ir.Expr analyzedArg = visit(ast.arguments().get(i));
            analyzedArgs.add(analyzedArg);
            Type expected = FinalFuncType.parameters().get(i);
            Type actual   = analyzedArg.type();
            if (!Environment.isSubtypeOf(actual, expected)) {
                throw new AnalyzeException(
                    "Type mismatch in argument " + (i + 1) +
                        " of function " + ast.name() +
                        ": expected " + expected + ", got " + actual,
                    Optional.of(ast.arguments().get(i))
                );
            }
        }
        return new Ir.Expr.Function(ast.name(), analyzedArgs, FinalFuncType.returns());
    }

    @Override
    public Ir.Expr.Method visit(Ast.Expr.Method ast) throws AnalyzeException {
        Ir.Expr receiver = visit(ast.receiver());
        Type receiver_type = receiver.type();
        Type propertyType;

        if (receiver_type instanceof Type.ObjectType object_ins) {
            propertyType = object_ins.scope().resolve(ast.name(), true)
                .orElseThrow(() -> new AnalyzeException("Property not found", Optional.of(ast)));
        } else if (receiver_type == Type.DYNAMIC) {
            propertyType = Type.DYNAMIC;
        } else {
            throw new AnalyzeException("Invalid property receiver",
                Optional.of(ast.receiver()));
        }
        List<Ir.Expr> arguments = new ArrayList<>();
        for(Ast.Expr argument : ast.arguments()){
            arguments.add(visit(argument));
        }
        if(propertyType instanceof Type.Function function){
            if(function.parameters().size() != arguments.size()){
                throw new AnalyzeException("Arguments don't match" + ast.name(), Optional.of(ast));
            }
            for (int i = 0; i < arguments.size(); i++) {
                if (!Environment.isSubtypeOf(arguments.get(i).type(), function.parameters().get(i))) {
                    throw new AnalyzeException("Argument type mismatch", Optional.of(ast.arguments().get(i)));
                }
            }
            return new Ir.Expr.Method(receiver, ast.name(), arguments, function.returns());
        }
        return new Ir.Expr.Method(receiver,ast.name(), arguments, Type.DYNAMIC);
    }

    @Override
    public Ir.Expr.ObjectExpr visit(Ast.Expr.ObjectExpr ast) throws AnalyzeException {
        Type.ObjectType object_type = new Type.ObjectType(Optional.empty(), new Scope(null));
        final Scope definingScope = object_type.scope();
        Scope previous = this.scope;
        this.scope = object_type.scope();
        List<Ir.Stmt.Let> lets = new ArrayList<>();
        List<Ir.Stmt.Def> defs = new ArrayList<>();
        try {
            for (var letstmt : ast.fields()) {
                if (object_type.scope().resolve(letstmt.name(), true).isPresent()) {
                    throw new AnalyzeException(
                        "Variable '" + ast.name() + "' is already defined in this scope.",
                        Optional.of(ast)
                    );
                }

                Type declaredType = null;
                if (letstmt.type().isPresent()) {
                    declaredType = resolveTypeName(letstmt.type().get());
                }

                Optional<Ir.Expr> irValue = Optional.empty();
                Type valueType = null;

                if (letstmt.value().isPresent()) {
                    Ir.Expr val = visit(letstmt.value().get());
                    irValue = Optional.of(val);
                    valueType = val.type();
                }

                Type variableType =
                    declaredType != null ? declaredType :
                        valueType != null ? valueType :
                            Type.DYNAMIC;

                if (declaredType != null && valueType != null) {
                    if (!valueType.isSubtypeOf(declaredType)) {
                        throw new AnalyzeException(
                            "Value type does not match declared type.",
                            Optional.of(ast)
                        );
                    }
                }

                scope.define(letstmt.name(), variableType);
                lets.add(new Ir.Stmt.Let(letstmt.name(), variableType, irValue));
            }

            for (var defstmt : ast.methods()) {
                if (object_type.scope().resolve(defstmt.name(), true).isPresent()) {
                    throw new AnalyzeException("Function already defined.", Optional.of(ast));
                }

                List<Type> paramTypes = new ArrayList<>();
                for (int i = 0; i < defstmt.parameters().size(); i++) {
                    if (defstmt.parameterTypes().get(i).isPresent()) {
                        paramTypes.add(resolveTypeName(defstmt.parameterTypes().get(i).get()));
                    } else {
                        paramTypes.add(Type.DYNAMIC);
                    }
                }

                Type returnType =
                    defstmt.returnType().isPresent()
                        ? resolveTypeName(defstmt.returnType().get())
                        : Type.DYNAMIC;

                List<Ir.Stmt.Def.Parameter> irParams = new ArrayList<>();
                for (int i = 0; i < defstmt.parameters().size(); i++) {
                    irParams.add(new Ir.Stmt.Def.Parameter(
                        defstmt.parameters().get(i),
                        paramTypes.get(i)
                    ));
                }

                scope.define(defstmt.name(), new Type.Function(paramTypes, returnType));
                Scope previous2 = scope;
                scope = new Scope(previous2);

                for (int i = 0; i < defstmt.parameters().size(); i++) {
                    scope.define(defstmt.parameters().get(i), paramTypes.get(i));
                }
                scope.define("$RETURN", returnType);

                List<Ir.Stmt> bodyIr = new ArrayList<>();
                for (var stmt : defstmt.body()) {
                    bodyIr.add(visit(stmt));
                }

                scope = previous2;
                defs.add(new Ir.Stmt.Def(defstmt.name(), irParams, returnType, bodyIr));
            }

        } finally {
            this.scope = previous;
        }

        return new Ir.Expr.ObjectExpr(Optional.empty(), lets, defs, object_type);
    }
}
