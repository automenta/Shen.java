package shen;

import java.util.Collection;

import static java.lang.Character.isUpperCase;
import static java.lang.Math.floorMod;
import static java.util.Arrays.fill;

public class Overrides {
    static final Symbol _true = Primitives.intern("true"),
            _false = Primitives.intern("false"),
            shen_tuple = Primitives.intern("shen.tuple");

    public static boolean variableP(Object x) {
        return x instanceof Symbol && isUpperCase(((Symbol) x).symbol.charAt(0));
    }

    public static boolean booleanP(Object x) {
        return x instanceof Boolean || _true == x || _false == x;
    }

    public static boolean symbolP(Object x) {
        return x instanceof Symbol && !booleanP(x);
    }

    public static long length(Collection x) {
        return Numbers.integer(x.size());
    }

    public static Object[] ATp(Object x, Object y) {
        return new Object[]{shen_tuple, x, y};
    }

    public static long hash(Object s, long limit) {
        long hash = s.hashCode();
        if (hash == 0) return 1;
        return Numbers.integer(floorMod(hash, limit >> Numbers.tag));
    }

    public static Object[] shen_fillvector(Object[] vector, long counter, long n, Object x) {
        fill(vector, (int) (counter >> Numbers.tag), (int) (n >> Numbers.tag) + 1, x);
        return vector;
    }
}