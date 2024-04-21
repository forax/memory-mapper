package com.github.forax.memorymapper;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Layout {
  enum Kind { STRUCT, UNION }

  Kind kind() default Kind.STRUCT;
  boolean autoPadding() default true;
}
