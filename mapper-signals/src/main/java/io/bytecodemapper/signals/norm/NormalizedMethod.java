package io.bytecodemapper.signals.norm;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Bytecode-level normalization and feature extraction for methods (Java 8, ASM 7.3.1). */
public final class NormalizedMethod {
  /** Fixed 17-bit ABI order. */
  public static final String[] MICRO_BITS = {
      "LEAF","SELF_RECURSIVE","THROWS_EXCEPTION","READS_ARRAY","WRITES_ARRAY",
      "HAS_LOOP","HAS_SWITCH","ALLOC_OBJ","ALLOC_ARRAY","WRITES_FIELD",
      "READS_FIELD","RETURNS_CONSTANT","IS_CONSTRUCTOR","RETURNS_VOID",
      "IS_LARGE","SYNCHRONIZED","HAS_TRYCATCH"
  };

  private final int[] hist = new int[256];
  private final Map<Integer,Integer> n2 = new LinkedHashMap<Integer,Integer>();
  private final Map<Integer,Integer> n3 = new LinkedHashMap<Integer,Integer>();
  private final SortedMap<String,Integer> calls = new TreeMap<String,Integer>();
  private final SortedMap<String,Integer> strings = new TreeMap<String,Integer>();
  private final int mask;
  private final String owner;
  private final String name;
  private final String desc;
  private final String fp;

  private NormalizedMethod(int mask, String owner, String name, String desc, String fp) {
    this.mask = mask; this.owner = owner; this.name = name; this.desc = desc; this.fp = fp;
  }

  public static NormalizedMethod from(String owner, MethodNode mn) {
    // Collect real opcodes only
    final List<AbstractInsnNode> ops = new ArrayList<AbstractInsnNode>();
    for (AbstractInsnNode in = mn.instructions.getFirst(); in != null; in = in.getNext()) {
      int op = in.getOpcode(); if (op >= 0) ops.add(in);
    }
    // Exclude guard: first 8 real ops, constant → *RETURN pairs
    final Set<Integer> skipIdx = new HashSet<Integer>();
    for (int i = 0; i < Math.min(ops.size(), 8) - 1; i++) {
      int o = ops.get(i).getOpcode(), r = ops.get(i + 1).getOpcode();
      if (isConst(o) && isReturn(r)) { skipIdx.add(i); skipIdx.add(i + 1); }
    }
    // Unwrap RuntimeException rethrow: drop ATHROW and do not mark HAS_TRYCATCH
    boolean hasTryCatch = mn.tryCatchBlocks != null && !mn.tryCatchBlocks.isEmpty();
    if (hasTryCatch) {
      for (int i = 0; i < mn.tryCatchBlocks.size(); i++) {
        TryCatchBlockNode tcb = (TryCatchBlockNode) mn.tryCatchBlocks.get(i);
        if ("java/lang/RuntimeException".equals(tcb.type)) {
          for (int j = 0; j < ops.size(); j++) if (ops.get(j).getOpcode() == Opcodes.ATHROW) skipIdx.add(j);
          hasTryCatch = false; // neutralize for HAS_TRYCATCH bit
          break;
        }
      }
    }
    // Collect opcodes after skips
    final List<Integer> seq = new ArrayList<Integer>();
    for (int i = 0; i < ops.size(); i++) if (!skipIdx.contains(i)) {
      int op = ops.get(i).getOpcode();
      if (op >= 0 && op < 256) seq.add(op);
    }
    // Histogram
    final int[] hist = new int[256];
    for (int op : seq) hist[op]++;
    // N-grams (2,3)
    final Map<Integer,Integer> n2 = new LinkedHashMap<Integer,Integer>();
    final Map<Integer,Integer> n3 = new LinkedHashMap<Integer,Integer>();
    for (int i = 0; i + 1 < seq.size(); i++) {
      int k = (seq.get(i) << 8) | seq.get(i + 1);
      n2.put(k, n2.getOrDefault(k, 0) + 1);
    }
    for (int i = 0; i + 2 < seq.size(); i++) {
      int k = (seq.get(i) << 16) | (seq.get(i + 1) << 8) | seq.get(i + 2);
      n3.put(k, n3.getOrDefault(k, 0) + 1);
    }
    // Bags: calls & strings
    final SortedMap<String,Integer> calls = new TreeMap<String,Integer>();
    final SortedMap<String,Integer> strings = new TreeMap<String,Integer>();
    boolean anyInvoke = false, selfRec = false;
    for (int i = 0; i < ops.size(); i++) {
      AbstractInsnNode in = ops.get(i);
      int op = in.getOpcode();
      if (in instanceof MethodInsnNode) {
        MethodInsnNode m = (MethodInsnNode) in;
        anyInvoke = true;
        if (owner.equals(m.owner)) selfRec = true;
        if (!(m.owner.startsWith("java/") || m.owner.startsWith("javax/"))) {
          String sig = m.owner + "#" + m.name + m.desc;
          calls.put(sig, calls.getOrDefault(sig, 0) + 1);
        }
      } else if (op == Opcodes.INVOKEDYNAMIC) {
        anyInvoke = true;
      } else if (in instanceof LdcInsnNode) {
        Object c = ((LdcInsnNode) in).cst;
        if (c instanceof String) {
          String s = (String) c;
          if (!s.matches(".*(Exception|StackTrace|Method|Class).*"))
            strings.put(s, strings.getOrDefault(s, 0) + 1);
        }
      }
    }
    // Micropatterns
    int mask = 0;
    if (!anyInvoke) mask |= 1; // LEAF
    if (selfRec) mask |= 1 << 1; // SELF_RECURSIVE
    if (contains(seq, Opcodes.ATHROW)) mask |= 1 << 2; // THROWS_EXCEPTION
    if (containsAny(seq, Opcodes.IALOAD,Opcodes.LALOAD,Opcodes.FALOAD,Opcodes.DALOAD,Opcodes.AALOAD,Opcodes.BALOAD,Opcodes.CALOAD,Opcodes.SALOAD)) mask |= 1 << 3; // READS_ARRAY
    if (containsAny(seq, Opcodes.IASTORE,Opcodes.LASTORE,Opcodes.FASTORE,Opcodes.DASTORE,Opcodes.AASTORE,Opcodes.BASTORE,Opcodes.CASTORE,Opcodes.SASTORE)) mask |= 1 << 4; // WRITES_ARRAY
    if (hasBackEdge(ops, skipIdx)) mask |= 1 << 5; // HAS_LOOP
    if (containsAny(seq, Opcodes.TABLESWITCH, Opcodes.LOOKUPSWITCH)) mask |= 1 << 6; // HAS_SWITCH
    if (contains(seq, Opcodes.NEW)) mask |= 1 << 7; // ALLOC_OBJ
    if (containsAny(seq, Opcodes.NEWARRAY, Opcodes.ANEWARRAY, Opcodes.MULTIANEWARRAY)) mask |= 1 << 8; // ALLOC_ARRAY
    if (containsAny(seq, Opcodes.PUTFIELD, Opcodes.PUTSTATIC)) mask |= 1 << 9; // WRITES_FIELD
    if (containsAny(seq, Opcodes.GETFIELD, Opcodes.GETSTATIC)) mask |= 1 << 10; // READS_FIELD
    if (returnsConstant(ops, skipIdx)) mask |= 1 << 11; // RETURNS_CONSTANT
    if ("<init>".equals(mn.name)) mask |= 1 << 12; // IS_CONSTRUCTOR
    if (mn.desc.endsWith(")V")) mask |= 1 << 13; // RETURNS_VOID
    if (seq.size() >= 50) mask |= 1 << 14; // IS_LARGE
    if ((mn.access & Opcodes.ACC_SYNCHRONIZED) != 0 || containsAny(seq, Opcodes.MONITORENTER, Opcodes.MONITOREXIT)) mask |= 1 << 15; // SYNCHRONIZED
    if (hasTryCatch) mask |= 1 << 16; // HAS_TRYCATCH

    // Fingerprint over canonical string
    String fp = sha256(hexCanon(owner, mn.desc, calls, strings, hist, mask));
  NormalizedMethod nm = new NormalizedMethod(mask, owner, mn.name, mn.desc, fp);
    System.arraycopy(hist, 0, nm.hist, 0, 256);
    nm.n2.putAll(n2); nm.n3.putAll(n3);
    nm.calls.putAll(calls); nm.strings.putAll(strings);
    return nm;
  }

  private static boolean contains(List<Integer> seq, int op) {
    for (int x : seq) if (x == op) return true; return false;
  }
  private static boolean containsAny(List<Integer> seq, int... ops){
    for(int o:ops) if(contains(seq,o)) return true; return false;
  }
  private static boolean isConst(int op){
    return (op>=Opcodes.ICONST_M1 && op<=Opcodes.DCONST_1) || op==Opcodes.LDC || op==Opcodes.ACONST_NULL || op==Opcodes.BIPUSH || op==Opcodes.SIPUSH;
  }
  private static boolean isReturn(int op){
    return op==Opcodes.IRETURN||op==Opcodes.ARETURN||op==Opcodes.FRETURN||op==Opcodes.DRETURN||op==Opcodes.LRETURN||op==Opcodes.RETURN;
  }
  private static boolean returnsConstant(List<AbstractInsnNode> ops, Set<Integer> skip){
    for(int i=0;i<ops.size()-1;i++){
      if(skip.contains(i) || skip.contains(i+1)) continue;
      int a=ops.get(i).getOpcode(), b=ops.get(i+1).getOpcode();
      if(isConst(a) && isReturn(b)) return true;
    }
    return false;
  }
  private static boolean hasBackEdge(List<AbstractInsnNode> ops, Set<Integer> skip){
    if (ops.isEmpty()) return false;
    Map<LabelNode,Integer> pos = new IdentityHashMap<LabelNode,Integer>();
    int idx=0;
    for(AbstractInsnNode in = ops.get(0); in != null; in = in.getNext()){
      if(in instanceof LabelNode) pos.put((LabelNode)in, idx);
      if(in.getOpcode()>=0) idx++;
    }
    int cur=0;
    for(int i=0;i<ops.size();i++){
      AbstractInsnNode in = ops.get(i);
      int op = in.getOpcode();
      if(op>=0){
        if(in instanceof JumpInsnNode){ LabelNode l=((JumpInsnNode)in).label; Integer p=pos.get(l); if(p!=null && p<cur && !skip.contains(i)) return true; }
        cur++;
      }
    }
    return false;
  }
  private static String sha256(String s){
    try{
      MessageDigest md=MessageDigest.getInstance("SHA-256");
      byte[] d=md.digest(s.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb=new StringBuilder(d.length*2);
      for(byte b:d) sb.append(String.format("%02x", b));
      return sb.toString();
    }catch(Exception e){ throw new RuntimeException(e); }
  }
  private static String hexCanon(String owner, String desc, SortedMap<String,Integer> calls, SortedMap<String,Integer> strings, int[] hist, int mask){
    StringBuilder sb=new StringBuilder();
    sb.append("owner=").append(owner).append('\n');
    sb.append("desc=").append(desc).append('\n');
    for(Map.Entry<String,Integer> e: calls.entrySet()) sb.append("call=").append(e.getKey()).append(':').append(e.getValue()).append('\n');
    for(Map.Entry<String,Integer> e: strings.entrySet()) sb.append("str=").append(e.getKey()).append(':').append(e.getValue()).append('\n');
    for(int i=0;i<256;i++) if(hist[i]!=0) sb.append("op=").append(i).append(':').append(hist[i]).append('\n');
    sb.append("bits=").append(mask).append('\n');
    return sb.toString();
  }

  // Accessors
  public int[] opcodeHistogram(){ return Arrays.copyOf(hist, hist.length); }
  public Map<Integer,Integer> ngrams2(){ return new LinkedHashMap<Integer,Integer>(n2); }
  public Map<Integer,Integer> ngrams3(){ return new LinkedHashMap<Integer,Integer>(n3); }
  public SortedMap<String,Integer> callBag(){ return new TreeMap<String,Integer>(calls); }
  public SortedMap<String,Integer> stringBag(){ return new TreeMap<String,Integer>(strings); }
  public int microMask(){ return mask; }
  /** Deterministic self signature owner#name(desc). */
  public String selfSignature(){ return owner + "#" + name + desc; }
  public String normalizedDescriptor(){ return desc; }
  public String fingerprintSha256(){ return fp; }
}
