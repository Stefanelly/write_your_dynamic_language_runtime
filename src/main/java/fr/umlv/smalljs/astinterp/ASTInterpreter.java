package fr.umlv.smalljs.astinterp;

import fr.umlv.smalljs.ast.Expr;
import fr.umlv.smalljs.ast.Expr.Block;
import fr.umlv.smalljs.ast.Expr.FieldAccess;
import fr.umlv.smalljs.ast.Expr.FieldAssignment;
import fr.umlv.smalljs.ast.Expr.Fun;
import fr.umlv.smalljs.ast.Expr.FunCall;
import fr.umlv.smalljs.ast.Expr.If;
import fr.umlv.smalljs.ast.Expr.Literal;
import fr.umlv.smalljs.ast.Expr.LocalVarAccess;
import fr.umlv.smalljs.ast.Expr.LocalVarAssignment;
import fr.umlv.smalljs.ast.Expr.MethodCall;
import fr.umlv.smalljs.ast.Expr.New;
import fr.umlv.smalljs.ast.Expr.Return;
import fr.umlv.smalljs.ast.Script;
import fr.umlv.smalljs.rt.Failure;
import fr.umlv.smalljs.rt.JSObject;
import fr.umlv.smalljs.rt.JSObject.Invoker;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static fr.umlv.smalljs.rt.JSObject.UNDEFINED;
import static java.util.stream.Collectors.joining;

public class ASTInterpreter {
    private static JSObject asJSObject(Object value, int lineNumber) {
        if (!(value instanceof JSObject jsObject)) {
            throw new Failure("at line " + lineNumber + ", type error " + value + " is not a JSObject");
        }
        return jsObject;
    }

    static Object visit(Expr expression, JSObject env) {
        return switch (expression) {
            case Block(List<Expr> instrs, int lineNumber) -> {
                //throw new UnsupportedOperationException("TODO Block");
                // TODO loop over all instructions
                for (var instr : instrs) {
                        visit(instr, env);
                }
                yield UNDEFINED;
            }
            case Literal<?>(Object value, int lineNumber) -> {
                yield value;
            }
            case FunCall(Expr qualifier, List<Expr> args, int lineNumber) -> {
                var function = visit(qualifier, env);
                yield asJSObject(function, lineNumber).invoke(UNDEFINED, args.stream().map(arg -> visit(arg, env)).toArray());
            }
            case LocalVarAccess(String name, int lineNumber) -> {
                yield env.lookup(name);
            }
            case LocalVarAssignment(String name, Expr expr, boolean declaration, int lineNumber) -> {
                var value = visit(expr, env);
                if (declaration) {
                    if (env.lookup(name) != UNDEFINED) {
                        throw new Failure("at line " + lineNumber + ", variable already declared");
                    }
                    env.register(name, value);
                }
                yield value;
            }
            case Fun(Optional<String> optName, List<String> parameters, Block body, int lineNumber) -> {
                var functionName = optName.orElse("lambda");
                Invoker invoker = new Invoker() {
                    @Override
                    public Object invoke(JSObject self, Object receiver, Object... args) {
                        // check the arguments length
                        // create a new environment
                        // add this and all the parameters
                        // visit the body
                        if (args.length != parameters.size()) {
                            throw new Failure("at line " + lineNumber + ", arity error");
                        }
                        var newEnv = JSObject.newEnv(env);
                        newEnv.register("this", self);
                        for (int i = 0; i < args.length; i++) {
                            newEnv.register(parameters.get(i), args[i]);
                        }
                        try {
                            return visit(body, newEnv);
                        } catch (ReturnError returnError) {
                            return returnError.getValue();
                        }
                    }
                };
                // create the JS function with the invoker
                // register it if necessary
                // yield the function
                var function = JSObject.newFunction(functionName, invoker);
                optName.ifPresent(s -> {
                    if (!s.equals("lambda")) {
                        env.register(s, function);
                    }
                });
                yield function;
            }
            case Return(Expr expr, int lineNumber) -> {
                throw new ReturnError(visit(expr, env));
            }
            case If(Expr condition, Block trueBlock, Block falseBlock, int lineNumber) -> {
                var value = visit(condition, env);
                if (value == UNDEFINED || value instanceof Integer n && n == 0){
                    yield visit(falseBlock, env);
                } else {
                    yield visit(trueBlock, env);
                }
            }
            case New(Map<String, Expr> initMap, int lineNumber) -> {
                throw new UnsupportedOperationException("TODO New");
//                initMap.entrySet().stream().map(
//                        entry -> Map.entry(entry.getKey(), visit(entry.getValue(), env))
//                )
            }
            case FieldAccess(Expr receiver, String name, int lineNumber) -> {
                throw new UnsupportedOperationException("TODO FieldAccess");
            }
            case FieldAssignment(Expr receiver, String name, Expr expr, int lineNumber) -> {
                throw new UnsupportedOperationException("TODO FieldAssignment");
            }
            case MethodCall(Expr receiver, String name, List<Expr> args, int lineNumber) -> {
                throw new UnsupportedOperationException("TODO MethodCall");
            }
        };
    }

    @SuppressWarnings("unchecked")
    public static void interpret(Script script, PrintStream outStream) {
        JSObject globalEnv = JSObject.newEnv(null);
        Block body = script.body();
        globalEnv.register("global", globalEnv);
        globalEnv.register("print", JSObject.newFunction("print", (self, receiver, args) -> {
            System.err.println("print called with " + Arrays.toString(args));
            outStream.println(Arrays.stream(args).map(Object::toString).collect(joining(" ")));
            return UNDEFINED;
        }));
        globalEnv.register("+", JSObject.newFunction("+", (self, receiver, args) -> (Integer) args[0] + (Integer) args[1]));
        globalEnv.register("-", JSObject.newFunction("-", (self, receiver, args) -> (Integer) args[0] - (Integer) args[1]));
        globalEnv.register("/", JSObject.newFunction("/", (self, receiver, args) -> (Integer) args[0] / (Integer) args[1]));
        globalEnv.register("*", JSObject.newFunction("*", (self, receiver, args) -> (Integer) args[0] * (Integer) args[1]));
        globalEnv.register("%", JSObject.newFunction("%", (self, receiver, args) -> (Integer) args[0] % (Integer) args[1]));

        globalEnv.register("==", JSObject.newFunction("==", (self, receiver, args) -> args[0].equals(args[1]) ? 1 : 0));
        globalEnv.register("!=", JSObject.newFunction("!=", (self, receiver, args) -> !args[0].equals(args[1]) ? 1 : 0));
        globalEnv.register("<", JSObject.newFunction("<", (self, receiver, args) -> (((Comparable<Object>) args[0]).compareTo(args[1]) < 0) ? 1 : 0));
        globalEnv.register("<=", JSObject.newFunction("<=", (self, receiver, args) -> (((Comparable<Object>) args[0]).compareTo(args[1]) <= 0) ? 1 : 0));
        globalEnv.register(">", JSObject.newFunction(">", (self, receiver, args) -> (((Comparable<Object>) args[0]).compareTo(args[1]) > 0) ? 1 : 0));
        globalEnv.register(">=", JSObject.newFunction(">=", (self, receiver, args) -> (((Comparable<Object>) args[0]).compareTo(args[1]) >= 0) ? 1 : 0));
        visit(body, globalEnv);
    }
}

