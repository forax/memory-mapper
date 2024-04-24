package com.github.forax.memorymapper.bench;

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
import java.lang.foreign.StructLayout;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.util.concurrent.TimeUnit;

import static java.lang.invoke.MethodHandles.constant;
import static java.lang.invoke.MethodHandles.dropArguments;
import static java.lang.invoke.MethodHandles.guardWithTest;
import static java.lang.invoke.MethodHandles.lookup;
import static java.lang.invoke.MethodType.methodType;

@Warmup(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class ReproducerBenchmarks {
  private static final StructLayout LAYOUT = MemoryLayout.structLayout(
      ValueLayout.JAVA_INT.withName("x"),
      ValueLayout.JAVA_INT.withName("y")
  );

  private static final VarHandle HANDLE_X =
      LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("x"));
  private static final VarHandle HANDLE_Y =
      LAYOUT.varHandle(MemoryLayout.PathElement.groupElement("y"));

  private static boolean test(String s, String s2) {
    return s == s2;
  }
  private static VarHandle boom(String s) {
    throw new AssertionError("boom");
  }

  private static final MethodHandle TEST, BOOM;
  static {
    var lookup = lookup();
    try {
      TEST = lookup.findStatic(ReproducerBenchmarks.class, "test",
          methodType(boolean.class, String.class, String.class));
      BOOM = lookup.findStatic(ReproducerBenchmarks.class, "boom",
          methodType(VarHandle.class, String.class));
    } catch (NoSuchMethodException | IllegalAccessException e) {
      throw new AssertionError(e);
    }
  }

  private static final MethodHandle MH_X = guardWithTest(
      TEST.bindTo("x"),
      dropArguments(constant(VarHandle.class, HANDLE_X), 0, String.class),
      BOOM);
  private static final MethodHandle MH_Y = guardWithTest(
      TEST.bindTo("y"),
      dropArguments(constant(VarHandle.class, HANDLE_Y), 0, String.class),
      BOOM);

  private static final MethodHandle MH = guardWithTest(
      TEST.bindTo("x"),
      dropArguments(constant(VarHandle.class, HANDLE_X), 0, String.class),
      guardWithTest(
          TEST.bindTo("y"),
          dropArguments(constant(VarHandle.class, HANDLE_Y), 0, String.class),
          BOOM
      ));

  private final MemorySegment segment = Arena.ofAuto().allocate(LAYOUT);

  @Benchmark
  public int control() {
    var x = (int) HANDLE_X.get(segment, 0L);
    var y = (int) HANDLE_Y.get(segment, 0L);
    return x + y;
  }

  @Benchmark
  public int gwt_methodhandle() throws Throwable {
    var x = (int) ((VarHandle) MH_X.invokeExact("x")).get(segment, 0L);
    var y = (int) ((VarHandle) MH_Y.invokeExact("y")).get(segment, 0L);
    return x + y;
  }

  @Benchmark
  public int gwt2_methodhandle() throws Throwable {
    var x = (int) ((VarHandle) MH.invokeExact("x")).get(segment, 0L);
    var y = (int) ((VarHandle) MH.invokeExact("y")).get(segment, 0L);
    return x + y;
  }
}
