package com.github.forax.memorymapper;

import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.VarHandle;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import static java.util.Objects.requireNonNull;

/**
 * This class provides several features helping to map a {@link MemorySegment memory segment} to record instances
 * or vice-versa.
 * <ul>
 *   <li>Create a memory access with auto-padding of the layout like in C, {@link #reflect(Lookup, Class)}
 *   <li>Convenient methods
 *   <li>Methods that
 *     <ul>
 *       <li>allocate a segment, {@link #newValue(Arena)} and {@link #newValue(Arena, Object)}
 *       <li>allocate an array, {@link #newArray(Arena, long)}
 *       <li>get/set a segment from/to a record, {@link #get(MemorySegment)} and {@link #set(MemorySegment, Object)}
 *       <li>get/set a segment at an index from/to a record, {@link #getAtIndex(MemorySegment, long)} and
 *           {@link #setAtIndex(MemorySegment, long, Object)}
 *       <li>get a {@link #varHandle(MemoryAccess, String)} VarHandle} or a
 *           {@link #byteOffset(MemoryAccess, String) byteOffset} from memory access and a string path.
 *     </ul>
 * </ul>
 *
 * Creating a {@link MemoryAccess} instance?
 * <p>
 * The idea is to declare a record for a struct and create a MemoryAccess instance.
 * A memory layout is derived from the record description with by default all the field correctly aligned,
 * using the byte order of the CPU.
 * {@snippet :
 * record Point(int x, int y) {}
 *
 * private static final MemoryAccess<Point> POINT = MemoryAccess.reflect(MethodHandles.lookup(), Point.class);
 *
 *   System.out.println(MemoryAccess.layout(POINT));
 *}
 *
 * The record can be decorated with annotations to specify the layout in memory. For example here, the padding
 * to align the fields is specified explicitly.
 * {@snippet :
 * @Layout(autoPadding = false, endPadding = 4)
 * record City(
 *   @LayoutElement(name = "city_id")
 *   int id,
 *   @LayoutElement(padding = 4)
 *   long population,
 *   @LayoutElement(padding = 0)
 *   int yearOfCreation
 * ) {}
 * }
 *
 * The annotation {@link LayoutElement} describes the {@link MemoryLayout layout} of each field.
 * The annotation {@link Layout} specifies the layout of the data structure.
 * <p>
 *
 * <b>Allocating an instance and accessing its fields using a record</b>
 * <p>
 * The method `newValue(arena)` allocates a memory segment of the size of the layout.
 * The method `newValue(arena, record)` initialize a memory segment and initialize all the fields from a record instance.
 * <p>
 * The methods {@link #get(MemorySegment)} and {@link #set(MemorySegment, Object)} respectively returns a record from
 * a struct and set the values of a struct from a record.
 * <p>
 * {@snippet :
 * private static final MemoryAccess<Point> POINT =
 *     MemoryAccess.reflect(MethodHandles.lookup(), Point.class);
 *
 *   try(Arena arena = Arena.ofConfined()) {
 *     MemorySegment s = POINT.newValue(arena, new Point(1, 2));  // s.x = 1; s.y = 2
 *     POINT.set(s, new Point(12, 5));  // s.x = 12; s.y = 5
 *     var p = POINT.get(s);            // p.x = s.x; p.y = s.y
 *   }
 * }
 *
 * <b>Allocating an array and accessing its elements using records</b>
 * <p>
 * The method {@link #newArray(Arena, long)} allocate an array with a size.
 * The methods {@link #getAtIndex(MemorySegment, long)}` and {@link #setAtIndex(MemorySegment, long, Object)}
 * respectively get a record from the array of structs using an index or set the struct of the array with an index.
 * <p>
 * {@snippet :
 * private static final MemoryAccess<Point> POINT =
 *     MemoryAccess.reflect(MethodHandles.lookup(), Point.class);
 *
 *   try(Arena arena = Arena.ofConfined()) {
 *     MemorySegment s = POINT.newArray(arena, 10);
 *     POINT.setAtIndex(s, 3L, new Point(12, 5));  // s[3].x = 12; s[3].y = 5
 *     var p = POINT.getAtIndex(s, 7L);            // p.x = s[7].x; p.y = s[7].y
 *   }
 * }
 *
 * <b>More high level methods</b>
 * <p>
 * The `MemoryAccess` API also provides the methods {@link #list(MemorySegment)} and {@link #stream(MemorySegment)}
 * that see an array respectively as a `java.util.List` (limited at 2G elements) and a `java.util.stream.Stream`
 * {@snippet :
 * private static final MemoryAccess<Point> POINT =
 *     MemoryAccess.reflect(MethodHandles.lookup(), Point.class);
 *
 *   MemorySegment s = POINT.newArray(arena.ofAuto(), 10);
 *   List<Point> l = POINT.list(segment);
 *   l.set(3, new Point(12, 5));   // s[3].x = 12; s[3].y = 5
 *   var p = l.get(7);             // p.x = s[7].x; p.y = s[7].y
 * }
 * <p>
 * <b>Convenient way to create a VarHandle for an element field</b>
 * <p>
 * The method {@link #varHandle(MemoryAccess, String)} returns a constant VarHandle allowing to get and set the values
 * of the fields from a string path.
 * <p>
 * {@snippet :
 * import static java.lang.invoke.MethodHandles.lookup;
 * import static com.github.forax.memorymapper.MemoryAccess.reflect;
 * import static com.github.forax.memorymapper.MemoryAccess.varHandle;
 *
 * private static final MemoryAccess<Point> POINT = reflect(lookup(), Point.class);
 * private static final VarHandle POINT_X = varHandle(POINT, ".x");
 * private static final VarHandle POINT_Y = varHandle(POINT, ".y");
 *
 *   try(Arena arena = Arena.ofConfined()) {
 *     MemorySegment s = POINT.newValue(arena);
 *
 *     POINT_X.set(s, 0L, 42);            // s.x = 42
 *     var y = (int) POINT_Y.get(s, 0L);  // s.y
 *   }
 * }
 *
 * <b>Convenient way to create a VarHandle for an element field of an array</b>
 * <p>
 * The method {@link #varHandle(MemoryAccess, String)} also allows to access to the elements of an array
 * of struct using the prefix '[]'.
 * <p>
 * {@snippet :
 * import static java.lang.invoke.MethodHandles.lookup;
 * import static com.github.forax.memorymapper.MemoryAccess.reflect;
 * import static com.github.forax.memorymapper.MemoryAccess.varHandle;
 *
 * private static final MemoryAccess<Point> POINT = reflect(lookup(), Point.class);
 * private static final VarHandle ARRAY_X = varHandle(POINT, "[].x");
 * private static final VarHandle ARRAY_Y = varHandle(POINT, "[].y");
 *
 *   try(Arena arena = Arena.ofConfined()) {
 *     MemorySegment s = POINT.newArray(arena, 10);
 *
 *     for(long i = 0L; i < 10L; i++) {
 *       ARRAY_X.set(s, 0L, i, 42);            // s[i].x = 42
 *       var y = (int) ARRAY_Y.get(s, 0L, i);  // s[i].y
 *     }
 *   }
 * }
 *
 * @param <T> type of the record
 */
public sealed interface MemoryAccess<T> permits MemoryAccessFactory.MemoryAccessImpl {
  /**
   * Allocates a new memory segment of the size of the layout.
   * All the bytes are initialized at 0.
   * @param arena the arena used for the allocation.
   * @return a new memory segment of the size of the layout.
   *
   * @see Arena#allocate(MemoryLayout)
   */
  MemorySegment newValue(Arena arena);

  /**
   * Allocates a new memory segment of the size of the layout
   * and initialized it with the values of the element.
   * @param arena the arena used for the allocation.
   * @param element an element used for the initialization.
   * @return a new memory segment of the size of the layout
   *         initialized with the values of the element.
   *
   * @see #newValue(Arena)
   * @see #set(MemorySegment, Object)
   */
  MemorySegment newValue(Arena arena, T element);

  /**
   * Allocates a new memory segment able to contain {@code size} element.
   * @param arena the arena used for the allocation.
   * @param size the number of elements
   * @return a new memory segment able to contain size element.
   * @throws IllegalArgumentException – if size &lt; 0
   */
  MemorySegment newArray(Arena arena, long size);

  /**
   * Gets the value from the segment and creates the corresponding record.
   * @param segment a memory segment.
   * @return the value from the segment and creates the corresponding record.
   * @throws IllegalArgumentException if the segment is not aligned to the layout alignment.
   * @throws UnsupportedOperationException if the layout does not describe a struct layout
   *                                       containing only values and other structs.
   * @see #set(MemorySegment, Object)
   */
  T get(MemorySegment segment);

  /**
   * Gets the value from the segment at an index and creates the corresponding record.
   * @param segment a memory segment.
   * @param index an index
   * @return the value from the segment at an index and creates the corresponding record.
   * @throws IndexOutOfBoundsException if the index is out of bounds.
   * @throws IllegalArgumentException if the segment is not aligned to the layout alignment.
   * @throws UnsupportedOperationException if the layout does not describe a struct layout
   *                                       containing only values and other structs.
   * @see #setAtIndex(MemorySegment, long, Object)
   */
  T getAtIndex(MemorySegment segment, long index);

  /**
   * Returns a stream of records from the values of the memory segment.
   * @param segment a memory segment.
   * @return a stream of records from the values of the memory segment.
   * @throws IllegalArgumentException if the size of the layout is zero or
   *                                  if the layout does not describe a struct layout
   *                                  containing only values and other structs.
   */
  Stream<T> stream(MemorySegment segment);

  /**
   * Returns a view of the memory segment as a list.
   * @param segment a memory segment.
   * @return a view of the memory segment as a list.
   * @throws IllegalArgumentException if the size of the layout is zero or
   *                                  if the layout does not describe a struct layout
   *                                  containing only values and other structs.
   */
  List<T> list(MemorySegment segment);

  /**
   * Sets the data of the segment with the values of the element.
   * @param segment the memory segment to be written.
   * @param element the element.
   * @throws IllegalArgumentException if the segment is not aligned to the layout alignment.
   * @throws UnsupportedOperationException if the layout does not describe a struct layout
   *                                       containing only values and other structs.
   *
   * @see #get(MemorySegment)
   */
  void set(MemorySegment segment, T element);

  /**
   * Sets the values of the record to the segment at an index.
   * @param segment a memory segment.
   * @param index an index.
   * @param element a record
   * @throws IndexOutOfBoundsException if the index is out of bounds.
   * @throws IllegalArgumentException if the segment is not aligned to the layout alignment.
   * @throws UnsupportedOperationException if the layout does not describe a struct layout
   *                                       containing only values and other structs.
   * @see #getAtIndex(MemorySegment, long)
   */
  void setAtIndex(MemorySegment segment, long index, T element);

  /**
   * Creates a memory access object from a record type (and a lookup).
   * @param lookup an access checking object.
   * @param recordType a record type.
   * @return a new memory access.
   * @param <T> the type of the record type.
   *
   * @see MemoryAccessFactory#create(Lookup, Class, Function)
   */
  static <T extends Record> MemoryAccess<T> reflect(Lookup lookup, Class<T> recordType) {
    requireNonNull(lookup, "lookup is null");
    requireNonNull(recordType, "recordType is null");
    return MemoryAccessFactory.create(lookup, recordType, MemoryAccessFactory::defaultLayout);
  }

  static <T> MemoryAccess<T> fromPrimitive(Class<T> primitiveType) {
    requireNonNull(primitiveType, "primitiveType is null");
    return MemoryAccessFactory.fromPrimitive(primitiveType);
  }

  /**
   * Returns the computed memory layout of a memory access.
   * @param memoryAccess the memory access
   * @return the computed memory layout of a memory access.
   *
   * @see MemoryAccessFactory#defaultLayout(Class)
   */
  static MemoryLayout layout(MemoryAccess<?> memoryAccess) {
    var impl = (MemoryAccessFactory.MemoryAccessImpl<?>) memoryAccess;
    return impl.layout();
  }

  /**
   * Returns a VarHandle able to access data from a path pattern.
   * @param memoryAccess the memory access
   * @param path a string encoding how to get the data, using {@code []} for array access,
   *             {@code .member} for member access. Those patterns can be composed by
   *             concatenating them.
   * @return a VarHandle able to access data using the path pattern.
   */
  static VarHandle varHandle(MemoryAccess<?> memoryAccess, String path) {
    var impl = (MemoryAccessFactory.MemoryAccessImpl<?>) memoryAccess;
    return MemoryAccessFactory.varHandle(impl.layout(), path);
  }

  /**
   * Returns the offset of the data from a path pattern.
   * @param memoryAccess the memory access
   * @param path a string encoding how to get the data, using {@code []} for array access,
   *             {@code .member} for member access. Those patterns can be composed by
   *             concatenating them.
   * @return the offset of the data from a path pattern.
   * @throws IllegalArgumentException if the path is not an interned string.
   */
  static long byteOffset(MemoryAccess<?> memoryAccess, String path) {
    var impl = (MemoryAccessFactory.MemoryAccessImpl<?>) memoryAccess;
    return MemoryAccessFactory.byteOffset(impl.layout(), path);
  }
}



