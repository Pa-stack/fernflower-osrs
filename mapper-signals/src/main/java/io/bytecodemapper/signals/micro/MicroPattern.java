// >>> AUTOGEN: BYTECODEMAPPER MicroPattern BEGIN
package io.bytecodemapper.signals.micro;

/** Frozen 17-bit order (part of ABI). */
public enum MicroPattern {
    NoParams,       // 0
    NoReturn,       // 1
    Recursive,      // 2
    SameName,       // 3
    Leaf,           // 4
    ObjectCreator,  // 5
    FieldReader,    // 6
    FieldWriter,    // 7
    TypeManipulator,// 8
    StraightLine,   // 9
    Looping,        // 10  (dominator back-edge; uses core Dominators)
    Exceptions,     // 11  (presence of ATHROW)
    LocalReader,    // 12
    LocalWriter,    // 13
    ArrayCreator,   // 14
    ArrayReader,    // 15
    ArrayWriter     // 16
}
 // <<< AUTOGEN: BYTECODEMAPPER MicroPattern END
