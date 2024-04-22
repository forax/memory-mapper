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
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.util.Objects.checkIndex;
import static java.util.Objects.requireNonNull;

public sealed interface MemoryAccess<T> permits MemoryAccessFactory.MemoryAccessImpl {
  MemoryLayout layout();

  default MemorySegment newValue(Arena arena) {
    requireNonNull(arena, "arena is null");
    return arena.allocate(layout());
  }

  default MemorySegment newValue(Arena arena, T element) {
    requireNonNull(arena, "arena is null");
    var segment = arena.allocate(layout());
    set(segment, element);
    return segment;
  }

  default MemorySegment newArray(Arena arena, long size) {
    requireNonNull(arena, "arena is null");
    return arena.allocate(layout(), size);
  }

  VarHandle vh(String path);

  long byteOffset(String path);

  T get(MemorySegment segment);

  default T getAtIndex(MemorySegment segment, long index) {
    requireNonNull(segment, "segment is null");
    var layout = layout();
    return get(segment.asSlice(index * layout.byteSize(), layout));
  }

  default Stream<T> stream(MemorySegment segment) {
    requireNonNull(segment, "segment is null");
    segment.asSlice(0, layout());  // check alignment
    var spliterator = segment.spliterator(layout());
    return StreamSupport.stream(new MemoryAccessFactory.MappingSpliterator<>(this, spliterator), false);
  }

  default List<T> list(MemorySegment segment) {
    requireNonNull(segment, "segment is null");
    var layout = layout();
    segment.asSlice(0, layout);  // check alignment
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

  void set(MemorySegment segment, T element);

  default void setAtIndex(MemorySegment segment, long index, T element) {
    requireNonNull(segment, "segment is null");
    requireNonNull(element, "element is null");
    var layout = layout();
    set(segment.asSlice(index * layout.byteSize(), layout), element);
  }

  default void fill(MemorySegment segment, long size, T element) {
    requireNonNull(segment);
    requireNonNull(element);
    var layout = layout();
    segment.asSlice(0, size * layout.byteSize(), layout.byteAlignment());  // check alignment
    // maybe use an intermediary segment + segment copy ?
    for(var i = 0L; i < size; i++) {
      setAtIndex(segment, i, element);
    }
  }

  static <T extends Record> MemoryAccess<T> reflect(Lookup lookup, Class<T> recordType) {
    requireNonNull(lookup, "lookup is null");
    requireNonNull(recordType, "recordType is null");
    return MemoryAccessFactory.create(lookup, recordType, MemoryAccessFactory::defaultLayout, MemoryAccessFactory::defaultPath);
  }
}



