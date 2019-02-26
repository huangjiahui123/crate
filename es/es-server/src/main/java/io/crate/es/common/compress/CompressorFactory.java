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

package io.crate.es.common.compress;

import io.crate.es.common.Nullable;
import io.crate.es.common.bytes.BytesReference;
import io.crate.es.common.io.stream.BytesStreamOutput;
import io.crate.es.common.io.stream.StreamInput;
import io.crate.es.common.xcontent.XContentHelper;
import io.crate.es.common.xcontent.XContentType;
import io.crate.es.core.internal.io.Streams;

import java.io.IOException;
import java.util.Objects;

public class CompressorFactory {

    public static final Compressor COMPRESSOR = new DeflateCompressor();

    @Nullable
    public static Compressor compressor(BytesReference bytes) {
            if (COMPRESSOR.isCompressed(bytes)) {
                // bytes should be either detected as compressed or as xcontent,
                // if we have bytes that can be either detected as compressed or
                // as a xcontent, we have a problem
                assert XContentHelper.xContentType(bytes) == null;
                return COMPRESSOR;
            }

        XContentType contentType = XContentHelper.xContentType(bytes);
        if (contentType == null) {
            if (isAncient(bytes)) {
                throw new IllegalStateException("unsupported compression: index was created before v2.0.0.beta1 and wasn't upgraded?");
            }
            throw new NotXContentException("Compressor detection can only be called on some xcontent bytes or compressed xcontent bytes");
        }

        return null;
    }

    /** true if the bytes were compressed with LZF: only used before elasticsearch 2.0 */
    private static boolean isAncient(BytesReference bytes) {
        return bytes.length() >= 3 &&
               bytes.get(0) == 'Z' &&
               bytes.get(1) == 'V' &&
               (bytes.get(2) == 0 || bytes.get(2) == 1);
    }

    /**
     * Uncompress the provided data, data can be detected as compressed using {@link #isCompressed(BytesReference)}.
     * @throws NullPointerException a NullPointerException will be thrown when bytes is null
     */
    public static BytesReference uncompressIfNeeded(BytesReference bytes) throws IOException {
        Compressor compressor = compressor(Objects.requireNonNull(bytes, "the BytesReference must not be null"));
        BytesReference uncompressed;
        if (compressor != null) {
            uncompressed = uncompress(bytes, compressor);
        } else {
            uncompressed = bytes;
        }

        return uncompressed;
    }

    /** Decompress the provided {@link BytesReference}. */
    public static BytesReference uncompress(BytesReference bytes) throws IOException {
        Compressor compressor = compressor(bytes);
        if (compressor == null) {
            throw new NotCompressedException();
        }
        return uncompress(bytes, compressor);
    }

    private static BytesReference uncompress(BytesReference bytes, Compressor compressor) throws IOException {
        StreamInput compressed = compressor.streamInput(bytes.streamInput());
        BytesStreamOutput bStream = new BytesStreamOutput();
        Streams.copy(compressed, bStream);
        compressed.close();
        return bStream.bytes();
    }
}
