import com.github.forax.memorymapper.MemoryAccess;

import java.lang.foreign.Arena;
import java.lang.invoke.MethodHandles;

import static java.lang.foreign.MemoryLayout.PathElement.groupElement;

/*struct data {
    byte kind;
    int payload;
    short ext;
  }*/

record Data(
    byte kind,
    int payload,
    short ext
) {}

private static final MemoryAccess<Data> DATA_ACCESS = MemoryAccess.reflect(MethodHandles.lookup(), Data.class);

void main() {
  System.out.println(DATA_ACCESS);

  try(var arena = Arena.ofConfined()) {
    var segment = DATA_ACCESS.newArray(arena, 100);
    for(var i = 0; i < 100; i++) {
      DATA_ACCESS.setAtIndex(segment, i, new Data((byte) 12, 42, (short) 17));
    }

    var data = DATA_ACCESS.getAtIndex(segment, 7);
    var kind = data.kind;
    var payload = data.payload;
    var ext = data.ext;
    System.out.println(kind + " " + payload + " " + ext);
  }
}
