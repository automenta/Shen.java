package shen;

import java.io.IOException;
import java.io.Reader;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

public class KLReader {
    static Map<Object, Integer> lines = new IdentityHashMap<>();
    static int currentLine;

    static List<Object> read(Reader reader) throws Exception {
        lines.clear();
        currentLine = 1;
        return tokenizeAll(new Scanner(reader).useDelimiter("(\\s|\\)|\")"));
    }

    static Object tokenize(Scanner sc) throws Exception {
        whitespace(sc);
        if (find(sc, "\\(")) return tokenizeAll(sc);
        if (find(sc, "\"")) return nextString(sc);
        if (find(sc, "\\)")) return null;
        if (sc.hasNextBoolean()) return sc.nextBoolean();
        if (sc.hasNextLong()) return Numbers.integer(sc.nextLong());
        if (sc.hasNextDouble()) return Numbers.real(sc.nextDouble());
        if (sc.hasNext()) return Primitives.intern(sc.next());
        return null;
    }

    static void whitespace(Scanner sc) {
        sc.skip("[^\\S\\n]*");
        while (find(sc, "\\n")) {
            currentLine++;
            sc.skip("[^\\S\\n]*");
        }
    }

    static boolean find(Scanner sc, String pattern) {
        return sc.findWithinHorizon(pattern, 1) != null;
    }

    static Object nextString(Scanner sc) throws IOException {
        String s = sc.findWithinHorizon("(?s).*?\"", 0);
        currentLine += s.replaceAll("[^\n]", "").length();
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