package com.github.forax.memorymapper;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.ValueLayout;
import java.nio.ByteOrder;
import java.util.stream.IntStream;

import static java.lang.invoke.MethodHandles.lookup;
import static org.junit.jupiter.api.Assertions.*;

public class MemoryAccessTest {
  @Nested
  public class AccessLayout {

    @Test
    public void layoutStructPoint() {
      record Point(int x, int y) {
      }

      var access = MemoryAccess.reflect(lookup(), Point.class);
      assertEquals(access.layout(), MemoryLayout.structLayout(
          ValueLayout.JAVA_INT.withName("x"),
          ValueLayout.JAVA_INT.withName("y")
      ));
    }

    @Test
    public void layoutStructOfStruct() {
      record Foo(int v) {
      }
      record Bar(int s, Foo foo) {
      }

      var access = MemoryAccess.reflect(lookup(), Bar.class);
      assertEquals(access.layout(), MemoryLayout.structLayout(
          ValueLayout.JAVA_INT.withName("s"),
          MemoryLayout.structLayout(
              ValueLayout.JAVA_INT.withName("v")
          ).withName("foo")
      ));
    }

    @Test
    public void layoutStructOffset() {
      record Point(int x, int y) {
      }

      var access = MemoryAccess.reflect(lookup(), Point.class);
      assertAll(
          () -> assertEquals(8, access.layout().byteSize()),
          () -> assertEquals(0, access.byteOffset(".x")),
          () -> assertEquals(4, access.byteOffset(".y"))
      );
    }

    @Test
    public void layoutStructOffset2() {
      record Pair(byte b1, byte b2) {
      }

      var access = MemoryAccess.reflect(lookup(), Pair.class);
      assertAll(
          () -> assertEquals(2, access.layout().byteSize()),
          () -> assertEquals(0, access.byteOffset(".b1")),
          () -> assertEquals(1, access.byteOffset(".b2"))
      );
    }

    @Test
    public void layoutStructWithAlignmentIssue() {
      record Foo(short s, int i) {
      }

      var access = MemoryAccess.reflect(lookup(), Foo.class);
      assertAll(
          () -> assertEquals(8, access.layout().byteSize()),
          () -> assertEquals(0, access.byteOffset(".s")),
          () -> assertEquals(4, access.byteOffset(".i"))
      );
    }

    @Test
    public void layoutStructWithAlignmentIssue2() {
      record Foo(byte b, short s, int i) {
      }

      var access = MemoryAccess.reflect(lookup(), Foo.class);
      assertAll(
          () -> assertEquals(8, access.layout().byteSize()),
          () -> assertEquals(0, access.byteOffset(".b")),
          () -> assertEquals(2, access.byteOffset(".s")),
          () -> assertEquals(4, access.byteOffset(".i"))
      );
    }

    @Test
    public void layoutStructWithAlignmentIssue3() {
      record Foo(byte b, byte b2, int i) {
      }

      var access = MemoryAccess.reflect(lookup(), Foo.class);
      assertAll(
          () -> assertEquals(8, access.layout().byteSize()),
          () -> assertEquals(0, access.byteOffset(".b")),
          () -> assertEquals(1, access.byteOffset(".b2")),
          () -> assertEquals(4, access.byteOffset(".i"))
      );
    }

    @Test
    public void layoutStructWithAlignmentIssue4() {
      record Foo(int i, byte b) {
      }

      var access = MemoryAccess.reflect(lookup(), Foo.class);
      assertAll(
          () -> assertEquals(8, access.layout().byteSize()),
          () -> assertEquals(0, access.byteOffset(".i")),
          () -> assertEquals(4, access.byteOffset(".b"))
      );
    }

    @Test
    public void layoutStructWithAlignmentIssue5() {
      record Foo(char c, long l, int i) {
      }

      var access = MemoryAccess.reflect(lookup(), Foo.class);
      assertAll(
          () -> assertEquals(24, access.layout().byteSize()),
          () -> assertEquals(0, access.byteOffset(".c")),
          () -> assertEquals(8, access.byteOffset(".l")),
          () -> assertEquals(16, access.byteOffset(".i"))
      );
    }
  }

  @Nested
  public class VarHandleAccess {

    @Test
    public void intVarHandle() {
      record IntValue(int value) {
      }

      var access = MemoryAccess.reflect(lookup(), IntValue.class);
      try (var arena = Arena.ofConfined()) {
        var segment = access.newValue(arena);

        access.vh(".value").set(segment, 0L, 42);
        var value = (int) access.vh(".value").get(segment, 0L);
        assertEquals(42, value);
      }
    }

    @Test
    public void newValueStruct() {
      record Point(int x, int y) {
      }

      var access = MemoryAccess.reflect(lookup(), Point.class);
      try (var arena = Arena.ofConfined()) {
        var segment = access.newValue(arena);

        access.vh(".x").set(segment, 0L, 45);
        access.vh(".y").set(segment, 0L, 99);

        assertEquals(45, (int) access.vh(".x").get(segment, 0L));
        assertEquals(99, (int) access.vh(".y").get(segment, 0L));
      }
    }

    @Test
    public void newArrayOfInt() {
      record IntValue(int value) {
      }

      var access = MemoryAccess.reflect(lookup(), IntValue.class);
      try (var arena = Arena.ofConfined()) {
        var segment = access.newArray(arena, 4);

        for (var i = 0L; i < 4L; i++) {
          access.vh("[].value").set(segment, 0L, i, 42);
        }

        for (var i = 0L; i < 4L; i++) {
          var value = (int) access.vh("[].value").get(segment, 0L, i);
          assertEquals(42, value);
        }
      }
    }

    @Test
    public void varHandleSharing() {
      record Foo(int value) {
      }

      var access = MemoryAccess.reflect(lookup(), Foo.class);
      assertSame(access.vh(".value"), access.vh(".value"));
    }
  }

  @Nested
  public class Annotation {
    @Test
    public void structByDefault() {
      @Layout
      record Foo(int value) {}

      var access = MemoryAccess.reflect(lookup(), Foo.class);
      assertEquals(access.layout(), MemoryLayout.structLayout(
          ValueLayout.JAVA_INT.withName("value")
      ));
    }

    @Test
    public void structExplicit() {
      @Layout(kind = Layout.Kind.STRUCT)
      record Foo(float value) {}

      var access = MemoryAccess.reflect(lookup(), Foo.class);
      assertEquals(access.layout(), MemoryLayout.structLayout(
          ValueLayout.JAVA_FLOAT.withName("value")
      ));
    }

    @Test
    public void union() {
      @Layout(kind = Layout.Kind.UNION)
      record Foo(int value, long value2) {}

      var access = MemoryAccess.reflect(lookup(), Foo.class);
      assertEquals(access.layout(), MemoryLayout.unionLayout(
          ValueLayout.JAVA_INT.withName("value"),
          ValueLayout.JAVA_LONG.withName("value2")
      ));
    }

    @Test
    public void noAutoPadding() {
      @Layout(autoPadding = false)
      record Foo(short s, @LayoutElement(byteAlignment = 1) int i) {}

      var access = MemoryAccess.reflect(lookup(), Foo.class);
      assertEquals(access.layout(), MemoryLayout.structLayout(
          ValueLayout.JAVA_SHORT.withName("s"),
          ValueLayout.JAVA_INT.withName("i").withByteAlignment(1)
      ));
    }

    @Test
    public void explicitPadding() {
      @Layout(autoPadding = false)
      record Foo(short s, @LayoutElement(padding = 2) int i) {}

      var access = MemoryAccess.reflect(lookup(), Foo.class);
      assertEquals(access.layout(), MemoryLayout.structLayout(
          ValueLayout.JAVA_SHORT.withName("s"),
          MemoryLayout.paddingLayout(2L),
          ValueLayout.JAVA_INT.withName("i")
      ));
    }

    @Test
    public void elementName() {
      record Foo(@LayoutElement(name = "bar") int value) {}

      var access = MemoryAccess.reflect(lookup(), Foo.class);
      assertEquals(access.layout(), MemoryLayout.structLayout(
          ValueLayout.JAVA_INT.withName("bar")
      ));
    }

    @Test
    public void elementByteOrderLittleEndian() {
      record Foo(@LayoutElement(byteOrder = LayoutElement.ByteOrder.LITTLE_ENDIAN) int value) {}

      var access = MemoryAccess.reflect(lookup(), Foo.class);
      assertEquals(access.layout(), MemoryLayout.structLayout(
          ValueLayout.JAVA_INT.withName("value").withOrder(ByteOrder.LITTLE_ENDIAN)
      ));
    }

    @Test
    public void elementByteOrderBigEndian() {
      record Foo(@LayoutElement(byteOrder = LayoutElement.ByteOrder.BIG_ENDIAN) int value) {}

      var access = MemoryAccess.reflect(lookup(), Foo.class);
      assertEquals(access.layout(), MemoryLayout.structLayout(
          ValueLayout.JAVA_INT.withName("value").withOrder(ByteOrder.BIG_ENDIAN)
      ));
    }

    @Test
    public void elementByteAlignment() {
      record Foo(byte b, @LayoutElement(byteAlignment = 1) int i) {}

      var access = MemoryAccess.reflect(lookup(), Foo.class);
      assertAll(
          () -> assertEquals(0, access.byteOffset(".b")),
          () -> assertEquals(1, access.byteOffset(".i"))
      );
    }
  }

  @Nested
  public class MappingRead {
    @Test
    public void readStructPoint() {
      record Point(int x, int y) {}

      var access = MemoryAccess.reflect(lookup(), Point.class);
      try(var arena = Arena.ofConfined()) {
        var segment = access.newValue(arena);
        access.vh(".x").set(segment, 0L, 42);

        var point = access.read(segment);
        assertEquals(new Point(42, 0), point);
      }
    }

    @Test
    public void readStructOfStruct() {
      record Coordinate(int value) {}
      record Point(Coordinate x, Coordinate y) {}

      var access = MemoryAccess.reflect(lookup(), Point.class);
      try(var arena = Arena.ofConfined()) {
        var segment = access.newValue(arena);
        access.vh(".x.value").set(segment, 0L, 42);

        var point = access.read(segment);
        assertEquals(new Point(new Coordinate(42), new Coordinate(0)), point);
      }
    }

    @Test
    public void readUnionNotSupported() {
      @Layout(kind = Layout.Kind.UNION)
      record Bad(int value) {}

      var access = MemoryAccess.reflect(lookup(), Bad.class);
      try(var arena = Arena.ofConfined()) {
        var segment = access.newValue(arena);

        assertThrows(UnsupportedOperationException.class, () -> access.read(segment));
      }
    }

    @Test
    public void readMemberUnionNotSupported() {
      @Layout(kind = Layout.Kind.UNION)
      record Union(int value) {}
      record Bad(Union union) {}

      var access = MemoryAccess.reflect(lookup(), Bad.class);
      try(var arena = Arena.ofConfined()) {
        var segment = access.newValue(arena);

        assertThrows(UnsupportedOperationException.class, () -> access.read(segment));
      }
    }

    @Test
    public void streamOfPoints() {
      record Point(int x, int y) {}

      var access = MemoryAccess.reflect(lookup(), Point.class);
      try(var arena = Arena.ofConfined()) {
        var segment = access.newArray(arena, 1_000);
        for(var i = 0L; i < 1_000L; i++) {
          access.vh("[].x").set(segment, 0L, i, 42);
        }

        var points = access.stream(segment);
        assertTrue(points.allMatch(p -> p.x == 42));
      }
    }
  }

  @Nested
  public class MappingWrite {
    @Test
    public void writeStructPoint() {
      record Point(int x, int y) {}

      var access = MemoryAccess.reflect(lookup(), Point.class);
      try (var arena = Arena.ofConfined()) {
        var segment = access.newValue(arena);

        access.write(segment, new Point(42, 17));

        assertAll(
            () -> assertEquals(42, (int) access.vh(".x").get(segment, 0L)),
            () -> assertEquals(17, (int) access.vh(".y").get(segment, 0L))
        );
      }
    }

    @Test
    public void writeStructOfStruct() {
      record Coordinate(int value) {}
      record Point(Coordinate x, Coordinate y) {}

      var access = MemoryAccess.reflect(lookup(), Point.class);
      try(var arena = Arena.ofConfined()) {
        var segment = access.newValue(arena);
        access.write(segment, new Point(new Coordinate(-5), new Coordinate(13)));

        assertAll(
            () -> assertEquals(-5, (int) access.vh(".x.value").get(segment, 0L)),
            () -> assertEquals(13, (int) access.vh(".y.value").get(segment, 0L))
        );
      }
    }

    @Test
    public void writeUnionNotSupported() {
      @Layout(kind = Layout.Kind.UNION)
      record Bad(int value) {}

      var access = MemoryAccess.reflect(lookup(), Bad.class);
      try(var arena = Arena.ofConfined()) {
        var segment = access.newValue(arena);

        assertThrows(UnsupportedOperationException.class, () -> access.write(segment, new Bad(3)));
      }
    }

    @Test
    public void writeMemberUnionNotSupported() {
      @Layout(kind = Layout.Kind.UNION)
      record Union(int value) {}
      record Bad(Union union) {}

      var access = MemoryAccess.reflect(lookup(), Bad.class);
      try(var arena = Arena.ofConfined()) {
        var segment = access.newValue(arena);

        assertThrows(UnsupportedOperationException.class, () -> access.write(segment, new Bad(new Union(3))));
      }
    }

    @Test
    public void writeAllIterablePoints() {
      record Point(int x, int y) {}

      var access = MemoryAccess.reflect(lookup(), Point.class);
      try(var arena = Arena.ofConfined()) {
        var segment = access.newArray(arena, 1_000);
        var points = (Iterable<Point>) IntStream.range(0, 1_000)
            .mapToObj(i -> new Point(i, i))
            ::iterator;
        access.writeAll(segment, points);

        for(var i = 0L; i < 1_000L; i++) {
          assertEquals((int) i, (int) access.vh("[].x").get(segment, 0L, i));
          assertEquals((int) i, (int) access.vh("[].y").get(segment, 0L, i));
        }
      }
    }

    @Test
    public void writeAllListPoints() {
      record Point(int x, int y) {}

      var access = MemoryAccess.reflect(lookup(), Point.class);
      try(var arena = Arena.ofConfined()) {
        var segment = access.newArray(arena, 1_000);
        var points = IntStream.range(0, 1_000)
            .mapToObj(i -> new Point(i, i))
            .toList();
        access.writeAll(segment, points);

        for(var i = 0L; i < 1_000L; i++) {
          assertEquals((int) i, (int) access.vh("[].x").get(segment, 0L, i));
          assertEquals((int) i, (int) access.vh("[].y").get(segment, 0L, i));
        }
      }
    }

    @Test
    public void writeAllIStreamPoints() {
      record Point(int x, int y) {}

      var access = MemoryAccess.reflect(lookup(), Point.class);
      try(var arena = Arena.ofConfined()) {
        var segment = access.newArray(arena, 1_000);
        var points = IntStream.range(0, 1_000)
            .mapToObj(i -> new Point(i, i));
        access.writeAll(segment, points);

        for(var i = 0L; i < 1_000L; i++) {
          assertEquals((int) i, (int) access.vh("[].x").get(segment, 0L, i));
          assertEquals((int) i, (int) access.vh("[].y").get(segment, 0L, i));
        }
      }
    }
  }
}