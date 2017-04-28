import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ByteArrayInputStream;
import java.io.ObjectOutputStream;

/**
 * Serializer between MyMessage and byte[]
 * 
 * @author danc
 *
 */
public class MsgSerializer {
	/**
	 * Serialize MyMessage to byte[]
	 * 
	 * @param msg
	 * @return
	 * @throws IOException
	 */
	public static byte[] serialize(MyMessage msg) throws IOException {
		try (ByteArrayOutputStream b = new ByteArrayOutputStream()) {
			try (ObjectOutputStream o = new ObjectOutputStream(b)) {
				o.writeObject(msg);
			}
			return b.toByteArray();
		}
	}

	/**
	 * Serialize byte[] to MyMessage
	 * 
	 * @param bytes
	 * @return
	 * @throws IOException
	 * @throws ClassNotFoundException
	 */
	public static MyMessage deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
		try (ByteArrayInputStream b = new ByteArrayInputStream(bytes)) {
			try (ObjectInputStream o = new ObjectInputStream(b)) {
				return (MyMessage) o.readObject();
			}
		}
	}

}
