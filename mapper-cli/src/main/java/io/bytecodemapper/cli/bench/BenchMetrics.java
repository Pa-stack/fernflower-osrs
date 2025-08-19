// >>> AUTOGEN: BYTECODEMAPPER CLI BenchMetrics BEGIN
package io.bytecodemapper.cli.bench;

public final class BenchMetrics {
    public String tag;
    public String oldJar;
    public String newJar;

    public Integer acceptedMethods;
    public Integer abstainedMethods;
    public Integer acceptedClasses;

    public Double churnJaccard;   // vs previous pair (coverage on middle jar)
    public Double osc3Coverage;   // symmetric-diff rate across middle jar coverage
    public Double ambiguousPairF1; // null (requires ground truth)
    public Integer ambiguousCount;

    public Double elapsedMs;
    public Double usedMB;
}
// <<< AUTOGEN: BYTECODEMAPPER CLI BenchMetrics END
