package com.github.forax.memorymapper.bench;
/*
import com.github.forax.memorymapper.MemoryCollections;
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

import java.lang.foreign.Arena;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toCollection;

// Benchmark                                               Mode  Cnt     Score     Error  Units
// MemoryCollectionsBenchmarks.getArrayList                avgt    5   343.566 ±   1.958  ns/op
// MemoryCollectionsBenchmarks.getSpecializedList          avgt    5   273.605 ±   1.518  ns/op
// MemoryCollectionsBenchmarks.iteratorArrayList           avgt    5   357.070 ±   0.504  ns/op
// MemoryCollectionsBenchmarks.iteratorSpecializedList     avgt    5   275.895 ±   1.356  ns/op

// MemoryCollectionsBenchmarks.newArrayList                avgt    5     4.053 ±   0.042  ns/op
// MemoryCollectionsBenchmarks.newSpecializedList          avgt    5    14.892 ±   0.232  ns/op
// MemoryCollectionsBenchmarks.newSpecializedListAuto      avgt    5   548.867 ± 681.347  ns/op
// MemoryCollectionsBenchmarks.newSpecializedListConfined  avgt    5    89.927 ±   0.452  ns/op
// MemoryCollectionsBenchmarks.newSpecializedListShared    avgt    5  7179.651 ± 256.424  ns/op

// $JAVA_HOME/bin/java -jar target/benchmarks.jar -prof dtraceasm
@Warmup(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class MemoryCollectionsBenchmarks {
  record Point(int x, int y) {}

  @Benchmark
  public void newArrayList(Blackhole blackhole) {
    var list = new ArrayList<>(16);
    blackhole.consume(list);
  }

  @Benchmark
  public void newSpecializedListShared(Blackhole blackhole) {
    try(var arena = Arena.ofShared()) {
      var list = MemoryCollections.newSpecializedList(arena, Point.class, 16);
      blackhole.consume(list);
    }
  }
  @Benchmark
  public void newSpecializedListConfined(Blackhole blackhole) {
    try(var arena = Arena.ofConfined()) {
      var list = MemoryCollections.newSpecializedList(arena, Point.class, 16);
      blackhole.consume(list);
    }
  }
  @Benchmark
  public void newSpecializedListAuto(Blackhole blackhole) {
    var list = MemoryCollections.newSpecializedList(Arena.ofAuto(), Point.class, 16);
    blackhole.consume(list);
  }

  @Benchmark
  public void newSpecializedList(Blackhole blackhole) {
    var list = MemoryCollections.newSpecializedList(Point.class);
    blackhole.consume(list);
  }


  private final List<Integer> arrayList = IntStream.range(0, 1_000).boxed().collect(toCollection(ArrayList::new));
  private final List<Integer> specializedList = IntStream.range(0, 1_000).boxed().collect(toCollection(() -> MemoryCollections.newSpecializedList(int.class)));

  @Benchmark
  public int getArrayList() {
    var sum = 0;
    for(var i = 0; i < arrayList.size(); i++) {
      sum += arrayList.get(i);
    }
    return sum;
  }
  @Benchmark
  public int getSpecializedList() {
    var sum = 0;
    for(var i = 0; i < specializedList.size(); i++) {
      sum += specializedList.get(i);
    }
    return sum;
  }

  @Benchmark
  public int iteratorArrayList() {
    var sum = 0;
    for (int integer : arrayList) {
      sum += integer;
    }
    return sum;
  }
  @Benchmark
  public int iteratorSpecializedList() {
    var sum = 0;
    for (int integer : specializedList) {
      sum += integer;
    }
    return sum;
  }
}
*/

