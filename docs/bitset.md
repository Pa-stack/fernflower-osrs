<!-- >>> AUTOGEN: BYTECODEMAPPER DOC bitset BEGIN -->
# Micropattern Bitset (Frozen Order)

Index → Pattern:
0 NoParams
1 NoReturn
2 Recursive
3 SameName
4 Leaf
5 ObjectCreator
6 FieldReader
7 FieldWriter
8 TypeManipulator
9 StraightLine
10 Looping
11 Exceptions (presence of `ATHROW`)
12 LocalReader
13 LocalWriter
14 ArrayCreator
15 ArrayReader
16 ArrayWriter

Extraction is from **analysis CFG** post minimal normalization.
Looping = **dominator back-edge** (u→v where v dominates u).
ArrayReader uses `*ALOAD` (e.g., FALOAD), not local `FLOAD`.
Exceptions = presence of `ATHROW`. Optional OSRS flag: `ThrowsOut`.
<!-- <<< AUTOGEN: BYTECODEMAPPER DOC bitset END -->
