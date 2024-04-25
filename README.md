# memory-mapper
A simple library on top of the Java  Foreign Function Memory API (java.lang.foreign)
that simplifies the mapping of Java records from/to off heap memory

The class `MemoryAccess` provides several features helping to map a memory segment to record instances or vice-versa.
- Create a memory access with auto-padding of the layout like in C `reflect(lookup, recordClass)`
- Access to a VarHandle or a byteOffset from a literal string path
- Methods that:
  - allocate a segment `newValue(arena)` and `newValue(arena, record)`
  - allocate an array `newArray(arena, size)`
  - get/ set a segment from/ to a record `get(memorySegment)` and `set(memorySegment, record)`
  - get/ set a segment at an index from/ to a record `getAtIndex(memorySegment, index)` and
    `setAtIndex(memorySegment, index, record)`


### Creating a `MemoryAccess` instance?

The idea is to declare a record for a struct and create a `MemoryAccess` instance.
A memory layout is derived from the record description with by default all the field correctly aligned,
using the byte order of the CPU.
```java
record Point(int x, int y) {}

private static final MemoryAccess<Point> POINT =
    MemoryAccess.reflect(MethodHandles.lookup(), Point.class);
  ...
  System.out.println(POINT.layout());
```
The record can be decorated with annotations to specify the layout in memory.
For example here, the padding to align the fields is specified explicitly.
```java
@Layout(autoPadding = false, endPadding = 4)
record City(
  @LayoutElement(name = "city_id")
  int id,
  @LayoutElement(padding = 4)
  long population,
  @LayoutElement(padding = 0)
  int yearOfCreation
) {}
```
The annotation `LayoutElement` describes the layout of each field.
The annotation `Layout` specifies the layout of the data structure.


### Allocating an instance and accessing its fields

The method `newValue(arena)` allocates a memory segment of the size of the layout.
The method `vh(path)` returns a constant VarHandle allowing to get and set the values of the fields
from a string path.
```java
private static final MemoryAccess<Point> POINT =
    MemoryAccess.reflect(MethodHandles.lookup(), Point.class);
  ...
  try(Arena arena = Arena.ofConfined()) {
    MemorySegment s = POINT.newValue(arena);

    POINT.vh(".x").set(s, 0L, 42);            // s.x = 42
    var y = (int) POINT.vh(".y").get(s, 0L);  // s.y
  }
```


### Allocating an array and accessing its element

The method `newArray(arena, size)` allocate an array with a size.
The method `vh(path)` also allows to access to the elements of an array using the prefix '[]'.
```java
private static final MemoryAccess<Point> POINT =
    MemoryAccess.reflect(MethodHandles.lookup(), Point.class);
  ...
  try(Arena arena = Arena.ofConfined()) {
    MemorySegment s = POINT.newArray(arena, 10);

    for(long i = 0L; i < 10L; i++) {
      POINT.vh("[].x").set(s, 0L, i, 42);            // s[i].x = 42
      var y = (int) POINT.vh("[].y").get(s, 0L, i);  // s[i].y
    }
  }
```


### Mapping a segment to record instances

The method `newValue(arena, record)` initialize a memory segment and initialize all the fields from a record instance.
The methods `getAtIndex(memorySegment, index)` and `setAtIndex(memorySegment, index, record)` get and set
all the fields in bulk from/ into a record instance.
```java
private static final MemoryAccess<Point> POINT =
    MemoryAccess.reflect(MethodHandles.lookup(), Point.class);
  ...
  try(Arena arena = Arena.ofConfined()) {
    MemorySegment s = POINT.newValue(arena, new Point(1, 2));  // s.x = 1; s.y = 2
    POINT.set(s, new Point(12, 5));  // s.x = 12; s.y = 5
    var p = POINT.get(s);            // p.x = s.x; p.y = s.y

    MemorySegment s2 = POINT.newArray(arena);
    POINT.setAtIndex(s2, 3L, new Point(12, 5));  // s2[3].x = 12; s2[3].y = 5
    var p2 = POINT.getAtIndex(segment2, 7L);     // p2.x = s2[7].x; p2.y = s2[7].y
  }
```
and the methods `list(memorySegment)` and `stream(memorySegment)` sees an array respectively as a `java.util.List`
(limited at 2G elements) and a `java.util.stream.Stream`
```java
private static final MemoryAccess<Point> POINT =
    MemoryAccess.reflect(MethodHandles.lookup(), Point.class);
  ...
  try(Arena arena = Arena.ofConfined()) {
    MemorySegment s = POINT.newArray(arena);
    List<Point> l = POINT.list(segment);
    l.set(3, new Point(12, 5));   // s[3].x = 12; s[3].y = 5
    var p = l.get(7);             // p.x = s[7].x; p.y = s[7].y
  }
```


