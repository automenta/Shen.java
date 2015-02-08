package shen;

import org.junit.Ignore;
import org.junit.Test;

public class TestProgramsTest {
    @Test
    public void test_programs() throws Throwable {
        Shen.install();
        Shen.eval("(cd \"shen/Test Programs\")");
        Shen.eval("(load \"README.shen\")");
        Shen.eval("(load \"tests.shen\")");
    }

    public static void main(String... args) throws Throwable {
        new TestProgramsTest().test_programs();
    }
}
