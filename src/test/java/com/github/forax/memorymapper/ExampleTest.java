package com.github.forax.memorymapper;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;

import static java.lang.invoke.MethodHandles.lookup;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ExampleTest {
  @Nested
  public class ExamplePoint {
    record Point(int x, int y) { }

    private static final MemoryAccess<Point> POINT = MemoryAccess.reflect(lookup(), Point.class);


    @Test
    public void newValue() {
      try(var arena = Arena.ofConfined()) {
        var point = POINT.newValue(arena);
        assertEquals(new Point(0, 0), POINT.get(point));
      }
    }

    @Test
    public void newValueWithInit() {
      try(var arena = Arena.ofConfined()) {
        var point = POINT.newValue(arena, new Point(1, 2));
        assertEquals(new Point(1, 2), POINT.get(point));
      }
    }

    @Test
    public void newValueAndSet() {
      try(var arena = Arena.ofConfined()) {
        var point = POINT.newValue(arena);
        POINT.set(point, new Point(1, 2));
        assertEquals(new Point(1, 2), POINT.get(point));
      }
    }

    @Test
    public void newArray() {
      try(var arena = Arena.ofConfined()) {
        var points = POINT.newArray(arena, 10);
        for(var i = 0; i < 10; i++) {
          assertEquals(new Point(0, 0), POINT.getAtIndex(points, i));
        }
      }
    }

    @Test
    public void newArrayIndexedGetAndSet() {
      try(var arena = Arena.ofConfined()) {
        var points = POINT.newArray(arena, 10);
        for(var i = 0; i < 10; i++) {
          POINT.setAtIndex(points, i, new Point(i, -i));
        }
        for(var i = 0; i < 10; i++) {
          assertEquals(new Point(i, -i), POINT.getAtIndex(points, i));
        }
      }
    }

    @Test
    public void newArrayAndList() {
      try(var arena = Arena.ofConfined()) {
        var segment = POINT.newArray(arena, 10);
        var points = POINT.list(segment);
        for(var i = 0; i < 10; i++) {
          points.set(i, new Point(0, 12));
        }
        for(var i = 0; i < 10; i++) {
          assertEquals(new Point(0, 12), points.get(i));
        }
      }
    }

    @Test
    public void newArrayAndStream() {
      try(var arena = Arena.ofConfined()) {
        var points = POINT.newArray(arena, 10);
        for(var i = 0; i < 10; i++) {
          POINT.setAtIndex(points, i, new Point(0, 12));
        }
        assertTrue(POINT.stream(points).allMatch(new Point(0, 12)::equals));
      }
    }
  }
}
