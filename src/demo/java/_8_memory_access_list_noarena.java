import com.github.forax.memorymapper.MemoryAccess;
import com.github.forax.memorymapper.MemoryCollections;

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

  var list = MemoryCollections.newSpecializedList(Data.class);
  for(var i = 0; i < 100; i++) {
    list.add(new Data((byte) 12, 42, (short) 17));
  }

  var data = list.get(7);
  var kind = data.kind;
   var payload = data.payload;
  var ext = data.ext;
  System.out.println(kind + " " + payload + " " + ext);
}
