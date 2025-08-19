// >>> AUTOGEN: BYTECODEMAPPER CLI FieldRef BEGIN
package io.bytecodemapper.cli.field;

public final class FieldRef {
    public final String owner; // internal name
    public final String name;
    public final String desc;
    public FieldRef(String owner, String name, String desc){ this.owner=owner; this.name=name; this.desc=desc; }

    @Override public boolean equals(Object o){
        if(!(o instanceof FieldRef)) return false;
        FieldRef r=(FieldRef)o;
        return owner.equals(r.owner) && name.equals(r.name) && desc.equals(r.desc);
    }
    @Override public int hashCode(){ int h= owner.hashCode(); h = 31*h + name.hashCode(); h = 31*h + desc.hashCode(); return h; }
    @Override public String toString(){ return owner + "/" + name + " " + desc; }
}
// <<< AUTOGEN: BYTECODEMAPPER CLI FieldRef END
