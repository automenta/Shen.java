package shen;

import org.junit.Ignore;
import org.junit.Test;

public class BenchmarksTest {
    @Test @Ignore
    public void benchmarks() throws Throwable {
        Shen.install();
        Shen.eval("(cd \"shen/benchmarks\")");
        Shen.eval("(load \"README.shen\")");
        Shen.eval("(load \"benchmarks.shen\")");
    }

    public static void main(String... args) throws Throwable {
        new BenchmarksTest().benchmarks();
    }
}
