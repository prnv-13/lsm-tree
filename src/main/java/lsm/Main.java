package lsm;

public class Main {

    public static void main(String[] args) {
        LSMTree<Integer, String> tree = new LSMTree<>(2);

        tree.insert(5, "a");
        tree.insert(3, "b");
        tree.delete(5);
        check("select(5)", tree.select(5), null);

        tree.insert(7, "c");
        check("select(3)", tree.select(3), "b");
        check("select(5)", tree.select(5), null);

        tree.insert(5, "d");
        check("select(5)", tree.select(5), "d");
        check("select(42)", tree.select(42), null);
    }

    private static void check(String label, String actual, String expected) {
        boolean ok = (expected == null) ? actual == null : expected.equals(actual);
        System.out.printf("%-12s expected=%-6s actual=%-6s %s%n", label, expected, actual, ok ? "OK" : "FAIL");
    }
}
