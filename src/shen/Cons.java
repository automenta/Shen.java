package shen;

import java.util.*;

import static java.util.Collections.EMPTY_LIST;
import static java.util.Collections.reverse;

public class Cons extends AbstractCollection {
    public final Object car, cdr;
    public final int size;

    public Cons(Object car, Object cdr) {
        this.car = car;
        this.cdr = cdr;
        this.size = cdr instanceof Cons ? 1 + (((Cons) cdr).size) : EMPTY_LIST.equals(cdr) ? 1 : 2;
    }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (o instanceof List && isList()) //noinspection unchecked
            return Shen.vec(toList().stream().map(Numbers::maybeNumber)).equals(o);
        if (o == null || getClass() != o.getClass()) return false;
        //noinspection ConstantConditions
        Cons cons = (Cons) o;
        return Primitives.EQ(car, cons.car) && cdr.equals(cons.cdr);
    }

    boolean isList() {
        return cdr instanceof Cons || EMPTY_LIST.equals(cdr);
    }

    public int hashCode() {
        return 31 * car.hashCode() + cdr.hashCode();
    }

    @SuppressWarnings("NullableProblems")
    public Iterator iterator() {
        if (!isList()) throw new IllegalStateException("cons pair is not a list: " + this);
        return new ConsIterator();
    }

    public int size() {
        return size;
    }

    public String toString() {
        if (isList()) return Shen.vec(toList().stream().map(Numbers::maybeNumber)).toString();
        return "[" + Numbers.maybeNumber(car) + " | " + Numbers.maybeNumber(cdr) + "]";
    }

    public List<Object> toList() {
        return new ArrayList<Object>(this);
    }

    public static Collection toCons(List<?> list) {
        if (list.isEmpty()) return list;
        Cons cons = null;
        list = new ArrayList<>(list);
        reverse(list);
        for (Object o : list) {
            if (o instanceof List) o = toCons((List<?>) o);
            if (cons == null) cons = new Cons(o, EMPTY_LIST);
            else cons = new Cons(o, cons);
        }
        return cons;
    }

    class ConsIterator implements Iterator {
        Cons cons = Cons.this;

        public boolean hasNext() {
            return cons != null;
        }

        public Object next() {
            if (cons == null) throw new NoSuchElementException();
            try {
                if (!cons.isList()) return cons;
                return cons.car;
            } finally {
                cons = !cons.isList() || EMPTY_LIST.equals(cons.cdr) ? null : (Cons) cons.cdr;
            }
        }
    }
}