package com.github.forax.memorymapper;

import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.PaddingLayout;
import java.lang.foreign.SegmentAllocator;
import java.lang.foreign.SequenceLayout;
import java.lang.foreign.StructLayout;
import java.lang.foreign.UnionLayout;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.invoke.MutableCallSite;
import java.lang.invoke.VarHandle;
import java.lang.reflect.RecordComponent;
import java.nio.ByteOrder;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.RandomAccess;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.lang.foreign.ValueLayout.JAVA_BOOLEAN;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_CHAR;
import static java.lang.foreign.ValueLayout.JAVA_DOUBLE;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;
import static java.lang.foreign.ValueLayout.JAVA_SHORT;
import static java.lang.foreign.MemoryLayout.PathElement;
import static java.lang.invoke.MethodHandles.empty;
import static java.lang.invoke.MethodHandles.filterArguments;
import static java.lang.invoke.MethodHandles.foldArguments;
import static java.lang.invoke.MethodHandles.insertArguments;
import static java.lang.invoke.MethodHandles.lookup;
import static java.lang.invoke.MethodHandles.permuteArguments;
import static java.lang.invoke.MethodType.methodType;
import static java.util.Objects.checkIndex;
import static java.util.Objects.requireNonNull;

/**
 * A factory to create fined tuned memory access.
 *
 * @see #create(Lookup, Class, Function)
 */
public final class MemoryAccessFactory {
  static final long DEFAULT_ALIGNMENT = -1;
  static final long DEFAULT_PADDING = -1;
  static final String DEFAULT_NAME = "";

  private MemoryAccessFactory() {
    throw new AssertionError();
  }

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

  static long computePadding(long byteAlignment, long byteUsed) {
    var shift = byteUsed % byteAlignment;
    return shift != 0 ? byteAlignment - shift : DEFAULT_PADDING ;
  }

  private static MemoryLayout recordLayout(Class<?> recordType, long byteUsed, boolean topLevel) {
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
        padding = computePadding(byteAlignment, byteUsed);
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
      padding = computePadding(maxAlignment, byteUsed);
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
      return recordLayout(type, byteUsed, false);
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
    return recordLayout(type, 0,true);
  }

  /**
   * Represent a path into the memory layout.
   */
  private sealed interface Path {
    /**
     * An array access, represented by default by the string {@code []}.
     */
    record ArrayPath() implements Path {}

    /**
     * A member access, represented by default by the string {@code .member}.
     * @param member the name of the member in the memory layout.
     */
    record FieldPath(String member) implements Path {
      /**
       * Creates a field path to a specific field.
       * @param member the field name
       */
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
  private static List<Path> defaultPath(String path) {
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

  static long byteOffset(MemoryLayout layout, String path) {
    var paths = defaultPath(path);
    var pathElements = paths.stream()
        .map(MemoryAccessFactory::toPathElement)
        .toArray(PathElement[]::new);
    return layout.byteOffset(pathElements);
  }

  static VarHandle varHandle(MemoryLayout layout, String path) {
    var paths = defaultPath(path);
    VarHandle varHandle;
    if (paths.getFirst() instanceof Path.ArrayPath) {
      varHandle = layout.arrayElementVarHandle(paths.stream().skip(1).map(MemoryAccessFactory::toPathElement).toArray(PathElement[]::new));
    } else {
      varHandle = layout.varHandle(paths.stream().map(MemoryAccessFactory::toPathElement).toArray(PathElement[]::new));
    }
    return varHandle.withInvokeExactBehavior();
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

  private static final class StructAccess extends MutableCallSite {
    private static final MethodHandle INIT_GETTER, INIT_SETTER;
    private static final MethodType INIT_GETTER_TYPE, INIT_SETTER_TYPE;
    static {
      var lookup = lookup();
      try {
        var initReaderType = methodType(Object.class, MemorySegment.class);
        INIT_GETTER_TYPE = initReaderType;
        INIT_GETTER = lookup.findVirtual(StructAccess.class, "initGetter", initReaderType);
        var initWriterType = methodType(void.class, MemorySegment.class, Object.class);
        INIT_SETTER_TYPE = initWriterType;
        INIT_SETTER = lookup.findVirtual(StructAccess.class, "initSetter", initWriterType);
      } catch (NoSuchMethodException | IllegalAccessException e) {
        throw new AssertionError(e);
      }
    }

    private final Lookup lookup;
    private final Class<?> recordType;
    private final MemoryLayout layout;

    StructAccess(Lookup lookup, Class<?> recordType, MemoryLayout layout, MethodHandle init, MethodType type) {
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

  private static MethodHandle lazyStructGetter(Lookup lookup, Class<?> recordType, MemoryLayout layout) {
    return new StructAccess(lookup, recordType, layout, StructAccess.INIT_GETTER, StructAccess.INIT_GETTER_TYPE).dynamicInvoker();
  }

  private static MethodHandle lazyStructSetter(Lookup lookup, Class<?> recordType, MemoryLayout layout) {
    return new StructAccess(lookup, recordType, layout, StructAccess.INIT_SETTER, StructAccess.INIT_SETTER_TYPE).dynamicInvoker();
  }

  static <T> MemoryAccess<T> shaveEndPadding(MemoryAccess<T> memoryAccess) {
    var impl = (MemoryAccessImpl<T>) memoryAccess;
    var layout = impl.layout;
    if (layout instanceof StructLayout structLayout) {
      var memoryLayouts = structLayout.memberLayouts();
      if (!memoryLayouts.isEmpty()) {
        var last =  memoryLayouts.getLast();
        if (last instanceof PaddingLayout) {
          var shavedLayout = MemoryLayout.structLayout(
              memoryLayouts.subList(0, memoryLayouts.size() - 1).toArray(MemoryLayout[]::new));
          return new MemoryAccessImpl<>(shavedLayout, impl.getterMH, impl.setterMH);
        }
      }
    }
    return impl;
  }

  private final static class MappingSpliterator<T> implements Spliterator<T> {
    private final MemoryAccessImpl<T> memoryAccess;
    private final Spliterator<MemorySegment> spliterator;

    MappingSpliterator(MemoryAccessImpl<T> memoryAccess, Spliterator<MemorySegment> spliterator) {
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
                             MethodHandle getterMH,
                             MethodHandle setterMH)
      implements MemoryAccess<T> {

    @SuppressWarnings("unchecked")
    private static <X extends Throwable> RuntimeException rethrow(Throwable throwable) throws X {
      throw (X) throwable;
    }

    @Override
    public MemorySegment newValue(SegmentAllocator allocator) {
      requireNonNull(allocator, "allocator is null");
      return allocator.allocate(layout);
    }

    @Override
    public MemorySegment newValue(SegmentAllocator allocator, T element) {
      requireNonNull(allocator, "allocator is null");
      requireNonNull(element, "element is null");
      var segment = allocator.allocate(layout);
      set(segment, element);
      return segment;
    }

    @Override
    public MemorySegment newArray(SegmentAllocator allocator, long size) {
      requireNonNull(allocator, "allocator is null");
      return allocator.allocate(layout, size);
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
    public T getAtIndex(MemorySegment segment, long index) {
      requireNonNull(segment, "segment is null");
      var layout = this.layout;
      return get(segment.asSlice(index * layout.byteSize(), layout));
    }

    @Override
    public Stream<T> stream(MemorySegment segment) {
      requireNonNull(segment, "segment is null");
      var spliterator = segment.spliterator(layout);
      return StreamSupport.stream(new MappingSpliterator<>(this, spliterator), false);
    }

    @Override
    public List<T> list(MemorySegment segment) {
      requireNonNull(segment, "segment is null");
      var layout = this.layout;
      if (layout.byteSize() == 0) {
        throw new IllegalArgumentException("element layout size cannot be zero");
      }
      segment.asSlice(0, layout);  // check alignment
      if ((segment.byteSize() % layout.byteSize()) != 0) {
        throw new IllegalArgumentException("segment size is not a multiple of layout size");
      }
      var size = Math.toIntExact(segment.byteSize() / layout.byteSize());
      class MappingList extends AbstractList<T> implements RandomAccess {
        @Override
        public int size() {
          return size;
        }

        @Override
        public T get(int index) {
          checkIndex(index, size);
          return getAtIndex(segment, index);
        }

        @Override
        public T set(int index, T element) {
          checkIndex(index, size);
          var old = getAtIndex(segment, index);
          setAtIndex(segment, index, element);
          return old;
        }

        @Override
        public Spliterator<T> spliterator() {
          var spliterator = segment.spliterator(layout);
          return new MappingSpliterator<>(MemoryAccessImpl.this, spliterator);
        }
      }
      return new MappingList();
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
    public void setAtIndex(MemorySegment segment, long index, T element) {
      requireNonNull(segment, "segment is null");
      requireNonNull(element, "element is null");
      var layout = layout();
      set(segment.asSlice(index * layout.byteSize(), layout), element);
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

  private static final class PrimitiveMemoryAccesses {
    private static final MethodType GETTER_TYPE = methodType(Object.class, MemorySegment.class);
    private static final MethodType SETTER_TYPE = methodType(void.class, MemorySegment.class, Object.class);

    private static <T> MemoryAccess<T> memoryAccess(ValueLayout layout) {
      var varHandle = layout.varHandle().withInvokeExactBehavior();
      return new MemoryAccessImpl<>(layout,
          insertArguments(varHandle.toMethodHandle(VarHandle.AccessMode.GET), 1, 0L).asType(GETTER_TYPE),
          insertArguments(varHandle.toMethodHandle(VarHandle.AccessMode.SET), 1, 0L).asType(SETTER_TYPE));
    }

    private static final MemoryAccess<Boolean> BOOLEAN = memoryAccess(JAVA_BOOLEAN);
    private static final MemoryAccess<Byte> BYTE = memoryAccess(JAVA_BYTE);
    private static final MemoryAccess<Boolean> CHAR = memoryAccess(JAVA_CHAR);
    private static final MemoryAccess<Boolean> SHORT = memoryAccess(JAVA_SHORT);
    private static final MemoryAccess<Boolean> INT = memoryAccess(JAVA_INT);
    private static final MemoryAccess<Boolean> LONG = memoryAccess(JAVA_LONG);
    private static final MemoryAccess<Boolean> FLOAT = memoryAccess(JAVA_FLOAT);
    private static final MemoryAccess<Boolean> DOUBLE = memoryAccess(JAVA_DOUBLE);
  }


  /**
   * Create a memory access object using the record type as definition.
   * <p>
   * This method should only be used if you want to configure how the layout is created,
   * otherwise, the method {@link MemoryAccess#reflect(Lookup, Class)}
   * should be used instead.
   *
   * @param lookup an access checking object.
   * @param recordType a type of record.
   * @param layoutFunction a function that creates a memory layout from a class.
   * @return a new memory access.
   * @throws IllegalArgumentException if the record type is not a record.
   * @param <T> the type of the record.
   *
   * @see #defaultLayout(Class)
   */
  public static <T extends Record> MemoryAccess<T> create(Lookup lookup,
                                              Class<T> recordType,
                                              Function<? super Class<?>, ? extends MemoryLayout> layoutFunction) {
    requireNonNull(lookup, "lookup is null");
    requireNonNull(recordType, "recordType is null");
    requireNonNull(layoutFunction, "layoutFunction is null");
    if (!recordType.isRecord()) {
      throw new IllegalArgumentException(recordType.getName() + " is not a record");
    }
    var layout = requireNonNull(layoutFunction.apply(recordType), "layoutFunction return value is null");
    var structGetterMH = lazyStructGetter(lookup, recordType, layout);
    var structSetterMH = lazyStructSetter(lookup, recordType, layout);
    return new MemoryAccessImpl<>(layout, structGetterMH, structSetterMH);
  }

  @SuppressWarnings("unchecked")
  public static <T> MemoryAccess<T> fromPrimitive(Class<T> primitiveType) {
    requireNonNull(primitiveType);
    if (!primitiveType.isPrimitive() || primitiveType == void.class) {
      throw new IllegalArgumentException(primitiveType + " is not a primitive type");
    }
    return (MemoryAccess<T>) switch (primitiveType.getName()) {
      case "boolean" -> PrimitiveMemoryAccesses.BOOLEAN;
      case "byte" -> PrimitiveMemoryAccesses.BYTE;
      case "char" -> PrimitiveMemoryAccesses.CHAR;
      case "short" -> PrimitiveMemoryAccesses.SHORT;
      case "int" -> PrimitiveMemoryAccesses.INT;
      case "long" -> PrimitiveMemoryAccesses.LONG;
      case "float" -> PrimitiveMemoryAccesses.FLOAT;
      case "double" -> PrimitiveMemoryAccesses.DOUBLE;
      default -> throw new AssertionError();
    };
  }
}
