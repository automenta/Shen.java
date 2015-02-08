package shen;

import java.io.IOException;
import java.io.Reader;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.regex.Pattern;

public class KLReader {
    static Map<Object, Integer> lines = new IdentityHashMap<>();
    static int currentLine;

    static List<Object> read(Reader reader) throws Exception {
        lines.clear();
        currentLine = 1;
        return tokenizeAll(new Scanner(reader).useDelimiter("(\\s|\\)|\")"));
    }

    final static Pattern t1 = Pattern.compile("\\(");
    final static Pattern t2 = Pattern.compile("\"");
    final static Pattern t3 = Pattern.compile("\\)");

    static Object tokenize(Scanner sc) throws Exception {
        whitespace(sc);
        if (find(sc, t1)) return tokenizeAll(sc);
        if (find(sc, t2)) return nextString(sc);
        if (find(sc, t3)) return null;
        if (sc.hasNextBoolean()) return sc.nextBoolean();
        if (sc.hasNextLong()) return Numbers.integer(sc.nextLong());
        if (sc.hasNextDouble()) return Numbers.real(sc.nextDouble());
        if (sc.hasNext()) return Primitives.intern(sc.next());
        return null;
    }

    final static Pattern whitespaceSkip = Pattern.compile("[^\\S\\n]*");
    final static Pattern newline = Pattern.compile("\\n");

    static void whitespace(Scanner sc) {
        sc.skip(whitespaceSkip);
        while (find(sc, newline)) {
            currentLine++;
            sc.skip(whitespaceSkip);
        }
    }

    @Deprecated static boolean find(Scanner sc, String pattern) {
        return sc.findWithinHorizon(pattern, 1) != null;
    }
    static boolean find(Scanner sc, Pattern pattern) {
        return sc.findWithinHorizon(pattern, 1) != null;
    }

    final static Pattern ns = Pattern.compile("(?s).*?\"");
    static Object nextString(Scanner sc) throws IOException {
        String s = sc.findWithinHorizon(ns, 0);
        currentLine += s.replaceAll("[^\n]", "").length();
        //        return Pattern.compile(regex).matcher(this).replaceAll(replacement);

        return s.substring(0, s.length() - 1);
    }

    static List<Object> tokenizeAll(Scanner sc) throws Exception {
        List<Object> list = Shen.list();
        lines.put(list, currentLine);
        Object x;
        while ((x = tokenize(sc)) != null) list.add(x);
        return list;
    }
}