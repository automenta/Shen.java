package shen;

import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.Label;
import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.internal.org.objectweb.asm.Type;
import jdk.internal.org.objectweb.asm.commons.GeneratorAdapter;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

import static java.lang.Double.doubleToLongBits;
import static java.lang.Double.longBitsToDouble;
import static java.lang.Math.toIntExact;
import static jdk.internal.org.objectweb.asm.Type.*;
import static jdk.internal.org.objectweb.asm.commons.GeneratorAdapter.*;
import static sun.invoke.util.BytecodeName.toBytecodeName;
import static sun.invoke.util.BytecodeName.toSourceName;

public class Numbers implements Opcodes {
    static final long tag = 1, real = 0, integer = 1;
    static final Set<Symbol> operators = new HashSet<>();

    // longs are either 63 bit signed integers or doubleToLongBits with bit 0 used as tag, 0 = double, 1 = long.
    // Java: 5ms, Shen.java: 10ms, Boxed Java: 15ms. Which ever branch that starts will be faster for some reason.
    static {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(V1_7, ACC_PUBLIC | ACC_FINAL, "shen/Shen$Operators", null, getInternalName(Object.class), null);

        binaryOp(cw, "+", ADD);
        binaryOp(cw, "-", SUB);
        binaryOp(cw, "*", MUL);
        binaryOp(cw, "/", realOp(DIV), integerDivision());
        binaryOp(cw, "%", REM);
        binaryComp(cw, "<", LT);
        binaryComp(cw, "<=", LE);
        binaryComp(cw, ">", GT);
        binaryComp(cw, ">=", GE);

        RT.register(Compiler.loader.loadClass(cw.toByteArray()), Numbers::op);
    }

    static Consumer<GeneratorAdapter> integerOp(int op) {
        return mv -> toInteger(mv, op);
    }

    static Consumer<GeneratorAdapter> realOp(int op) {
        return mv -> toReal(mv, op);
    }

    static Consumer<GeneratorAdapter> integerDivision() {
        return mv -> {
            Label notZero = new Label();
            mv.dup2();
            mv.visitInsn(L2I);
            mv.ifZCmp(IFNE, notZero);
            mv.newInstance(getType(ArithmeticException.class));
            mv.dup();
            mv.push("Division by zero");
            mv.invokeConstructor(getType(ArithmeticException.class),
                    Compiler.method("<init>", Compiler.desc(void.class, String.class)));
            mv.throwException();
            mv.visitLabel(notZero);
            mv.visitInsn(L2D);
            mv.swap(DOUBLE_TYPE, LONG_TYPE);
            mv.visitInsn(L2D);
            mv.swap(DOUBLE_TYPE, DOUBLE_TYPE);
            toReal(mv, DIV);
        };
    }

    static void toInteger(GeneratorAdapter mv, int op) {
        mv.math(op, LONG_TYPE);
        mv.push((int) tag);
        mv.visitInsn(LSHL);
        mv.push(integer);
        mv.visitInsn(LOR);
    }

    static void toReal(GeneratorAdapter mv, int op) {
        mv.math(op, DOUBLE_TYPE);
        mv.invokeStatic(getType(Double.class),
                Compiler.method("doubleToRawLongBits", Compiler.desc(long.class, double.class)));
        mv.push(~integer);
        mv.visitInsn(LAND);
    }

    static void binaryComp(ClassWriter cw, String op, int test) {
        binaryOp(cw, op, boolean.class, comparison(DOUBLE_TYPE, test), comparison(LONG_TYPE, test));
    }

    static Consumer<GeneratorAdapter> comparison(Type type, int test) {
        return mv -> {
            Label _else = new Label();
            mv.ifCmp(type, test, _else);
            mv.push(false);
            mv.returnValue();
            mv.visitLabel(_else);
            mv.push(true);
            mv.returnValue();
        };
    }

    static void binaryOp(ClassWriter cw, String op, int instruction) {
        binaryOp(cw, op, long.class, realOp(instruction), integerOp(instruction));
    }

    static void binaryOp(ClassWriter cw, String op, Consumer<GeneratorAdapter> realOp, Consumer<GeneratorAdapter> integerOp) {
        binaryOp(cw, op, long.class, realOp, integerOp);
    }

    static void binaryOp(ClassWriter cw, String op, Class<?> returnType, Consumer<GeneratorAdapter> realOp,
                         Consumer<GeneratorAdapter> integerOp) {
        GeneratorAdapter mv = new GeneratorAdapter(ACC_PUBLIC + ACC_STATIC,
                Compiler.method(toBytecodeName(op), Compiler.desc(returnType, long.class, long.class)), null, null, cw);

        isInteger(mv, 0);
        Label argOneIsLong = new Label();
        mv.ifZCmp(IFNE, argOneIsLong);
        asDouble(mv, 0);
        isInteger(mv, 1);
        Label argTwoIsLong = new Label();
        mv.ifZCmp(IFNE, argTwoIsLong);
        asDouble(mv, 1);
        Label doubleOperation = new Label();
        mv.goTo(doubleOperation);
        mv.visitLabel(argTwoIsLong);
        asLong(mv, 1);
        mv.visitInsn(L2D);
        mv.goTo(doubleOperation);
        mv.visitLabel(argOneIsLong);
        isInteger(mv, 1);
        Label longOperation = new Label();
        mv.ifZCmp(IFNE, longOperation);
        asLong(mv, 0);
        mv.visitInsn(L2D);
        asDouble(mv, 1);
        mv.visitLabel(doubleOperation);
        realOp.accept(mv);
        mv.returnValue();
        mv.visitLabel(longOperation);
        asLong(mv, 0);
        asLong(mv, 1);
        integerOp.accept(mv);
        mv.returnValue();
        mv.endMethod();
    }

    static void asDouble(GeneratorAdapter mv, int arg) {
        mv.loadArg(arg);
        mv.invokeStatic(getType(Double.class), Compiler.method("longBitsToDouble",
                Compiler.desc(double.class, long.class)));
    }

    static void asLong(GeneratorAdapter mv, int arg) {
        mv.loadArg(arg);
        mv.push((int) tag);
        mv.visitInsn(LSHR);
    }

    static void isInteger(GeneratorAdapter mv, int arg) {
        mv.loadArg(arg);
        mv.visitInsn(L2I);
        mv.push((int) tag);
        mv.visitInsn(IAND);
    }

    static void op(Method op) {
        try {
            Symbol symbol = Primitives.intern(toSourceName(op.getName()));
            symbol.fn.add(RT.lookup.unreflect(op));
            operators.add(symbol);
        } catch (IllegalAccessException e) {
            throw Shen.uncheck(e);
        }
    }

    static Object maybeNumber(Object o) {
        return o instanceof Long ? asNumber((Long) o) : o;
    }

    public static long number(Number n) {
        return n instanceof Double ? real(n.doubleValue()) : integer(n.longValue());
    }

    static long real(double d) {
        return ~tag & doubleToLongBits(d);
    }

    static long integer(long l) {
        return l << tag | tag;
    }

    static double asDouble(long l) {
        return isInteger(l) ? l >> tag : longBitsToDouble(l);
    }

    public static int asInt(long l) {
        return toIntExact(asNumber(l).longValue());
    }

    public static Number asNumber(long fp) { //noinspection RedundantCast
        return isInteger(fp) ? (Number) (fp >> tag) : (Number) longBitsToDouble(fp);
    }

    static boolean isInteger(long l) {
        return (tag & l) == integer;
    }
}