package shen;

import jdk.internal.org.objectweb.asm.*;
import jdk.internal.org.objectweb.asm.commons.GeneratorAdapter;
import jdk.internal.org.objectweb.asm.tree.ClassNode;
import jdk.internal.org.objectweb.asm.util.ASMifier;
import jdk.internal.org.objectweb.asm.util.TraceClassVisitor;
import sun.invoke.anon.AnonymousClassLoader;
import sun.misc.Unsafe;

import java.io.PrintWriter;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Stream;

import static java.lang.System.err;
import static java.lang.invoke.MethodType.methodType;
import static java.util.Arrays.asList;
import static java.util.Arrays.stream;
import static java.util.Collections.nCopies;
import static java.util.Collections.singleton;
import static java.util.stream.Stream.concat;
import static java.util.stream.Stream.empty;
import static jdk.internal.org.objectweb.asm.ClassReader.SKIP_DEBUG;
import static jdk.internal.org.objectweb.asm.ClassWriter.COMPUTE_FRAMES;
import static jdk.internal.org.objectweb.asm.Type.*;
import static jdk.internal.org.objectweb.asm.Type.getType;

import static sun.invoke.util.BytecodeName.toBytecodeName;
import static sun.invoke.util.Wrapper.asPrimitiveType;
import static sun.invoke.util.Wrapper.forBasicType;

public class Compiler implements Opcodes {
    static final AnonymousClassLoader loader = AnonymousClassLoader.make(unsafe(), RT.class);
    static final Map<Symbol, MethodHandle> macros = new HashMap<>();
    static final List<Class<?>> literals = asList(Long.class, String.class, Boolean.class, Handle.class);
    static final Handle
            applyBSM = handle(RT.class, "applyBSM"), invokeBSM = handle(RT.class, "invokeBSM"),
            symbolBSM = handle(RT.class, "symbolBSM"), or = handle(Primitives.class, "or"),
            and = handle(Primitives.class, "and");
    static final Map<Class, MethodHandle> push = new HashMap<>();

    static {
        RT.register(Macros.class, Compiler::macro);
    }

    static int id = 1;

    String className;
    ClassWriter cw;

    byte[] bytes;
    GeneratorAdapter mv;
    Object kl;
    static ThreadLocal<MethodType> typeHint = new ThreadLocal<>();

    Symbol self;
    jdk.internal.org.objectweb.asm.commons.Method method;
    Map<Symbol, Integer> locals;
    List<Symbol> args;
    List<Type> argTypes;
    Type topOfStack;
    Label recur;

    static class TypedValue {
        final Type type;
        final Object value;

        TypedValue(Type type, Object value) {
            this.type = type;
            this.value = value;
        }
    }

    public Compiler(Object kl, Symbol... args) throws Throwable {
        this(null, "shen/ShenEval" + id++, kl, args);
    }

    public Compiler(ClassWriter cn, String className, Object kl, Symbol... args) throws Throwable {
        this.cw = cn;
        this.className = className;
        this.kl = kl;
        this.args = Shen.list(args);
        this.locals = new HashMap<>();
    }

    static ClassWriter classWriter(String name, Class<?> anInterface) {
        ClassWriter cw = new ClassWriter(COMPUTE_FRAMES) {
        }; // Needs to be in this package for some reason.
        cw.visit(V1_7, ACC_PUBLIC | ACC_FINAL, name, null, getInternalName(Object.class), new String[]{getInternalName(anInterface)});
        return cw;
    }

    static jdk.internal.org.objectweb.asm.commons.Method method(String name, String desc) {
        return new jdk.internal.org.objectweb.asm.commons.Method(name, desc);
    }


    static String desc(Class<?> returnType, Class<?>... argumentTypes) {
        return methodType(returnType, argumentTypes).toMethodDescriptorString();
    }

    static String desc(Type returnType, List<Type> argumentTypes) {
        return getMethodDescriptor(returnType, argumentTypes.toArray(new Type[argumentTypes.size()]));
    }

    static Handle handle(Class<?> declaringClass, String name) {
        return handle(getInternalName(declaringClass), name, RT.mh(declaringClass, name).type().toMethodDescriptorString());
    }

    static Handle handle(String className, String name, String desc) {
        return new Handle(Opcodes.H_INVOKESTATIC, className, name, desc);
    }

    static Type boxedType(Type type) {
        if (!isPrimitive(type)) return type;
        return getType(forBasicType(type.getDescriptor().charAt(0)).wrapperType());
    }

    static boolean isPrimitive(Type type) {
        return type.getSort() < ARRAY;
    }

    static void macro(Method m) {
        try {
            macros.put(Primitives.intern(RT.unscramble(m.getName())), RT.lookup.unreflect(m));
        } catch (IllegalAccessException e) {
            throw Shen.uncheck(e);
        }
    }

    GeneratorAdapter generator(int access, jdk.internal.org.objectweb.asm.commons.Method method) {
        return new GeneratorAdapter(access, method, cw.visitMethod(access, method.getName(), method.getDescriptor(), null, null));
    }

    TypedValue compile(Object kl) {
        return compile(kl, true);
    }

    TypedValue compile(Object kl, boolean tail) {
        return compile(kl, getType(Object.class), tail);
    }

    TypedValue compile(Object kl, Type returnType, boolean tail) {
        return compile(kl, returnType, true, tail);
    }

    TypedValue compile(Object kl, Type returnType, boolean handlePrimitives, boolean tail) {
        try {
            Class literalClass = Shen.find(literals.stream(), c -> c.isInstance(kl));
            if (literalClass != null) push(literalClass, kl);
            else if (kl instanceof Symbol) symbol((Symbol) kl);
            else if (kl instanceof Collection) {
                @SuppressWarnings("unchecked")
                List<Object> list = new ArrayList<>((Collection<?>) kl);
                lineNumber(list);
                if (list.isEmpty()) emptyList();
                else {
                    Object first = list.get(0);
                    if (first instanceof Symbol && !inScope((Symbol) first)) {
                        Symbol s = (Symbol) first;
                        if (macros.containsKey(s)) macroExpand(s, Shen.rest(list), returnType, tail);
                        else indy(s, Shen.rest(list), returnType, tail);

                    } else {
                        compile(first, tail);
                        apply(returnType, Shen.rest(list));
                    }
                }
            } else
                throw new IllegalArgumentException("Cannot compile: " + kl + " (" + kl.getClass() + ")");
            if (handlePrimitives) handlePrimitives(returnType);
            return new TypedValue(topOfStack, kl);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw Shen.uncheck(t);
        }
    }

    void handlePrimitives(Type returnType) {
        if (isPrimitive(returnType) && !isPrimitive(topOfStack)) unbox(returnType);
        else if (!isPrimitive(returnType) && isPrimitive(topOfStack)) box();
    }

    void lineNumber(List<Object> list) {
        if (KLReader.lines.containsKey(list))
            mv.visitLineNumber(KLReader.lines.get(list), mv.mark());
    }

    boolean inScope(Symbol x) {
        return (locals.containsKey(x) || args.contains(x));
    }

    void macroExpand(Symbol s, List<Object> args, Type returnType, boolean tail) throws Throwable {
        macros.get(s).invokeWithArguments(Shen.into(asList(new Macros(), tail, returnType),
                Shen.vec(args.stream().map(x -> x instanceof Cons ? ((Cons) x).toList() : x))));
    }

    void indy(Symbol s, List<Object> args, Type returnType, boolean tail) throws ReflectiveOperationException {
        Iterator<Type> selfCallTypes = asList(method.getArgumentTypes()).iterator();
        List<TypedValue> typedValues = Shen.vec(args.stream().map(o -> compile(o, isSelfCall(s, args)
                ? selfCallTypes.next() : getType(Object.class), false, false)));
        List<Type> argumentTypes = Shen.vec(typedValues.stream().map(t -> t.type));
        if (isSelfCall(s, args)) {
            if (tail) {
                Shen.debug("recur: %s", s);
                recur(argumentTypes);
            } else {
                Shen.debug("can only recur from tail position: %s", s);
                mv.invokeDynamic(toBytecodeName(s.symbol), desc(method.getReturnType(), argumentTypes), invokeBSM);
                returnType = method.getReturnType();
            }
        } else {
            if (Numbers.operators.contains(s) && returnType.equals(getType(Object.class)) && argumentTypes.size() == 2)
                returnType = getType(s.fn.get(0).type().returnType());
            mv.invokeDynamic(toBytecodeName(s.symbol), desc(returnType, argumentTypes), invokeBSM);
        }
        topOfStack = returnType;
    }

    void recur(List<Type> argumentTypes) {
        for (int i = args.size() - 1; i >= 0; i--) {
            if (!isPrimitive(method.getArgumentTypes()[i])) mv.valueOf(argumentTypes.get(i));
            mv.storeArg(i);
        }
        mv.goTo(recur);
    }

    boolean isSelfCall(Symbol s, List<Object> args) {
        return self.equals(s) && args.size() == this.args.size();
    }

    void apply(Type returnType, List<Object> args) throws ReflectiveOperationException {
        if (!topOfStack.equals(getType(MethodHandle.class)))
            mv.invokeStatic(getType(RT.class), method("function", desc(MethodHandle.class, Object.class)));
        List<Type> argumentTypes = Shen.cons(getType(MethodHandle.class), Shen.vec(args.stream().map(o -> compile(o, false).type)));
        mv.invokeDynamic("__apply__", desc(returnType, argumentTypes), applyBSM);
        topOfStack = returnType;
    }

    class Macros {
        public void trap_error(boolean tail, Type returnType, Object x, Object f) throws Throwable {
            Label after = mv.newLabel();

            Label start = mv.mark();
            compile(x, returnType, tail);
            mv.goTo(after);

            mv.catchException(start, mv.mark(), getType(Throwable.class));
            compile(f, false);
            maybeCast(MethodHandle.class);
            mv.swap();
            bindTo();

            mv.invokeVirtual(getType(MethodHandle.class), method("invokeExact", desc(Object.class)));
            if (isPrimitive(returnType)) unbox(returnType);
            else topOfStack(Object.class);
            mv.visitLabel(after);
        }

        public void KL_if(boolean tail, Type returnType, Object test, Object then, Object _else) throws Exception {
            if (test == Boolean.TRUE || test == Primitives.intern("true")) {
                compile(then, returnType, tail);
                return;
            }
            if (test == Boolean.FALSE || test == Primitives.intern("false")) {
                compile(_else, returnType, tail);
                return;
            }

            Label elseStart = mv.newLabel();
            Label end = mv.newLabel();

            compile(test, BOOLEAN_TYPE, false);
            if (!BOOLEAN_TYPE.equals(topOfStack)) {
                popStack();
                mv.throwException(getType(IllegalArgumentException.class), "boolean expected");
                return;
            }
            mv.visitJumpInsn(IFEQ, elseStart);

            compile(then, returnType, tail);
            Type typeOfThenBranch = topOfStack;
            mv.goTo(end);

            mv.visitLabel(elseStart);
            compile(_else, returnType, tail);

            mv.visitLabel(end);
            if (!typeOfThenBranch.equals(topOfStack) && !isPrimitive(returnType))
                topOfStack(Object.class);
        }

        public void cond(boolean tail, Type returnType, List... clauses) throws Exception {
            if (clauses.length == 0)
                mv.throwException(getType(IllegalArgumentException.class), "condition failure");
            else {
                List clause = clauses[0];
                KL_if(tail, returnType, clause.get(0), clause.get(1),
                        Shen.cons(Primitives.intern("cond"), Shen.rest(Shen.list((Object[]) clauses))));
            }
        }

        public void or(boolean tail, Type returnType, Object x, Object... clauses) throws Exception {
            if (clauses.length == 0)
                bindTo(or, x);
            else {
                KL_if(tail, BOOLEAN_TYPE, x, true, (clauses.length > 1 ?
                        Shen.cons(Primitives.intern("or"), Shen.list(clauses)) : clauses[0]));
                if (!isPrimitive(returnType)) mv.box(returnType);
            }
        }

        public void and(boolean tail, Type returnType, Object x, Object... clauses) throws Exception {
            if (clauses.length == 0)
                bindTo(and, x);
            else {
                KL_if(tail, BOOLEAN_TYPE, x, (clauses.length > 1 ?
                        Shen.cons(Primitives.intern("and"), Shen.list(clauses)) : clauses[0]), false);
                if (!isPrimitive(returnType)) mv.box(returnType);
            }
        }

        public void lambda(boolean tail, Type returnType, Symbol x, Object y) throws Throwable {
            fn("__lambda__", y, x);
        }

        public void freeze(boolean tail, Type returnType, Object x) throws Throwable {
            fn("__freeze__", x);
        }

        public void defun(boolean tail, Type returnType, Symbol name, final List<Symbol> args, Object body) throws Throwable {
            push(name);
            Shen.debug("compiling: %s%s in %s", name, args, getObjectType(className).getClassName());
            name.source = Cons.toCons(asList(Primitives.intern("defun"), name, args, body));
            if (Shen.booleanProperty("shen-*installing-kl*") && RT.typesForInstallation.containsKey(name))
                typeHint.set(RT.typesForInstallation.get(name));
            fn(name.symbol, body, args.toArray(new Symbol[args.size()]));
            mv.invokeStatic(getType(RT.class), method("defun", desc(Symbol.class, Symbol.class, MethodHandle.class)));
            topOfStack(Symbol.class);
        }

        public void let(boolean tail, Type returnType, Symbol x, Object y, Object z) throws Throwable {
            Label start = mv.mark();
            compile(y, false);
            Integer hidden = locals.get(x);
            int let = hidden != null && tail ? hidden : mv.newLocal(topOfStack);
            mv.storeLocal(let);
            locals.put(x, let);
            compile(z, returnType, tail);
            if (hidden != null) locals.put(x, hidden);
            else locals.remove(x);
            if (!tail) {
                mv.push((String) null);
                mv.storeLocal(let);
            }
            mv.visitLocalVariable(x.symbol, mv.getLocalType(let).getDescriptor(), null, start, mv.mark(), let);
        }

        public void KL_do(boolean tail, Type returnType, Object... xs) throws Throwable {
            for (int i = 0; i < xs.length; i++) {
                boolean last = i == xs.length - 1;
                compile(xs[i], last ? returnType : getType(Object.class), last && tail);
                if (!last) popStack();
            }
        }

        public void thaw(boolean tail, Type returnType, Object f) throws Throwable {
            compile(f, false);
            maybeCast(MethodHandle.class);
            mv.invokeVirtual(getType(MethodHandle.class), method("invokeExact", desc(Object.class)));
            topOfStack(Object.class);
        }
    }

    void fn(String name, Object kl, Symbol... args) throws Throwable {
        String bytecodeName = toBytecodeName(name) + "_" + id++;
        List<Symbol> scope = Shen.vec(closesOver(new HashSet<>(asList(args)), kl).distinct());
        scope.retainAll(Shen.into(locals.keySet(), this.args));

        if (name.startsWith("__")) typeHint.remove();
        List<Type> types = Shen.into(Shen.vec(scope.stream().map(this::typeOf)), typeHint.get() != null
                ? Shen.vec(typeHint.get().parameterList().stream().map(Type::getType)) : nCopies(args.length, getType(Object.class)));
        Type returnType = typeHint.get() != null ? getType(typeHint.get().returnType()) : getType(Object.class);
        typeHint.remove();
        push(handle(className, bytecodeName, desc(returnType, types)));
        insertArgs(0, scope);

        scope.addAll(asList(args));
        Compiler fn = new Compiler(cw, className, kl, scope.toArray(new Symbol[scope.size()]));
        fn.method(ACC_PUBLIC | ACC_STATIC | ACC_FINAL, Primitives.intern(name), bytecodeName, returnType, types);
    }

    @SuppressWarnings({"unchecked"})
    Stream<Symbol> closesOver(Set<Symbol> scope, Object kl) {
        if (kl instanceof Symbol && !scope.contains(kl))
            return singleton((Symbol) kl).stream();
        if (kl instanceof Collection) {
            List<Object> list = new ArrayList<>((Collection<?>) kl);
            if (!list.isEmpty())
                switch (list.get(0).toString()) {
                    case "let":
                        return concat(closesOver(scope, list.get(2)), closesOver(Shen.conj(scope, list.get(2)), list.get(3)));
                    case "lambda":
                        return closesOver(Shen.conj(scope, list.get(2)), list.get(2));
                    case "defun":
                        return closesOver(Shen.into(scope, (Collection) list.get(2)), list.get(3));
                }
            return list.stream().flatMap(o -> closesOver(scope, o));
        }
        return empty();
    }

    void emptyList() {
        mv.getStatic(getType(Collections.class), "EMPTY_LIST", getType(List.class));
        topOfStack(List.class);
    }

    void symbol(Symbol s) throws Throwable {
        if (asList("true", "false").contains(s.symbol)) {
            push(Boolean.class, Boolean.valueOf(s.symbol));
            return;
        } else if (locals.containsKey(s)) mv.loadLocal(locals.get(s));
        else if (args.contains(s)) mv.loadArg(args.indexOf(s));
        else push(s);
        topOfStack = typeOf(s);
    }

    Type typeOf(Symbol s) {
        if (locals.containsKey(s)) return mv.getLocalType(locals.get(s));
        else if (args.contains(s)) return argTypes.get(args.indexOf(s));
        return getType(Symbol.class);
    }

    void loadArgArray(List<?> args) {
        mv.push(args.size());
        mv.newArray(getType(Object.class));

        for (int i = 0; i < args.size(); i++) {
            mv.dup();
            mv.push(i);
            compile(args.get(i), false);
            box();
            mv.arrayStore(getType(Object.class));
        }
        topOfStack(Object[].class);
    }

    void push(Symbol kl) {
        mv.invokeDynamic(toBytecodeName(kl.symbol), desc(Symbol.class), symbolBSM);
        topOfStack(Symbol.class);
    }

    void push(Handle handle) {
        mv.push(handle);
        topOfStack(MethodHandle.class);
    }

    void push(Class<?> aClass, Object kl) throws Throwable {
        aClass = asPrimitiveType(aClass);
        push.computeIfAbsent(aClass, c -> RT.mh(mv.getClass(), "push", c)).invoke(mv, kl);
        topOfStack(aClass);
    }

    void box() {
        Type maybePrimitive = topOfStack;
        mv.valueOf(maybePrimitive);
        topOfStack = boxedType(maybePrimitive);
    }

    void unbox(Type type) {
        mv.unbox(type);
        topOfStack = type;
    }

    void popStack() {
        if (topOfStack.getSize() == 1) mv.pop();
        else mv.pop2();
    }

    void maybeCast(Class<?> type) {
        maybeCast(getType(type));
    }

    void maybeCast(Type type) {
        if (!type.equals(topOfStack)) mv.checkCast(type);
        topOfStack = type;
    }

    void topOfStack(Class<?> aClass) {
        topOfStack = getType(aClass);
    }

    public <T> Class<T> load(String source, Class<T> anInterface) throws Exception {
        try {
            cw = classWriter(className, anInterface);
            cw.visitSource(source, null);
            constructor();
            Method sam = RT.findSAM(anInterface);
            List<Type> types = Shen.vec(stream(sam.getParameterTypes()).map(Type::getType));
            method(ACC_PUBLIC, Primitives.intern(sam.getName()), toBytecodeName(sam.getName()), getType(sam.getReturnType()), types);
            bytes = cw.toByteArray();
            if (Shen.booleanProperty("*debug-asm*")) printASM(bytes, sam);
            //noinspection unchecked
            return (Class<T>) loader.loadClass(bytes);
        } catch (VerifyError e) {
            printASM(bytes, null);
            throw e;
        }
    }

    void method(int modifiers, Symbol name, String bytecodeName, Type returnType, List<Type> argumentTypes) {
        this.self = name;
        this.argTypes = argumentTypes;
        this.method = method(bytecodeName, desc(returnType, argumentTypes));
        mv = generator(modifiers, method);
        recur = mv.mark();
        compile(kl, returnType, true);
        maybeCast(returnType);
        mv.returnValue();
        mv.endMethod();
    }

    void constructor() {
        GeneratorAdapter ctor = generator(ACC_PUBLIC, method("<init>", desc(void.class)));
        ctor.loadThis();
        ctor.invokeConstructor(getType(Object.class), method("<init>", desc(void.class)));
        ctor.returnValue();
        ctor.endMethod();
    }

    void bindTo(Handle handle, Object arg) {
        push(handle);
        compile(arg, false);
        box();
        bindTo();
    }

    void bindTo() {
        mv.invokeStatic(getType(RT.class), method("bindTo", desc(MethodHandle.class, MethodHandle.class, Object.class)));
        topOfStack(MethodHandle.class);
    }

    void insertArgs(int pos, List<?> args) {
        if (args.isEmpty()) return;
        mv.push(pos);
        loadArgArray(args);
        mv.invokeStatic(getType(MethodHandles.class), method("insertArguments",
                desc(MethodHandle.class, MethodHandle.class, int.class, Object[].class)));
        topOfStack(MethodHandle.class);
    }

    static void printASM(byte[] bytes, Method method) {
        ASMifier asm = new ASMifier();
        PrintWriter pw = new PrintWriter(err);
        TraceClassVisitor printer = new TraceClassVisitor(null, asm, pw);
        if (method == null)
            new ClassReader(bytes).accept(printer, SKIP_DEBUG);
        else {
            ClassNode cn = new ClassNode();
            new ClassReader(bytes).accept(cn, SKIP_DEBUG);
            Shen.find(cn.methods.stream(), mn -> mn.name.equals(method.getName())).accept(printer);
            asm.print(pw);
            pw.flush();
        }
    }

    static Unsafe unsafe() {
        try {
            Field unsafe = Unsafe.class.getDeclaredField("theUnsafe");
            unsafe.setAccessible(true);
            return (Unsafe) unsafe.get(null);
        } catch (Exception e) {
            throw Shen.uncheck(e);
        }
    }
}