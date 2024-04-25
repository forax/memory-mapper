package com.github.forax.memorymapper.bench;

import com.github.forax.memorymapper.MemoryAccess;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.VarHandle;
import java.util.concurrent.TimeUnit;

import static java.lang.invoke.MethodHandles.lookup;
/*
// $JAVA_HOME/bin/java -jar target/benchmarks.jar -prof dtraceasm
@Warmup(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class MemoryAccessBenchmarks {
  record Point(int x, int y) {}

  private static final MemoryAccess<Point> POINT_ACCESS = MemoryAccess.reflect(lookup(), Point.class);
  private static final VarHandle POINT_ACCESS_X = MemoryAccess.varHandle(POINT_ACCESS, ".x");
  private static final VarHandle POINT_ACCESS_Y = MemoryAccess.varHandle(POINT_ACCESS, ".y");
  private static final VarHandle ARRAY_ACCESS_X = MemoryAccess.varHandle(POINT_ACCESS, "[].x");
  private static final VarHandle ARRAY_ACCESS_Y = MemoryAccess.varHandle(POINT_ACCESS, "[].y");

  private static final MemoryLayout POINT_LAYOUT = MemoryAccess.layout(POINT_ACCESS);
  private static final VarHandle POINT_X = POINT_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("x"));
  private static final VarHandle POINT_Y = POINT_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("y"));
  private static final VarHandle ARRAY_POINT_X = POINT_LAYOUT.arrayElementVarHandle(MemoryLayout.PathElement.groupElement("x"));
  private static final VarHandle ARRAY_POINT_Y = POINT_LAYOUT.arrayElementVarHandle(MemoryLayout.PathElement.groupElement("y"));

  private final MemorySegment pointSegment = POINT_ACCESS.newValue(Arena.ofAuto());

  private final MemorySegment arraySegment = POINT_ACCESS.newArray(Arena.ofAuto(), 1_000);


  @Benchmark
  public int item_varHandle() {
    var x = (int) POINT_X.get(pointSegment, 0L);
    var y = (int) POINT_Y.get(pointSegment, 0L);
    return x + y;
  }
  @Benchmark
  public int item_varHandle_access() {
    var x = (int) POINT_ACCESS_X.get(pointSegment, 0L);
    var y = (int) POINT_ACCESS_Y.get(pointSegment, 0L);
    return x + y;
  }
  @Benchmark
  public int item_record_access() {
    var point = POINT_ACCESS.get(pointSegment);
    return point.x + point.y;
  }


  @Benchmark
  public int array_varHandle() {
    var sum = 0;
    for(var i = 0L; i < 1_000L; i++) {
      var x = (int) ARRAY_POINT_X.get(arraySegment, 0L, i);
      var y = (int) ARRAY_POINT_Y.get(arraySegment, 0L, i);
      sum += x + y;
    }
    return sum;
  }

  @Benchmark
  public int array_varHandle_access() {
    var sum = 0;
    for(var i = 0L; i < 1_000L; i++) {
      var x = (int) ARRAY_ACCESS_X.get(arraySegment, 0L, i);
      var y = (int) ARRAY_ACCESS_Y.get(arraySegment, 0L, i);
      sum += x + y;
    }
    return sum;
  }

  @Benchmark
  public int array_record_access() {
    var sum = 0;
    for(var i = 0L; i < 1_000L; i++) {
      var point = POINT_ACCESS.getAtIndex(arraySegment, i);
      sum += point.x + point.y;
    }
    return sum;
  }
}
*/
