# memory-mapper
A simple library on top of the Java  Foreign Function Memory API (java.lang.foreign)
that simplifies the mapping of Java records from/to off heap memory

The class `MemoryAccess` provides several features helping to map a memory segment to record instances or vice-versa.
- Create a memory access with auto-padding of the layout like in C `reflect(lookup, recordClass)`
- Methods that:
  - allocate a segment `newValue(arena)` and `newValue(arena, record)`
  - allocate a segment seen as an array `newArray(arena, size)`
  - get/ set a segment from/ to a record `get(memorySegment)` and `set(memorySegment, record)`
  - get/ set a segment seen as an array at an index from/ to a record `getAtIndex(memorySegment, index)` and
    `setAtIndex(memorySegment, index, record)`
  - create `VarHandle`s from a string path `MemoryAccess.varHandle(memoryAccess, path)`


### Creating a `MemoryAccess` instance?

The idea is to declare a record for a struct and create a `MemoryAccess` instance.
A memory layout is derived from the record description with by default all the field correctly aligned,
using the byte order of the CPU.
```java
record Point(int x, int y) {}

private static final MemoryAccess<Point> POINT =
    MemoryAccess.reflect(MethodHandles.lookup(), Point.class);
  ...
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


### Allocating an instance and accessing its fields using a record

The method `newValue(arena)` allocates a memory segment of the size of the layout.
The method `newValue(arena, record)` initialize a memory segment and initialize all the fields from a record instance.

The methods `get(memorySegment)` and `set(memorySegment, record)` respectively returns a record from
a struct and set the values of a struct from a record. 

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

### Allocating an array and accessing its elements using records

The method `newArray(arena, size)` allocate an array with a size.
The methods `getAtIndex(memorySegment, index)` and `setAtIndex(memorySegment, index, record)`respectively
get a record from the array of structs using an index or set the struct of the array with an index.

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

### More high level methods

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

### Convenient way to create a VarHandle for an element field


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


### Convenient way to create a VarHandle for an element field of an array


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
	    <version>1.0</version>
	</dependency>
```


