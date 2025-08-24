package io.bytecodemapper.core.dom;

import io.bytecodemapper.core.cfg.ReducedCFG;
import io.bytecodemapper.core.cfg.ReducedCFG.Block;
import it.unimi.dsi.fastutil.ints.*;

import java.util.*;

/** Cooper–Harvey–Kennedy dominators over ReducedCFG (deterministic, cycle-safe). */
public final class Dominators {
    private final ReducedCFG cfg;
    private final int[] nodes; private final Int2IntMap indexOf; private final int[] rpo;
    private final int[] idomIndex; private final int[] idomIds; private final int[] depth; private final Map<Integer,int[]> children = new HashMap<Integer,int[]>();
    private Dominators(ReducedCFG cfg,int[] nodes,Int2IntMap indexOf,int[] rpo,int[] idomIndex,int[] idomIds,int[] depth){this.cfg=cfg; this.nodes=nodes; this.indexOf=indexOf; this.rpo=rpo; this.idomIndex=idomIndex; this.idomIds=idomIds; this.depth=depth; buildChildren();}

    // >>> CODEGEN: DOM DEBUG TOGGLE BEGIN
    private static final boolean DEBUG = Boolean.getBoolean("mapper.debug");
    private static void dbg(String s){ if (DEBUG) System.out.println(s); }
    // <<< CODEGEN: DOM DEBUG TOGGLE END

    public static Dominators compute(ReducedCFG cfg){int[] nodes=cfg.allBlockIds(); Arrays.sort(nodes); int n=nodes.length; if(n==0) return new Dominators(cfg,new int[0],new Int2IntOpenHashMap(),new int[0],new int[0],new int[0],new int[0]); Int2IntOpenHashMap idx=new Int2IntOpenHashMap(); idx.defaultReturnValue(-1); for(int i=0;i<n;i++) idx.put(nodes[i], i);
        int[][] succIdx=new int[n][], predIdx=new int[n][]; for(int i=0;i<n;i++){Block b=cfg.block(nodes[i]); succIdx[i]=mapIdsToIdx(b.succs(),idx); predIdx[i]=mapIdsToIdx(b.preds(),idx);} int entry=0; for(int i=1;i<n;i++) if(nodes[i]<nodes[entry]) entry=i;
        // >>> CODEGEN: RPO + rpoPos BEGIN
        final int[] rpoPos=new int[n];
        int[] rpo=computeRpo(entry,succIdx,n,rpoPos);
        // <<< CODEGEN: RPO + rpoPos END
    int[] idom=new int[n]; Arrays.fill(idom,-1); idom[entry]=entry;
        // >>> CODEGEN: CHK LOOP WITH CAP BEGIN
        boolean changed; int iter=0, MAX=Math.max(8, n*8);
        do{changed=false; for(int i=0;i<n;i++){int b=rpo[i]; if(b==entry) continue; int newI=-1; for(int p:predIdx[b]) if(p>=0&&p<n&&idom[p]!=-1){newI=p; break;} if(newI==-1) continue; for(int p:predIdx[b]){ if(p<0||p>=n||idom[p]==-1||p==newI) continue; newI=intersect(idom,rpoPos,p,newI);} if(idom[b]!=newI){idom[b]=newI; changed=true;}} iter++; if(iter>MAX){ dbg("[DOM] CHK capped at iter="+iter+" (n="+n+")"); break; } }while(changed);
        // <<< CODEGEN: CHK LOOP WITH CAP END
        int[] idomIds=new int[n], depth=new int[n]; for(int i=0;i<n;i++) idomIds[i]=nodes[idom[i]]; for(int i=0;i<n;i++){int d=0,cur=i,guard=0; while(idom[cur]!=cur && guard++<n+2){d++; cur=idom[cur];} depth[i]=d; }
        return new Dominators(cfg,nodes,idx,rpo,idom,idomIds,depth);
    }

    private static int[] mapIdsToIdx(int[] ids, Int2IntMap idx){int[] out=new int[ids.length]; for(int i=0;i<ids.length;i++){int v=idx.get(ids[i]); out[i]=v<0? -1: v;} return out;}
    // >>> CODEGEN: computeRpo + rpoPos BEGIN
    private static int[] computeRpo(int entry,int[][] succ,int n,int[] rpoPos){boolean[] seen=new boolean[n]; IntArrayList post=new IntArrayList(); Deque<Integer> stack=new ArrayDeque<Integer>(); Deque<Integer> it=new ArrayDeque<Integer>(); stack.push(entry); it.push(0); while(!stack.isEmpty()){int u=stack.peek(); int k=it.pop(); if(!seen[u]){seen[u]=true; k=0;} if(k<succ[u].length){int v=succ[u][k]; it.push(k+1); if(v>=0 && v<n && !seen[v]){stack.push(v); it.push(0);} } else {post.add(u); stack.pop();}} for(int i=0;i<n;i++) if(!seen[i]) post.add(i); int[] r=new int[post.size()]; for(int i=0;i<post.size();i++) r[i]=post.getInt(i); for(int i=0,j=r.length-1;i<j;i++,j--){int tmp=r[i]; r[i]=r[j]; r[j]=tmp;} Arrays.fill(rpoPos,-1); for(int pos=0; pos<r.length; pos++){ rpoPos[r[pos]] = pos; } return r;}
    // <<< CODEGEN: computeRpo + rpoPos END
    private static int[] append(int[] a,int v){int[] r=Arrays.copyOf(a,a.length+1); r[a.length]=v; return r;}
    // >>> CODEGEN: SAFE INTERSECT (monotonic, uses rpoPos[]) BEGIN
    private static int intersect(int[] idom,int[] rpoPos,int a0,int b0){int a=a0,b=b0; int guard=0, MAX=idom.length+4; while(a!=b && guard++<MAX){int pa=(a>=0&&a<rpoPos.length)? rpoPos[a]: -1; int pb=(b>=0&&b<rpoPos.length)? rpoPos[b]: -1; if(pa==-1||pb==-1) break; if(pa<pb){ // b is deeper (larger pos) -> move b up
                int next=idom[b]; if(next==-1||next==b) break; b=next;
            } else if(pb<pa){ // a is deeper -> move a up
                int next=idom[a]; if(next==-1||next==a) break; a=next;
            } else {
                break;
            }} return (a==b)? a: a0;}
    // <<< CODEGEN: SAFE INTERSECT END

    // >>> CODEGEN: Dominators.debugDump (guarded) BEGIN
    @SuppressWarnings("unused")
    private static void debugDumpIdoms(int[] nodes,int[] idomIndex,int[] rpo){ if(!DEBUG) return; StringBuilder sb=new StringBuilder(); sb.append("# idoms (node->idom)\n"); for(int i=0;i<nodes.length;i++){int me=nodes[i]; int pi=idomIndex[i]; sb.append(me).append(" -> ").append(pi>=0? nodes[pi]: -1).append('\n');} sb.append("# rpo\n"); for(int i=0;i<rpo.length;i++){sb.append(i).append(':').append(rpo[i]).append('\n');} System.err.print(sb.toString()); }
    // <<< CODEGEN: Dominators.debugDump (guarded) END
    private void buildChildren(){for(int id: nodes) children.put(id, new int[0]); for(int i=0;i<nodes.length;i++){int me=nodes[i]; int pi=idomIndex[i]; if(pi==i) continue; int parent=nodes[pi]; children.put(parent, append(children.get(parent), me)); } for(Map.Entry<Integer,int[]> e: children.entrySet()){int[] a=e.getValue(); Arrays.sort(a); e.setValue(a);} }
    public int idom(int blockId){int i=indexOf.get(blockId); if(i<0) return -1; return idomIds[i];}
    public int domDepth(int blockId){int i=indexOf.get(blockId); if(i<0) return -1; return depth[i];}
    public int[] children(int blockId){int[] ch=children.get(blockId); return ch==null? new int[0]: Arrays.copyOf(ch,ch.length);}
    public boolean dominates(int vBlock,int uBlock){ if(vBlock==uBlock) return true; int vi=indexOf.get(vBlock), ui=indexOf.get(uBlock); if(vi<0||ui<0) return false; int v=vi, u=ui; int guard=0; while(idomIndex[u]!=u && guard++<nodes.length+2){u=idomIndex[u]; if(u==v) return true;} return false; }
}
