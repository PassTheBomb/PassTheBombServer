

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * Contains static methods that facilitate the conversion of messages that are
 * able to be sent over to the network and received on the other end
 * 
 */
public class MsgHandler {
	/**
	 * Converts the byte encoded message to a transferable and receivable
	 * network message
	 * 
	 * @param encodedMsg
	 *            the byte encoded message to be sent
	 * @return the byte encoded transferable and receivable network message
	 */
	public static byte[] createNetworkMsg(byte[] encodedMsg) {
		ByteBuffer byteBuffer = ByteBuffer.allocate(encodedMsg.length + 4);
		byteBuffer.putInt(encodedMsg.length);
		byteBuffer.put(encodedMsg);
		return byteBuffer.array();
	}

	/**
	 * Acquires a receivable byte encoded network message and converts it into a
	 * standard byte encoded message
	 * 
	 * @param in
	 *            the input stream to acquire the network message from
	 * @return the standard byte encoded message
	 * @throws IOException
	 *             if the input stream could not be read
	 */
	public static byte[] acquireNetworkMsg(InputStream in) throws IOException {
		byte[] byteArray = new byte[4];
		in.read(byteArray, 0, 4);
		byteArray = new byte[ByteBuffer.wrap(byteArray).getInt()];
		in.read(byteArray);
		return byteArray;
	}
}
