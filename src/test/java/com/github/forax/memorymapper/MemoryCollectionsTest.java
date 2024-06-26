package com.github.forax.memorymapper;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.stream.IntStream.range;
import static org.junit.jupiter.api.Assertions.*;

public class MemoryCollectionsTest {
  @Nested
  public class SpecializedList {

    @Test
    public void newSpecializedListEmpty() {
      record Point(int x, int y) {}

      var list = MemoryCollections.newSpecializedList(Point.class);
      assertEquals(List.of(), list);
    }

    @Test
    public void newSpecializedListWithOneElement() {
      record Point(int x, int y) {}

      var list = MemoryCollections.newSpecializedList(Point.class);
      list.add(new Point(1, 2));
      assertEquals(List.of(new Point(1, 2)), list);
    }

    @Test
    public void newSpecializedListEquals() {
      record Point(int x, int y) {}

      var list1 = MemoryCollections.newSpecializedList(Point.class);
      range(0, 1_000).forEach(i -> list1.add(new Point(i, i)));
      var list2 = MemoryCollections.newSpecializedList(Point.class);
      range(0, 1_000).forEach(i -> list2.add(new Point(i, i)));
      assertEquals(list1, list2);
    }

    @Test
    public void newSpecializedListNotEquals() {
      record Point(int x, int y) {}

      var list1 = MemoryCollections.newSpecializedList(Point.class);
      range(0, 1_000).forEach(i -> list1.add(new Point(i % 100, i % 100)));
      var list2 = MemoryCollections.newSpecializedList(Point.class);
      range(0, 1_000).forEach(i -> list2.add(new Point(i, i)));
      assertNotEquals(list1, list2);
    }


    @Test
    public void newSpecializedListPrimitiveEmpty() {
      var list = MemoryCollections.newSpecializedList(int.class);
      assertEquals(List.of(), list);
    }

    @Test
    public void newSpecializedListPrimitiveBoolean() {
      var list = MemoryCollections.newSpecializedList(boolean.class, 777);
      range(0, 2_000).forEach(i -> list.add(i % 2 == 0));

      for(var i = 0; i < list.size(); i++) {
        assertEquals(list.get(i), i % 2 == 0, "" + i);
      }
    }
    @Test
    public void newSpecializedListPrimitiveByte() {
      var list = MemoryCollections.newSpecializedList(byte.class, 777);
      range(0, 2_000).forEach(i -> list.add((byte) (i % 2)));

      for(var i = 0; i < list.size(); i++) {
        assertEquals(list.get(i), (byte) (i % 2), "" + i);
      }
    }
    @Test
    public void newSpecializedListPrimitiveShort() {
      var list = MemoryCollections.newSpecializedList(short.class, 777);
      range(0, 2_000).forEach(i -> list.add((short) (i % 2)));

      for(var i = 0; i < list.size(); i++) {
        assertEquals(list.get(i), (short) (i % 2), "" + i);
      }
    }
    @Test
    public void newSpecializedListPrimitiveChar() {
      var list = MemoryCollections.newSpecializedList(char.class, 777);
      range(0, 2_000).forEach(i -> list.add((char) (i % 2)));

      for(var i = 0; i < list.size(); i++) {
        assertEquals(list.get(i), (char) (i % 2), "" + i);
      }
    }
    @Test
    public void newSpecializedListPrimitiveInt() {
      var list = MemoryCollections.newSpecializedList(int.class, 777);
      range(0, 2_000).forEach(i -> list.add(i % 2));

      for(var i = 0; i < list.size(); i++) {
        assertEquals(list.get(i), i % 2, "" + i);
      }
    }
    @Test
    public void newSpecializedListPrimitiveLong() {
      var list = MemoryCollections.newSpecializedList(long.class, 777);
      range(0, 2_000).forEach(i -> list.add((long) (i % 2)));

      for(var i = 0; i < list.size(); i++) {
        assertEquals(list.get(i), i % 2, "" + i);
      }
    }
    @Test
    public void newSpecializedListPrimitiveFloat() {
      var list = MemoryCollections.newSpecializedList(float.class, 777);
      range(0, 2_000).forEach(i -> list.add((float) (i % 2)));

      for(var i = 0; i < list.size(); i++) {
        assertEquals(list.get(i), (float) (i % 2), "" + i);
      }
    }
    @Test
    public void newSpecializedListPrimitiveDouble() {
      var list = MemoryCollections.newSpecializedList(double.class, 777);
      range(0, 2_000).forEach(i -> list.add((double) (i % 2)));

      for(var i = 0; i < list.size(); i++) {
        assertEquals(list.get(i), i % 2, "" + i);
      }
    }


    @Test
    public void newSpecializedListPrimitiveWithOneElement() {
      var list = MemoryCollections.newSpecializedList(int.class);
      list.add(42);
      assertEquals(List.of(42), list);
    }

    @Test
    public void newSpecializedListPrimitiveEquals() {
      var list1 = MemoryCollections.newSpecializedList(int.class, 1_000);
      range(0, 1_000).forEach(list1::add);
      var list2 = MemoryCollections.newSpecializedList(int.class, 1_000);
      range(0, 1_000).forEach(list2::add);
      assertEquals(list1, list2);
    }

    @Test
    public void newSpecializedListPrimitiveNotEquals() {
      var list1 = MemoryCollections.newSpecializedList(int.class, 1_000);
      range(0, 1_000).forEach(i -> list1.add(i % 100));
      var list2 = MemoryCollections.newSpecializedList(int.class, 1_000);
      range(0, 1_000).forEach(list2::add);
      assertNotEquals(list1, list2);
    }

    @Test @EnabledOnOs(OS.LINUX)
    public void capacityOutOfBounds() {
      var list = MemoryCollections.newSpecializedList(byte.class);
      assertThrows(OutOfMemoryError.class, () -> {
        for (var i = 0L; i < 3_000_000_000L; i++) {
          list.add((byte) 42);
        }
      });
    }
  }

  @Nested
  public class SpecializedMap {
    @Test
    public void newSpecializedMapBooleanToBoolean() {
      var map = MemoryCollections.newSpecializedMap(boolean.class, boolean.class);
      map.put(true, false);
      assertAll(
          () -> assertEquals(1, map.size()),
          () -> assertEquals(Set.of(Map.entry(true, false)), map.entrySet())
      );
    }

    @Test
    public void newSpecializedMapWithOneElement() {
      record Point(int x, int y) {}

      var map = MemoryCollections.newSpecializedMap(int.class, Point.class);
      map.put(3, new Point(4, 5));

      assertAll(
          () -> assertEquals(1, map.size()),
          () -> assertEquals(new Point(4, 5), map.get(3)),
          () -> assertTrue(map.containsKey(3)),
          () -> assertEquals(Set.of(Map.entry(3, new Point(4, 5))), map.entrySet())
      );
    }

    @Test
    public void newSpecializedMapEmptyEntrySet() {
      var map = MemoryCollections.newSpecializedMap(int.class, int.class);
      assertAll(
          () -> assertEquals(0, map.size()),
          () -> assertEquals(Set.of(), map.entrySet()),
          () -> assertFalse(map.entrySet().iterator().hasNext())
      );
    }

    @Test
    public void newSpecializedMapEntryOneEntry() {
      var map = MemoryCollections.newSpecializedMap(int.class, int.class);
      map.put(2, 3);
      assertAll(
          () -> assertEquals(1, map.size()),
          () -> assertEquals(Set.of(Map.entry(2, 3)), map.entrySet()),
          () -> assertEquals(Map.entry(2, 3), map.entrySet().iterator().next())
      );
    }

    @Test
    public void newSpecializedMapContainsKeyWithSeveralIntegers() {
      var map = MemoryCollections.newSpecializedMap(int.class, int.class);
      range(0, 12).forEach(i -> map.put(i, i));

      assertAll(
          () -> range(0, 12).forEach(i -> assertEquals(i, map.get(i))),
          () -> range(0, 12).forEach(i -> assertEquals(i, map.getOrDefault(i, -1), "" + i)),
          () -> range(0, 12).forEach(i -> assertTrue(map.containsKey(i), "" + i))
      );

    }

    @Test
    public void newSpecializedMapReHashALot() {
      var map = MemoryCollections.newSpecializedMap(int.class, int.class, 2);
      range(0, 100_000).forEach(i -> map.put(i, i));

      assertAll(
          () -> assertEquals(100_000, map.size()),
          () -> range(0, 100_000).forEach(i -> assertEquals(i, map.getOrDefault(i, -1), "" + i)),
          () -> range(0, 100_000).forEach(i -> assertTrue(map.containsKey(i), "" + i))
      );
    }

    @Test
    public void newSpecializedMapOfInt() {
      record Pair(int fiest, int second) {}
      var map = MemoryCollections.newSpecializedMap(int.class, Pair.class);
      for(var i = 0; i < 100; i++) {
        map.put(i, new Pair(i, i));
      }
      assertEquals(100, map.size());
    }

    @Test
    public void newSpecializedMapPresized() {
      var map = MemoryCollections.newSpecializedMap(int.class, int.class, 4);
      for(var i = 0; i < 2; i++) {
        map.put(i, i);
      }
      assertEquals(2, map.size());
    }

    @Test  @EnabledOnOs(OS.LINUX)
    public void capacityOutOfBounds() {
      var map = MemoryCollections.newSpecializedMap(int.class, byte.class);
      assertThrows(OutOfMemoryError.class, () -> {
        for (var i = 0; i < Integer.MAX_VALUE; i++) {
          map.put(Integer.MIN_VALUE + i, (byte) 42);
        }
      });
    }
  }

  @Nested
  public class Allocator {
    private static SegmentAllocator stackAllocator(MemorySegment segment) {
      var slicingAllocator = SegmentAllocator.slicingAllocator(segment);
      return (byteSize, byteAlignment) -> {
        var slice = slicingAllocator.allocate(byteSize, byteAlignment);
        slice.fill((byte) 0);
        return slice;
      };
    }

    @Test
    public void stackAllocationList() {
      try(var arena = Arena.ofConfined()) {
        var segment = arena.allocate(2_048);

        for(var repeat = 0; repeat < 10_000; repeat++) {
          var allocator = stackAllocator(segment);

          record Point(int x, int y) {}
          var list = MemoryCollections.newSpecializedList(allocator, Point.class, 16);
          for(var i = 0; i < 100; i++) {
            list.add(new Point(i, i));
          }
          assertEquals(100, list.size());
        }
      }
    }

    @Test
    public void stackAllocationMap() {
      try(var arena = Arena.ofConfined()) {
        var segment = arena.allocate(8_192);

        for(var repeat = 0; repeat < 10_00; repeat++) {
          var allocator = stackAllocator(segment);

          record Point(int x, int y) {}
          var map = MemoryCollections.newSpecializedMap(allocator, int.class, Point.class, 16);
          for(var i = 0; i < 100; i++) {
            map.put(i, new Point(i, i));
          }
          assertEquals(100, map.size());
        }
      }
    }
  }
}