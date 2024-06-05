# memory-mapper
A simple library on top of the Java  Foreign Function Memory API (java.lang.foreign)
that simplifies the mapping of Java records from/to off heap memory

This library provides
- a high level API through the class `MemoryCollections` that provides `List` and `Map`implementation
  specialized for primitive types and record that are stored as value (instead of as reference).
- a low level API through the class `MemoryAccess` that map a memory segment to a primitive type or
  a record type.

[javadoc](https://javadoc.jitpack.io/com/github/forax/memory-mapper/master/javadoc/com/github/forax/memorymapper/package-summary.html)

## Using a specialized list/map

Specialized list/map implementations of respectively `java.util.List` and `java.util.Map`
that uses off-heap memory (memory not managed by the GC) to store their elements/keys/values
in a data structure more compact and more sympathetic to actual CPUs (avoiding using pointers/references).

```java
record Pair(int fiest, int second) {}
  ...
  List<Integer> list = MemoryCollections.newSpecializedList(int.class);
  list.add(2);
  list.add(42);
  
  Map<Integer, Pair> map = MemoryCollections.newSpecializedMap(int.class, Pair.class);
  for(int value : list) {
    map.put(value, new Pair(value, value));
  }
```

## Creating a `MemoryAccess` instance?

A `MemoryAccess` object can be created either on a primitive type or on a record (to define a struct)
and helps to access off-heap memory in a simple way.
A memory layout is derived from the description, with by default, all the field correctly aligned and
using the byte order of the CPU.
```java
record Point(int x, int y) {}

private static final MemoryAccess<Point> INT =
    MemoryAccess.from(int.class);
private static final MemoryAccess<Point> POINT =
    MemoryAccess.reflect(MethodHandles.lookup(), Point.class);
  ...
  System.out.println(MemoryAccess.layout(INT));
  System.out.println(MemoryAccess.layout(POINT));
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


### Allocating an instance and accessing its fields using a record/boxed value

The method `newValue(allocator)` allocates a memory segment of the size of the layout.
The method `newValue(allocator, value)` initialize a memory segment with the provided value
(a record or a boxed primitive value).

The methods `get(memorySegment)` and `set(memorySegment, value)` respectively returns a record/boxed value
from a memory segment or set the value from a record/boxed value. 

```java
private static final MemoryAccess<Point> POINT =
    MemoryAccess.reflect(MethodHandles.lookup(), Point.class);
  ...
  try(Arena arena = Arena.ofConfined()) {
    MemorySegment s = POINT.newValue(arena, new Point(1, 2));  // s.x = 1; s.y = 2
    POINT.set(s, new Point(12, 5));  // s.x = 12; s.y = 5
    var p = POINT.get(s);            // p.x = s.x; p.y = s.y
  }
```

### Allocating an array and accessing its elements using records/boxed values

The method `newArray(allocator, size)` allocate an array with a size.
The methods `getAtIndex(memorySegment, index)` and `setAtIndex(memorySegment, index, value)`respectively
get a value (a record or a boxed value) from the array using an index or set the element of an array with an index.

```java
private static final MemoryAccess<Point> POINT =
    MemoryAccess.reflect(MethodHandles.lookup(), Point.class);
  ...
  try(Arena arena = Arena.ofConfined()) {
    MemorySegment s = POINT.newArray(arena, 10);
    POINT.setAtIndex(s, 3L, new Point(12, 5));  // s[3].x = 12; s[3].y = 5
    var p = POINT.getAtIndex(s, 7L);            // p.x = s[7].x; p.y = s[7].y
  }
```

### More methods

The `MemoryAccess` API also provides the methods `list(memorySegment)` and `stream(memorySegment)` that see
an array respectively as a `java.util.List` (limited at 2G elements) and a `java.util.stream.Stream`
```java
private static final MemoryAccess<Point> POINT =
    MemoryAccess.reflect(MethodHandles.lookup(), Point.class);
  ...
  MemorySegment s = POINT.newArray(arena.ofAuto(), 10);
  List<Point> l = POINT.list(segment);
  l.set(3, new Point(12, 5));   // s[3].x = 12; s[3].y = 5
  var p = l.get(7);             // p.x = s[7].x; p.y = s[7].y
```

#### Convenient way to create a VarHandle for an element field

The method `MemoryAccess.varHandle(memoryAccess, path)` returns a constant VarHandle allowing to get and set the values of the fields
from a string path.

```java
import static java.lang.invoke.MethodHandles.lookup;
import static com.github.forax.memorymapper.MemoryAccess.reflect;
import static com.github.forax.memorymapper.MemoryAccess.varHandle;

private static final MemoryAccess<Point> POINT = reflect(lookup(), Point.class);
private static final VarHandle POINT_X = varHandle(POINT, ".x");
private static final VarHandle POINT_Y = varHandle(POINT, ".y");        
  ...
  try(Arena arena = Arena.ofConfined()) {
    MemorySegment s = POINT.newValue(arena);

    POINT_X.set(s, 0L, 42);            // s.x = 42
    var y = (int) POINT_Y.get(s, 0L);  // s.y
  }
```

#### Convenient way to create a VarHandle for an element field of an array

The method `MemoryAccess.varHandle(memoryAccess, path)` also allows to access to the elements of an array
of struct using the prefix '[]'.

```java
import static java.lang.invoke.MethodHandles.lookup;
import static com.github.forax.memorymapper.MemoryAccess.reflect;
import static com.github.forax.memorymapper.MemoryAccess.varHandle;

private static final MemoryAccess<Point> POINT = reflect(lookup(), Point.class);
private static final VarHandle ARRAY_X = varHandle(POINT, "[].x");
private static final VarHandle ARRAY_Y = varHandle(POINT, "[].y");    
  ...
  try(Arena arena = Arena.ofConfined()) {
    MemorySegment s = POINT.newArray(arena, 10);

    for(long i = 0L; i < 10L; i++) {
      ARRAY_X.set(s, 0L, i, 42);            // s[i].x = 42
      var y = (int) ARRAY_Y.get(s, 0L, i);  // s[i].y
    }
  }
```

## How to use it ?

The artifacts are stored on jitpack.

With Maven, add a new repository jitpack.io 
```pom
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>
```

then you can add the dependency to the library
```pom
<dependency>
    <groupId>com.github.forax.memorymapper</groupId>
    <artifactId>memory-mapper</artifactId>
    <version>2.0</version>
</dependency>
```


