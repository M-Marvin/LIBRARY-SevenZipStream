package de.m_marvin.sevenzip;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import sevenzip.compression.lzma.Encoder;

public class LZMAOutputStream extends OutputStream {
	
	private final OutputStream stream;
	private final Encoder encoder = new Encoder();
	private final LZMAOptions options;

	private final PipedInputStream bufferStreamOut;
	private final PipedOutputStream bufferStreamIn;
	private final CompletableFuture<Void> encoderWorker;
	private long remaining;
	
	public LZMAOutputStream(OutputStream stream) throws IOException {
		this(stream, 1024);
	}
	
	public LZMAOutputStream(OutputStream stream, int bufferSize) throws IOException {
		this(stream, bufferSize, new LZMAOptions());
	}
	
	public LZMAOutputStream(OutputStream stream, int bufferSize, LZMAOptions options) throws IOException {
		this.stream = stream;
		this.options = options;
		
		if (!this.encoder.SetDictionarySize(this.options.dictionary))
			throw new IllegalArgumentException("dictionariy size is invalid!");
		if (!this.encoder.SetNumFastBytes(this.options.fastBytes))
			throw new IllegalArgumentException("num fast bytes is invalid!");
		if (!this.encoder.SetMatchFinder(this.options.matchFinderBT4 ? 1 : 0))
			throw new IllegalArgumentException("match finder is invalid!");
		if (!this.encoder.SetLcLpPb(this.options.literakContextBits, this.options.literalPosBits, this.options.posBits))
			throw new IllegalArgumentException("lc-lp-pb is invalid!");
		this.encoder.SetEndMarkerMode(this.options.dataLen == -1);
		this.remaining = this.options.dataLen;
		
		try {
			this.encoder.WriteCoderProperties(this.stream);
			this.stream.write(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(this.remaining).array());
		} catch (IOException e) {
			throw new IOException("failed to write lzma header", e);
		}
		
		this.bufferStreamIn = new PipedOutputStream();
		this.bufferStreamOut = new PipedInputStream(this.bufferStreamIn, bufferSize);
		this.encoderWorker = CompletableFuture.runAsync(this::encode);
	}
	
	private void encode() {
		try {
			this.encoder.Code(this.bufferStreamOut, this.stream, -1, -1, null);
		} catch (IOException e) {
			throw new RuntimeException(e);
		} finally {
			try {
				this.bufferStreamOut.close();
			} catch (IOException e) {}
		}
	}
	
	@Override
	public void write(int b) throws IOException {
		if (this.options.dataLen != -1) {
			if (this.remaining < 0)
				throw new IOException("lzma stream full");
			this.remaining--;
		}
		try {
			this.bufferStreamIn.write(b);
		} catch (Exception e) {
			if (this.encoderWorker.isCompletedExceptionally())
				throw new IOException("encoder encountered an exception", this.encoderWorker.exceptionNow().getCause());
			throw e;
		}
	}
	
	@Override
	public void write(byte[] b) throws IOException {
		if (this.options.dataLen != -1) {
			if (this.remaining < 0)
				throw new IOException("lzma stream full");
			this.remaining -= b.length;
		}
		try {
			this.bufferStreamIn.write(b);
		} catch (Exception e) {
			if (this.encoderWorker.isCompletedExceptionally())
				throw new IOException("encoder encountered an exception", this.encoderWorker.exceptionNow().getCause());
			throw e;
		}
	}
	
	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		if (this.options.dataLen != -1) {
			if (this.remaining < 0)
				throw new IOException("lzma stream full");
			this.remaining -= len;
		}
		try {
			this.bufferStreamIn.write(b, off, len);
		} catch (Exception e) {
			if (this.encoderWorker.isCompletedExceptionally())
				throw new IOException("encoder encountered an exception", this.encoderWorker.exceptionNow().getCause());
			throw e;
		}
	}
	
	@Override
	public void flush() throws IOException {
		this.stream.flush();
	}
	
	@Override
	public void close() throws IOException {
		this.bufferStreamIn.close();
		try {
			this.encoderWorker.get();
			flush();
			this.stream.close();
		} catch (InterruptedException | ExecutionException e) {
			throw new IOException("inerrupted when waiting for encoder to finish", e);
		}
	}
	
}
