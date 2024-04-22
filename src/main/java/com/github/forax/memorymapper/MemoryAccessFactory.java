package com.github.forax.memorymapper;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.PaddingLayout;
import java.lang.foreign.SequenceLayout;
import java.lang.foreign.StructLayout;
import java.lang.foreign.UnionLayout;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.invoke.MutableCallSite;
import java.lang.invoke.VarHandle;
import java.lang.reflect.RecordComponent;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;

import static java.lang.foreign.ValueLayout.JAVA_BOOLEAN;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_CHAR;
import static java.lang.foreign.ValueLayout.JAVA_DOUBLE;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;
import static java.lang.foreign.ValueLayout.JAVA_SHORT;
import static java.lang.foreign.MemoryLayout.PathElement;
import static java.lang.invoke.MethodHandles.constant;
import static java.lang.invoke.MethodHandles.dropArguments;
import static java.lang.invoke.MethodHandles.empty;
import static java.lang.invoke.MethodHandles.filterArguments;
import static java.lang.invoke.MethodHandles.foldArguments;
import static java.lang.invoke.MethodHandles.guardWithTest;
import static java.lang.invoke.MethodHandles.insertArguments;
import static java.lang.invoke.MethodHandles.lookup;
import static java.lang.invoke.MethodHandles.permuteArguments;
import static java.lang.invoke.MethodType.methodType;
import static java.util.Objects.requireNonNull;

/**
 * A factory to create fined tuned memory access.
 *
 * @see #create(Lookup, Class, Function, Function)
 */
public final class MemoryAccessFactory {
  static final long DEFAULT_ALIGNMENT = -1;
  static final long DEFAULT_PADDING = -1;
  static final String DEFAULT_NAME = "";

  @Layout
  private record AnnotationDefault(@LayoutElement Void element) {
    private static final Layout LAYOUT = AnnotationDefault.class.getAnnotation(Layout.class);
    private static final LayoutElement LAYOUT_ELEMENT =
        AnnotationDefault.class.getRecordComponents()[0].getAnnotation(LayoutElement.class);
  }

  static Layout layoutAnnotation(Class<?> recordType) {
    var layout = recordType.getAnnotation(Layout.class);
    return layout == null ? AnnotationDefault.LAYOUT : layout;
  }

  static LayoutElement layoutElementAnnotation(RecordComponent recordComponent) {
    var layoutElement = recordComponent.getAnnotation(LayoutElement.class);
    return layoutElement == null ? AnnotationDefault.LAYOUT_ELEMENT : layoutElement;
  }

  private static MemoryLayout withByteAlignment(MemoryLayout element, LayoutElement layoutElement) {
    if (layoutElement.alignment() == DEFAULT_ALIGNMENT) {
      return element;
    }
    return element.withByteAlignment(layoutElement.alignment());
  }

  private static MemoryLayout withName(MemoryLayout element, LayoutElement layoutElement, RecordComponent component) {
    var name = layoutElement.name().equals(DEFAULT_NAME) ? component.getName() : layoutElement.name();
    return element.withName(name);
  }

  private static MemoryLayout withOrder(MemoryLayout element, LayoutElement layoutElement) {
    if (layoutElement.order() == LayoutElement.ByteOrder.NATIVE) {
      return element;
    }
    if (!(element instanceof ValueLayout valueLayout)) {
      throw new IllegalStateException("byte order can only be defined on primitive type");
    }
    return valueLayout.withOrder(switch (layoutElement.order()) {
      case BIG_ENDIAN -> ByteOrder.BIG_ENDIAN;
      case LITTLE_ENDIAN -> ByteOrder.LITTLE_ENDIAN;
      case NATIVE -> throw new AssertionError();
    });
  }

  private static MemoryLayout record(Class<?> recordType, long byteUsed, boolean topLevel) {
    var layout = layoutAnnotation(recordType);
    var isStruct = layout.kind() == Layout.Kind.STRUCT;
    var autoPadding = layout.autoPadding() & isStruct;
    var components = recordType.getRecordComponents();
    var maxAlignment = 1L;
    var memberLayouts = new ArrayList<MemoryLayout>();
    for (var component : components) {
      var layoutElement = layoutElementAnnotation(component);
      var element = element(component.getType(), byteUsed);

      // byte alignment
      element = withByteAlignment(element, layoutElement);

      // padding
      long padding = layoutElement.padding();
      if (padding == DEFAULT_PADDING & autoPadding) {
        var byteAlignment = element.byteAlignment();
        maxAlignment = Math.max(maxAlignment, byteAlignment);
        var shift = byteUsed % byteAlignment;
        padding = shift != 0 ? byteAlignment - shift : DEFAULT_PADDING ;
      }
      if (padding != DEFAULT_PADDING) {
        memberLayouts.add(MemoryLayout.paddingLayout(padding));
        byteUsed += isStruct ? padding : 0;
      }

      // name
      element = withName(element, layoutElement, component);

      // byte order
      element = withOrder(element, layoutElement);

      memberLayouts.add(element);
      byteUsed += isStruct ? element.byteSize() : 0;
    }

    // add end padding so the layout can be used as an array element
    long padding = layout.endPadding();
    if (padding == DEFAULT_PADDING & autoPadding & topLevel) {
      var shift = byteUsed % maxAlignment;
      padding = shift != 0 ? maxAlignment - shift : DEFAULT_PADDING;
    }
    if (padding != DEFAULT_PADDING) {
      memberLayouts.add(MemoryLayout.paddingLayout(padding));
    }

    var layoutArray = memberLayouts.toArray(MemoryLayout[]::new);
    return isStruct ? MemoryLayout.structLayout(layoutArray) : MemoryLayout.unionLayout(layoutArray);
  }

  private static MemoryLayout element(Class<?> type, long byteUsed) {
    if (type.isPrimitive()) {
      return switch (type.getName()) {
        case "boolean" -> JAVA_BOOLEAN;
        case "byte" -> JAVA_BYTE;
        case "short" -> JAVA_SHORT;
        case "char" -> JAVA_CHAR;
        case "int" -> JAVA_INT;
        case "long" -> JAVA_LONG;
        case "float" -> JAVA_FLOAT;
        case "double" -> JAVA_DOUBLE;
        default -> throw new AssertionError();
      };
    }
    if (type.isArray()) {
      return MemoryLayout.sequenceLayout(0, element(type.getComponentType(), byteUsed));
    }
    if (type.isRecord()) {
      return record(type, byteUsed, false);
    }
    throw new IllegalStateException("invalid type " + type.getName());
  }

  /**
   * Returns a memory layout from the description of a record.
   * @param type the record type.
   * @return a memory layout from the description of a record.
   *
   * @see Layout
   * @see LayoutElement
   */
  public static MemoryLayout defaultLayout(Class<?> type) {
    requireNonNull(type, "type is null");
    if (!type.isRecord()) {
      throw new IllegalArgumentException(type.getName() + " is not a record");
    }
    return record(type, 0,true);
  }

  /**
   * Represent a path into the memory layout.
   */
  public sealed interface Path {
    /**
     * An array access, represented by default by the string {@code []}.
     */
    record ArrayPath() implements Path {}

    /**
     * A member access, represented by default by the string {@code .member}.
     * @param member the name of the member in the memory layout.
     */
    record FieldPath(String member) implements Path {
      public FieldPath {
        requireNonNull(member);
      }
    }
  }

  static PathElement toPathElement(Path path) {
    return switch (path) {
      case Path.ArrayPath _ -> PathElement.sequenceElement();
      case Path.FieldPath(var field) -> PathElement.groupElement(field);
    };
  }

  private static final Pattern PATH_PATTERN = Pattern.compile("\\.(\\w+)|\\[\\]");

  /**
   * Create a path from a string description.
   * @param path a string path.
   * @return a Path object corresponding to the string description.
   */
  public static List<Path> defaultPath(String path) {
    requireNonNull(path, "path is null");
    var matcher = PATH_PATTERN.matcher(path);
    return matcher
        .results()
        .<Path>map(result -> {
          if (result.start(1) != -1) {
            return new Path.FieldPath(result.group(1));
          }
          return new Path.ArrayPath();
        })
        .toList();
  }

  private static long offset(MemoryLayout layout, String path, Function<? super String, ? extends List<? extends Path>> pathFunction) {
    var paths = requireNonNull(pathFunction.apply(path), "pathFunction return value is null");
    var pathElements = paths.stream()
        .map(MemoryAccessFactory::toPathElement)
        .toArray(PathElement[]::new);
    return layout.byteOffset(pathElements);
  }

  private static VarHandle varHandle(MemoryLayout layout, String path, Function<? super String, ? extends List<? extends Path>> pathFunction) {
    var paths = requireNonNull(pathFunction.apply(path), "pathFunction return value is null");
    VarHandle varHandle;
    if (paths.getFirst() instanceof Path.ArrayPath) {
      varHandle = layout.arrayElementVarHandle(paths.stream().skip(1).map(MemoryAccessFactory::toPathElement).toArray(PathElement[]::new));
    } else {
      varHandle = layout.varHandle(paths.stream().map(MemoryAccessFactory::toPathElement).toArray(PathElement[]::new));
    }
    return varHandle.withInvokeExactBehavior();
  }

  private static final class Cache extends MutableCallSite {
    private static final MethodHandle FALLBACK, TEST;
    static {
      var lookup = MethodHandles.lookup();
      try {
        FALLBACK = lookup.findVirtual(Cache.class, "fallback", methodType(Object.class, String.class));
        TEST = lookup.findStatic(Cache.class, "test", methodType(boolean.class, String.class, String.class));
      } catch (NoSuchMethodException | IllegalAccessException e) {
        throw new AssertionError(e);
      }
    }

    private static boolean test(String s1, String s2) {
      return s1 == s2;
    }

    private final Function<String, Object> computeFunction;

    private Cache(Class<?> constantType, Function<String, Object> computeFunction) {
      super(methodType(constantType, String.class));
      this.computeFunction = computeFunction;
      setTarget(FALLBACK.asType(FALLBACK.type().changeReturnType(constantType)).bindTo(this));
    }

    private static void checkInterned(String path) {
      if (path != path.intern()) {
        throw new IllegalArgumentException("path is not an interned string");
      }
    }

    private Object fallback(String path) {
      checkInterned(path);
      var constantValue = computeFunction.apply(path);
      var constantType = type().returnType();
      var guard = guardWithTest(
          TEST.bindTo(path),
          dropArguments(constant(constantType, constantValue), 0, String.class),
          new Cache(constantType, computeFunction).dynamicInvoker());
      setTarget(guard);

      return constantValue;
    }
  }

  private static MethodHandle cache(Class<?> constantType, Function<String, Object> computeFunction) {
    return new Cache(constantType, computeFunction).dynamicInvoker();
  }

  private static MethodHandle findCanonicalConstructor(Lookup lookup, RecordComponent[] components, Class<?> recordType) {
    var parameterTypes = Arrays.stream(components)
        .map(RecordComponent::getType)
        .toArray(Class<?>[]::new);
    try {
      return lookup.findConstructor(recordType, methodType(void.class, parameterTypes));
    } catch (NoSuchMethodException e) {
      throw (NoSuchMethodError) new NoSuchMethodError().initCause(e);
    } catch (IllegalAccessException e) {
      throw (IllegalAccessError) new IllegalAccessError().initCause(e);
    }
  }

  private static MethodHandle getter(Lookup lookup, Class<?> recordType, StructLayout layout, long base) {
    var components = recordType.getRecordComponents();
    var memberLayouts = layout.memberLayouts();
    var accessors = new ArrayList<MethodHandle>(memberLayouts.size());
    loop: for (int i = 0; i < memberLayouts.size(); i++) {
      var memberLayout = memberLayouts.get(i);
      MethodHandle mh;
      switch (memberLayout) {
        case PaddingLayout _:
          continue loop;
        case UnionLayout _, SequenceLayout _ :
          throw new UnsupportedOperationException("invalid member layout " + memberLayout);
        case StructLayout structLayout : {
          var offset = layout.byteOffset(PathElement.groupElement(i));
          mh = getter(lookup, components[accessors.size()].getType(), structLayout, offset);
          break;
        }
        case ValueLayout _: {
          var target = layout.varHandle(PathElement.groupElement(i))
              .withInvokeExactBehavior()
              .toMethodHandle(VarHandle.AccessMode.GET);
          mh = insertArguments(target, 1, base);
          break;
        }
      }
      accessors.add(mh);
    }
    var constructor = findCanonicalConstructor(lookup, components, recordType);
    var mh = filterArguments(constructor, 0, accessors.toArray(MethodHandle[]::new));
    return permuteArguments(mh, methodType(recordType, MemorySegment.class), new int[accessors.size()]);
  }

  private static MethodHandle accessor(Lookup lookup, RecordComponent component) {
    try {
      return lookup.unreflect(component.getAccessor());
    } catch (IllegalAccessException e) {
      throw (IllegalAccessError) new IllegalAccessError().initCause(e);
    }
  }

  private static MethodHandle setter(Lookup lookup, Class<?> recordType, StructLayout layout, long base) {
    var components = recordType.getRecordComponents();
    var memberLayouts = layout.memberLayouts();
    var result = empty(methodType(void.class, MemorySegment.class, recordType));
    var index = 0;
    loop: for (int i = 0; i < memberLayouts.size(); i++) {
      var memberLayout = memberLayouts.get(i);
      MethodHandle mh;
      switch (memberLayout) {
        case PaddingLayout _:
          continue loop;
        case UnionLayout _, SequenceLayout _ :
          throw new UnsupportedOperationException("invalid member layout " + memberLayout);
        case StructLayout structLayout : {
          var offset = layout.byteOffset(PathElement.groupElement(i));
          mh = setter(lookup, components[index].getType(), structLayout, offset);
          break;
        }
        case ValueLayout _: {
          var target = layout.varHandle(PathElement.groupElement(i))
              .withInvokeExactBehavior()
              .toMethodHandle(VarHandle.AccessMode.SET);
          mh = insertArguments(target, 1, base);
          break;
        }
      }
      var target = filterArguments(mh, 1, accessor(lookup, components[index++]));
      result = foldArguments(result, target);
    }
    return result;
  }

  private static final class Lazy extends MutableCallSite {
    private static final MethodHandle INIT_GETTER, INIT_SETTER;
    private static final MethodType INIT_GETTER_TYPE, INIT_SETTER_TYPE;
    static {
      var lookup = lookup();
      try {
        var initReaderType = methodType(Object.class, MemorySegment.class);
        INIT_GETTER_TYPE = initReaderType;
        INIT_GETTER = lookup.findVirtual(Lazy.class, "initGetter", initReaderType);
        var initWriterType = methodType(void.class, MemorySegment.class, Object.class);
        INIT_SETTER_TYPE = initWriterType;
        INIT_SETTER = lookup.findVirtual(Lazy.class, "initSetter", initWriterType);
      } catch (NoSuchMethodException | IllegalAccessException e) {
        throw new AssertionError(e);
      }
    }

    private final Lookup lookup;
    private final Class<?> recordType;
    private final MemoryLayout layout;

    Lazy(Lookup lookup, Class<?> recordType, MemoryLayout layout, MethodHandle init, MethodType type) {
      super(type);
      this.lookup = lookup;
      this.recordType = recordType;
      this.layout = layout;
      setTarget(init.bindTo(this));
    }

    private Object initGetter(MemorySegment memorySegment) throws Throwable {
      if (!(layout instanceof StructLayout structLayout)) {
        throw new UnsupportedOperationException("the layout is not a struct");
      }
      var target = getter(lookup, recordType, structLayout, 0L).asType(type());
      setTarget(target);
      return target.invokeExact(memorySegment);
    }

    private void initSetter(MemorySegment memorySegment, Object record) throws Throwable {
      if (!(layout instanceof StructLayout structLayout)) {
        throw new UnsupportedOperationException("the layout is not a struct");
      }
      var target = setter(lookup, recordType, structLayout, 0L).asType(type());
      setTarget(target);
      target.invokeExact(memorySegment, record);
    }
  }

  private static MethodHandle lazyGetter(Lookup lookup, Class<?> recordType, MemoryLayout layout) {
    return new Lazy(lookup, recordType, layout, Lazy.INIT_GETTER, Lazy.INIT_GETTER_TYPE).dynamicInvoker();
  }

  private static MethodHandle lazySetter(Lookup lookup, Class<?> recordType, MemoryLayout layout) {
    return new Lazy(lookup, recordType, layout, Lazy.INIT_SETTER, Lazy.INIT_SETTER_TYPE).dynamicInvoker();
  }

  final static class MappingSpliterator<T> implements Spliterator<T> {
    private final MemoryAccess<T> memoryAccess;
    private final Spliterator<MemorySegment> spliterator;

    MappingSpliterator(MemoryAccess<T> memoryAccess, Spliterator<MemorySegment> spliterator) {
      this.memoryAccess = memoryAccess;
      this.spliterator = spliterator;
    }

    @Override
    public boolean tryAdvance(Consumer<? super T> action) {
      requireNonNull(action, "action is null");
      return spliterator.tryAdvance(segment -> action.accept(memoryAccess.get(segment)));
    }

    @Override
    public Spliterator<T> trySplit() {
      var spliterator2 = spliterator.trySplit();
      if (spliterator2 == null) {
        return null;
      }
      return new MappingSpliterator<>(memoryAccess, spliterator2);
    }

    @Override
    public long estimateSize() {
      return spliterator.estimateSize();
    }

    @Override
    public int characteristics() {
      return spliterator.characteristics();
    }

    @Override
    public void forEachRemaining(Consumer<? super T> action) {
      requireNonNull(action, "action is null");
      spliterator.forEachRemaining(segment -> action.accept(memoryAccess.get(segment)));
    }
  }

  record MemoryAccessImpl<T>(MemoryLayout layout,
                             MethodHandle offsetMH,
                             MethodHandle varHandleMH,
                             MethodHandle getterMH,
                             MethodHandle setterMH)
      implements MemoryAccess<T> {

    @SuppressWarnings("unchecked")
    private static <X extends Throwable> RuntimeException rethrow(Throwable throwable) throws X {
      throw (X) throwable;
    }

    @Override
    public long byteOffset(String path) {
      requireNonNull(path, "path is null");
      try {
        return (long) offsetMH.invokeExact(path);
      } catch (Throwable e) {
        throw rethrow(e);
      }
    }

    @Override
    public VarHandle vh(String path) {
      requireNonNull(path, "path is null");
      try {
        return (VarHandle) varHandleMH.invokeExact(path);
      } catch (Throwable e) {
        throw rethrow(e);
      }
    }

    @Override
    @SuppressWarnings("unchecked")
    public T get(MemorySegment segment) {
      requireNonNull(segment, "segment is null");
      try {
        return (T) getterMH.invokeExact(segment);
      } catch (Throwable e) {
        throw rethrow(e);
      }
    }

    @Override
    public void set(MemorySegment segment, T element) {
      requireNonNull(segment, "segment is null");
      requireNonNull(element, "element is null");
      try {
        setterMH.invokeExact(segment, element);
      } catch (Throwable e) {
        throw rethrow(e);
      }
    }

    @Override
    public boolean equals(Object obj) {
      return this == obj;
    }
    @Override
    public int hashCode() {
      return System.identityHashCode(this);
    }

    @Override
    public String toString() {
      return layout.toString();
    }
  }

  /**
   * Create a memory access object using the record type as definition.
   * <p>
   * This method should only be used if you want to configure how the layout is created
   * or change the syntax of the path. Otherwise, the method {@link MemoryAccess#reflect(Lookup, Class)}
   * should be used instead.
   *
   * @param lookup an access checking object.
   * @param recordType a type of record.
   * @param layoutFunction a function that creates a memory layout from a class.
   * @param pathFunction a function that creates a path from a string.
   * @return a new memory access.
   * @throws IllegalArgumentException if the record type is not a record.
   * @param <T> the type of the record.
   *
   * @see #defaultLayout(Class)
   * @see #defaultPath(String)
   */
  public static <T extends Record> MemoryAccess<T> create(Lookup lookup,
                                              Class<T> recordType,
                                              Function<? super Class<?>, ? extends MemoryLayout> layoutFunction,
                                              Function<? super String, ? extends List<? extends Path>> pathFunction) {
    requireNonNull(lookup, "lookup is null");
    requireNonNull(recordType, "recordType is null");
    requireNonNull(pathFunction, "layoutFunction is null");
    requireNonNull(pathFunction, "pathFunction is null");
    if (!recordType.isRecord()) {
      throw new IllegalArgumentException(recordType.getName() + " is not a record");
    }
    var layout = requireNonNull(layoutFunction.apply(recordType), "layoutFunction return value is null");
    var offsetMH = cache(long.class, path -> offset(layout, path, pathFunction));
    var varHandleMH = cache(VarHandle.class, path -> varHandle(layout, path, pathFunction));
    var getterMH = lazyGetter(lookup, recordType, layout);
    var setterMH = lazySetter(lookup, recordType, layout);
    return new MemoryAccessImpl<>(layout, offsetMH, varHandleMH, getterMH, setterMH);
  }
}
