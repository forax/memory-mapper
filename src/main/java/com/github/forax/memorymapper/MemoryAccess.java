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
 *   <li>Create a memory access with auto-padding of the layout like in C  ({@link #reflect(Lookup, Class)})
 *   <li>Convenient methods to get a {@link #varHandle(MemoryAccess, String)}  VarHandle} or
 *       a {@link #byteOffset(MemoryAccess, String) byteOffset} from a literal string path
 *   <li>Methods that
 *     <ul>
 *       <li>allocate a segment ({@link #newValue(Arena)} and {@link #newValue(Arena, Object)})
 *       <li>allocate an array ({@link #newArray(Arena, long)})
 *       <li>get/set a segment from/to a record ({@link #get(MemorySegment)} and {@link #set(MemorySegment, Object)})@
 *       <li>get/set a segment at an index from/to a record ({@link #getAtIndex(MemorySegment, long)} and
 *           {@link #setAtIndex(MemorySegment, long, Object)})
 *     </ul>
 * </ul>
 *
 * Creating a {@link MemoryAccess} instance?
 * <p>
 * The idea is to declare a record for a struct and create a MemoryAccess instance.
 * A memory layout is derived from the record description with by default all the field correctly aligned
 * and the byte order of the CPU.
 * {@snippet :
 * record Point(int x, int y) {}
 *
 * private static final MemoryAccess<Point> POINT = MemoryAccess.reflect(MethodHandles.lookup(), Point.class);
 *
 *   System.out.println(POINT.layout());
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
 *   int yearOfCreation
 * ) {}
 * }
 *
 * The annotation {@link LayoutElement} describes the {@link MemoryLayout layout} of each field.
 * The annotation {@link Layout} specifies the layout of the data structure.
 * <p>
 * <b>Allocating an instance and accessing its fields</b>
 * <p>
 * The method {@link #newValue(Arena)} allocates a memory segment of the size of the layout.
 * The method {@link #varHandle(MemoryAccess, String)} returns a constant {@link VarHandle} allowing to get
 * or set the values of the fields from a string path.
 * <p>
 * {@snippet :
 * private static final MemoryAccess<Point> POINT =
 *     MemoryAccess.reflect(MethodHandles.lookup(), Point.class);
 * private static final VarHandle POINT_X = MemoryAccess.varHandle(POINT, ".x");
 * private static final VarHandle POINT_Y = MemoryAccess.varHandle(POINT, ".y");
 *
 *   try(Arena arena = Arena.ofConfined()) {
 *     MemorySegment s = POINT.newValue(arena);
 *
 *     POINT_X.set(s, 0L, 42);            // s.x = 42
 *     var y = (int) POINT_Y.get(s, 0L);  // s.y
 *   }
 * }
 *
 * <b>Allocating an array and accessing its element</b>
 * <p>
 * The method {@link #newArray(Arena, long)}  allocate an array with a size.
 * The method {@link #varHandle(MemoryAccess, String)} also allows to access to the elements of an array
 * using the prefix "[]".
 * <p>
 * {@snippet :
 * private static final MemoryAccess<Point> POINT =
 *     MemoryAccess.reflect(MethodHandles.lookup(), Point.class);
 * private static final VarHandle POINT_INDEX_X = MemoryAccess.varHandle(POINT, "[].x");
 * private static final VarHandle POINT_INDEX_Y = MemoryAccess.varHandle(POINT, "[].y");
 *
 *   try(Arena arena = Arena.ofConfined()) {
 *     MemorySegment s = POINT.newArray(arena, 10);
 *
 *     for(long i = 0L; i < 10L; i++) {
 *       POINT_INDEX_X.set(s, 0L, i, 42);            // s[i].x = 42
 *       var y = (int) POINT_INDEX_Y.get(s, 0L, i);  // s[i].y
 *     }
 *   }
 * }
 *
 * <b>Mapping a segment to record instances</b>
 * <p>
 * The method {@link #newValue(Arena, Object)} initialize a memory segment and initialize all the fields from a
 * record instance.
 * The methods {@link #getAtIndex(MemorySegment, long)} and {@link #setAtIndex(MemorySegment, long, Object)}
 * get and set all the fields in bulk from/into a record instance.
 * {@snippet :
 *  private static final MemoryAccess<Point> POINT =
 *      MemoryAccess.reflect(MethodHandles.lookup(), Point.class);
 *
 *    try(Arena arena = Arena.ofConfined()) {
 *      MemorySegment s = POINT.newValue(arena, new Point(1, 2));  // s.x = 1, s.y = 2
 *      POINT.set(s, new Point(12, 5));  // s.x = 12, s.y = 5
 *      var p = POINT.get(s);            // p.x = s.x, p.y = s.y
 *
 *      MemorySegment s2 = POINT.newArray(arena);
 *      POINT.setAtIndex(s2, 3L, new Point(12, 5));  // s2[3].x = 12, s2[3].y = 5
 *      var p2 = POINT.getAtIndex(segment2, 7L);     // p2.x = s2[7].x, p2.y = s2[7].y
 *    }
 *}
 *
 * and the methods {@link #list(MemorySegment)} and {@link #stream(MemorySegment)} sees an array
 * respectively as a {@code java.util.List} (limited at 2G elements) and a {@code java.util.stream.Stream}.
 * {@snippet :
 * private static final MemoryAccess<Point> POINT =
 *     MemoryAccess.reflect(MethodHandles.lookup(), Point.class);
 *
 *   try(Arena arena = Arena.ofConfined()) {
 *     MemorySegment s = POINT.newArray(arena);
 *     List<Point> l = POINT.list(segment);
 *     l.set(3, new Point(12, 5));   // s[3].x = 12, s[3].y = 5
 *     var p = l.get(7);             // p.x = s[7].x, p.y = s[7].y
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
   * Sets the value from the segment at an index and creates the corresponding record.
   * @param segment a memory segment.
   * @param index an index.
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
   * @see MemoryAccessFactory#create(Lookup, Class, Function, Function)
   */
  static <T extends Record> MemoryAccess<T> reflect(Lookup lookup, Class<T> recordType) {
    requireNonNull(lookup, "lookup is null");
    requireNonNull(recordType, "recordType is null");
    return MemoryAccessFactory.create(lookup, recordType, MemoryAccessFactory::defaultLayout, MemoryAccessFactory::defaultPath);
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
   *
   * @see MemoryAccessFactory#defaultPath(String)
   */
  static VarHandle varHandle(MemoryAccess<?> memoryAccess, String path) {
    var impl = (MemoryAccessFactory.MemoryAccessImpl<?>) memoryAccess;
    return MemoryAccessFactory.varHandle(impl.layout(), path, impl.pathFunction());
  }

  /**
   * Returns the offset of the data from a path pattern.
   * @param memoryAccess the memory access
   * @param path a string encoding how to get the data, using {@code []} for array access,
   *             {@code .member} for member access. Those patterns can be composed by
   *             concatenating them.
   * @return the offset of the data from a path pattern.
   * @throws IllegalArgumentException if the path is not an interned string.
   *
   * @see MemoryAccessFactory#defaultPath(String)
   */
  static long byteOffset(MemoryAccess<?> memoryAccess, String path) {
    var impl = (MemoryAccessFactory.MemoryAccessImpl<?>) memoryAccess;
    return MemoryAccessFactory.byteOffset(impl.layout(), path, impl.pathFunction());
  }
}



