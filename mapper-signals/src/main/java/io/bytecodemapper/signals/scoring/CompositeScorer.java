package io.bytecodemapper.signals.scoring;

import io.bytecodemapper.signals.idf.IdfStore;
import io.bytecodemapper.signals.idf.Tfidf;
import io.bytecodemapper.signals.norm.NormalizedMethod;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Composite multi-signal scorer and deterministic greedy per-class assignment. */
public final class CompositeScorer {
  // Weights (literals required)
  public static final double W_CALLS=0.45, W_MICRO=0.25, W_OPCODE=0.15, W_STRINGS=0.10;
  public static final double TAU=0.60, MARGIN=0.05, ALPHA_MICRO=0.60;

  private CompositeScorer() {}

  /** Deterministic result container. Keys are stable ids per side. */
  public static final class Result {
    public final SortedMap<String,String> matches = new TreeMap<String,String>();
    public final SortedMap<String,Double> scores = new TreeMap<String,Double>();
    public byte[] toBytes(){
      StringBuilder sb=new StringBuilder();
      for (Map.Entry<String,String> e: matches.entrySet()){
        double s = scores.containsKey(e.getKey())? scores.get(e.getKey()) : 0.0;
        sb.append(e.getKey()).append("->").append(e.getValue()).append(':')
          .append(String.format(java.util.Locale.ROOT,"%.4f", s)).append('\n');
      }
      return sb.toString().getBytes(StandardCharsets.UTF_8);
    }
  }

  public static double scoreTotal(NormalizedMethod a, NormalizedMethod b, IdfStore idf, Map<String,String> ownerMap){
    double sCalls   = Tfidf.cosineCalls(remapCalls(a.callBag(), ownerMap), remapCalls(b.callBag(), ownerMap), idf);
    double sStr     = Tfidf.cosineStrings(a.stringBag(), b.stringBag(), idf);
    double sOpcode  = cosineHist(a.opcodeHistogram(), b.opcodeHistogram());
    double sMicro   = microBlend(a.microMask(), b.microMask());
    double total = W_CALLS*sCalls + W_MICRO*sMicro + W_OPCODE*sOpcode + W_STRINGS*sStr;
    // Minimal smart filter: leaf vs non-leaf penalty
    boolean leafA = (a.microMask() & 1) != 0, leafB = (b.microMask() & 1) != 0;
    if (leafA != leafB) total = Math.max(0.0, total - 0.05);
    return Math.max(0.0, Math.min(1.0, total));
  }

  public static Result assignPerClass(List<NormalizedMethod> oldMs, List<NormalizedMethod> newMs, IdfStore idf, Map<String,String> ownerMap){
    List<NormalizedMethod> olds = new ArrayList<NormalizedMethod>(oldMs);
    List<NormalizedMethod> news = new ArrayList<NormalizedMethod>(newMs);
    class Cand { String oid,nid; double s; Cand(String o,String n,double s){this.oid=o;this.nid=n;this.s=s;} }
    List<Cand> eligibles = new ArrayList<Cand>();
    // Stable ids (use fingerprint if canonical owner#name(desc) unavailable)
    SortedMap<String,NormalizedMethod> oMap = new TreeMap<String,NormalizedMethod>();
    SortedMap<String,NormalizedMethod> nMap = new TreeMap<String,NormalizedMethod>();
    for (NormalizedMethod m: olds) oMap.put("old#"+m.fingerprintSha256(), m);
    for (NormalizedMethod m: news) nMap.put("new#"+m.fingerprintSha256(), m);
    // Per old: find best and second, then gate by TAU/MARGIN
    for (Map.Entry<String,NormalizedMethod> eo : oMap.entrySet()){
      String oid = eo.getKey(); NormalizedMethod om = eo.getValue();
      String bestN=null; double bestS=-1, secondS=-1; String secondN=null;
      for (Map.Entry<String,NormalizedMethod> en : nMap.entrySet()){
        double s = scoreTotal(om, en.getValue(), idf, ownerMap);
        if (s>bestS || (s==bestS && (bestN==null || en.getKey().compareTo(bestN)<0))) {
          secondS=bestS; secondN=bestN; bestS=s; bestN=en.getKey();
        } else if (s>secondS || (s==secondS && (secondN==null || en.getKey().compareTo(secondN)<0))) {
          secondS=s; secondN=en.getKey();
        }
      }
      if (bestN!=null){
        double margin = (secondN==null)? bestS : (bestS-secondS);
        if (bestS>=TAU && margin>=MARGIN) eligibles.add(new Cand(oid,bestN,bestS));
      }
    }
    // Greedy deterministic selection: sort by (score desc, oid asc, nid asc)
    Collections.sort(eligibles, new Comparator<Cand>(){
      public int compare(Cand a, Cand b){
        int c = Double.compare(b.s, a.s); if (c!=0) return c;
        c = a.oid.compareTo(b.oid); if (c!=0) return c;
        return a.nid.compareTo(b.nid);
      }
    });
    Result r = new Result();
    Set<String> takenO = new HashSet<String>(), takenN = new HashSet<String>();
    for (Cand c: eligibles){
      if (takenO.contains(c.oid) || takenN.contains(c.nid)) continue;
      r.matches.put(c.oid, c.nid);
      r.scores.put(c.oid, c.s);
      takenO.add(c.oid); takenN.add(c.nid);
    }
    return r;
  }

  private static SortedMap<String,Integer> remapCalls(SortedMap<String,Integer> bag, Map<String,String> map){
    SortedMap<String,Integer> out = new TreeMap<String,Integer>();
    for (Map.Entry<String,Integer> e: bag.entrySet()){
      // key format owner#name(desc)
      String k = e.getKey();
      int idx = k.indexOf('#');
      String owner = idx>=0? k.substring(0, idx) : k;
      String rest = idx>=0? k.substring(idx) : "";
      String norm = (map!=null && map.containsKey(owner))? (map.get(owner)+rest) : k;
      Integer prev = out.get(norm);
      out.put(norm, (prev==null? e.getValue() : prev.intValue()+e.getValue()));
    }
    return out;
  }
  private static double cosineHist(int[] a, int[] b){
    long dot=0; long na=0; long nb=0;
    int len = Math.min(a.length, b.length);
    for (int i=0;i<len;i++){ int x=a[i], y=b[i]; dot += (long)x*y; na += (long)x*x; nb += (long)y*y; }
    if (na==0 || nb==0) return 0.0;
    return dot / (Math.sqrt(na)*Math.sqrt(nb));
  }
  private static double microBlend(int ma, int mb){
    // Jaccard
    int ia = Integer.bitCount(ma & mb);
    int uu = Integer.bitCount(ma | mb);
    double j = (uu==0)? 1.0 : ((double)ia)/uu;
    // Cosine on 0/1 vectors
    int ca = Integer.bitCount(ma), cb = Integer.bitCount(mb);
    double cos = (ca==0 || cb==0)? 0.0 : (ia / (Math.sqrt((double)ca)*Math.sqrt((double)cb)));
    return ALPHA_MICRO*j + (1.0-ALPHA_MICRO)*cos;
  }
}
