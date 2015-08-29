/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure;

import java.io.IOException;
import java.nio.ByteBuffer;

public class WholeMessageAdapter<T> extends MessageAdapter<T> {

	public static int DEFAULT_MAX_MESSAGE_SIZE = 256 * 1024;
	protected StringBuilder builder;
	protected ByteBuffer byteBuffer;
	protected int maxMessageSize;

	public WholeMessageAdapter() {
		this(DEFAULT_MAX_MESSAGE_SIZE);
	}

	public WholeMessageAdapter(int maxMessageSize) {
		builder = new StringBuilder();
		this.maxMessageSize = maxMessageSize;
	}

	@Override
	public void onTextMessage(T data, String message, boolean remaining) throws IOException {
		if (!remaining) {
			if (builder.length() == 0) {
				onWholeTextMessage(data, message);
			} else {
				if (builder.length() + message.length() <= maxMessageSize) {
					builder.append(message);
					String wm = builder.toString();
					builder.delete(0, builder.length());
					onWholeTextMessage(data, wm);
				} else {
					throw new IOException("Message size is too large > " + maxMessageSize
							+ "(current setting), we need set a larger value");
				}
			}
		} else {
			if (builder.length() + message.length() <= maxMessageSize) {
				builder.append(message);
			} else {
				throw new IOException("Message size is too large > " + maxMessageSize
						+ "(current setting), we need set a larger value");
			}
		}
	}

	@Override
	public void onBinaryMessage(T data, ByteBuffer message, boolean remaining) throws IOException {
		if (!remaining) {
			if (byteBuffer == null || byteBuffer.position() == 0) {
				onWholeBiniaryMessage(data, message);
			} else {
				if (byteBuffer == null) {
					byteBuffer = ByteBuffer.allocate(maxMessageSize);
				}
				if (byteBuffer.remaining() >= message.remaining()) {
					byteBuffer.put(message);
					byteBuffer.flip();
					onWholeBiniaryMessage(data, byteBuffer);
					byteBuffer = null;
				} else {
					throw new IOException("Message size is too large > " + maxMessageSize
							+ "(current setting), we need set a larger value");
				}
			}
		} else {
			if (byteBuffer == null) {
				byteBuffer = ByteBuffer.allocate(maxMessageSize);
			}
			if (byteBuffer.remaining() >= message.remaining()) {
				byteBuffer.put(message);
			} else {
				throw new IOException("Message size is too large > " + maxMessageSize
						+ "(current setting), we need set a larger value");
			}
		}
	}

	public void onWholeTextMessage(T data, String message) throws IOException {
	}

	public void onWholeBiniaryMessage(T data, ByteBuffer message) throws IOException {
	}

}
