package io.bytecodemapper.signals.idf;

import org.junit.Test;

import java.util.SortedMap;
import java.util.TreeMap;

import static org.junit.Assert.*;

public class TfidfTest {
  @Test public void cosineMonotonicAndEdges(){
    IdfStore idf = IdfStore.createDefault();
    // weight calls higher idf to check scaling doesn't break cos
    idf.put("call.a", 2.0);
    idf.put("call.b", 1.0);
    idf.put("str.s", 1.5);

    SortedMap<String,Integer> A = new TreeMap<String,Integer>();
    SortedMap<String,Integer> B = new TreeMap<String,Integer>();
    A.put("a", 2); A.put("b", 1);
    B.put("a", 1); B.put("b", 0);

    double c1 = Tfidf.cosineCalls(A, B, idf);
    double c2 = Tfidf.cosineCalls(A, A, idf);
    assertTrue(c2 >= c1 - 1e-9);
    assertTrue(c2 <= 1.0 + 1e-12);

    SortedMap<String,Integer> Z = new TreeMap<String,Integer>();
    assertEquals(0.0, Tfidf.cosineCalls(Z, A, idf), 0.0);
    assertEquals(0.0, Tfidf.cosineStrings(Z, Z, idf), 0.0);

    // strings path smoke
    SortedMap<String,Integer> S1 = new TreeMap<String,Integer>();
    SortedMap<String,Integer> S2 = new TreeMap<String,Integer>();
    S1.put("s", 2);
    S2.put("s", 4);
    double cs = Tfidf.cosineStrings(S1, S2, idf);
    assertTrue(cs > 0.0 && cs <= 1.0);

    System.out.println("ACCEPT TFIDF c1=" + String.format(java.util.Locale.ROOT, "%.4f", c1));
    System.out.println("ACCEPT TFIDF c2=" + String.format(java.util.Locale.ROOT, "%.4f", c2));
    System.out.println("ACCEPT TFIDF cs=" + String.format(java.util.Locale.ROOT, "%.4f", cs));
  }
  @Test public void cosineNumericalStability_largeTF(){
    IdfStore s = IdfStore.createDefault();
    s.put("call.k#x()V", 1.0);
    java.util.SortedMap<String,Integer> a = new java.util.TreeMap<String,Integer>();
    java.util.SortedMap<String,Integer> b = new java.util.TreeMap<String,Integer>();
    a.put("k#x()V", 1_000_000);
    b.put("k#x()V", 1_000_001);
    double c = Tfidf.cosineCalls(a, b, s);
    System.out.println("cos.calls.large=" + String.format(java.util.Locale.ROOT, "%.4f", c));
    org.junit.Assert.assertTrue(c >= 0.0 && c <= 1.0);
  }
}
