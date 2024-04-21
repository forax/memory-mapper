package com.github.forax.memorymapper;

import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.VarHandle;
import java.util.List;
import java.util.RandomAccess;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

public sealed interface MemoryAccess<T> permits MemoryAccessFactory.MemoryAccessImpl {
  MemoryLayout layout();

  default MemorySegment newValue(Arena arena) {
    requireNonNull(arena, "arena is null");
    return arena.allocate(layout());
  }

  default MemorySegment newArray(Arena arena, long size) {
    requireNonNull(arena, "arena is null");
    return arena.allocate(layout(), size);
  }

  VarHandle vh(String path);

  long byteOffset(String path);

  T read(MemorySegment segment);

  default Stream<T> stream(MemorySegment segment) {
    requireNonNull(segment, "segment is null");
    return segment.elements(layout()).map(this::read);
  }

  void write(MemorySegment segment, T record);

  default void writeAll(MemorySegment segment, Iterable<? extends T> iterable) {
    requireNonNull(segment, "segment is null");
    requireNonNull(segment, "iterable is null");
    if (iterable instanceof List<? extends T> list && iterable instanceof RandomAccess) {
      writeAll(segment, list);
      return;
    }
    var layout = layout();
    var offset = 0L;
    for(var element : iterable) {
      var slice = segment.asSlice(offset, layout);
      write(slice, element);
      offset += layout.byteSize();
    }
  }

  private void writeAll(MemorySegment segment, List<? extends T> list) {
    var layout = layout();
    for (int i = 0; i < list.size(); i++) {
      var element = list.get(i);
      var slice = segment.asSlice(i * layout.byteSize(), layout);
      write(slice, element);
    }
  }

  default void writeAll(MemorySegment segment, Stream<? extends T> stream) {
    requireNonNull(segment, "segment is null");
    requireNonNull(segment, "iterable is null");
    var layout = layout();
    var box = new Object() { long offset; };
    stream.forEach(element -> {
      var slice = segment.asSlice(box.offset, layout);
      write(slice, element);
      box.offset += layout.byteSize();
    });
  }

  static <T extends Record> MemoryAccess<T> reflect(Lookup lookup, Class<T> recordType) {
    requireNonNull(lookup, "lookup is null");
    requireNonNull(recordType, "recordType is null");
    return MemoryAccessFactory.create(lookup, recordType, MemoryAccessFactory::defaultLayout, MemoryAccessFactory::defaultPath);
  }
}



