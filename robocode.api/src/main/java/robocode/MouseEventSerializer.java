package robocode;


import net.sf.robocode.serialization.RbSerializer;
import java.awt.event.MouseEvent;
import java.nio.ByteBuffer;
import net.sf.robocode.security.SafeComponent;


public class MouseEventSerializer {

    public static void serialize(RbSerializer serializer, ByteBuffer buffer, MouseEvent src) {
        serializer.serialize(buffer, src.getButton());
        serializer.serialize(buffer, src.getClickCount());
        serializer.serialize(buffer, src.getX());
        serializer.serialize(buffer, src.getY());
        serializer.serialize(buffer, src.getID());
        serializer.serialize(buffer, src.getModifiersEx());
        serializer.serialize(buffer, src.getWhen());
    }

    public static MouseEvent deserialize(RbSerializer serializer, ByteBuffer buffer) {
        int button = buffer.getInt();
        int clickCount = buffer.getInt();
        int x = buffer.getInt();
        int y = buffer.getInt();
        int id = buffer.getInt();
        int modifiersEx = buffer.getInt();
        long when = buffer.getLong();

        return new MouseEvent(SafeComponent.getSafeEventComponent(), id, when, modifiersEx, x, y, clickCount, false, button);
    }
}
