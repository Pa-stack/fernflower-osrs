// >>> AUTOGEN: BYTECODEMAPPER CLI TestHarness BEGIN
package io.bytecodemapper.cli;

public final class TestHarness {
    /** Executes Main without System.exit; Main should route to pure run methods. */
    public static int runMain(String argsLine) {
        try {
            String trimmed = argsLine == null ? "" : argsLine.trim();
            String[] parts = trimmed.isEmpty() ? new String[0] : trimmed.split("\\s+");
            return io.bytecodemapper.cli.Router.dispatch(parts);
        } catch (Throwable t) {
            t.printStackTrace();
            return 1;
        }
    }
    private TestHarness(){}
}
// >>> AUTOGEN: BYTECODEMAPPER CLI TestHarness END
