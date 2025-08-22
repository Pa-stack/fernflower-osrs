package io.bytecodemapper.signals.idf;

import java.util.*;

/** TF-IDF utilities (raw TF, EMA-backed IDF, cosine in double precision). */
public final class Tfidf {
  private Tfidf() {}
  public static double cosineCalls(SortedMap<String,Integer> bagA, SortedMap<String,Integer> bagB, IdfStore idf) {
    return cosine(weight(bagA, idf, "call."), weight(bagB, idf, "call."));
  }
  public static double cosineStrings(SortedMap<String,Integer> bagA, SortedMap<String,Integer> bagB, IdfStore idf) {
    return cosine(weight(bagA, idf, "str."), weight(bagB, idf, "str."));
  }
  private static SortedMap<String,Double> weight(SortedMap<String,Integer> bag, IdfStore idf, String prefix){
    SortedMap<String,Double> w = new TreeMap<String,Double>();
    for (Map.Entry<String,Integer> e : bag.entrySet()) {
      String k = prefix + e.getKey();
      double tf = e.getValue();
      double idfv = idf.get(k, 1.0);
      w.put(k, tf * idfv);
    }
    return w;
  }
  private static double cosine(SortedMap<String,Double> a, SortedMap<String,Double> b){
    Iterator<Map.Entry<String,Double>> ia=a.entrySet().iterator(), ib=b.entrySet().iterator();
    Map.Entry<String,Double> ea=ia.hasNext()?ia.next():null, eb=ib.hasNext()?ib.next():null;
    double dot=0, na=0, nb=0;
    while (ea!=null || eb!=null){
      int cmp = (ea==null)?1 : (eb==null? -1 : ea.getKey().compareTo(eb.getKey()));
      if (cmp==0){ dot += ea.getValue()*eb.getValue(); na += ea.getValue()*ea.getValue(); nb += eb.getValue()*eb.getValue(); ea=ia.hasNext()?ia.next():null; eb=ib.hasNext()?ib.next():null; }
      else if (cmp<0){ na += ea.getValue()*ea.getValue(); ea=ia.hasNext()?ia.next():null; }
      else { nb += eb.getValue()*eb.getValue(); eb=ib.hasNext()?ib.next():null; }
    }
    if (na==0 || nb==0) return 0.0;
    return dot / (Math.sqrt(na)*Math.sqrt(nb));
  }
}
