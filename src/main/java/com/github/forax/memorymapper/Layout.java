package com.github.forax.memorymapper;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.invoke.MethodHandles;

import static com.github.forax.memorymapper.MemoryAccessFactory.DEFAULT_PADDING;

/**
 * Top level annotation that annotates a record to describe the layout of values in memory.
 *
 * @see MemoryAccess#reflect(MethodHandles.Lookup, Class)
 * @see LayoutElement
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Layout {
  /**
   * Kind of layout, struct or union.
   */
  enum Kind {
    /** struct layout */ STRUCT,
    /** union layout */  UNION
  }

  /**
   * Defines the kind of layout.
   * The default value is struct.
   * @return the kind of layout.
   */
  Kind kind() default Kind.STRUCT;

  /**
   * Defines if the auto-padding is enabled.
   * The auto-padding is enabled by default.
   * @return if the auto-padding is enabled.
   *
   * @see LayoutElement#padding()
   */
  boolean autoPadding() default true;

  /**
   * Defines the padding in bytes at the end of the layout (so consecutive values are correctly aligned).
   * The default value is the number of bytes necessary so if a value with the same layout is initialized
   * just after it will be aligned.
   * @return the padding in bytes at the end of the layout.
   *
   * @see LayoutElement#padding()
   */
  long endPadding() default DEFAULT_PADDING;
}
