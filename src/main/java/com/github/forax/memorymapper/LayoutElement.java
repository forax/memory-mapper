package com.github.forax.memorymapper;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static com.github.forax.memorymapper.MemoryAccessFactory.DEFAULT_ALIGNMENT;
import static com.github.forax.memorymapper.MemoryAccessFactory.DEFAULT_NAME;
import static com.github.forax.memorymapper.MemoryAccessFactory.DEFAULT_PADDING;

/**
 * Describes the layout of a peculiar member by annotation the corresponding record component.
 *
 * @see Layout
 */
@Target(ElementType.RECORD_COMPONENT)
@Retention(RetentionPolicy.RUNTIME)
public @interface LayoutElement {
  /**
   * Byte order of the member.
   */
  enum ByteOrder {
    /** Little Endian order */     LITTLE_ENDIAN,
    /** Big Endian order */        BIG_ENDIAN,
    /** Native order of the CPU */ NATIVE
  }

  /**
   * Defines the byte order of the member.
   * Using a fixed byte order is important if the data are stored or
   * sent by the network, in those case, {@link ByteOrder#BIG_ENDIAN} is usually used.
   * @return the byte order of the member.
   */
  ByteOrder order() default ByteOrder.NATIVE;

  /**
   * Defines the alignment in bytes, of the member (1 if no alignment).
   * By default, the alignment is the size of the primitive types,
   * 1 for boolean and byte, 2 for char and short, 4 for int and float,
   * 8 for double and long.
   * This alignment is used by the {@link Layout#autoPadding()} algorithm
   * to correctly align the members value. Only primitive types can be aligned.
   * @return the alignment of the member.
   */
  long alignment() default DEFAULT_ALIGNMENT;

  /**
   * Defines the padding in bytes, in front of the member.
   * The default value if {@link Layout#autoPadding()} is enabled is to
   * add padding in front of the members so there are correctly aligned.
   * @return the padding in front of the member.
   *
   * @see Layout#endPadding()
   */
  long padding() default DEFAULT_PADDING;

  /**
   * Defines the name of the member.
   * If no name is provided, the name of the record component is used.
   * @return the name of the member.
   */
  String name() default DEFAULT_NAME;
}
