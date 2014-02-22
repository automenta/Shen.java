package shen;

import sun.invoke.util.Wrapper;

import java.lang.invoke.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Consumer;

import static java.lang.String.format;
import static java.lang.invoke.MethodHandleProxies.asInterfaceInstance;
import static java.lang.invoke.MethodHandles.*;
import static java.lang.invoke.MethodHandles.guardWithTest;
import static java.lang.invoke.MethodHandles.insertArguments;
import static java.lang.invoke.MethodType.genericMethodType;
import static java.lang.invoke.MethodType.methodType;
import static java.lang.invoke.SwitchPoint.invalidateAll;
import static java.lang.reflect.Modifier.isPublic;
import static java.util.Arrays.asList;
import static java.util.Arrays.stream;
import static java.util.Objects.deepEquals;
import static java.util.function.Predicate.isEqual;

import static sun.invoke.util.BytecodeName.toBytecodeName;
import static sun.invoke.util.BytecodeName.toSourceName;
import static sun.invoke.util.Wrapper.*;
import static sun.invoke.util.Wrapper.forBasicType;

public class RT {
    static final MethodHandles.Lookup lookup = lookup();
    static final Set<Symbol> overrides = new HashSet<>();
    static final Set<Symbol> builtins = new HashSet<>();
    static final Map<Symbol, MethodType> typesForInstallation = new HashMap<>();
    static final Map<Object, CallSite> sites = new HashMap<>();
    static final Map<Object, MethodHandle> guards = new HashMap<>();

    static final MethodHandle
            link = mh(RT.class, "link"), proxy = mh(RT.class, "proxy"),
            checkClass = mh(RT.class, "checkClass"), toIntExact = mh(Math.class, "toIntExact"),
            asNumber = mh(Numbers.class, "asNumber"), number = mh(Numbers.class, "number"),
            asInt = mh(Numbers.class, "asInt"), toList = mh(Cons.class, "toList"),
            partial = mh(RT.class, "partial"), arityCheck = mh(RT.class, "arityCheck");

    public static Object link(MutableCallSite site, String name, Object... args) throws Throwable {
        name = toSourceName(name);
        MethodType type = site.type();
        Shen.debug("LINKING: %s%s %s", name, type, Shen.vec(stream(args).map(Numbers::maybeNumber)));
        List<Class<?>> actualTypes = Shen.vec(stream(args).map(Object::getClass));
        Shen.debug("actual types: %s", actualTypes);
        Symbol symbol = Primitives.intern(name);
        Shen.debug("candidates: %s", symbol.fn);

        if (symbol.fn.isEmpty()) {
            MethodHandle java = javaCall(site, name, type, args);
            if (java != null) {
                Shen.debug("calling java: %s", java);
                site.setTarget(java.asType(type));
                return java.invokeWithArguments(args);
            }
            throw new NoSuchMethodException("undefined function " + name + type
                    + (symbol.fn.isEmpty() ? "" : " in " + Shen.vec(symbol.fn.stream().map(MethodHandle::type))));
        }

        int arity = symbol.fn.get(0).type().parameterCount();
        if (arity > args.length) {
            MethodHandle partial = insertArguments(reLinker(name, arity), 0, args);
            Shen.debug("partial: %s", partial);
            return partial;
        }

        MethodHandle match = Shen.find(symbol.fn.stream(), f -> Shen.every(actualTypes, f.type().parameterList(), RT::canCastStrict));
        if (match == null) throw new NoSuchMethodException("undefined function " + name + type);
        Shen.debug("match based on argument types: %s", match);

        MethodHandle fallback = linker(site, toBytecodeName(name)).asType(type);
        if (symbol.fn.size() > 1 && !match.type().parameterList().stream().allMatch(isEqual(long.class))) {
            match = guards.computeIfAbsent(asList(type, symbol.fn), key -> guard(type, symbol.fn));
            Shen.debug("selected: %s", match);
        }

        synchronized (symbol.symbol) {
            if (symbol.fnGuard == null) symbol.fnGuard = new SwitchPoint();
            site.setTarget(symbol.fnGuard.guardWithTest(match.asType(type), fallback));
        }
        Object result = match.invokeWithArguments(args);
        //maybeRecompile(type, symbol, result == null ? Object.class : result.getClass());
        try {
            maybeRecompile(type, symbol, result == null ? Object.class : result.getClass());
        } catch (Exception e) {
        }
        return result;
    }

    static void maybeRecompile(MethodType type, Symbol symbol, Class returnType) throws Throwable {
        if (symbol.source == null || Shen.booleanProperty("shen-*installing-kl*")) return;
        MethodType signature = typeSignature(symbol);
        type = signature != null ? signature : type.changeReturnType(isWrapperType(returnType) ? wrapper(returnType).primitiveType()
                : isPrimitiveType(returnType) ? returnType : Object.class);
        if ((signature != null || (type.changeReturnType(Object.class).hasPrimitives() && !builtins.contains(symbol))))
            recompile(type, symbol);
    }

    static void recompile(MethodType type, Symbol symbol) throws Throwable {
        if (symbol.source == null || symbol.fn.stream().map(MethodHandle::type).anyMatch(isEqual(type))) return;
        Shen.debug("recompiling as %s: %s", type, symbol.source);
        List<MethodHandle> fn = new ArrayList<>(symbol.fn);
        try {
            Compiler.typeHint.set(type);
            Primitives.eval_kl(symbol.source);
        } finally {
            Compiler.typeHint.remove();
            symbol.fn.addAll(fn);
            if (!type.returnType().equals(Object.class))
                symbol.fn.removeIf(f -> f.type().equals(type.changeReturnType(Object.class)));
        }
    }

    static final Map<Object, Class> types = new HashMap<>();

    static {
        types.put(Primitives.intern("symbol"), Symbol.class);
        types.put(Primitives.intern("number"), long.class);
        types.put(Primitives.intern("boolean"), boolean.class);
        types.put(Primitives.intern("string"), String.class);
        types.put(Primitives.intern("exception"), Exception.class);
        types.put(Primitives.intern("list"), Iterable.class);
        types.put(Primitives.intern("vector"), Object[].class);
    }

    static Set<Symbol> tooStrictTypes = new HashSet<>(asList(Primitives.intern("concat"), Primitives.intern("fail-if"),
            Primitives.intern("tail"), Primitives.intern("systemf")));

    static MethodType typeSignature(Symbol symbol) throws Throwable {
        if (tooStrictTypes.contains(symbol) || !hasKnownSignature(symbol)) return null;
        return typeSignature(symbol, shenTypeSignature(symbol));
    }

    static MethodType typeSignature(Symbol symbol, List<Object> shenTypes) {
        List<Class<?>> javaTypes = new ArrayList<>();
        for (Object argumentType : shenTypes) {
            if (argumentType instanceof Cons)
                argumentType = ((Cons) argumentType).car;
            javaTypes.add(types.containsKey(argumentType) ? types.get(argumentType) :
                    argumentType instanceof Class ? (Class<?>) argumentType : Object.class);
        }
        MethodType type = methodType(javaTypes.remove(javaTypes.size() - 1), javaTypes);
        Shen.debug("%s has Shen type signature: %s mapped to Java %s", symbol, shenTypes, type);
        return type;
    }

    static boolean hasKnownSignature(Symbol symbol) {
        return Primitives.intern("shen.*signedfuncs*").var instanceof Cons && ((Cons) Primitives.intern("shen.*signedfuncs*").var).contains(symbol);
    }

    static List<Object> shenTypeSignature(Symbol symbol) throws Throwable {
        return shenTypeSignature(((Cons) Shen.eval(format("(shen-typecheck %s (gensym A))", symbol))).toList());
    }

    static List<Object> shenTypeSignature(List<Object> signature) {
        if (signature.size() != 3)
            return Shen.vec(signature.stream().filter(isEqual(Primitives.intern("-->")).negate()));
        List<Object> argumentTypes = new ArrayList<>();
        for (; signature.size() == 3; signature = ((Cons) signature.get(2)).toList()) {
            argumentTypes.add(signature.get(0));
            if (!(signature.get(2) instanceof Cons) || signature.get(2) instanceof Cons
                    && !((Cons) signature.get(2)).contains(Primitives.intern("-->"))) {
                argumentTypes.add(signature.get(2));
                break;
            }
        }
        return argumentTypes;
    }

    static MethodHandle guard(MethodType type, List<MethodHandle> candidates) {
        candidates = bestMatchingMethods(type, candidates);
        Shen.debug("applicable candidates: %s", candidates);
        MethodHandle match = candidates.get(candidates.size() - 1).asType(type);
        for (int i = candidates.size() - 1; i > 0; i--) {
            MethodHandle fallback = candidates.get(i);
            MethodHandle target = candidates.get(i - 1);
            Class<?> differentType = Shen.find(target.type().parameterList(), fallback.type().parameterList(), (x, y) -> !x.equals(y));
            int firstDifferent = target.type().parameterList().indexOf(differentType);
            if (firstDifferent == -1) firstDifferent = 0;
            Shen.debug("switching on %d argument type %s", firstDifferent, differentType);
            Shen.debug("target: %s ; fallback: %s", target, fallback);
            MethodHandle test = checkClass.bindTo(differentType);
            test = dropArguments(test, 0, type.dropParameterTypes(firstDifferent, type.parameterCount()).parameterList());
            test = test.asType(test.type().changeParameterType(firstDifferent, type.parameterType(firstDifferent)));
            match = guardWithTest(test, target.asType(type), match);
        }
        return match;
    }

    static List<MethodHandle> bestMatchingMethods(MethodType type, List<MethodHandle> candidates) {
        return Shen.vec(candidates.stream()
                .filter(f -> Shen.every(type.parameterList(), f.type().parameterList(), RT::canCast))
                .sorted((x, y) -> y.type().changeReturnType(type.returnType()).equals(y.type().erase()) ? -1 : 1)
                .sorted((x, y) -> Shen.every(y.type().parameterList(), x.type().parameterList(), RT::canCast) ? -1 : 1));
    }

    public static boolean checkClass(Class<?> xClass, Object x) {
        return xClass != null && canCastStrict(x.getClass(), xClass);
    }

    static MethodHandle relinkOn(Class<? extends Throwable> exception, MethodHandle fn, MethodHandle fallback) {
        return catchException(fn.asType(fallback.type()), exception, dropArguments(fallback, 0, Exception.class));
    }

    static MethodHandle javaCall(MutableCallSite site, String name, MethodType type, Object... args) throws Exception {
        if (name.endsWith(".")) {
            Class aClass = Primitives.intern(name.substring(0, name.length() - 1)).value();
            if (aClass != null)
                return findJavaMethod(type, aClass.getName(), aClass.getConstructors());
        }
        if (name.startsWith("."))
            return relinkOn(ClassCastException.class, findJavaMethod(type, name.substring(1), args[0].getClass().getMethods()),
                    linker(site, toBytecodeName(name)));
        String[] classAndMethod = name.split("/");
        if (classAndMethod.length == 2 && Primitives.intern(classAndMethod[0]).var instanceof Class)
            return findJavaMethod(type, classAndMethod[1], ((Class) Primitives.intern(classAndMethod[0]).value()).getMethods());
        return null;
    }

    public static Object proxy(Method sam, Object x) throws Throwable {
        if (x instanceof MethodHandle) {
            MethodHandle target = (MethodHandle) x;
            int arity = sam.getParameterTypes().length;
            int actual = target.type().parameterCount();
            if (arity < actual) target = insertArguments(target, arity, new Object[actual - arity]);
            if (arity > actual)
                target = dropArguments(target, actual, asList(sam.getParameterTypes()).subList(actual, arity));
            return asInterfaceInstance(sam.getDeclaringClass(), target);
        }
        return null;
    }

    static MethodHandle filterJavaTypes(MethodHandle method) throws IllegalAccessException {
        MethodHandle[] filters = new MethodHandle[method.type().parameterCount()];
        for (int i = 0; i < method.type().parameterCount() - (method.isVarargsCollector() ? 1 : 0); i++)
            if (isSAM(method.type().parameterType(i)))
                filters[i] = proxy.bindTo(findSAM(method.type().parameterType(i)))
                        .asType(methodType(method.type().parameterType(i), Object.class));
            else if (canCast(method.type().parameterType(i), int.class))
                filters[i] = asInt.asType(methodType(method.type().parameterType(i), Object.class));
            else if (canCast(method.type().wrap().parameterType(i), Number.class))
                filters[i] = asNumber.asType(methodType(method.type().parameterType(i), Object.class));
        if (canCast(method.type().wrap().returnType(), Number.class))
            method = filterReturnValue(method, number.asType(methodType(long.class, method.type().returnType())));
        return filterArguments(method, 0, filters);
    }

    static <T extends Executable> MethodHandle findJavaMethod(MethodType type, String method, T[] methods) {
        return Shen.some(stream(methods), m -> {
            try {
                if (m.getName().equals(method)) {
                    m.setAccessible(true);
                    MethodHandle mh = (m instanceof Method) ? lookup.unreflect((Method) m) : lookup.unreflectConstructor((Constructor) m);
                    mh.asType(methodType(type.returnType(), Shen.vec(type.parameterList().stream()
                            .map(c -> c.equals(Long.class) ? Integer.class : c.equals(long.class) ? int.class : c))));
                    return filterJavaTypes(mh);
                }
            } catch (WrongMethodTypeException | IllegalAccessException ignored) {
            }
            return null;
        });
    }

    public static MethodHandle function(Object target) throws Exception {
        return target instanceof Shen.Invokable ? Primitives.function((Shen.Invokable) target) : (MethodHandle) target;
    }

    static MethodHandle linker(MutableCallSite site, String name) {
        return insertArguments(link, 0, site, name).asCollector(Object[].class, site.type().parameterCount());
    }

    static MethodHandle reLinker(String name, int arity) throws IllegalAccessException {
        MutableCallSite reLinker = new MutableCallSite(genericMethodType(arity));
        return relinkOn(IllegalStateException.class, reLinker.dynamicInvoker(), linker(reLinker, toBytecodeName(name)));
    }

    public static CallSite invokeBSM(Lookup lookup, String name, MethodType type) throws IllegalAccessException {
        if (isOverloadedInternalFunction(name)) return invokeCallSite(name, type);
        return sites.computeIfAbsent(name + type, key -> invokeCallSite(name, type));
    }

    static boolean isOverloadedInternalFunction(String name) {
        return Primitives.intern(toSourceName(name)).fn.size() > 1;
    }

    static CallSite invokeCallSite(String name, MethodType type) {
        MutableCallSite site = new MutableCallSite(type);
        site.setTarget(linker(site, name).asType(type));
        return site;
    }

    public static CallSite symbolBSM(Lookup lookup, String name, MethodType type) {
        return sites.computeIfAbsent(name, key -> new ConstantCallSite(constant(Symbol.class, Primitives.intern(toSourceName(name)))));
    }

    public static CallSite applyBSM(Lookup lookup, String name, MethodType type) throws Exception {
        return sites.computeIfAbsent(name + type, key -> applyCallSite(type));
    }

    public static Object partial(MethodHandle target, Object... args) throws Throwable {
        if (args.length > target.type().parameterCount()) return uncurry(target, args);
        return insertArguments(target, 0, args);
    }

    public static boolean arityCheck(int arity, MethodHandle target) throws Throwable {
        return target.type().parameterCount() == arity;
    }

    static CallSite applyCallSite(MethodType type) {
        MethodHandle apply = invoker(type.dropParameterTypes(0, 1));
        MethodHandle test = insertArguments(arityCheck, 0, type.parameterCount() - 1);
        return new ConstantCallSite(guardWithTest(test, apply, partial.asType(type)).asType(type));
    }

    static MethodHandle mh(Class<?> aClass, String name, Class... types) {
        try {
            return lookup.unreflect(Shen.find(stream(aClass.getMethods()), m -> m.getName().equals(name)
                    && (types.length == 0 || deepEquals(m.getParameterTypes(), types))));
        } catch (IllegalAccessException e) {
            throw Shen.uncheck(e);
        }
    }

    static MethodHandle field(Class<?> aClass, String name) {
        try {
            return lookup.unreflectGetter(aClass.getField(name));
        } catch (Exception e) {
            throw Shen.uncheck(e);
        }
    }

    static boolean canCast(Class<?> a, Class<?> b) {
        return a == Object.class || b == Object.class || canCastStrict(a, b);
    }

    static boolean canCastStrict(Class<?> a, Class<?> b) {
        return a == b || b.isAssignableFrom(a) || canWiden(a, b);
    }

    static boolean canWiden(Class<?> a, Class<?> b) {
        return wrapper(b).isNumeric() && wrapper(b).isConvertibleFrom(wrapper(a));
    }

    static Wrapper wrapper(Class<?> type) {
        if (isPrimitiveType(type)) return forPrimitiveType(type);
        if (isWrapperType(type)) return forWrapperType(type);
        return forBasicType(type);
    }

    public static Symbol defun(Symbol name, MethodHandle fn) throws Throwable {
        if (overrides.contains(name)) return name;
        synchronized (name.symbol) {
            SwitchPoint guard = name.fnGuard;
            name.fn.clear();
            name.fn.add(fn);
            if (guard != null) {
                name.fnGuard = new SwitchPoint();
                invalidateAll(new SwitchPoint[]{guard});
            }
            return name;
        }
    }

    static void register(Class<?> aClass, Consumer<? super Method> hook) {
        stream(aClass.getDeclaredMethods()).filter(m -> isPublic(m.getModifiers())).forEach(hook);
    }

    static void override(Method m) {
        overrides.add(defun(m));
    }

    static Symbol defun(Method m) {
        try {
            Symbol name = Primitives.intern(unscramble(m.getName()));
            name.fn.add(lookup.unreflect(m));
            return name;
        } catch (IllegalAccessException e) {
            throw Shen.uncheck(e);
        }
    }

    static Object uncurry(Object chain, Object... args) throws Throwable {
        for (Object arg : args)
            chain = ((MethodHandle) chain).invokeExact(arg);
        return chain;
    }

    public static MethodHandle bindTo(MethodHandle fn, Object arg) {
        return insertArguments(fn, 0, arg);
    }

    static String unscramble(String s) {
        return toSourceName(s).replaceAll("_", "-").replaceAll("^KL-", "").replaceAll("GT", ">").replaceAll("EQ", "=")
                .replaceAll("LT", "<").replaceAll("EX$", "!").replaceAll("P$", "?").replaceAll("^AT", "@");
    }

    static MethodHandle findSAM(Object lambda) {
        try {
            return lookup.unreflect(findSAM(lambda.getClass())).bindTo(lambda);
        } catch (IllegalAccessException e) {
            throw Shen.uncheck(e);
        }
    }

    static Method findSAM(Class<?> lambda) {
        List<Method> methods = Shen.vec(stream(lambda.getDeclaredMethods()).filter(m -> !m.isSynthetic()));
        return methods.size() == 1 ? methods.get(0) : null;
    }

    static boolean isSAM(Class<?> aClass) {
        return findSAM(aClass) != null;
    }
}