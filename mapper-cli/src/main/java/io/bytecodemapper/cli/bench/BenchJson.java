// >>> AUTOGEN: BYTECODEMAPPER CLI BenchJson BEGIN
package io.bytecodemapper.cli.bench;

import java.io.IOException;
import java.io.Writer;
import java.util.*;

final class BenchJson {
    private BenchJson(){}

    static void write(Writer w, List<BenchMetrics> rows, double totalMs, double peakMB, Set<String> ablate) throws IOException {
        w.write("{\"version\":1,");
        w.write("\"totalMs\":" + fmt(totalMs) + ",");
        w.write("\"peakMB\":" + fmt(peakMB) + ",");
        w.write("\"ablate\":[");
        int i=0;
        for (String a : new TreeSet<String>(ablate)) {
            if (i++>0) w.write(",");
            w.write("\""+esc(a)+"\"");
        }
        w.write("],\"pairs\":[");
        for (int j=0;j<rows.size();j++){
            BenchMetrics m = rows.get(j);
            if (j>0) w.write(",");
            w.write("{");
            kv(w,"tag",m.tag,true);
            kv(w,"oldJar",m.oldJar,true);
            kv(w,"newJar",m.newJar,true);
            kvn(w,"acceptedMethods",m.acceptedMethods);
            kvn(w,"abstainedMethods",m.abstainedMethods);
            kvn(w,"acceptedClasses",m.acceptedClasses);
            kvn(w,"churnJaccard",m.churnJaccard);
            kvn(w,"osc3Coverage",m.osc3Coverage);
            kvn(w,"ambiguousPairF1",m.ambiguousPairF1);
            kvn(w,"ambiguousCount",m.ambiguousCount);
            kvn(w,"elapsedMs",m.elapsedMs);
            kvn(w,"usedMB",m.usedMB, false);
            w.write("}");
        }
        w.write("]}");
    }

    private static void kv(Writer w, String k, String v, boolean comma) throws IOException {
        w.write("\""+esc(k)+"\":\""+esc(v==null?"":v)+"\"");
        if (comma) w.write(",");
    }
    private static void kvn(Writer w, String k, Number v) throws IOException {
        kvn(w,k,v,true);
    }
    private static void kvn(Writer w, String k, Number v, boolean comma) throws IOException {
        w.write("\""+esc(k)+"\":");
        w.write(v==null? "null" : fmt(v.doubleValue()));
        if (comma) w.write(",");
    }
    private static String fmt(double d){ return String.format(java.util.Locale.ROOT,"%.3f", d); }
    private static String esc(String s){
        StringBuilder b=new StringBuilder(s.length()+8);
        for(char c:s.toCharArray()){
            switch(c){
                case '\\': b.append("\\\\"); break;
                case '"': b.append("\\\""); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\t': b.append("\\t"); break;
                default:
                    if (c < 0x20) b.append(String.format("\\u%04x",(int)c));
                    else b.append(c);
            }
        }
        return b.toString();
    }
}
// <<< AUTOGEN: BYTECODEMAPPER CLI BenchJson END
