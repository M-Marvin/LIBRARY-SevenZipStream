package de.m_marvin.sevenzip;

import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import sevenzip.compression.lzma.Decoder;
import sevenzip.compression.lzma.Encoder;

public class LZMAInputStream extends InputStream {
	
	private final InputStream stream;
	private final Decoder decoder = new Decoder();
	
	private final PipedInputStream bufferStreamOut;
	private final PipedOutputStream bufferStreamIn;
	private final CompletableFuture<Void> decoderWorker;
	private long dataLen = 0;
	
	public LZMAInputStream(InputStream stream, int bufferSize) throws IOException {
		this.stream = stream;
		
		try {
			this.decoder.SetDecoderProperties(this.stream.readNBytes(Encoder.kPropSize));
			this.dataLen = ByteBuffer.wrap(this.stream.readNBytes(8)).order(ByteOrder.LITTLE_ENDIAN).getLong();
		} catch (IOException e) {
			throw new IOException("failed to read lzma header");
		}
		
		this.bufferStreamIn = new PipedOutputStream();
		this.bufferStreamOut = new PipedInputStream(this.bufferStreamIn, bufferSize);
		this.decoderWorker = CompletableFuture.runAsync(this::decode);
	}
	
	private void decode() {
		try {
			this.decoder.Code(this.stream, this.bufferStreamIn, this.dataLen);
			this.bufferStreamIn.close();
		} catch (IOException e) {
		 throw new RuntimeException(e);
		} finally {
			try {
				this.bufferStreamIn.close();
			} catch (IOException e) {}
		}
	}
	
	@Override
	public int read() throws IOException {
		try {
			return this.bufferStreamOut.read();
		} catch (Exception e) {
			if (this.decoderWorker.isCompletedExceptionally())
				throw new IOException("decoder encountered an exception", this.decoderWorker.exceptionNow().getCause());
			throw e;
		}
	}
	
	@Override
	public int read(byte[] b) throws IOException {
		try {
			return this.bufferStreamOut.read(b);
		} catch (Exception e) {
			if (this.decoderWorker.isCompletedExceptionally())
				throw new IOException("decoder encountered an exception", this.decoderWorker.exceptionNow().getCause());
			throw e;
		}
	}
	
	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		try {
			return this.bufferStreamOut.read(b, off, len);
		} catch (Exception e) {
			if (this.decoderWorker.isCompletedExceptionally())
				throw new IOException("decoder encountered an exception", this.decoderWorker.exceptionNow().getCause());
			throw e;
		}
	}
	
	@Override
	public void close() throws IOException {
		this.stream.close();
		this.bufferStreamIn.close();
		try {
			this.decoderWorker.get();
		} catch (InterruptedException | ExecutionException e) {
			throw new IOException("inerrupted when waiting for decoder to finish", e);
		}
	}
	
}
