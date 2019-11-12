/**
 *  Copyright (C) Zhang,Yuexiang (xfeep)
 *
 */
package nginx.clojure;

import static nginx.clojure.MiniConstants.NGX_CLOJURE_BUF_FILE_FLAG;
import static nginx.clojure.MiniConstants.NGX_CLOJURE_BUF_FLUSH_FLAG;
import static nginx.clojure.MiniConstants.NGX_CLOJURE_BUF_LAST_FLAG;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class NginxChainWrappedInputStream extends InputStream {
	
	protected NginxRequest r;
	protected long chain;
	protected int index;
	protected InputStream[] streams;
	protected int flag;
	protected long total;

	public static class RangeSeekableFileInputStream extends InputStream {

		protected final RandomAccessFile file;
		protected final long start;
		protected long pos;
		protected final long length;
		protected final String filePath;
		protected final boolean shadowFromNative;
		
		public RangeSeekableFileInputStream() {
			file = null;
			start = 0;
			length = pos = 0;
			filePath = null;
			shadowFromNative = false;
		}
		
		public RangeSeekableFileInputStream(String file, long pos, long len) throws IOException {
			this.file = new RandomAccessFile(file, "r");
			this.file.seek(pos);
			this.start = this.pos = pos;
			this.length = len;
			this.filePath = file;
			shadowFromNative = false;
		}
		
		public RangeSeekableFileInputStream(int fd, String file, long pos, long len) throws IOException {
			if (NginxClojureRT.getLog().isDebugEnabled()) {
				NginxClojureRT.getLog().info("RangeSeekableFileInputStream fd : %d, file : %s, pos : %d, len %d", fd, file, pos, len);
			}
			this.file = HackUtils.buildShadowRandomAccessFile(fd);
			this.file.seek(pos);
			this.start = this.pos = pos;
			this.length = len;
			this.filePath = file;
			shadowFromNative = true;
		}
		
		
		@Override
		public int read() throws IOException {
			if (pos == length) {
				return -1;
			}
			
			pos++;
			return file.read();
		}
		
		/* (non-Javadoc)
		 * @see java.io.InputStream#read(byte[], int, int)
		 */
		@Override
		public int read(byte[] b, int off, int len) throws IOException {
			if (pos == length) {
				return -1;
			}
			
			if (len == 0) {
				return 0;
			}
			
			if (pos + len >= length) {
				len = (int)(length - pos);
			}
			
			len = file.read(b, off, len);
			pos += len;
			return len;
		}
		
		public void rewind() throws IOException {
			pos = start;
			file.seek(pos);
		}
		
		@Override
		public void close() throws IOException {
			if (file != null && !shadowFromNative) {
				file.close();
			}
		}
		
		/* (non-Javadoc)
		 * @see java.io.InputStream#available()
		 */
		@Override
		public int available() throws IOException {
			return length - pos >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int)(length - pos);
		}
		
		@Override
		public String toString() {
			return filePath;
		}
		
	}
	
	public NginxChainWrappedInputStream() {
		this.chain = total = 0;
	}
	
	public NginxChainWrappedInputStream(NginxRequest r, long chain) throws IOException {
		this.r = r;
		this.chain = chain;
		this.total = 0;
		
		while (chain != 0) {
			ByteBuffer buf = NginxClojureRT.pickByteBuffer();
			chain = NginxClojureRT.ngx_http_clojure_mem_get_chain_info(chain, buf.array(), MiniConstants.BYTE_ARRAY_OFFSET, buf.remaining());
			buf.limit(buf.capacity());
			if (chain < 0) {
				throw new RuntimeException("Invalid request and chain: { chain=" + this.chain + ", request:" + r + ", rc=" + chain + "}");
			}
			
			buf.order(ByteOrder.nativeOrder());
			int streamsLen = (int)buf.getLong();
			int streamsPos = 0;
			if (streams == null) {
				streams = new InputStream[streamsLen];
			}else {
				streamsPos = streams.length;
				InputStream[] newStreams = new InputStream[streamsPos + streamsLen];
				System.arraycopy(streams, 0, newStreams, 0, streamsPos);
				streams = newStreams;
			}
			
			while (streamsPos < streams.length) {
				long typeAndLen = buf.getLong();
				long addr = buf.getLong();
				int type = (int)(typeAndLen >> 56);
				long len = typeAndLen & 0x00ffffffffffffffL;
				
				if ( (type & NGX_CLOJURE_BUF_FILE_FLAG) != 0) {
					long fd = buf.getLong();
					ByteBuffer fileNameBuf = buf.slice();
					fileNameBuf.limit((int)(addr >> 48));
					String file = HackUtils.decode(fileNameBuf, MiniConstants.DEFAULT_ENCODING, NginxClojureRT.pickCharBuffer());
					streams[streamsPos++] = new RangeSeekableFileInputStream((int)fd, file, addr & 0x0000ffffffffffffL, len);
				}else {
					streams[streamsPos++] = new NativeInputStream(addr, len);
				}
				
				if ( (type & NGX_CLOJURE_BUF_LAST_FLAG) != 0) {
					flag |= NGX_CLOJURE_BUF_LAST_FLAG;
					total += len;
					if (streamsPos != streams.length) {
						InputStream[] sa = new InputStream[streamsPos];
						System.arraycopy(streams, 0, sa, 0, streamsPos);
						streams = sa;
					}
					break;
				}
				
				if ( (type & NGX_CLOJURE_BUF_FLUSH_FLAG) != 0) {
					flag |= NGX_CLOJURE_BUF_FLUSH_FLAG;
				}
				
				total += len;
			}
		}
	}

	/* (non-Javadoc)
	 * @see java.io.InputStream#read()
	 */
	@Override
	public int read() throws IOException {
		if (chain == 0 || total == 0 || index >= streams.length) {
			return -1;
		}
		
		int c = streams[index].read();
		
		while (c == -1 && ++index < streams.length) {
			c = streams[index].read();
		}
		return c;
	}
	
	/* (non-Javadoc)
	 * @see java.io.InputStream#read(byte[], int, int)
	 */
	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		if (chain == 0 || index >= streams.length) {
			return -1;
		}
		
		if (len == 0) {
			return 0;
		}
		
		int total = 0;
		int c = 0;
		
		//now it is more eager than 0.5.0 and read more bytes as possible.
		while (index < streams.length && total < len) {
			c = streams[index].read(b, off + total, len - total);
			if (c <= 0) {
				index ++;
			} else {
				total += c;
			}
		}

		return total;
	}

	public long nativeChain() {
		return chain;
	}
	
	public NginxRequest getRquest() {
		return r;
	}
	
	public boolean isLast() {
		return  (flag & NGX_CLOJURE_BUF_LAST_FLAG) != 0;
	}
	
	public long total() {
		return total;
	}
	
	public void rewind() throws IOException {
		index = 0;
		for (InputStream in : streams) {
			if (in instanceof RangeSeekableFileInputStream) {
				RangeSeekableFileInputStream rin = (RangeSeekableFileInputStream) in;
				rin.rewind();
			}else {
				((NativeInputStream)in).rewind();
			}
		}
	}
	
	@Override
	public void close() throws IOException {
		
		if (streams == null) {
			return;
		}
		
		index = streams.length;
		Throwable e = null;
		for (InputStream in : streams) {
			try {
				in.close();
			}catch(Throwable ex) {
				if (e == null) {
					e = ex;
				}
			}
		}
		
		if (e != null) {
			if (e instanceof IOException) {
				throw (IOException)e;
			}else {
				throw new IOException("NginxChainWrappedInputStream.close meets error", e);
			}
		}
	}
	
	/* (non-Javadoc)
	 * @see java.io.InputStream#available()
	 */
	@Override
	public int available() throws IOException {
		if (chain == 0 || total == 0 || index >= streams.length) {
			return 0;
		}
		
		long a = 0;
		
		for (int i = index; i < streams.length; i++) {
			a += streams[i].available();
		}
		
		return a > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int)a;
	}
	
	public void prefetchNativeData() throws IOException {
		for (int i = 0; i < streams.length; i++) {
			InputStream in = streams[i];
			if (in instanceof NativeInputStream) {
				byte[] data = new byte[in.available()];
				in.read(data);
				streams[i] = new ByteArrayInputStream(data);
			}
		}
	}
}
