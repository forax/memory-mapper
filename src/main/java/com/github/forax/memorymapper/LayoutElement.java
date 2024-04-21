package com.github.forax.memorymapper;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static com.github.forax.memorymapper.MemoryAccessFactory.AnnotationDefault.DEFAULT_ALIGNMENT;
import static com.github.forax.memorymapper.MemoryAccessFactory.AnnotationDefault.DEFAULT_NAME;
import static com.github.forax.memorymapper.MemoryAccessFactory.AnnotationDefault.DEFAULT_PADDING;

@Target(ElementType.RECORD_COMPONENT)
@Retention(RetentionPolicy.RUNTIME)
public @interface LayoutElement {
  enum ByteOrder { LITTLE_ENDIAN, BIG_ENDIAN, NATIVE }

  ByteOrder byteOrder() default ByteOrder.NATIVE;
  long byteAlignment() default DEFAULT_ALIGNMENT;
  long padding() default DEFAULT_PADDING;
  String name() default DEFAULT_NAME;
}
