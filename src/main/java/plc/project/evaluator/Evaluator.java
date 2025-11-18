package plc.project.evaluator;

import plc.project.parser.Ast;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class Evaluator implements Ast.Visitor<RuntimeValue, EvaluateException> {

    private Scope scope;

    public Evaluator(Scope scope) {
        this.scope = scope;
    }

    public Scope getScope() {
        return scope;
    }

    @Override
    public RuntimeValue visit(Ast.Source ast) throws EvaluateException {
        RuntimeValue value = new RuntimeValue.Primitive(null);
        try {
            for (var stmt : ast.statements()) {
                value = visit(stmt);
            }
        } catch (ReturnException e) {
            throw new EvaluateException("Return outside of function.", Optional.of(ast));
        }
        return value;

    }

    @Override
    public RuntimeValue visit(Ast.Stmt.Let ast) throws EvaluateException {
        if (scope.resolve(ast.name(), true).isPresent()) {
            throw new EvaluateException(
                "Variable '" + ast.name() + "' is already defined in current scope.",
                Optional.of(ast)
            );
        }
        RuntimeValue value = new RuntimeValue.Primitive(null);
        if (ast.value().isPresent()) {
            value = visit(ast.value().get());
        }
        scope.define(ast.name(), value);
        return value;
    }


    @Override
    public RuntimeValue visit(Ast.Stmt.Def ast) throws EvaluateException {
        if (scope.resolve(ast.name(), true).isPresent()) {
            throw new EvaluateException("Function already defined.", Optional.of(ast));
        }
        final Scope definingScope = this.scope;
        scope.define(ast.name(),
            new RuntimeValue.Function(ast.name(), arguments -> {
                if (arguments.size() != ast.parameters().size()) {
                    throw new EvaluateException("Argument count mismatch.", Optional.of(ast));
                }

                Scope parameterScope = new Scope(definingScope);
                for (int i = 0; i < ast.parameters().size(); i++) {
                    String paramName = ast.parameters().get(i);
                    if (parameterScope.resolve(paramName, true).isPresent()) {
                        throw new EvaluateException("Duplicate parameter name: " + paramName, Optional.of(ast));
                    }
                    parameterScope.define(paramName, arguments.get(i));
                }

                Scope bodyScope = new Scope(parameterScope);
                Scope previous = this.scope;
                this.scope = bodyScope;
                try {
                    RuntimeValue result = new RuntimeValue.Primitive(null);
                    for (var stmt : ast.body()) {
                        visit(stmt);
                    }
                    return new RuntimeValue.Primitive(null);
                } catch (ReturnException e){
                    return e.getValue();
                } finally {
                    this.scope = previous;
                }
            })
        );
        return scope.resolve(ast.name(),true).get();
    }

    @Override
    public RuntimeValue visit(Ast.Stmt.If ast) throws EvaluateException {
        RuntimeValue conditionValue = visit(ast.condition());
        var condition = requireType(conditionValue, RuntimeValue.Primitive.class)
            .orElseThrow(() -> new EvaluateException("Condition must be a primitive.", Optional.of(ast.condition())));
        if (!(condition.value() instanceof Boolean bool)) {
            throw new EvaluateException("Condition is not a Boolean.", Optional.of(ast.condition()));
        }
        List<Ast.Stmt> body = bool ? ast.thenBody() : ast.elseBody();
        Scope previous = scope;
        scope = new Scope(previous);
        RuntimeValue result = new RuntimeValue.Primitive(null);
        try {
            for (var stmt : body) {
                result = visit(stmt);
            }
        } finally {
            scope = previous;
        }

        return result;
    }

    @Override
    public RuntimeValue visit(Ast.Stmt.For ast) throws EvaluateException {
        var iterableValue = visit(ast.expression());
        var Value = requireType(iterableValue, RuntimeValue.Primitive.class)
            .map(RuntimeValue.Primitive::value)
            .orElseThrow(() -> new EvaluateException("Right must be a primitive.", Optional.of(ast.expression())));
        if (!(Value instanceof Iterable<?> iterable))
            throw new EvaluateException("Expression must be iterable", Optional.of(ast.expression()));

        for (var element : iterable) {
            Scope iterationScope = new Scope(scope);
            // double check //
            RuntimeValue checkedRuntime = element instanceof  RuntimeValue r ? r: new RuntimeValue.Primitive(element);
            iterationScope.define(ast.name(), checkedRuntime);
            Scope bodyScope = new Scope(iterationScope);
            Scope previous = this.scope;
            this.scope = bodyScope;
            try {
                for (var stmt : ast.body()) {
                    visit(stmt);
                }
            } finally {
                this.scope = previous;
            }
        }
        return new RuntimeValue.Primitive(null);
    }

    public class ReturnException extends RuntimeException {
        private final RuntimeValue value;
        public ReturnException(RuntimeValue value) {
            this.value = value;
        }
        public RuntimeValue getValue() {
            return value;
        }
    }

    @Override
    public RuntimeValue visit(Ast.Stmt.Return ast) throws EvaluateException {
        RuntimeValue value;
        if (ast.value().isPresent()) {
            value = visit(ast.value().get());
        }else{
            value = new RuntimeValue.Primitive(null);
        }
        throw new ReturnException(value);
    }

    @Override
    public RuntimeValue visit(Ast.Stmt.Expression ast) throws EvaluateException {
        return visit(ast.expression());
    }

    @Override
    public RuntimeValue visit(Ast.Stmt.Assignment ast) throws EvaluateException {
        if (ast.expression() instanceof Ast.Expr.Variable variable) {
            Optional<RuntimeValue> existing = scope.resolve(variable.name(), false);
            if (existing.isEmpty()) {
                throw new EvaluateException("Variable '" + variable.name() + "' is not defined.",
                    Optional.of(ast.expression()));
            }
            RuntimeValue right = visit(ast.value());
            scope.assign(variable.name(), right);
            return right;
        } else if (ast.expression() instanceof Ast.Expr.Property property) {
            RuntimeValue receiverValue = visit(property.receiver());
            var object = requireType(receiverValue, RuntimeValue.ObjectValue.class)
                .orElseThrow(() -> new EvaluateException("Receiver is not an object.", Optional.of(property.receiver())));;

            if (object.scope().resolve(property.name(), true).isEmpty()) {
                throw new EvaluateException("Property '" + property.name() + "' not defined in object.",
                    Optional.of(ast.expression()));
            }
            RuntimeValue right = visit(ast.value());
            object.scope().assign(property.name(), right);
            return right;
        } else {
            throw new EvaluateException("Invalid assignment target.", Optional.of(ast.expression()));
        }
    }

    @Override
    public RuntimeValue visit(Ast.Expr.Literal ast) throws EvaluateException {
        return new RuntimeValue.Primitive(ast.value());
    }

    @Override
    public RuntimeValue visit(Ast.Expr.Group ast) throws EvaluateException {
        return visit(ast.expression());
    }

    @Override
    public RuntimeValue visit(Ast.Expr.Binary ast) throws EvaluateException {
        switch(ast.operator()){
            case "+": {
                var left = visit(ast.left());

                boolean leftValid = requireType(left, String.class).isPresent()
                    || requireType(left, BigInteger.class).isPresent()
                    || requireType(left, BigDecimal.class).isPresent();

                var right = visit(ast.right());

                if (left instanceof RuntimeValue.Primitive(String lStr)
                    || right instanceof RuntimeValue.Primitive(String rStr)) {
                    return new RuntimeValue.Primitive(left.print() + right.print());
                }

                var leftInt = requireType(left, BigInteger.class);
                var rightInt = requireType(right, BigInteger.class);
                if (leftInt.isPresent() && rightInt.isPresent()) {
                    var result = leftInt.get().add(rightInt.get());
                    return new RuntimeValue.Primitive(result);
                }

                var leftDec = requireType(left, BigDecimal.class);
                var rightDec = requireType(right, BigDecimal.class);
                if (leftDec.isPresent() && rightDec.isPresent()) {
                    var result = leftDec.get().add(rightDec.get());
                    return new RuntimeValue.Primitive(result);
                }

                if (!leftValid) {
                    throw new EvaluateException("Operands of + must be String, BigInteger, or BigDecimal",
                        Optional.of(ast.left()));
                }

                throw new EvaluateException("Operands of + must be String, BigInteger, or BigDecimal",
                    Optional.of(ast.right()));
            }

            case "-": {
                var left = visit(ast.left());

                boolean leftValid = requireType(left, BigInteger.class).isPresent()
                    || requireType(left, BigDecimal.class).isPresent();

                if (!leftValid) {
                    throw new EvaluateException("Left operand of - must be numeric", Optional.of(ast.left()));
                }

                var right = visit(ast.right());

                var leftInt = requireType(left, BigInteger.class);
                var rightInt = requireType(right, BigInteger.class);
                if (leftInt.isPresent() && rightInt.isPresent()) {
                    var result = leftInt.get().subtract(rightInt.get());
                    return new RuntimeValue.Primitive(result);
                }

                var leftDec = requireType(left, BigDecimal.class);
                var rightDec = requireType(right, BigDecimal.class);
                if (leftDec.isPresent() && rightDec.isPresent()) {
                    var result = leftDec.get().subtract(rightDec.get());
                    return new RuntimeValue.Primitive(result);
                }

                throw new EvaluateException("Operands of - must be numeric", Optional.of(ast.right()));

            }
            case "*": {
                var left = visit(ast.left());
                var right = visit(ast.right());

                var leftInt = requireType(left, BigInteger.class);
                var rightInt = requireType(right, BigInteger.class);
                if (leftInt.isPresent() && rightInt.isPresent()) {
                    var result = leftInt.get().multiply(rightInt.get());
                    return new RuntimeValue.Primitive(result);
                }
                var leftDec = requireType(left, BigDecimal.class);
                var rightDec = requireType(right, BigDecimal.class);
                if (leftDec.isPresent() && rightDec.isPresent()) {
                    var result = leftDec.get().multiply(rightDec.get());
                    return new RuntimeValue.Primitive(result);
                }
                throw new EvaluateException("Operands of * must be numeric", Optional.of(ast.right()));
            }

            case "/": {
                var left = visit(ast.left());
                var right = visit(ast.right());

                var leftInt = requireType(left, BigInteger.class);
                var rightInt = requireType(right, BigInteger.class);
                if (leftInt.isPresent() && rightInt.isPresent()) {
                    var dividend = leftInt.get();
                    var divisor = rightInt.get();
                    if (divisor.equals(BigInteger.ZERO))
                        throw new EvaluateException("Division by zero.", Optional.of(ast.right()));

                    var quotient = dividend.divide(divisor);
                    var remainder = dividend.remainder(divisor);
                    if (!remainder.equals(BigInteger.ZERO) && (dividend.signum() ^ divisor.signum()) < 0) {
                        quotient = quotient.subtract(BigInteger.ONE);
                    }
                    return new RuntimeValue.Primitive(quotient);
                }

                var leftDec = requireType(left, BigDecimal.class);
                var rightDec = requireType(right, BigDecimal.class);
                if (leftDec.isPresent() && rightDec.isPresent()) {
                    var divisor = rightDec.get();
                    if (divisor.compareTo(BigDecimal.ZERO) == 0)
                        throw new EvaluateException("Division by zero.", Optional.of(ast.right()));

                    var result = leftDec.get().divide(divisor, RoundingMode.HALF_EVEN);
                    return new RuntimeValue.Primitive(result);
                }

                throw new EvaluateException("Operands of / must be numeric", Optional.of(ast.right()));
            }

            case "==": {
                var left = visit(ast.left());
                var right = visit(ast.right());
                boolean isEqual = Objects.equals(left, right);
                return new RuntimeValue.Primitive(isEqual);
            }

            case "!=": {
                var left = visit(ast.left());
                var right = visit(ast.right());
                boolean isNotEqual = !Objects.equals(left, right);
                return new RuntimeValue.Primitive(isNotEqual);
            }

            case "<": {
                var left = visit(ast.left());
                var right = visit(ast.right());

                var leftValue = requireType(left, RuntimeValue.Primitive.class)
                    .map(RuntimeValue.Primitive::value)
                    .orElseThrow(() -> new EvaluateException("Left must be a primitive.", Optional.of(ast.left())));

                var rightValue = requireType(right, RuntimeValue.Primitive.class)
                    .map(RuntimeValue.Primitive::value)
                    .orElseThrow(() -> new EvaluateException("Right must be a primitive.", Optional.of(ast.right())));

                if(!(leftValue instanceof Comparable<?> comparableLeft)){
                    throw new EvaluateException("Left must be a comparable", Optional.of(ast.left()));
                }
                if(!(leftValue.getClass()).equals(rightValue.getClass())){
                    throw new EvaluateException("Operands must be of the same type", Optional.of(ast.right()));
                }
                int comparison = ((Comparable) leftValue).compareTo(rightValue);
                return new RuntimeValue.Primitive(comparison < 0);
            }
            case "<=": {
                var left = visit(ast.left());
                var right = visit(ast.right());

                var leftValue = requireType(left, RuntimeValue.Primitive.class)
                    .map(RuntimeValue.Primitive::value)
                    .orElseThrow(() -> new EvaluateException("Left must be a primitive.", Optional.of(ast.left())));

                var rightValue = requireType(right, RuntimeValue.Primitive.class)
                    .map(RuntimeValue.Primitive::value)
                    .orElseThrow(() -> new EvaluateException("Right must be a primitive.", Optional.of(ast.right())));

                if(!(leftValue instanceof Comparable<?> comparableLeft)){
                    throw new EvaluateException("Left must be a comparable", Optional.of(ast.left()));
                }
                if(!(leftValue.getClass()).equals(rightValue.getClass())){
                    throw new EvaluateException("Operands must be of the same type", Optional.of(ast.right()));
                }
                int comparison = ((Comparable) leftValue).compareTo(rightValue);
                return new RuntimeValue.Primitive(comparison <= 0);
            }
            case ">": {
                var left = visit(ast.left());
                var right = visit(ast.right());

                var leftValue = requireType(left, RuntimeValue.Primitive.class)
                    .map(RuntimeValue.Primitive::value)
                    .orElseThrow(() -> new EvaluateException("Left must be a primitive.", Optional.of(ast.left())));

                var rightValue = requireType(right, RuntimeValue.Primitive.class)
                    .map(RuntimeValue.Primitive::value)
                    .orElseThrow(() -> new EvaluateException("Right must be a primitive.", Optional.of(ast.right())));

                if(!(leftValue instanceof Comparable<?> comparableLeft)){
                    throw new EvaluateException("Left must be a comparable", Optional.of(ast.left()));
                }
                if(!(leftValue.getClass()).equals(rightValue.getClass())){
                    throw new EvaluateException("Operands must be of the same type", Optional.of(ast.right()));
                }
                int comparison = ((Comparable) leftValue).compareTo(rightValue);
                return new RuntimeValue.Primitive(comparison > 0);
            }
            case ">=": {
                var left = visit(ast.left());
                var right = visit(ast.right());

                var leftValue = requireType(left, RuntimeValue.Primitive.class)
                    .map(RuntimeValue.Primitive::value)
                    .orElseThrow(() -> new EvaluateException("Left must be a primitive.", Optional.of(ast.left())));

                var rightValue = requireType(right, RuntimeValue.Primitive.class)
                    .map(RuntimeValue.Primitive::value)
                    .orElseThrow(() -> new EvaluateException("Right must be a primitive.", Optional.of(ast.right())));

                if(!(leftValue instanceof Comparable<?> comparableLeft)){
                    throw new EvaluateException("Left must be a comparable", Optional.of(ast.left()));
                }
                if(!(leftValue.getClass()).equals(rightValue.getClass())){
                    throw new EvaluateException("Operands must be of the same type", Optional.of(ast.right()));
                }
                int comparison = ((Comparable) leftValue).compareTo(rightValue);
                return new RuntimeValue.Primitive(comparison >= 0);
            }
            case "AND": {
                var left = visit(ast.left());
                var leftValue = requireType(left, RuntimeValue.Primitive.class)
                    .map(RuntimeValue.Primitive::value)
                    .orElseThrow(() -> new EvaluateException("Left must be a primitive.", Optional.of(ast.left())));

                if (!(leftValue instanceof Boolean leftBool)) {
                    throw new EvaluateException("Left operand of AND must be a Boolean.", Optional.of(ast.left()));
                }

                if (!leftBool) {
                    return new RuntimeValue.Primitive(false);
                }

                var right = visit(ast.right());
                var rightValue = requireType(right, RuntimeValue.Primitive.class)
                    .map(RuntimeValue.Primitive::value)
                    .orElseThrow(() -> new EvaluateException("Right must be a primitive.", Optional.of(ast.right())));

                if (!(rightValue instanceof Boolean rightBool)) {
                    throw new EvaluateException("Right operand of AND must be a Boolean.", Optional.of(ast.right()));
                }

                return new RuntimeValue.Primitive(leftBool && rightBool);
            }

            case "OR": {
                var left = visit(ast.left());
                var leftValue = requireType(left, RuntimeValue.Primitive.class)
                    .map(RuntimeValue.Primitive::value)
                    .orElseThrow(() -> new EvaluateException("Left must be a primitive.", Optional.of(ast.left())));

                if (!(leftValue instanceof Boolean leftBool)) {
                    throw new EvaluateException("Left operand of OR must be a Boolean.", Optional.of(ast.left()));
                }

                if (leftBool) {
                    return new RuntimeValue.Primitive(true);
                }

                var right = visit(ast.right());
                var rightValue = requireType(right, RuntimeValue.Primitive.class)
                    .map(RuntimeValue.Primitive::value)
                    .orElseThrow(() -> new EvaluateException("Right must be a primitive.", Optional.of(ast.right())));

                if (!(rightValue instanceof Boolean rightBool)) {
                    throw new EvaluateException("Right operand of OR must be a Boolean.", Optional.of(ast.right()));
                }

                return new RuntimeValue.Primitive(leftBool || rightBool);
            }
            default:
                throw new EvaluateException
                    ("Unknown operator" + ast.operator(),
                        Optional.of(ast)
                    );
        }
    }

    @Override
    public RuntimeValue visit(Ast.Expr.Variable ast) throws EvaluateException {
        Optional<RuntimeValue> value = scope.resolve(ast.name(), false);
        if (value.isEmpty()) {
            throw new EvaluateException(
                "Variable '" + ast.name() + "' is not defined.",
                Optional.of(ast)
            );
        }
        return value.get();
    }

    @Override
    public RuntimeValue visit(Ast.Expr.Property ast) throws EvaluateException {
        var receiver = visit(ast.receiver());
        if(!(receiver instanceof RuntimeValue.ObjectValue object)){
            throw new EvaluateException("Receiver must be an object.", Optional.of(ast.receiver()));
        }
        RuntimeValue.ObjectValue current = object;
        while(true){
            var property = current.scope().resolve(ast.name(), true);
            if (property.isPresent()) {
                return property.get();
            }
            var prototype = current.scope().resolve("prototype", true);
            if (prototype.isEmpty()) {
                break;
            }
            if (prototype.get() instanceof RuntimeValue.ObjectValue prototypeObject) {
                current = prototypeObject;
            } else {
                throw new EvaluateException("Prototype must be an object.", Optional.of(ast));
            }
        }
        throw new EvaluateException("Undefined property: " + ast.name(), Optional.of(ast));
    }

    @Override
    public RuntimeValue visit(Ast.Expr.Function ast) throws EvaluateException {
        var value = scope.resolve(ast.name(), false).orElseThrow(() -> {
            return new EvaluateException("Function " + ast.name() + " is not defined." , Optional.of(ast));
        });
        var function = requireType(value, RuntimeValue.Function.class).orElseThrow(() -> {
            return new EvaluateException(
                "Function " + ast.name() + " is not defined as a RuntimeValue.Function (" + value + ")",
                Optional.of(ast)
            );
        });
        List<RuntimeValue> evaluatedArguments = new java.util.ArrayList<>();
        for (var arg : ast.arguments()) {
            evaluatedArguments.add(visit(arg));
        }
        return function.definition().invoke(evaluatedArguments);
    }

    @Override
    public RuntimeValue visit(Ast.Expr.Method ast) throws EvaluateException {
        var receiver = visit(ast.receiver());
        if(!(receiver instanceof RuntimeValue.ObjectValue object)){
            throw new EvaluateException("Receiver must be an object.", Optional.of(ast.receiver()));
        }

        RuntimeValue.ObjectValue current = object;

        while(true){
            var method = current.scope().resolve(ast.name(), true);
            if (method.isPresent()) {
                var value = method.get();
                if (!(value instanceof RuntimeValue.Function function)) {
                    throw new EvaluateException("Method is not callable.", Optional.of(ast));
                }
                List<RuntimeValue> arguments = new ArrayList<>();
                arguments.add(receiver);
                for(Ast.Expr argument : ast.arguments()){
                    arguments.add(visit(argument));
                }
                return function.definition().invoke(arguments);
            }
            var prototype = current.scope().resolve("prototype", true);
            if (prototype.isEmpty()) {
                break;
            }

            if (prototype.get() instanceof RuntimeValue.ObjectValue prototypeObject) {
                current = prototypeObject;
            } else {
                throw new EvaluateException("Prototype must be an object.", Optional.of(ast));
            }
        }

        throw new EvaluateException("Undefined property: " + ast.name(), Optional.of(ast));
    }

    @Override
    public RuntimeValue visit(Ast.Expr.ObjectExpr ast) throws EvaluateException {
        var object = new RuntimeValue.ObjectValue(ast.name(), new Scope(scope));
        final Scope definingScope = object.scope();
        Scope previous = this.scope;
        this.scope = object.scope();
        try {
            for (var letstmt : ast.fields()) {
                if (definingScope.resolve(letstmt.name(), true).isPresent()) {
                    throw new EvaluateException("Field already defined.", Optional.of(letstmt));
                }
                visit(letstmt);
            }
            for (var defstmt : ast.methods()) {
                if (definingScope.resolve(defstmt.name(), true).isPresent()) {
                    throw new EvaluateException("Function already defined.", Optional.of(defstmt));
                }
                definingScope.define(defstmt.name(),
                    new RuntimeValue.Function(defstmt.name(), arguments -> {
                        if (arguments.size() != defstmt.parameters().size() + 1) {
                            throw new EvaluateException("Argument count mismatch.", Optional.of(defstmt));
                        }
                        Scope parameterScope = new Scope(definingScope);
                        if (defstmt.parameters().contains("this")) {
                            throw new EvaluateException("Cannot use 'this' as parameter name", Optional.of(defstmt));
                        }
                        parameterScope.define("this", arguments.get(0));
                        for (int i = 0; i < defstmt.parameters().size(); i++) {
                            String paramName = defstmt.parameters().get(i);
                            if (parameterScope.resolve(paramName, true).isPresent()) {
                                throw new EvaluateException("Duplicate parameter name: " + paramName, Optional.of(defstmt));
                            }
                            parameterScope.define(paramName, arguments.get(i+1));
                        }

                        Scope bodyScope = new Scope(parameterScope);
                        Scope prev = this.scope;
                        this.scope = bodyScope;
                        try {
                            RuntimeValue result = new RuntimeValue.Primitive(null);
                            for (var stmt : defstmt.body()) {
                                visit(stmt);
                            }
                            return new RuntimeValue.Primitive(null);
                        } catch (ReturnException e) {
                            return e.getValue();
                        } finally {
                            this.scope = prev;
                        }
                    })
                );
            }
        } finally {
            this.scope = previous;
        }
        return object;
    }

    /**
     * Helper function for extracting RuntimeValues of specific types. If type
     * is a subclass of {@link RuntimeValue} the check applies to the value
     * itself, otherwise the value must be a {@link RuntimeValue.Primitive} and
     * the check applies to the primitive value.
     */
    private static <T> Optional<T> requireType(RuntimeValue value, Class<T> type) {
        //To be discussed in lecture
        Optional<Object> unwrapped = RuntimeValue.class.isAssignableFrom(type)
            ? Optional.of(value)
            : requireType(value, RuntimeValue.Primitive.class).map(RuntimeValue.Primitive::value);
        return (Optional<T>) unwrapped.filter(type::isInstance); //cast checked by isInstance
    }

}
