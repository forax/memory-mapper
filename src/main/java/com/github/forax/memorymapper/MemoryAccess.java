package com.github.forax.memorymapper;

import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.VarHandle;
import java.util.AbstractList;
import java.util.List;
import java.util.RandomAccess;
import java.util.Spliterator;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.util.Objects.checkIndex;
import static java.util.Objects.requireNonNull;

/**
 * This class provides several features helping to map a {@link MemorySegment memory segment} to record instances
 * or vice-versa.
 * <ul>
 *   <li>Create a memory access with auto-padding of the layout like in C  ({@link #reflect(Lookup, Class)})
 *   <li>Access to a {@link #vh(String) VarHandle} or a {@link #byteOffset(String) byteOffset}
 *       from a literal string path
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
 * @param <T> type of the record
 */
public sealed interface MemoryAccess<T> permits MemoryAccessFactory.MemoryAccessImpl {
  /**
   * Returns the computed memory layout.
   * @return the computed memory layout.
   * 
   * @see MemoryAccessFactory#defaultLayout(Class)
   */
  MemoryLayout layout();

  /**
   * Allocates a new memory segment of the size of the layout.
   * All the bytes are initialized at 0.
   * @param arena the arena used for the allocation.
   * @return a new memory segment of the size of the layout.
   *
   * @see Arena#allocate(MemoryLayout)
   */
  default MemorySegment newValue(Arena arena) {
    requireNonNull(arena, "arena is null");
    return arena.allocate(layout());
  }

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
  default MemorySegment newValue(Arena arena, T element) {
    requireNonNull(arena, "arena is null");
    requireNonNull(element, "element is null");
    var segment = arena.allocate(layout());
    set(segment, element);
    return segment;
  }

  /**
   * Allocates a new memory segment able to contain {@code size} element.
   * @param arena the arena used for the allocation.
   * @param size the number of elements
   * @return a new memory segment able to contain size element.
   * @throws IllegalArgumentException – if size &lt; 0
   */
  default MemorySegment newArray(Arena arena, long size) {
    requireNonNull(arena, "arena is null");
    return arena.allocate(layout(), size);
  }

  /**
   * Returns a VarHandle able to access data from a path pattern.
   *
   * @param path a string encoding how to get the data, using {@code []} for array access,
   *             {@code .member} for member access. Those patterns can be composed by
   *             concatenating them.
   * @return a VarHandle able to access data using the path pattern.
   * @throws IllegalArgumentException if the path is not an interned string.
   *
   * @see MemoryAccessFactory#defaultPath(String)
   */
  VarHandle vh(String path);

  /**
   * Returns the offset of the data from a path pattern.
   * @param path a string encoding how to get the data, using {@code []} for array access,
   *             {@code .member} for member access. Those patterns can be composed by
   *             concatenating them.
   * @return the offset of the data from a path pattern.
   * @throws IllegalArgumentException if the path is not an interned string.
   *
   * @see MemoryAccessFactory#defaultPath(String)
   */
  long byteOffset(String path);

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
  default T getAtIndex(MemorySegment segment, long index) {
    requireNonNull(segment, "segment is null");
    var layout = layout();
    return get(segment.asSlice(index * layout.byteSize(), layout));
  }

  /**
   * Returns a stream of records from the values of the memory segment.
   * @param segment a memory segment.
   * @return a stream of records from the values of the memory segment.
   * @throws IllegalArgumentException if the size of the layout is zero or
   *                                  if the layout does not describe a struct layout
   *                                  containing only values and other structs.
   */
  default Stream<T> stream(MemorySegment segment) {
    requireNonNull(segment, "segment is null");
    var spliterator = segment.spliterator(layout());
    return StreamSupport.stream(new MemoryAccessFactory.MappingSpliterator<>(this, spliterator), false);
  }

  /**
   * Returns a view of the memory segment as a list.
   * @param segment a memory segment.
   * @return a view of the memory segment as a list.
   * @throws IllegalArgumentException if the size of the layout is zero or
   *                                  if the layout does not describe a struct layout
   *                                  containing only values and other structs.
   */
  default List<T> list(MemorySegment segment) {
    requireNonNull(segment, "segment is null");
    var layout = layout();
    if (layout.byteSize() == 0) {
      throw new IllegalArgumentException("element layout size cannot be zero");
    }
    segment.asSlice(0, layout);  // check alignment
    if ((segment.byteSize() % layout.byteSize()) != 0) {
      throw new IllegalArgumentException("segment size is not a multiple of layout size");
    }
    var size = Math.toIntExact(segment.byteSize() / layout.byteSize());
    class MappingList extends AbstractList<T> implements RandomAccess {
      @Override
      public int size() {
        return size;
      }

      @Override
      public T get(int index) {
        checkIndex(index, size);
        return MemoryAccess.this.getAtIndex(segment, index);
      }

      @Override
      public T set(int index, T element) {
        checkIndex(index, size);
        var old = MemoryAccess.this.getAtIndex(segment, index);
        MemoryAccess.this.setAtIndex(segment, index, element);
        return old;
      }

      @Override
      public Spliterator<T> spliterator() {
        var spliterator = segment.spliterator(layout);
        return new MemoryAccessFactory.MappingSpliterator<>(MemoryAccess.this, spliterator);
      }
    }
    return new MappingList();
  }

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
  default void setAtIndex(MemorySegment segment, long index, T element) {
    requireNonNull(segment, "segment is null");
    requireNonNull(element, "element is null");
    var layout = layout();
    set(segment.asSlice(index * layout.byteSize(), layout), element);
  }

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
}



