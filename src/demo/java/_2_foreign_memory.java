import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.invoke.VarHandle;

import static java.lang.foreign.MemoryLayout.PathElement.groupElement;
import static java.lang.foreign.MemoryLayout.structLayout;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT_UNALIGNED;
import static java.lang.foreign.ValueLayout.JAVA_SHORT_UNALIGNED;

/*struct data {
    byte kind;
    int payload;
    short ext;
  }*/

private static final MemoryLayout DATA_LAYOUT = structLayout(
    JAVA_BYTE.withName("kind"),
    JAVA_INT_UNALIGNED.withName("payload"),
    JAVA_SHORT_UNALIGNED.withName("ext")
);

private static final VarHandle DATA_KIND = DATA_LAYOUT.varHandle(groupElement("kind"));
private static final VarHandle DATA_PAYLOAD = DATA_LAYOUT.varHandle(groupElement("payload"));
private static final VarHandle DATA_EXT = DATA_LAYOUT.varHandle(groupElement("ext"));

void main() {
  System.out.println(DATA_LAYOUT);

  try(var arena = Arena.ofConfined()) {
    var segment = arena.allocate(DATA_LAYOUT);
    DATA_KIND.set(segment, 0L, (byte) 12);
    DATA_PAYLOAD.set(segment, 0L, 42);
    DATA_EXT.set(segment, 0L, (short) 17);

    var kind = (byte) DATA_KIND.get(segment, 0L);
    var payload = (int) DATA_PAYLOAD.get(segment, 0L);
    var ext = (short) DATA_EXT.get(segment, 0L);
    System.out.println(kind + " " + payload + " " + ext);
  }
}
