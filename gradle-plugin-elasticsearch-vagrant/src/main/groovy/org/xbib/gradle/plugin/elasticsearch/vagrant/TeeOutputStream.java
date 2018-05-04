package org.xbib.gradle.plugin.elasticsearch.vagrant;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Classic splitter of OutputStream. Named after the unix 'tee'
 * command. It allows a stream to be branched off so there
 * are now two streams.
 *
 */
public class TeeOutputStream extends ProxyOutputStream {

    /** the second OutputStream to write to */
    protected OutputStream branch; //TODO consider making this private

    /**
     * Constructs a TeeOutputStream.
     * @param out the main OutputStream
     * @param branch the second OutputStream
     */
    public TeeOutputStream(final OutputStream out, final OutputStream branch) {
        super(out);
        this.branch = branch;
    }

    /**
     * Write the bytes to both streams.
     * @param b the bytes to write
     * @throws IOException if an I/O error occurs
     */
    @Override
    public synchronized void write(final byte[] b) throws IOException {
        super.write(b);
        this.branch.write(b);
    }

    /**
     * Write the specified bytes to both streams.
     * @param b the bytes to write
     * @param off The start offset
     * @param len The number of bytes to write
     * @throws IOException if an I/O error occurs
     */
    @Override
    public synchronized void write(final byte[] b, final int off, final int len) throws IOException {
        super.write(b, off, len);
        this.branch.write(b, off, len);
    }

    /**
     * Write a byte to both streams.
     * @param b the byte to write
     * @throws IOException if an I/O error occurs
     */
    @Override
    public synchronized void write(final int b) throws IOException {
        super.write(b);
        this.branch.write(b);
    }

    /**
     * Flushes both streams.
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void flush() throws IOException {
        super.flush();
        this.branch.flush();
    }

    /**
     * Closes both output streams.
     *
     * If closing the main output stream throws an exception, attempt to close the branch output stream.
     *
     * If closing the main and branch output streams both throw exceptions, which exceptions is thrown by this method is
     * currently unspecified and subject to change.
     *
     * @throws IOException
     *             if an I/O error occurs
     */
    @Override
    public void close() throws IOException {
        try {
            super.close();
        } finally {
            this.branch.close();
        }
    }

}
