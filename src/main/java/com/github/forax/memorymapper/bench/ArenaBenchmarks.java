package com.github.forax.memorymapper.bench;
/*
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import sun.misc.Unsafe;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.concurrent.TimeUnit;

// Benchmark                                Mode  Cnt     Score     Error  Units
// ArenaBenchmarks.newArray                 avgt    5     2.529 ±   0.013  ns/op
// ArenaBenchmarks.newSegmentAuto           avgt    5   409.666 ± 210.548  ns/op
// ArenaBenchmarks.newSegmentConfined       avgt    5    82.353 ±   0.263  ns/op
// ArenaBenchmarks.newSegmentShared         avgt    5  7094.796 ± 297.683  ns/op
// ArenaBenchmarks.newSegmentWrap           avgt    5     6.454 ±   0.113  ns/op
// ArenaBenchmarks.newUnsafeMemory          avgt    5    22.753 ±   0.023  ns/op
// ArenaBenchmarks.newUnsafeMemoryWithInit  avgt    5    71.563 ±   0.827  ns/op

// $JAVA_HOME/bin/java -jar target/benchmarks.jar -prof dtraceasm
@Warmup(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class ArenaBenchmarks {
  private static Unsafe UNSAFE;
  static {
    Unsafe unsafe;
    try {
      var unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
      unsafeField.setAccessible(true);
      unsafe = (Unsafe) unsafeField.get(null);
    } catch (NoSuchFieldException | IllegalAccessException e) {
      throw new AssertionError(e);
    }
    UNSAFE = unsafe;
  }

  @Benchmark
  public void newUnsafeMemory(Blackhole blackhole) {
    var memory = UNSAFE.allocateMemory(16 * 4);
    try {
      blackhole.consume(memory);
    } finally {
      UNSAFE.freeMemory(memory);
    }
  }

  @Benchmark
  public void newUnsafeMemoryWithInit(Blackhole blackhole) {
    var memory = UNSAFE.allocateMemory(16 * 4);
    try {
      UNSAFE.setMemory(memory, 16 * 4,  (byte) 0);
      blackhole.consume(memory);
    } finally {
      UNSAFE.freeMemory(memory);
    }
  }

  @Benchmark
  public void newArray(Blackhole blackhole) {
    var array = new int[16];
    blackhole.consume(array);
  }


  @Benchmark
  public void newSegmentWrap(Blackhole blackhole) {
    var array = new int[16];
    var segment = MemorySegment.ofArray(array);
    blackhole.consume(segment);
  }
  @Benchmark
  public void newSegmentShared(Blackhole blackhole) {
    try(var arena = Arena.ofShared()) {
      var segment = arena.allocate(16 * 4, 4);
      blackhole.consume(segment);
    }
  }
  @Benchmark
  public void newSegmentConfined(Blackhole blackhole) {
    try(var arena = Arena.ofConfined()) {
      var segment = arena.allocate(16 * 4, 4);
      blackhole.consume(segment);
    }
  }
  @Benchmark
  public void newSegmentAuto(Blackhole blackhole) {
    var arena = Arena.ofAuto();
    var segment = arena.allocate(16 * 4, 4);
    blackhole.consume(segment);
  }
}
*/


