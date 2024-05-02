package com.github.forax.memorymapper;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.util.AbstractList;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.RandomAccess;
import java.util.Set;

import static com.github.forax.memorymapper.MemoryAccessFactory.DEFAULT_PADDING;
import static java.lang.invoke.MethodHandles.Lookup.ClassOption.NESTMATE;
import static java.lang.invoke.MethodHandles.Lookup.ClassOption.STRONG;
import static java.lang.invoke.MethodType.methodType;
import static java.util.Objects.checkIndex;
import static java.util.Objects.requireNonNull;

public final class MemoryCollections {
  private MemoryCollections() {
    throw new AssertionError();
  }

  private static byte[] template(Class<?> type) {
    try {
      try(var input = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
        if (input == null) {
          throw new AssertionError("input is null");
        }
        return input.readAllBytes();
      }
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  private static final byte[] LIST_TEMPLATE,
      MAP_TEMPLATE, ENTRY_SET_TEMPLATE, ENTRY_SET_ITERATOR_TEMPLATE;
  static {
    LIST_TEMPLATE = template(SpecializedList.class);
    MAP_TEMPLATE = template(SpecializedMap.class);
    ENTRY_SET_TEMPLATE = template(EntrySet.class);
    ENTRY_SET_ITERATOR_TEMPLATE = template(EntrySetIterator.class);
  }

  private static MemoryAccess<?> computeMemoryAccess(Class<?> type) {
    if (type.isPrimitive()) {
      return MemoryAccess.fromPrimitive(type);
    }
    if (type.isRecord()) {
      MethodHandles.Lookup typeLookup;
      try {
        typeLookup = MethodHandles.privateLookupIn(type, MethodHandles.lookup());
      } catch (IllegalAccessException e) {
        throw (IllegalAccessError) new IllegalAccessError(type.getName() + " is not open").initCause(e);
      }
      return MemoryAccess.reflect(typeLookup,  type.asSubclass(Record.class));
    }
    throw new IllegalArgumentException(type.getName() + " is neither a primitive nor a record");
  }

  private static final ClassValue<MemoryAccess<?>> MEMORY_ACCESS_CACHE = new ClassValue<>() {
    @Override
    protected MemoryAccess<?> computeValue(Class<?> type) {
      return computeMemoryAccess(type);
    }
  };

  private static MethodHandle specialize(byte[] template, MethodType constructorType, boolean initialize, Object classData) {
    MethodHandles.Lookup hiddenLookup;
    try {
      hiddenLookup = LOOKUP.defineHiddenClassWithClassData(template, classData, initialize, STRONG, NESTMATE);
    } catch (IllegalAccessException e) {
      throw new AssertionError(e);
    }

    try {
      return hiddenLookup.findStatic(hiddenLookup.lookupClass(), "create", constructorType);
    } catch (NoSuchMethodException | IllegalAccessException e) {
      throw new AssertionError(e);
    }
  }

  private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

  private static final ClassValue<MethodHandle> LIST_FACTORY = new ClassValue<>() {
    @Override
    protected MethodHandle computeValue(Class<?> type) {
      var memoryAccess = MEMORY_ACCESS_CACHE.get(type);
      return specialize(LIST_TEMPLATE, methodType(List.class), true, memoryAccess);
    }
  };

  private static final class SpecializedList<E> extends AbstractList<E> implements RandomAccess {
    private static final MemoryAccess<?> ACCESS;
    private static final long BYTE_SIZE;
    static {
      var lookup = MethodHandles.lookup();
      MemoryAccess<?> access;
      try {
        access = MethodHandles.classData(lookup, "_", MemoryAccess.class);
      } catch (IllegalAccessException e) {
        throw new AssertionError(e);
      }
      ACCESS = access;
      BYTE_SIZE = MemoryAccess.layout(access).byteSize();
    }

    @SuppressWarnings("unused") // called by reflection
    static <T> List<T> create() {
      return new SpecializedList<>();
    }

    private MemorySegment segment;
    private int size;

    private SpecializedList() {
      segment = ACCESS.newArray(Arena.ofAuto(), 16);
    }

    private void resize() {
      if (size >= Integer.MAX_VALUE) {  // FIXME
        throw new OutOfMemoryError("size bigger than Integer.MAX_VALUE");
      }
      var segment = ACCESS.newArray(Arena.ofAuto(), (long) size << 1);
      segment.copyFrom(this.segment);
      this.segment = segment;
    }

    @Override
    public int size() {
      return size;
    }

    @Override
    @SuppressWarnings("unchecked")
    public E get(int index) {
      checkIndex(index, size);
      return (E) ACCESS.getAtIndex(segment, index);
    }

    @Override
    @SuppressWarnings("unchecked")
    public E set(int index, E element) {
      requireNonNull(element);
      checkIndex(index, size);
      var old = ACCESS.getAtIndex(segment, index);
      ((MemoryAccess<E>) ACCESS).setAtIndex(segment, index, element);
      return (E) old;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void add(int index, E element) {
      if (index < 0 || index > size) {
        throw new IndexOutOfBoundsException("bad index " + index);
      }
      if (size * BYTE_SIZE == segment.byteSize()) {
        resize();
      }
      segment.asSlice((index + 1) * BYTE_SIZE)
          .copyFrom(segment.asSlice(index * BYTE_SIZE, (size - index) * BYTE_SIZE));
      ((MemoryAccess<E>) ACCESS).setAtIndex(segment, index, element);
      size++;
    }

    @Override
    @SuppressWarnings("unchecked")
    public E remove(int index) {
      checkIndex(index, size);
      var element = ACCESS.getAtIndex(segment, index);
      segment.asSlice(index * BYTE_SIZE)
          .copyFrom(segment.asSlice((index + 1) * BYTE_SIZE, (size - index - 1) * BYTE_SIZE));
      size--;
      return (E) element;
    }

    // optimized methods

    @Override
    @SuppressWarnings("unchecked")
    public boolean add(E element) {
      if (size * BYTE_SIZE == segment.byteSize()) {
        resize();
      }
      ((MemoryAccess<E>) ACCESS).setAtIndex(segment, size, element);
      size++;
      return true;
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof SpecializedList<?> list)) {
        return super.equals(o);
      }
      if (size != list.size) {
        return false;
      }
      // avoid record creations
      return segment.asSlice(0, size * BYTE_SIZE)
          .mismatch(list.segment.asSlice(0, size * BYTE_SIZE)) == -1;
    }
  }


  private record MapClassData(StructLayout structLayout, MemoryAccess<?> keyMemoryAccess, MemoryAccess<?> valueMemoryAccess) {}

  private static StructLayout structLayout(MemoryAccess<?> keyMemoryAccess, MemoryAccess<?> valueMemoryaccess) {
    var elementLayouts = new ArrayList<MemoryLayout>();
    var hashLayout = ValueLayout.JAVA_INT;
    elementLayouts.add(hashLayout.withName("hash"));
    var byteUsed = 4L;
    var maxAlignment = 4L;

    var keyLayout = MemoryAccess.layout(keyMemoryAccess);
    var keyByteAlignment = keyLayout.byteAlignment();
    var padding = MemoryAccessFactory.computePadding(keyByteAlignment, byteUsed);
    if (padding != DEFAULT_PADDING) {
      maxAlignment = Math.max(maxAlignment, keyByteAlignment);
      elementLayouts.add(MemoryLayout.paddingLayout(padding));
      byteUsed += padding;
    }
    elementLayouts.add(keyLayout.withName("key"));
    byteUsed += keyLayout.byteSize();

    var valueLayout = MemoryAccess.layout(valueMemoryaccess);
    var valueByteAlignment = keyLayout.byteAlignment();
    padding = MemoryAccessFactory.computePadding(valueByteAlignment, byteUsed);
    if (padding != DEFAULT_PADDING) {
      maxAlignment = Math.max(maxAlignment, valueByteAlignment);
      elementLayouts.add(MemoryLayout.paddingLayout(padding));
      byteUsed += padding;
    }
    elementLayouts.add(valueLayout.withName("value"));
    byteUsed += valueLayout.byteSize();

    padding = MemoryAccessFactory.computePadding(maxAlignment, byteUsed);
    if (padding != DEFAULT_PADDING) {
      elementLayouts.add(MemoryLayout.paddingLayout(padding));
    }

    return MemoryLayout.structLayout(elementLayouts.toArray(MemoryLayout[]::new));
  }

  private static final ClassValue<ClassValue<MethodHandle>> MAP_FACTORY = new ClassValue<>() {
    @Override
    protected ClassValue<MethodHandle> computeValue(Class<?> keyType) {
      var keyMemoryAccess = MEMORY_ACCESS_CACHE.get(keyType);
      return new ClassValue<>() {
        @Override
        protected MethodHandle computeValue(Class<?> valueType) {
          var valueMemoryAccess = MEMORY_ACCESS_CACHE.get(valueType);
          var structLayout = structLayout(keyMemoryAccess, valueMemoryAccess);
          return specialize(MAP_TEMPLATE, methodType(Map.class), true, new MapClassData(structLayout, keyMemoryAccess, valueMemoryAccess));
        }
      };
    }
  };

  private static MapClassData retrieveMapClassData(MethodHandles.Lookup lookup) {
    try {
      return MethodHandles.classData(lookup, "_", MapClassData.class);
    } catch (IllegalAccessException e) {
      throw new AssertionError(e);
    }
  }

  private static final int EMPTY = 0;
  private static final int TOMBSTONE = 0x7FFF_FFFF;

  // Inner classes like {@link SpecializedMap.EntrySet} can not have a dependency on SpecializedMap
  //a hidden class does not have a name at runtime, so we use a named class to host these fields
  private static abstract class ShareableSpecializedMap<K,V> extends AbstractMap<K,V> {
    int modCount;   // should be read to detect if segment or size has changed during the iteration
    MemorySegment segment;
    int size;
  }

  private static final class SpecializedMap<K,V> extends ShareableSpecializedMap<K,V> {
    private static final MethodHandle ENTRY_SET_FACTORY;
    private static final MemoryAccess<?> KEY_ACCESS, VALUE_ACCESS;
    private static final VarHandle HASH_VH;
    private static final long KEY_OFFSET, VALUE_OFFSET;
    private static final StructLayout STRUCT_LAYOUT;
    private static final long KEY_BYTE_SIZE, VALUE_BYTE_SIZE, BYTE_SIZE;
    static {
      var mapClassData = retrieveMapClassData(MethodHandles.lookup());
      ENTRY_SET_FACTORY = specialize(ENTRY_SET_TEMPLATE,
          methodType(Set.class, ShareableSpecializedMap.class), false, mapClassData);
      var structLayout = mapClassData.structLayout;
      var keyMemoryAccess = mapClassData.keyMemoryAccess;
      var valueMemoryAccess = mapClassData.valueMemoryAccess;
      KEY_ACCESS = keyMemoryAccess;
      VALUE_ACCESS = valueMemoryAccess;
      HASH_VH = structLayout.varHandle(MemoryLayout.PathElement.groupElement("hash"));
      KEY_OFFSET = structLayout.byteOffset(MemoryLayout.PathElement.groupElement("key"));
      VALUE_OFFSET = structLayout.byteOffset(MemoryLayout.PathElement.groupElement("value"));
      STRUCT_LAYOUT = structLayout;
      KEY_BYTE_SIZE = MemoryAccess.layout(keyMemoryAccess).byteSize();
      VALUE_BYTE_SIZE = MemoryAccess.layout(valueMemoryAccess).byteSize();
      BYTE_SIZE = structLayout.byteSize();
    }

    @SuppressWarnings("unused")  // called by reflection
    static <K,V> Map<K,V> create() {
      return new SpecializedMap<>();
    }

    public SpecializedMap() {
      this.segment = Arena.ofAuto().allocate(STRUCT_LAYOUT, 16);
    }

    private static Object getKeyAt(MemorySegment segment, long index) {
      var keySegment = segment.asSlice(index * BYTE_SIZE + KEY_OFFSET, KEY_BYTE_SIZE);
      return KEY_ACCESS.get(keySegment);
    }
    private static Object getValueAt(MemorySegment segment, long index) {
      var valueSegment = segment.asSlice(index * BYTE_SIZE + VALUE_OFFSET, VALUE_BYTE_SIZE);
      return VALUE_ACCESS.get(valueSegment);
    }

    @SuppressWarnings("unchecked")
    private static <K> void setKeyAt(MemorySegment segment, long index, K key) {
      var keySegment = segment.asSlice(index * BYTE_SIZE + KEY_OFFSET, KEY_BYTE_SIZE);
      ((MemoryAccess<K>) KEY_ACCESS).set(keySegment, key);
    }
    @SuppressWarnings("unchecked")
    private static <V> void setValueAt(MemorySegment segment, long index, V value) {
      var valueSegment = segment.asSlice(index * BYTE_SIZE + VALUE_OFFSET, VALUE_BYTE_SIZE);
      ((MemoryAccess<V>) VALUE_ACCESS).set(valueSegment, value);
    }

    private static int getHashValue(MemorySegment segment, long index) {
      return (int) HASH_VH.get(segment, index * BYTE_SIZE);
    }
    private static void setHashValue(MemorySegment segment, long index, int hashValue) {
      HASH_VH.set(segment, index * BYTE_SIZE, hashValue);
    }

    @Override
    @SuppressWarnings("unchecked")
    public V getOrDefault(Object key, V defaultValue) {
      requireNonNull(key);
      var segment = this.segment;
      var capacity = (int) (segment.byteSize() / BYTE_SIZE);
      var hashCode = key.hashCode();
      var index = hashCode & (capacity - 1);
      hashCode = hashCode | 0x8000_0000;
      for(;;) {
        var hashValue = getHashValue(segment, index);
        if (hashValue == EMPTY) {
          return null;
        }
        if (hashValue == hashCode) {
          var keyValue = getKeyAt(segment, index);
          if (key.equals(keyValue)) {
            return (V) getValueAt(segment, index);
          }
        }
        index = (index + 1) & (capacity - 1);
      }
    }

    @Override
    public V get(Object key) {
      return getOrDefault(key, null);
    }


    private static void insert(MemorySegment segment, MemorySegment newSegment, long newSegmentCapacity, long offset, int hashCode) {
      var index = hashCode & (newSegmentCapacity - 1);
      for(;;) {
        var hashValue = getHashValue(newSegment, index);
        if (hashValue == EMPTY) {
          newSegment.asSlice(index * BYTE_SIZE, BYTE_SIZE)
              .copyFrom(segment.asSlice(offset, BYTE_SIZE));
          return;
        }
        index = (index + 1) & (newSegmentCapacity - 1);
      }
    }

    private void rehash() {
      var segment = this.segment;
      var capacity = (int) (segment.byteSize() / BYTE_SIZE);
      var newCapacity = capacity << 1;
      var newSegment = Arena.ofAuto().allocate(STRUCT_LAYOUT, newCapacity);
      for(var offset = 0L; offset < segment.byteSize(); offset += BYTE_SIZE) {
        var hashValue = (int) HASH_VH.get(segment, offset);
        if ((hashValue & 0x8000_0000) != 0) {  // != EMPTY and != TOMBSTONE
          insert(segment, newSegment, newCapacity, offset, hashValue);
        }
      }
      this.segment = newSegment;
    }

    @Override
    @SuppressWarnings("unchecked")
    public V put(K key, V value) {
      requireNonNull(key);
      requireNonNull(value);
      var segment = this.segment;
      var capacity = (int) (segment.byteSize() / BYTE_SIZE);
      var hashCode = key.hashCode();
      var index = hashCode & (capacity - 1);
      hashCode = hashCode | 0x8000_0000;
      for(;;) {
        var hashValue = getHashValue(segment, index);
        if ((hashValue & 0x8000_0000) == 0) {  // == EMPTY or == TOMBSTONE
          setHashValue(segment, index, hashCode);
          setKeyAt(segment, index, key);
          setValueAt(segment, index, value);
          if (size == capacity >> 1) {  // rehash if half-empty
            rehash();
          }
          size++;
          modCount++;
          return null;
        }
        if (hashValue == hashCode) {
          var keyValue = (K) getKeyAt(segment, index);
          if (key.equals(keyValue)) {
            var oldValue = (V) getValueAt(segment, index);
            setValueAt(segment, index, value);
            return oldValue;
          }
        }
        index = (index + 1) & (capacity - 1);
      }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Set<Entry<K, V>> entrySet() {
      //return new EntrySet<>(this);
      try {
        return (Set<Entry<K, V>>) ENTRY_SET_FACTORY.invokeExact((ShareableSpecializedMap<K,V>) this);
      } catch (Throwable e) {
        throw rethrow(e);
      }
    }


    // optimized methods

    @Override
    public boolean containsKey(Object key) {
      requireNonNull(key);
      var segment = this.segment;
      var capacity = (int) (segment.byteSize() / BYTE_SIZE);
      var hashCode = key.hashCode();
      var index = hashCode & (capacity - 1);
      hashCode = hashCode | 0x8000_0000;
      for(;;) {
        var hashValue = getHashValue(segment, index);
        if (hashValue == EMPTY) {
          return false;
        }
        if (hashValue == hashCode) {
          var keyValue = getKeyAt(segment, index);
          if (key.equals(keyValue)) {
            return true;
          }
        }
        index = (index + 1) & (capacity - 1);
      }
    }

    @Override
    @SuppressWarnings("unchecked")
    public V remove(Object key) {
      requireNonNull(key);
      var segment = this.segment;
      var capacity = (int) (segment.byteSize() / BYTE_SIZE);
      var hashCode = key.hashCode();
      var index = hashCode & (capacity - 1);
      hashCode = hashCode | 0x8000_0000;
      for(;;) {
        var hashValue = getHashValue(segment, index);
        if (hashValue == EMPTY) {
          return null;
        }
        if (hashValue == hashCode) {
          var keyValue = getKeyAt(segment, index);
          if (key.equals(keyValue)) {
            var value = getValueAt(segment, index);
            setHashValue(segment, index, TOMBSTONE);
            size--;
            modCount++;
            return (V) value;
          }
        }
        index = (index + 1) & (capacity - 1);
      }
    }
  }

  private static final class EntrySet<K,V> extends AbstractSet<Map.Entry<K, V>> {
    private static final MethodHandle ENTRY_SET_ITERATOR_FACTORY;
    static {
      var mapClassData = retrieveMapClassData(MethodHandles.lookup());
      ENTRY_SET_ITERATOR_FACTORY = specialize(ENTRY_SET_ITERATOR_TEMPLATE,
          methodType(Iterator.class, ShareableSpecializedMap.class), false, mapClassData);
    }

    private final ShareableSpecializedMap<K,V> shareableSpecializedMap;

    @SuppressWarnings("unused")  // called by reflection
    static <K,V> Set<Map.Entry<K,V>> create(ShareableSpecializedMap<K, V> shareableSpecializedMap) {
      return new EntrySet<>(shareableSpecializedMap);
    }

    private EntrySet(ShareableSpecializedMap<K, V> shareableSpecializedMap) {
      this.shareableSpecializedMap = shareableSpecializedMap;
    }

    @Override
    public int size() {
      return shareableSpecializedMap.size;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Iterator<Map.Entry<K, V>> iterator() {
      //return new EntrySetIterator<>(shareableSpecializedMap);
      try {
        return (Iterator<Map.Entry<K, V>>) ENTRY_SET_ITERATOR_FACTORY.invokeExact(shareableSpecializedMap);
      } catch (Throwable e) {
        throw rethrow(e);
      }
    }
  }

  private static final class EntrySetIterator<K,V> implements Iterator<Map.Entry<K, V>> {
    private static final MemoryAccess<?> KEY_ACCESS, VALUE_ACCESS;
    private static final VarHandle HASH_VH;
    private static final long KEY_OFFSET, VALUE_OFFSET;
    private static final long KEY_BYTE_SIZE, VALUE_BYTE_SIZE, BYTE_SIZE;
    static {
      var mapClassData = retrieveMapClassData(MethodHandles.lookup());
      var structLayout = mapClassData.structLayout;
      var keyMemoryAccess = mapClassData.keyMemoryAccess;
      var valueMemoryAccess = mapClassData.valueMemoryAccess;
      KEY_ACCESS = keyMemoryAccess;
      VALUE_ACCESS = valueMemoryAccess;
      HASH_VH = structLayout.varHandle(MemoryLayout.PathElement.groupElement("hash"));
      KEY_OFFSET = structLayout.byteOffset(MemoryLayout.PathElement.groupElement("key"));
      VALUE_OFFSET = structLayout.byteOffset(MemoryLayout.PathElement.groupElement("value"));
      KEY_BYTE_SIZE = MemoryAccess.layout(keyMemoryAccess).byteSize();
      VALUE_BYTE_SIZE = MemoryAccess.layout(valueMemoryAccess).byteSize();
      BYTE_SIZE = structLayout.byteSize();
    }

    private static Object getKeyAt(MemorySegment segment, long index) {
      var keySegment = segment.asSlice(index * BYTE_SIZE + KEY_OFFSET, KEY_BYTE_SIZE);
      return KEY_ACCESS.get(keySegment);
    }
    private static Object getValueAt(MemorySegment segment, long index) {
      var valueSegment = segment.asSlice(index * BYTE_SIZE + VALUE_OFFSET, VALUE_BYTE_SIZE);
      return VALUE_ACCESS.get(valueSegment);
    }

    private static int getHashValue(MemorySegment segment, long index) {
      return (int) HASH_VH.get(segment, index * BYTE_SIZE);
    }
    private static void setHashValue(MemorySegment segment, long index, int hashValue) {
      HASH_VH.set(segment, index * BYTE_SIZE, hashValue);
    }

    private final ShareableSpecializedMap<K,V> shareableSpecializedMap;
    private final MemorySegment segment;
    private final int capacity;
    private int modCount;
    private int index;
    private int previousIndex;

    @SuppressWarnings("unused")  // called by reflection
    static <K,V> Iterator<Map.Entry<K,V>> create(ShareableSpecializedMap<K, V> shareableSpecializedMap) {
      return new EntrySetIterator<>(shareableSpecializedMap);
    }

    private EntrySetIterator(ShareableSpecializedMap<K,V> shareableSpecializedMap) {
      this.shareableSpecializedMap = shareableSpecializedMap;
      var segment = shareableSpecializedMap.segment;
      this.segment = segment;
      this.capacity = (int) (segment.byteSize() / BYTE_SIZE);
      this.modCount = shareableSpecializedMap.modCount;
      this.index = nextIndex(0);
      this.previousIndex = -1;
    }

    private int nextIndex(int index) {
      var segment = this.segment;
      for(; index < capacity; index++) {
        var hashValue = getHashValue(segment, index);
        if ((hashValue & 0x8000_0000) != 0) {  // != EMPTY and != TOMBSTONE
          return index;
        }
      }
      return capacity;
    }

    @Override
    public boolean hasNext() {
      return index < capacity;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map.Entry<K, V> next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
      if (modCount != shareableSpecializedMap.modCount) {
        throw new ConcurrentModificationException();
      }
      var index = this.index;
      var segment = this.segment;
      var key = (K) getKeyAt(segment, index);
      var value = (V) getValueAt(segment, index);
      previousIndex = index;
      this.index = nextIndex(index + 1);
      return Map.entry(key, value);
    }

    @Override
    public void remove() {
      if (previousIndex == -1) {
        throw new IllegalStateException();
      }
      if (modCount != shareableSpecializedMap.modCount) {
        throw new ConcurrentModificationException();
      }
      setHashValue(segment, previousIndex, TOMBSTONE);
      shareableSpecializedMap.size--;
      modCount = ++shareableSpecializedMap.modCount;
      previousIndex = -1;
    }
  }

  @SuppressWarnings("unchecked")
  private static <X extends Throwable> RuntimeException rethrow(Throwable throwable) throws X {
    throw (X) throwable;
  }

  @SuppressWarnings("unchecked")
  public static <T> List<T> newSpecializedList(Class<T> type) {
    requireNonNull(type);
    var constructor = LIST_FACTORY.get(type);
    try {
      return (List<T>)(List<?>) constructor.invokeExact();
    } catch (Throwable e) {
      throw rethrow(e);
    }
  }

  @SuppressWarnings("unchecked")
  public static <K,V> Map<K, V> newSpecializedMap(Class<K> keyType, Class<V> valueType) {
    requireNonNull(keyType);
    requireNonNull(valueType);
    var constructor = MAP_FACTORY.get(keyType).get(valueType);
    try {
      return (Map<K,V>)(Map<?,?>) constructor.invokeExact();
    } catch (Throwable e) {
      throw rethrow(e);
    }
  }
}
