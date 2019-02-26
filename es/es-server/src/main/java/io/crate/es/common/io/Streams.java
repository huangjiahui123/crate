/*
 * Licensed to Crate under one or more contributor license agreements.
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.  Crate licenses this file
 * to you under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.  You may
 * obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 *
 * However, if you have executed another commercial license agreement
 * with Crate these terms will supersede the license and you may use the
 * software solely pursuant to the terms of the relevant commercial
 * agreement.
 */

package io.crate.es.common.io;

import io.crate.es.common.io.stream.BytesStream;
import io.crate.es.common.bytes.BytesReference;
import io.crate.es.common.io.stream.StreamOutput;
import io.crate.es.core.internal.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Objects;

/**
 * Simple utility methods for file and stream copying.
 * All copy methods use a block size of 4096 bytes,
 * and close all affected streams when done.
 * <p>
 * Mainly for use within the framework,
 * but also useful for application code.
 */
public abstract class Streams {

    public static final int BUFFER_SIZE = 1024 * 8;


    //---------------------------------------------------------------------
    // Copy methods for java.io.InputStream / java.io.OutputStream
    //---------------------------------------------------------------------


    public static long copy(InputStream in, OutputStream out) throws IOException {
        return copy(in, out, new byte[BUFFER_SIZE]);
    }

    /**
     * Copy the contents of the given InputStream to the given OutputStream.
     * Closes both streams when done.
     *
     * @param in  the stream to copy from
     * @param out the stream to copy to
     * @return the number of bytes copied
     * @throws IOException in case of I/O errors
     */
    public static long copy(InputStream in, OutputStream out, byte[] buffer) throws IOException {
        Objects.requireNonNull(in, "No InputStream specified");
        Objects.requireNonNull(out, "No OutputStream specified");
        boolean success = false;
        try {
            long byteCount = 0;
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                byteCount += bytesRead;
            }
            out.flush();
            success = true;
            return byteCount;
        } finally {
            if (success) {
                IOUtils.close(in, out);
            } else {
                IOUtils.closeWhileHandlingException(in, out);
            }
        }
    }

    //---------------------------------------------------------------------
    // Copy methods for java.io.Reader / java.io.Writer
    //---------------------------------------------------------------------

    /**
     * Copy the contents of the given Reader to the given Writer.
     * Closes both when done.
     *
     * @param in  the Reader to copy from
     * @param out the Writer to copy to
     * @return the number of characters copied
     * @throws IOException in case of I/O errors
     */
    public static int copy(Reader in, Writer out) throws IOException {
        Objects.requireNonNull(in, "No Reader specified");
        Objects.requireNonNull(out, "No Writer specified");
        boolean success = false;
        try {
            int byteCount = 0;
            char[] buffer = new char[BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                byteCount += bytesRead;
            }
            out.flush();
            success = true;
            return byteCount;
        } finally {
            if (success) {
                IOUtils.close(in, out);
            } else {
                IOUtils.closeWhileHandlingException(in, out);
            }
        }
    }

    /**
     * Copy the contents of the given Reader into a String.
     * Closes the reader when done.
     *
     * @param in the reader to copy from
     * @return the String that has been copied to
     * @throws IOException in case of I/O errors
     */
    public static String copyToString(Reader in) throws IOException {
        StringWriter out = new StringWriter();
        copy(in, out);
        return out.toString();
    }

    public static int readFully(InputStream reader, byte[] dest, int offset, int len) throws IOException {
        int read = 0;
        while (read < len) {
            final int r = reader.read(dest, offset + read, len - read);
            if (r == -1) {
                break;
            }
            read += r;
        }
        return read;
    }

    /**
     * Wraps the given {@link BytesStream} in a {@link StreamOutput} that simply flushes when
     * close is called.
     */
    public static BytesStream flushOnCloseStream(BytesStream os) {
        return new FlushOnCloseOutputStream(os);
    }

    /**
     * A wrapper around a {@link BytesStream} that makes the close operation a flush. This is
     * needed as sometimes a stream will be closed but the bytes that the stream holds still need
     * to be used and the stream cannot be closed until the bytes have been consumed.
     */
    private static class FlushOnCloseOutputStream extends BytesStream {

        private final BytesStream delegate;

        private FlushOnCloseOutputStream(BytesStream bytesStreamOutput) {
            this.delegate = bytesStreamOutput;
        }

        @Override
        public void writeByte(byte b) throws IOException {
            delegate.writeByte(b);
        }

        @Override
        public void writeBytes(byte[] b, int offset, int length) throws IOException {
            delegate.writeBytes(b, offset, length);
        }

        @Override
        public void flush() throws IOException {
            delegate.flush();
        }

        @Override
        public void close() throws IOException {
            flush();
        }

        @Override
        public void reset() throws IOException {
            delegate.reset();
        }

        @Override
        public BytesReference bytes() {
            return delegate.bytes();
        }
    }
}
