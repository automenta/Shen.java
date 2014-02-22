package shen;

import org.junit.Test;

import java.util.LinkedList;
import java.util.List;

import static java.lang.System.out;
import static java.util.Arrays.asList;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.Assert.assertThat;

// These are the main methods from the interpreter and compiler, no structure or niceness.
// Tests lots of random stuff, written while developing, most this should be covered in PrimitivesTest.
// Both run against the compiler, as the interpreter has been removed.
public class SmokeTest {
    @Test
    public void interpreter() throws Throwable {
        out.println(Primitives.eval_kl(Primitives.intern("x")));
        out.println(Shen.eval("(or false)"));
        out.println(Shen.eval("(or false false)"));
        out.println(Shen.eval("(or false true)"));
        out.println(Shen.eval("(or false false false)"));
        out.println(Shen.eval("((or false) true)"));
        out.println(Shen.eval("()"));
        out.println(Shen.eval("(cons 2 3)"));

        out.println(Shen.eval("(absvector? (absvector 10))"));
        out.println(Shen.eval("(absvector 10)"));
        out.println(Shen.eval("(absvector? ())"));
        out.println(Shen.eval("(+ 1 2)"));
        out.println(Shen.eval("((+ 6.5) 2.0)"));
        out.println(Shen.eval("(+ 1.0 2.0)"));
        out.println(Shen.eval("(* 5 2)"));
        out.println(Shen.eval("(* 5)"));
        out.println(Shen.eval("(let x 42 x)"));
        out.println(Shen.eval("(let x 42 (let y 2 (cons x y)))"));
        out.println(Shen.eval("((lambda x (lambda y (cons x y))) 2 3)"));
        out.println(Shen.eval("((lambda x (lambda y (cons x y))) 2)"));
        out.println(Shen.eval("((let x 3 (lambda y (cons x y))) 2)"));
        out.println(Shen.eval("(cond (true 1))"));
        out.println(Shen.eval("(cond (false 1) ((> 10 3) 3))"));
        out.println(Shen.eval("(cond (false 1) ((> 10 3) ()))"));

        out.println(Shen.eval("(defun fib (n) (if (<= n 1) n (+ (fib (- n 1)) (fib (- n 2)))))"));
        out.println(Shen.eval("(fib 10)"));

        out.println(Shen.eval("(defun factorial (cnt acc) (if (= 0 cnt) acc (factorial (- cnt 1) (* acc cnt)))"));
        out.println(Shen.eval("(factorial 10 1)"));
        out.println(Shen.eval("(factorial 12)"));
        out.println(Shen.eval("((factorial 19) 1)"));

        out.println(Primitives.eval_kl(asList(Primitives.intern("lambda"), Primitives.intern("x"), Primitives.intern("x"))));
        out.println(Primitives.eval_kl(asList(Primitives.intern("defun"), Primitives.intern("my-fun"), asList(Primitives.intern("x")), Primitives.intern("x"))));
        out.println(Primitives.str(Primitives.eval_kl(asList(Primitives.intern("my-fun"), 3L))));
        out.println(Primitives.eval_kl(asList(Primitives.intern("defun"), Primitives.intern("my-fun2"), asList(Primitives.intern("x"), Primitives.intern("y")), asList(Primitives.intern("cons"), Primitives.intern("y"), asList(Primitives.intern("cons"), Primitives.intern("x"), new LinkedList())))));
        out.println(Primitives.eval_kl(asList(Primitives.intern("my-fun2"), 3L, 5L)));
        out.println(Primitives.eval_kl(asList(Primitives.intern("defun"), Primitives.intern("my-fun3"), asList(), "Hello")));
        out.println(Primitives.str(Primitives.eval_kl(asList(Primitives.intern("my-fun3")))));
    }

    @Test
    public void compiler() throws Throwable {
        out.println(Shen.eval("(trap-error my-symbol my-handler)"));
        out.println(Shen.eval("(trap-error (simple-error \"!\") (lambda x x))"));
        out.println(Shen.eval("(if true \"true\" \"false\")"));
        out.println(Shen.eval("(if false \"true\" \"false\")"));
        out.println(Shen.eval("(cond (false 1) (true 2))"));
        out.println(Shen.eval("(cond (false 1) ((or true false) 3))"));
        out.println(Shen.eval("(or false)"));
        out.println(Shen.eval("((or false) false)"));
        out.println(Shen.eval("(or false false)"));
        out.println(Shen.eval("(or false true false)"));
        out.println(Shen.eval("(and true true)"));
        out.println(Shen.eval("(and true true (or false false))"));
        out.println(Shen.eval("(and true false)"));
        out.println(Shen.eval("(and true)"));
        out.println(Shen.eval("(lambda x x)"));
        out.println(Shen.eval("((lambda x x) 2)"));
        out.println(Shen.eval("(let x \"str\" x)"));
        out.println(Shen.eval("(let x 10 x)"));
        out.println(Shen.eval("(let x 10 (let y 5 x))"));
        out.println(Shen.eval("((let x 42 (lambda y x)) 0)"));
        out.println(Shen.eval("((lambda x ((lambda y x) 42)) 0)"));
        out.println(Shen.eval("(get-time unix)"));
        out.println(Shen.eval("(value *language*)"));
        out.println(Shen.eval("(+ 1 1)"));
        out.println(Shen.eval("(+ 1.2 1.1)"));
        out.println(Shen.eval("(+ 1.2 1)"));
        out.println(Shen.eval("(+ 1 1.3)"));
        out.println(Shen.eval("(cons x y)"));
        out.println(Shen.eval("(cons x)"));
        out.println(Shen.eval("((cons x) z)"));
        out.println(Shen.eval("(cons x y)"));
        out.println(Shen.eval("(absvector? (absvector 10))"));
        out.println(Shen.eval("(trap-error (/ 1 0) (lambda x x))"));
        out.println(Shen.eval("(defun fun (x y) (+ x y))"));
        out.println(Shen.eval("(defun fun2 () (fun 1 2))"));
        out.println(Shen.eval("(fun2)"));
        out.println(Shen.eval("(defun fun (x y) (- x y))"));
        out.println(Shen.eval("(fun2)"));
        out.println(Shen.eval("(fun 1 2)"));
        out.println(Shen.eval("(set x y)"));
        out.println(Shen.eval("(value x)"));
        out.println(Shen.eval("(set x z)"));
        out.println(Shen.eval("(value x)"));
        out.println(Shen.eval("()"));
        out.println(Shen.eval("(cond (true ()) (false 2))"));
        out.println(Shen.eval("(if (<= 3 3) x y)"));
        out.println(Shen.eval("(eval-kl (cons + (cons 1 (cons 2 ()))))"));
    }

    /*
        This tests a function which is recursive and which uses the let keyword. e.g.

            (define funcLetAndRecurse
              X -> (let Z (- X 1)
                     (if (= Z 1) (* 3 X) (funcLetAndRecurse Z))
                   )
            )

        This function returns 6

        In the function below we use the Klambda code which is :

        (defun funcLetAndRecurse (V503) (let Z (- V503 1) (if (= Z 1) (* 3 V503) (funcLetAndRecurse Z))))
    */
    @Test
    public void other() throws Throwable {
        String funcDef1 = "(defun funcLetAndRecurse (V503) (let Z (- V503 1) (if (= Z 1) (* 3 V503) (funcLetAndRecurse Z))))";
        String funcCall = "(funcLetAndRecurse 10)";

        //tests  that let and recurse works fine when combined together
        Shen.eval(funcDef1);
        is(6L, funcCall);

        //tests that second call gives same answer as first
        is(6L, funcCall);

        //this tests that redefinition works
        String funcDef2 = " (defun funcLetAndRecurse (V503) (let Z (- V503 1) (if (= Z 1) (* 2 V503) (funcLetAndRecurse Z))))";
        Shen.eval(funcDef2);
        is(4L, funcCall);
    }

    void is(Object expected, String actual) {
        Object 神 = 神(actual);
        if (expected instanceof Class)
            if (expected == Double.class) assertThat(Numbers.isInteger((Long) 神), equalTo(false));
            else assertThat(神, instanceOf((Class<?>) expected));
        else if (神 instanceof Long)
            assertThat(Numbers.asNumber((Long) 神), equalTo(expected));
        else if (神 instanceof Cons && expected instanceof List)
            assertThat(((Cons) 神).toList(), equalTo(expected));
        else
            assertThat(神, equalTo(expected));
    }

    Object 神(String shen) {
        try {
            return Shen.eval(shen);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }
}
