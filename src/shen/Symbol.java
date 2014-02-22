package shen;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.SwitchPoint;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class Symbol implements Shen.Invokable {
    public final String symbol;
    public List<MethodHandle> fn = new ArrayList<>();
    public SwitchPoint fnGuard;
    public Object var;
    public Collection source;

    Symbol(String symbol) {
        this.symbol = symbol.intern();
    }

    public String toString() {
        return symbol;
    }

    public <T> T value() {
        if (var == null) throw new IllegalArgumentException("variable " + this + " has no value");
        //noinspection unchecked
        return (T) var;
    }

    public boolean equals(Object o) { //noinspection StringEquality
        return o instanceof Symbol && symbol == ((Symbol) o).symbol;
    }

    public int hashCode() {
        return symbol.hashCode();
    }

    public MethodHandle invoker() throws IllegalAccessException {
        if (fn.isEmpty()) return RT.reLinker(symbol, 0);
        MethodHandle mh = fn.get(0);
        if (fn.size() > 1) return RT.reLinker(symbol, mh.type().parameterCount());
        return mh;
    }
}