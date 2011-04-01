/*--------------------------------------------------------------------------
 *  Copyright 2011 Taro L. Saito
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *--------------------------------------------------------------------------*/
//--------------------------------------
// XerialJ
//
// SnappyOutputStream.java
// Since: 2011/03/31 17:44:10
//
// $URL$
// $Author$
//--------------------------------------
package org.xerial.snappy;

import java.io.IOException;
import java.io.OutputStream;

/**
 * This class implements a stream filter for writing compressed data using
 * Snappy.
 * 
 * The input data is blocked into 32KB size, and each block is compressed and
 * then passed to the given {@link OutputStream}.
 * 
 * The output data format is a sequence of (compressed chunk size, compressed
 * data chunk binary...) pair.
 * 
 * @author leo
 * 
 */
public class SnappyOutputStream extends OutputStream
{
    static final int             DEFAULT_BLOCK_SIZE = 4 * 1024 * 1024; // Use 4 MB for the default block size

    protected final OutputStream out;
    private final int            blockSize;
    private int                  cursor             = 0;
    protected byte[]             uncompressed;
    protected byte[]             compressed;

    public SnappyOutputStream(OutputStream out) throws IOException {
        this(out, DEFAULT_BLOCK_SIZE);
    }

    public SnappyOutputStream(OutputStream out, int blockSize) throws IOException {
        this.out = out;
        this.blockSize = blockSize;
        uncompressed = new byte[blockSize];
        compressed = new byte[Snappy.maxCompressedLength(blockSize)];
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {

        for (int readBytes = 0; readBytes < len;) {
            int copyLen = Math.min(uncompressed.length - cursor, len - readBytes);
            System.arraycopy(b, off + readBytes, uncompressed, cursor, copyLen);
            readBytes += copyLen;
            cursor += copyLen;

            if (cursor >= uncompressed.length) {
                dump();
            }
        }
    }

    @Override
    public void write(int b) throws IOException {
        if (cursor >= uncompressed.length) {
            dump();
        }
        uncompressed[cursor++] = (byte) b;
    }

    @Override
    public void flush() throws IOException {
        dump();
        out.flush();
    }

    public static void writeInt(OutputStream out, int value) throws IOException {
        out.write((value >> 24) & 0xFF);
        out.write((value >> 16) & 0xFF);
        out.write((value >> 8) & 0xFF);
        out.write((value >> 0) & 0xFF);
    }

    public static int readInt(byte[] buffer, int pos) {
        int b1 = (buffer[pos] & 0xFF) << 24;
        int b2 = (buffer[pos + 1] & 0xFF) << 16;
        int b3 = (buffer[pos + 2] & 0xFF) << 8;
        int b4 = buffer[pos + 3] & 0xFF;
        return b1 | b2 | b3 | b4;
    }

    protected void dump() throws IOException {
        if (cursor <= 0)
            return; // no need to dump

        // Compress and dump the buffer content
        try {
            int compressedSize = Snappy.compress(uncompressed, 0, cursor, compressed, 0);
            writeInt(out, compressedSize);
            out.write(compressed, 0, compressedSize);
            cursor = 0;
        }
        catch (SnappyException e) {
            throw new IOException(e);
        }
    }

    @Override
    public void close() throws IOException {
        flush();

        super.close();
        out.close();
    }

}