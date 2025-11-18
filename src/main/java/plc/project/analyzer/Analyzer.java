package plc.project.analyzer;

import plc.project.evaluator.EvaluateException;
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
                    Type.ANY;

        if (declaredType != null && valueType != null) {
            if (!valueType.isSubtypeOf(declaredType)) {
                throw new AnalyzeException(
                    "Value type does not match declared type.",
                    Optional.of(ast)
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
        throw new UnsupportedOperationException("TODO"); //TODO
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
        throw new UnsupportedOperationException("TODO"); //TODO
    }

    @Override
    public Ir.Stmt.Return visit(Ast.Stmt.Return ast) throws AnalyzeException {
        throw new UnsupportedOperationException("TODO"); //TODO
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
                throw new AnalyzeException("Variable doesnt exist", Optional.of(ast));
            }
            Ir.Expr right = visit(ast.value());
            if (!right.type().isSubtypeOf(existing.get())) {
                throw new AnalyzeException("Type mismatch in assignment", Optional.of(ast));
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
                throw new AnalyzeException("Invalid property receiver", Optional.of(ast));
            }

            Type actualtype = Type.DYNAMIC;
            if (type instanceof Type.ObjectType objectType) {
                Optional<Type> resolved = objectType.scope().resolve(property.name(), true);
                if (resolved.isEmpty()) {
                    throw new AnalyzeException("Invalid property receiver", Optional.of(ast));
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
        throw new UnsupportedOperationException("TODO"); //TODO
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
        throw new UnsupportedOperationException("TODO"); //TODO
    }

    @Override
    public Ir.Expr.ObjectExpr visit(Ast.Expr.ObjectExpr ast) throws AnalyzeException {
        throw new UnsupportedOperationException("TODO"); //TODO
    }

}
