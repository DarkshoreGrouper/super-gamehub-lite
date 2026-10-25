import com.xj.winemu.sidebar.SuperInputStats;

public final class SuperInputStatsTest {
    private static void contains(String value, String expected) {
        if (!value.contains(expected)) throw new AssertionError(expected + " missing from:\n" + value);
    }
    public static void main(String[] args) {
        SuperInputStats.reset();
        SuperInputStats.recordTouch(2,2);
        SuperInputStats.recordMouse(System.nanoTime(),0);
        contains(SuperInputStats.report(),"Touch move dispatches: 0");
        contains(SuperInputStats.report(),"Native mouse calls: 0");
        SuperInputStats.setEnabled(true);
        SuperInputStats.recordTouch(2,2);
        SuperInputStats.recordMouse(System.nanoTime(),0);
        SuperInputStats.recordMouse(System.nanoTime(),0);
        SuperInputStats.recordMouse(System.nanoTime(),4);
        SuperInputStats.recordMouse(System.nanoTime(),1);
        String report = SuperInputStats.report();
        contains(report,"Touch move dispatches: 1");
        contains(report,"Touch samples (history x pointers): 6");
        contains(report,"Native mouse calls: 4");
        contains(report,"Mouse movement calls: 2");
        contains(report,"Wheel calls: 1");
        contains(report,"Other mouse calls: 1");
        SuperInputStats.setEnabled(false);
        SuperInputStats.recordTouch(0,1);
        SuperInputStats.recordMouse(System.nanoTime(),0);
        contains(SuperInputStats.report(),"Native mouse calls: 4");
        contains(SuperInputStats.report(),"Touch move dispatches: 1");
        SuperInputStats.setEnabled(true);
        contains(SuperInputStats.report(),"Native mouse calls: 0");
        long beforeReset = System.nanoTime();
        SuperInputStats.reset();
        SuperInputStats.recordMouse(beforeReset,0);
        contains(SuperInputStats.report(),"Native mouse calls: 0");
        SuperInputStats.recordMouse(System.nanoTime(),0);
        contains(SuperInputStats.report(),"Native mouse calls: 1");
        SuperInputStats.setEnabled(false);
        SuperInputStats.reset();
        report = SuperInputStats.report();
        if (report.contains("NaN") || report.contains("Infinity")) throw new AssertionError(report);
        contains(report,"Session duration: 0.00 s");
        System.out.println("PASS: disabled path, historical touch samples, movement classification, stop/freeze, fresh sessions and reset during an in-flight native call.");
    }
}
