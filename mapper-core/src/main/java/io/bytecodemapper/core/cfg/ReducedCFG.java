package io.bytecodemapper.core.cfg;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.*;

/** Deterministic reduced CFG built directly from MethodNode; no external deps. */
public final class ReducedCFG implements Opcodes {
    // Minimal option types to keep compatibility with fingerprints and callers
    public enum ExceptionEdgePolicy { LOOSE, STRICT }
    public static final class Options { public ExceptionEdgePolicy exceptionEdgesPolicy = ExceptionEdgePolicy.LOOSE; public boolean mergeLinearChains = true; public static Options defaults(){ return new Options(); } }
    public static final class Block {
        public final int id; public final int startIdx; public int endIdx; public final boolean handlerStart; // canonical
        // Back-compat alias used in some tests (field and accessor)
        public final boolean isHandlerStart; // alias of handlerStart
        public boolean isHandlerStart(){ return handlerStart; }
        private int[] preds = new int[0], succs = new int[0];
        Block(int id,int s,int e,boolean h){this.id=id;this.startIdx=s;this.endIdx=e;this.handlerStart=h; this.isHandlerStart=h;}
        public int[] preds(){return preds;} public int[] succs(){return succs;}
    }
    private final MethodNode method; private final Map<Integer,Block> blocks = new HashMap<Integer,Block>();
    private ReducedCFG(MethodNode m){this.method=m;}
    public MethodNode method(){return method;}
    public Collection<Block> blocks(){List<Block> list=new ArrayList<Block>(blocks.values()); list.sort(new Comparator<Block>(){public int compare(Block a,Block b){return a.id-b.id;}}); return list;}
    public Block block(int id){return blocks.get(id);} public int[] allBlockIds(){int n=blocks.size(),i=0; int[] a=new int[n]; for(Block b:blocks()) a[i++]=b.id; return a;}

    public static ReducedCFG build(MethodNode mn){ReducedCFG cfg=new ReducedCFG(mn); cfg.buildLoose(); cfg.dropUnreachable(); cfg.mergeLinear(); cfg.sortEdges(); return cfg;}
    public static ReducedCFG from(MethodNode mn){return build(mn);} // alias

    private void buildLoose(){InsnList ins=method.instructions; IdentityHashMap<AbstractInsnNode,Integer> idx=new IdentityHashMap<AbstractInsnNode,Integer>(); int N=0; for(AbstractInsnNode p=ins.getFirst();p!=null;p=p.getNext()) idx.put(p,N++);
        if(N==0) return; boolean[] leader=new boolean[N]; Arrays.fill(leader,false); AbstractInsnNode first=ins.getFirst(); leader[idx.get(first)]=true; Set<LabelNode> targets=new HashSet<LabelNode>();
        for(AbstractInsnNode p=first;p!=null;p=p.getNext()){int op=p.getOpcode(); if(op<0) continue; if(p instanceof JumpInsnNode){targets.add(((JumpInsnNode)p).label); AbstractInsnNode q=p.getNext(); if(q!=null) leader[idx.get(q)]=true;} else if(p instanceof TableSwitchInsnNode){TableSwitchInsnNode ts=(TableSwitchInsnNode)p; targets.add(ts.dflt); targets.addAll(ts.labels); AbstractInsnNode q=p.getNext(); if(q!=null) leader[idx.get(q)]=true;} else if(p instanceof LookupSwitchInsnNode){LookupSwitchInsnNode ls=(LookupSwitchInsnNode)p; targets.add(ls.dflt); targets.addAll(ls.labels); AbstractInsnNode q=p.getNext(); if(q!=null) leader[idx.get(q)]=true;} else if(isTerminal(op)){AbstractInsnNode q=p.getNext(); if(q!=null) leader[idx.get(q)]=true;}}
        for(LabelNode l:targets){Integer i=idx.get(l); if(i!=null) leader[i]=true;}
        Set<Integer> handlerStarts=new HashSet<Integer>(); if(method.tryCatchBlocks!=null){for(Object o:method.tryCatchBlocks){TryCatchBlockNode t=(TryCatchBlockNode)o; Integer hi=idx.get(t.handler); if(hi!=null){leader[hi]=true; handlerStarts.add(hi);}}}
        List<Integer> starts=new ArrayList<Integer>(); for(int i=0;i<N;i++) if(leader[i]) starts.add(i); Collections.sort(starts);
        Map<Integer,Block> byStart=new HashMap<Integer,Block>(); for(int i=0;i<starts.size();i++){int s=starts.get(i), e=(i+1<starts.size()?starts.get(i+1)-1:N-1); Block b=new Block(s,s,e,handlerStarts.contains(s)); byStart.put(s,b); blocks.put(b.id,b);}
        // succs
        for(Block b:blocks()){AbstractInsnNode last=ins.get(b.endIdx); int op=last==null?-1:last.getOpcode(); TreeSet<Integer> succ=new TreeSet<Integer>(); if(last instanceof JumpInsnNode){int t=labelBlock(byStart,idx,((JumpInsnNode)last).label); if(t>=0) succ.add(t); if(op!=GOTO){Integer fall=nextStartAfter(starts,b.endIdx); if(fall!=null) succ.add(fall);}} else if(last instanceof TableSwitchInsnNode){TableSwitchInsnNode ts=(TableSwitchInsnNode)last; succ.add(labelBlock(byStart,idx,ts.dflt)); for(Object o:ts.labels) succ.add(labelBlock(byStart,idx,(LabelNode)o));} else if(last instanceof LookupSwitchInsnNode){LookupSwitchInsnNode ls=(LookupSwitchInsnNode)last; succ.add(labelBlock(byStart,idx,ls.dflt)); for(Object o:ls.labels) succ.add(labelBlock(byStart,idx,(LabelNode)o));} else if(!isTerminal(op)){Integer fall=nextStartAfter(starts,b.endIdx); if(fall!=null) succ.add(fall);} b.succs=toArraySorted(succ);
        }
        // exception edges (loose)
        if(method.tryCatchBlocks!=null){for(Object o:method.tryCatchBlocks){TryCatchBlockNode t=(TryCatchBlockNode)o; Integer s=idx.get(t.start), e=idx.get(t.end), h=idx.get(t.handler); if(s==null||e==null||h==null) continue; int hid=h; for(Block b:blocks()){if(intersects(b.startIdx,b.endIdx+1,s,e)) addSucc(b,hid);} }}
        rebuildPreds();
    }

    private void dropUnreachable(){if(blocks.isEmpty()) return; int entry=Integer.MAX_VALUE; for(Block b:blocks()) entry=Math.min(entry,b.id); Set<Integer> seen=new HashSet<Integer>(); Deque<Integer> st=new ArrayDeque<Integer>(); if(entry!=Integer.MAX_VALUE){seen.add(entry); st.push(entry);} while(!st.isEmpty()){int id=st.pop(); Block b=blocks.get(id); if(b==null) continue; for(int s: b.succs) if(seen.add(s)) st.push(s);} List<Integer> rm=new ArrayList<Integer>(); for(Block b:blocks()) if(!seen.contains(b.id)) rm.add(b.id); for(Integer id:rm) blocks.remove(id); rebuildPreds(); }

    private void mergeLinear(){boolean changed; do{changed=false; List<Block> order=new ArrayList<Block>(blocks()); for(Block b:order){if(!blocks.containsKey(b.id)) continue; int[] s=b.succs; if(s.length!=1) continue; Block c=blocks.get(s[0]); if(c==null) continue; if(c.preds.length!=1) continue; AbstractInsnNode last=method.instructions.get(b.endIdx); int op=last==null?-1:last.getOpcode(); if(last instanceof JumpInsnNode|| last instanceof TableSwitchInsnNode|| last instanceof LookupSwitchInsnNode|| isTerminal(op)) continue; if(b.handlerStart||c.handlerStart) continue; b.endIdx=c.endIdx; b.succs=c.succs; blocks.remove(c.id); rebuildPreds(); changed=true; break; }}while(changed); }

    private void sortEdges(){for(Block b:blocks()){b.succs=sortedDistinct(b.succs); b.preds=sortedDistinct(b.preds);} }
    private void rebuildPreds(){for(Block b:blocks()) b.preds=new int[0]; for(Block b:blocks()){for(int s: b.succs){Block t=blocks.get(s); if(t==null) continue; t.preds=append(t.preds,b.id);} } sortEdges(); }

    // helpers
    private static boolean isTerminal(int op){switch(op){case RETURN:case ARETURN:case IRETURN:case LRETURN:case FRETURN:case DRETURN:case ATHROW:return true;default:return false;}}
    private static int[] toArraySorted(TreeSet<Integer> s){int n=s.size(),i=0; int[] a=new int[n]; for(Integer v:s)a[i++]=v.intValue(); return a;}
    private static void addSucc(Block b,int t){if(t>=0) b.succs=append(b.succs,t);} private static int[] append(int[] a,int v){int n=a.length; int[] out=Arrays.copyOf(a,n+1); out[n]=v; return out;}
    private static Integer nextStartAfter(List<Integer> starts,int insn){for(int v:starts) if(v>insn) return v; return null;}
    private static int labelBlock(Map<Integer,Block> byStart, IdentityHashMap<AbstractInsnNode,Integer> idx, LabelNode l){Integer li=idx.get(l); if(li==null) return -1; Block b=byStart.get(li); return b==null?-1:b.id;}
    private static boolean intersects(int a0,int a1,int b0,int b1){return a0<b1 && b0<a1;}
    private static int[] sortedDistinct(int[] a){if(a==null||a.length==0) return new int[0]; Arrays.sort(a); int w=0; for(int i=0;i<a.length;i++){if(i==0||a[i]!=a[i-1]) a[w++]=a[i];} return Arrays.copyOf(a,w);}
}
