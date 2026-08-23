package de.m_marvin.sevenzip;

import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

public class SegmentedImageOutputStream extends OutputStream {
	
	public static final String MAGIC_STRING = "SEGIMGv2.0";
	
	private final DataOutputStream stream;
	private int width;
	private int height;
	private int segmentCnt = 0;
	private ByteBuffer segmentBuffer;

	public SegmentedImageOutputStream(DataOutputStream stream, int segmentWidth, int segmentHeight) {
		this.stream = stream;
		this.width = segmentWidth;
		this.height = segmentHeight;
		this.segmentBuffer = ByteBuffer.allocate(this.width * this.height * 4);
	}

	public SegmentedImageOutputStream(OutputStream stream, int segmentWidth, int segmentHeight) {
		this.stream = new DataOutputStream(stream);
		this.width = segmentWidth;
		this.height = segmentHeight;
		this.segmentBuffer = ByteBuffer.allocate(this.width * this.height * 4);
	}

	public SegmentedImageOutputStream(DataOutputStream stream) {
		this.stream = stream;
		this.width = 0;
		this.height = 0;
		this.segmentBuffer = null;
	}
	
	public SegmentedImageOutputStream(OutputStream stream) {
		this.stream = new DataOutputStream(stream);
		this.width = 0;
		this.height = 0;
		this.segmentBuffer = null;
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
	
	public void setSegmentSize(int width, int height) {
		if (this.segmentCnt > 0 || (this.segmentBuffer != null && this.segmentBuffer.position() != 0))
			throw new IllegalStateException("can't change dimensions after segments have been written");
		this.width = width;
		this.height = height;
		this.segmentBuffer = ByteBuffer.allocate(this.width * this.height * 4);
	}
	
	private void writeHeader() throws IOException {
		this.stream.writeUTF(MAGIC_STRING);
		this.stream.writeInt(this.width);
		this.stream.writeInt(this.height);
		this.stream.flush();
	}
	
	private void writeSegment() throws IOException {
		if (this.segmentBuffer == null)
			throw new IllegalStateException("segment size hasn't been set yet");
		try {
			if (this.segmentCnt == 0)
				writeHeader();
			ByteArrayOutputStream compressed = new ByteArrayOutputStream();
			OutputStream segmentStream = new LZMAOutputStream(compressed, this.width * this.height * 4);
			segmentStream.write(this.segmentBuffer.array(), 0, this.segmentBuffer.limit());
			segmentStream.close();
			DataOutputStream dataSteam = new DataOutputStream(this.stream);
			dataSteam.writeInt(compressed.size());
			dataSteam.write(compressed.toByteArray());
			dataSteam.flush();
			this.segmentCnt++;
		} catch (IOException e) {
			throw new IOException("exception occured while writing segment data", e);
		}
	}

	@Override
	public void write(int b) throws IOException {
		if (this.segmentBuffer == null)
			throw new IllegalStateException("segment size hasn't been set yet");
		this.segmentBuffer.put((byte) b);
		if (this.segmentBuffer.remaining() == 0) {
			this.segmentBuffer.flip();
			writeSegment();
			this.segmentBuffer.clear();
		}
	}
	
	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		if (this.segmentBuffer == null)
			throw new IllegalStateException("segment size hasn't been set yet");
		for (int i = 0; i < len;) {
			int n = Math.min(this.segmentBuffer.remaining(), len - i);
			this.segmentBuffer.put(b, off + i, n);
			if (this.segmentBuffer.remaining() == 0) {
				this.segmentBuffer.flip();
				writeSegment();
				this.segmentBuffer.clear();
			}
			i += n;
		}
	}
	
	public void writePixels(int[] b, int off, int len) throws IOException {
		if (this.segmentBuffer == null)
			throw new IllegalStateException("segment size hasn't been set yet");
		IntBuffer segment = this.segmentBuffer.asIntBuffer();
		for (int i = 0; i < len;) {
			int n = Math.min(segment.remaining(), len - i);
			segment.put(b, i + off, n);
			if (segment.remaining() == 0) {
				// the int buffer has its own position, so we manually have to update the byte buffer
				this.segmentBuffer.position(this.segmentBuffer.position() + segment.position() * 4);
				this.segmentBuffer.flip();
				writeSegment();
				this.segmentBuffer.clear();
				segment = this.segmentBuffer.asIntBuffer();
			}
			i += n;
		}
		// the int buffer has its own position, so we manually have to update the byte buffer
		this.segmentBuffer.position(this.segmentBuffer.position() + segment.position() * 4);
	}
	
	public void writeImage(BufferedImage image) throws IOException {
		if (image.getWidth() != this.width)
			throw new IllegalArgumentException("image does not match segmentw width");
		writePixels(image.getRGB(0, 0, this.width, image.getHeight(), null, 0, this.width), 0, this.width * image.getHeight());
	}
	
	public void writeRescaled(BufferedImage image) throws IOException {
		if (this.segmentBuffer == null)
			throw new IllegalStateException("segment size hasn't been set yet");
		if (image.getWidth() != this.width) {
			AffineTransform scale = new AffineTransform();
			double d = this.width / (double) image.getWidth();
			scale.scale(d, d);
			AffineTransformOp scaleOp = new AffineTransformOp(scale, AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
			image = scaleOp.filter(image, null);
		}
		writeImage(image);
	}
	
	@Override
	public void flush() throws IOException {
		if (this.segmentBuffer.position() > 0) {
			this.segmentBuffer.flip();
			writeSegment();
			this.segmentBuffer.clear();
		}
		this.stream.flush();
	}
	
	@Override
	public void close() throws IOException {
		if (this.segmentBuffer != null)
			flush();
		this.stream.close();
	}
	
}
