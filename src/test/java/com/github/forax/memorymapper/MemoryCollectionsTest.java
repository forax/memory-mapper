package com.github.forax.memorymapper;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
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
      record Point(int x, int y) {}

      var list = MemoryCollections.newSpecializedList(int.class);
      assertEquals(List.of(), list);
    }

    @Test
    public void newSpecializedListPrimitiveWithOneElement() {
      var list = MemoryCollections.newSpecializedList(int.class);
      list.add(42);
      assertEquals(List.of(42), list);
    }

    @Test
    public void newSpecializedListPrimitiveEquals() {
      var list1 = MemoryCollections.newSpecializedList(int.class);
      range(0, 1_000).forEach(list1::add);
      var list2 = MemoryCollections.newSpecializedList(int.class);
      range(0, 1_000).forEach(list2::add);
      assertEquals(list1, list2);
    }

    @Test
    public void newSpecializedListPrimitiveNotEquals() {
      var list1 = MemoryCollections.newSpecializedList(int.class);
      range(0, 1_000).forEach(i -> list1.add(i % 100));
      var list2 = MemoryCollections.newSpecializedList(int.class);
      range(0, 1_000).forEach(list2::add);
      assertNotEquals(list1, list2);
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
      var map = MemoryCollections.newSpecializedMap(int.class, int.class);
      range(0, 100_000).forEach(i -> map.put(i, i));

      assertAll(
          () -> assertEquals(100_000, map.size()),
          () -> range(0, 100_000).forEach(i -> assertEquals(i, map.getOrDefault(i, -1), "" + i)),
          () -> range(0, 100_000).forEach(i -> assertTrue(map.containsKey(i), "" + i))
      );
    }
  }

  @Nested
  public class Allocator {
    @Test
    public void stackAllocation() {
      try(var arena = Arena.ofConfined()) {
        var segment = arena.allocate(1_024);
        var allocator = SegmentAllocator.prefixAllocator(segment);

        record Point(int x, int y) {}
        for(var i = 0; i < 10_000; i++) {
          var list = MemoryCollections.newSpecializedList(allocator, Point.class);
          for(var v = 0; v < 100; v++) {
            list.add(new Point(v, v));
          }
          assertEquals(100, list.size());
        }
      }
    }
  }
}