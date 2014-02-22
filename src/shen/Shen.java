package shen;

import java.io.*;
import java.lang.invoke.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.function.*;
import java.util.jar.Manifest;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.ClassLoader.getSystemClassLoader;
import static java.lang.ClassLoader.getSystemResources;
import static java.lang.String.format;
import static java.lang.System.*;
import static java.util.Arrays.*;
import static java.util.Collections.*;
import static java.util.function.Predicate.isEqual;
import static java.util.jar.Attributes.Name.IMPLEMENTATION_VERSION;
import static java.util.stream.Collectors.toSet;
import static java.util.stream.Stream.concat;

@SuppressWarnings({"UnusedDeclaration", "Convert2Diamond"})
public class Shen {
    public static void main(String... args) throws Throwable {
        install();
        eval("(shen.shen)");
    }

    static final Map<String, Symbol> symbols = new HashMap<>();

    static {
        Primitives.set("*language*", "Java");
        Primitives.set("*implementation*", format("%s (build %s)", getProperty("java.runtime.name"), getProperty("java.runtime.version")));
        Primitives.set("*porters*", new String("Håkan Råberg".getBytes(), Charset.forName("ISO-8859-1")));
        Primitives.set("*port*", version());
        Primitives.set("*stinput*", in);
        Primitives.set("*stoutput*", out);
        Primitives.set("*debug*", Boolean.getBoolean("shen.debug"));
        Primitives.set("*debug-asm*", Boolean.getBoolean("shen.debug.asm"));
        Primitives.set("*compile-path*", getProperty("shen.compile.path", "target/classes"));
        Primitives.set("*home-directory*", getProperty("user.dir"));

        RT.register(Primitives.class, RT::defun);
        RT.register(Overrides.class, RT::override);

        asList(Math.class, System.class).forEach(Primitives::KL_import);
    }

    interface LLPredicate {
        boolean test(long a, long b);
    }

    interface Invokable {
        MethodHandle invoker() throws Exception;
    }

    static boolean isDebug() {
        return booleanProperty("*debug*");
    }

    static boolean booleanProperty(String property) {
        return Primitives.intern(property).var == Boolean.TRUE;
    }

    public static Object eval(String kl) throws Throwable {
        return Primitives.eval_kl(KLReader.read(new StringReader(kl)).get(0));
    }

    static void install() throws Throwable {
        readTypes();
        Primitives.set("shen-*installing-kl*", true);
        for (String file : asList("toplevel", "core", "sys", "sequent", "yacc", "reader",
                "prolog", "track", "load", "writer", "macros", "declarations", "types", "t-star"))
            load("klambda/" + file, Callable.class).newInstance().call();
        for (String file : asList("types"))
            load("klambda-custom/" + file, Callable.class).newInstance().call();
        Primitives.set("shen-*installing-kl*", false);
        Primitives.set("*home-directory*", getProperty("user.dir")); //Resetting it because it gets overwritten in declarations.kl
        RT.builtins.addAll(vec(symbols.values().stream().filter(s -> !s.fn.isEmpty())));
    }

    static void readTypes() throws Throwable {
        try {
            getSystemClassLoader().loadClass("klambda.types");
        } catch (ClassNotFoundException ignored) {
            try (Reader in = resource("klambda/types.kl")) {
                List<Object> declarations = vec(KLReader.read(in).stream().filter(List.class::isInstance)
                        .filter(c -> ((List) c).get(0).equals(Primitives.intern("declare"))));
                for (Object declaration : declarations) {
                    List list = (List) declaration;
                    Symbol symbol = (Symbol) list.get(1);
                    if (!RT.tooStrictTypes.contains(symbol))
                        //noinspection unchecked
                        RT.typesForInstallation.put(symbol, RT.typeSignature(symbol, RT.shenTypeSignature(((Cons) Primitives.eval_kl(list.get(2))).toList())));
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    static <T> Class<T> load(String file, Class<T> aClass) throws Throwable {
        try {
            return (Class<T>) getSystemClassLoader().loadClass(file.replaceAll("/", "."));
        } catch (ClassNotFoundException e) {
            debug("compiling: %s", file);
            return compile(file, aClass);
        }
    }

    static <T> Class<T> compile(String file, Class<T> aClass) throws Throwable {
        try (Reader in = resource(format("%s.kl", file))) {
            debug("loading: %s", file);
            Compiler compiler = new Compiler(null, file, cons(Primitives.intern("do"), KLReader.read(in)));
            //noinspection RedundantCast
            File compilePath = new File((String) Primitives.intern("*compile-path*").value());
            File classFile = new File(compilePath, file + ".class");
            if (!(compilePath.mkdirs() || compilePath.isDirectory()))
                throw new IOException("could not make directory: " + compilePath);
            try {
                return compiler.load(classFile.getName().replaceAll(".class$", ".kl"), aClass);
            } finally {
                KLReader.lines.clear();
                if (compiler.bytes != null)
                    try (OutputStream out = new FileOutputStream(classFile)) {
                        out.write(compiler.bytes);
                    }
            }
        }
    }

    static Reader resource(String resource) {
        return new BufferedReader(new InputStreamReader(getSystemClassLoader().getResourceAsStream(resource)));
    }

    static String version() {
        try (InputStream manifest = find(Collections.list(getSystemResources("META-INF/MANIFEST.MF")).stream(),
                u -> u.getPath().matches(".*shen.java.*?.jar!.*")).openStream()) {
            return new Manifest(manifest).getMainAttributes().getValue(IMPLEMENTATION_VERSION);
        } catch (IOException | NullPointerException ignored) {
        }
        return "<unknown>";
    }

    static void debug(String format, Object... args) {
        if (isDebug()) err.println(format(format,
                stream(args).map(o -> o != null && o.getClass() == Object[].class
                        ? deepToString((Object[]) o) : o).toArray()));
    }

    @SafeVarargs
    static <T> List<T> list(T... items) {
        return new ArrayList<>(asList(items));
    }

    @SuppressWarnings("unchecked")
    static <T> List<T> vec(Stream<T> coll) {
        return new ArrayList<>(coll.collect(Collectors.<T>toList()));
    }

    static <T> T find(Stream<T> coll, Predicate<? super T> pred) {
        return coll.filter(pred).findFirst().orElse(null);
    }

    static <T, R> R some(Stream<T> coll, Function<? super T, ? extends R> pred) {
        return coll.map(pred).filter(isEqual(true).or(Objects::nonNull)).findFirst().orElse(null);
    }

    static <T, C extends Collection<T>> C into(C to, Collection<? extends T> from) {
        Collector<Object, ?, ? extends Collection<Object>> collector = to instanceof Set ? toSet() : Collectors.toList();
        //noinspection unchecked
        return (C) concat(to.stream(), from.stream()).collect(collector);
    }

    static <T, C extends Collection<T>> C conj(C coll, Object x) { //noinspection unchecked
        return into(coll, singleton((T) x));
    }

    static <T> List<T> cons(T x, List<T> seq) {
        return into(singletonList(x), seq);
    }

    static <T, R> Stream<R> map(Stream<T> c1, Stream<T> c2, BiFunction<T, T, R> f) {
        Iterator<T> it1 = c1.iterator();
        Iterator<T> it2 = c2.iterator();
        List<R> result = new ArrayList<R>();
        while (it1.hasNext() && it2.hasNext()) {
            result.add(f.apply(it1.next(), it2.next()));
        }
        return result.stream();
    }

    static <T> boolean every(Collection<T> c1, Collection<T> c2, BiPredicate<T, T> pred) {
        return map(c1.stream(), c2.stream(), pred::test).allMatch(isEqual(true));
    }

    static <T> T find(Collection<T> c1, Collection<T> c2, BiPredicate<T, T> pred) {
        return find(map(c1.stream(), c2.stream(), (x, y) -> pred.test(x, y) ? x : null), Objects::nonNull);
    }

    static <T> List<T> rest(List<T> coll) {
        return coll.isEmpty() ? coll : coll.subList(1, coll.size());
    }

    static RuntimeException uncheck(Throwable t) {
        return uncheckAndThrow(t);
    }

    static <T extends Throwable> T uncheckAndThrow(Throwable t) throws T { //noinspection unchecked
        throw (T) t;
    }
}

