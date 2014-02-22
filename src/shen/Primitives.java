package shen;

import java.io.*;
import java.lang.invoke.MethodHandle;
import java.util.Objects;
import java.util.concurrent.Callable;

import static java.lang.System.currentTimeMillis;
import static java.util.Arrays.deepToString;
import static java.util.Arrays.fill;
import static java.util.Collections.EMPTY_LIST;

public class Primitives {
    public static boolean EQ(Object left, Object right) {
        if (Objects.equals(left, right)) return true;
        if (absvectorP(left) && absvectorP(right)) {
            Object[] leftA = (Object[]) left;
            Object[] rightA = (Object[]) right;
            if (leftA.length != rightA.length) return false;
            for (int i = 0; i < leftA.length; i++)
                if (!EQ(leftA[i], rightA[i]))
                    return false;
            return true;
        }
        if (numberP(left) && numberP(right)) {
            long a = (Long) left;
            long b = (Long) right;
            return (Numbers.tag & a) == Numbers.integer && (Numbers.tag & b) == Numbers.integer ? a == b : Numbers.asDouble(a) == Numbers.asDouble(b);
        }
        return false;
    }

    public static Class KL_import(Symbol s) throws ClassNotFoundException {
        Class aClass = Class.forName(s.symbol);
        return set(intern(aClass.getSimpleName()), aClass);
    }

    static Class KL_import(Class type) {
        try {
            return KL_import(intern(type.getName()));
        } catch (ClassNotFoundException e) {
            throw Shen.uncheck(e);
        }
    }

    public static Cons cons(Object x, Object y) {
        return new Cons(x, y);
    }

    public static boolean consP(Object x) {
        return x instanceof Cons;
    }

    public static Object simple_error(String s) {
        throw new RuntimeException(s, null, false, false) {
        };
    }

    public static String error_to_string(Throwable e) {
        return e.getMessage() == null ? e.toString() : e.getMessage();
    }

    public static Object hd(Cons cons) {
        return cons.car;
    }

    public static Object tl(Cons cons) {
        return cons.cdr;
    }

    public static String str(Object x) {
        if (consP(x)) throw new IllegalArgumentException(x + " is not an atom; str cannot convert it to a string.");
        if (x != null && x.getClass().isArray()) return deepToString((Object[]) x);
        if (x instanceof Long) x = Numbers.asNumber((Long) x);
        return String.valueOf(x);
    }

    public static String pos(String x, long n) {
        return str(x.charAt((int) (n >> Numbers.tag)));
    }

    public static String tlstr(String x) {
        return x.substring(1);
    }

    public static Class type(Object x) {
        return x.getClass();
    }

    public static Object[] absvector(long n) {
        Object[] objects = new Object[(int) (n >> Numbers.tag)];
        fill(objects, intern("fail!"));
        return objects;
    }

    public static boolean absvectorP(Object x) {
        return x.getClass() == Object[].class;
    }

    public static Object LT_address(Object[] vector, long n) {
        return vector[((int) (n >> Numbers.tag))];
    }

    public static Object[] address_GT(Object[] vector, long n, Object value) {
        vector[((int) (n >> Numbers.tag))] = value;
        return vector;
    }

    public static boolean numberP(Object x) {
        return x instanceof Long;
    }

    public static boolean stringP(Object x) {
        return x instanceof String;
    }

    public static String n_GTstring(long n) {
        if (n >> Numbers.tag < 0) throw new IllegalArgumentException(n + " is not a valid character");
        return Character.toString((char) (n >> Numbers.tag));
    }

    public static String byte_GTstring(long n) {
        return n_GTstring(n >> Numbers.tag);
    }

    public static long string_GTn(String s) {
        return Numbers.integer((int) s.charAt(0));
    }

    public static long read_byte(InputStream s) throws IOException {
        return Numbers.integer(s.read());
    }

    public static Long convertToLong(Object x) {
        return (Long) Numbers.asNumber((Long) x);
    }

    public static <T> T write_byte(T x, OutputStream s) throws IOException {
        s.write(convertToLong(x).byteValue());
        s.flush();
        return x;
    }

    public static Closeable open(String string, Symbol direction) throws IOException {
        File file = new File(string);
        if (!file.isAbsolute()) {
            //noinspection RedundantCast
            file = new File((String) intern("*home-directory*").value(), string);
        }

        switch (direction.symbol) {
            case "in":
                return new BufferedInputStream(new FileInputStream(file));
            case "out":
                return new BufferedOutputStream(new FileOutputStream(file));
        }
        throw new IllegalArgumentException("invalid direction");
    }

    public static Object close(Closeable stream) throws IOException {
        stream.close();
        return EMPTY_LIST;
    }

    static long startTime = System.currentTimeMillis();

    public static long get_time(Symbol time) {
        switch (time.symbol) {
            case "run":
                return Numbers.real((currentTimeMillis() - startTime) / 1000.0);
            case "unix":
                return Numbers.integer(currentTimeMillis() / 1000);
        }
        throw new IllegalArgumentException("get-time does not understand the parameter " + time);
    }

    public static String cn(String s1, String s2) {
        return s1 + s2;
    }

    public static Symbol intern(String name) {
        return Shen.symbols.computeIfAbsent(name, Symbol::new);
    }

    public static <T> T value(Symbol x) {
        return x.value();
    }

    @SuppressWarnings("unchecked")
    public static <T> T set(Symbol x, T y) {
        return (T) (x.var = y);
    }

    static <T> T set(String x, T y) {
        return set(intern(x), y);
    }

    public static MethodHandle function(Shen.Invokable x) throws Exception {
        return x.invoker();
    }

    static MethodHandle function(String x) throws Exception {
        return function(intern(x));
    }

    public static Object eval_kl(Object kl) throws Throwable {
        return new Compiler(kl).load("__eval__", Callable.class).newInstance().call();
    }

    public static boolean or(boolean x, boolean y) {
        return x || y;
    }

    public static boolean and(boolean x, boolean y) {
        return x && y;
    }
}