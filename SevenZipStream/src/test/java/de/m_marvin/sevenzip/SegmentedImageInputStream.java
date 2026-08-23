package de.m_marvin.sevenzip;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

public class SegmentedImageInputStream extends InputStream {

	private final DataInputStream stream;
	private int width;
	private int height;
	private int segmentCnt = 0;
	private ByteBuffer segmentBuffer;

	public SegmentedImageInputStream(DataInputStream stream) {
		this.stream = stream;
	}
	
	public SegmentedImageInputStream(InputStream stream) {
		this.stream = new DataInputStream(stream);
	}
	
	public int getWidth() {
		return width;
	}
	
	public int getHeight() {
		return height;
	}
	
	public int getSegmentCount() {
		return segmentCnt;
	}
	
	public void readHeader() throws IOException {
		if (this.segmentCnt > 0 || this.segmentBuffer != null)
			throw new IllegalStateException("header has already been read");
		if (!this.stream.readUTF().equals(SegmentedImageOutputStream.MAGIC_STRING))
			throw new IOException("file not a segimg file");
		this.width = this.stream.readInt();
		this.height = this.stream.readInt();
		this.segmentBuffer = ByteBuffer.allocate(this.width * this.height * 4);
		this.segmentBuffer.position(this.segmentBuffer.capacity());
	}
	
	public boolean skipNextSegment() throws IOException {
		try {
			if (this.segmentCnt == 0)
				readHeader();
			this.stream.skip(this.stream.readInt());
			this.segmentCnt++;
			return true;
		} catch (EOFException e) {
			return false;
		} catch (IOException e) {
			throw new IOException("exception occured while reading segment data", e);
		}
	}
	
	@SuppressWarnings("resource")
	private boolean fillBuffer() throws IOException {
		try {
			if (this.segmentCnt == 0 && this.segmentBuffer == null)
				readHeader();
			ByteArrayInputStream compressed = new ByteArrayInputStream(this.stream.readNBytes(this.stream.readInt()));
			InputStream segmentStream = new LZMAInputStream(compressed, this.width * this.height * 4);
			int n = segmentStream.read(this.segmentBuffer.array());
			if (n < 0)
				throw new IOException("unexpected end of segment stream, segimg corrupted");
			this.segmentBuffer.position(0);
			this.segmentBuffer.limit(n);
			this.segmentCnt++;
			return true;
		} catch (EOFException e) {
			return false;
		} catch (IOException e) {
			throw new IOException("exception occured while reading segment data", e);
		}
	}
	
	@Override
	public int read() throws IOException {
		if (this.segmentCnt == 0 || this.segmentBuffer.remaining() == 0)
			if (!fillBuffer())
				return 0;
		return this.segmentBuffer.get();
	}
	
	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		for (int i = 0; i < len;) {
			if (this.segmentCnt == 0 || this.segmentBuffer.remaining() == 0)
				if (!fillBuffer())
					return 0;
			int n = Math.min(this.segmentBuffer.remaining(), len);
			this.segmentBuffer.get(b, i + off, n);
			i += n;
		}
		return len;
	}
	
	public int readPixels(int[] b, int off, int len) throws IOException {
		for (int i = 0; i < len;) {
			if (this.segmentCnt == 0 || this.segmentBuffer.remaining() == 0)
				if (!fillBuffer())
					return i;
			IntBuffer segment = this.segmentBuffer.asIntBuffer();
			int n = Math.min(segment.remaining(), len - i);
			segment.get(b, i + off, n);
			this.segmentBuffer.position(this.segmentBuffer.position() + segment.position() * 4);
			i += n;
		}
		return len;
	}
	
	public BufferedImage readSegment() throws IOException {
		if (this.segmentCnt == 0 && this.segmentBuffer == null)
			readHeader();
		int[] pixels = new int[this.width * this.height];
		int n = readPixels(pixels, 0, pixels.length);
		if (n < this.width)
			return null;
		BufferedImage image = new BufferedImage(this.width, n / this.width, BufferedImage.TYPE_INT_ARGB);
		image.setRGB(0, 0, this.width, n / this.width, pixels, 0, this.width);
		return image;
	}
	
	@Override
	public void close() throws IOException {
		this.stream.close();
	}
	
}
