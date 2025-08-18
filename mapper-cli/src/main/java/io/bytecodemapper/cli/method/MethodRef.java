// >>> AUTOGEN: BYTECODEMAPPER CLI MethodRef BEGIN
package io.bytecodemapper.cli.method;

public final class MethodRef {
    public final String owner; // internal name
    public final String name;
    public final String desc;

    public MethodRef(String owner, String name, String desc) {
        this.owner = owner; this.name = name; this.desc = desc;
    }

    @Override public String toString() { return owner + "#" + name + desc; }
    @Override public int hashCode() { return (owner.hashCode()*31 + name.hashCode())*31 + desc.hashCode(); }
    @Override public boolean equals(Object o) {
        if (!(o instanceof MethodRef)) return false;
        MethodRef m = (MethodRef) o;
        return owner.equals(m.owner) && name.equals(m.name) && desc.equals(m.desc);
    }
}
// <<< AUTOGEN: BYTECODEMAPPER CLI MethodRef END
