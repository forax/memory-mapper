/**
 * The class {@link com.github.forax.memorymapper.MemoryAccess}
 * <ul>
 *   <li>defines the structure of a {@link java.lang.foreign.MemorySegment memory segment} using a record
 *       (optionally annotated with annotations)
 *   <li>access to members using {@link java.lang.invoke.VarHandle VarHandle}s configured using a literal path String
 *   <li>get/set a record instance from/to a method segment (indexed or not)
 * </ul>
 */
package com.github.forax.memorymapper;