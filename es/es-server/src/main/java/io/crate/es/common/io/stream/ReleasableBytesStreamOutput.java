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

package io.crate.es.common.io.stream;

import io.crate.es.common.bytes.BytesReference;
import io.crate.es.common.bytes.ReleasablePagedBytesReference;
import io.crate.es.common.lease.Releasable;
import io.crate.es.common.lease.Releasables;
import io.crate.es.common.util.BigArrays;
import io.crate.es.common.util.ByteArray;

/**
 * An bytes stream output that allows providing a {@link BigArrays} instance
 * expecting it to require releasing its content ({@link #bytes()}) once done.
 * <p>
 * Please note, closing this stream will release the bytes that are in use by any
 * {@link ReleasablePagedBytesReference} returned from {@link #bytes()}, so this
 * stream should only be closed after the bytes have been output or copied
 * elsewhere.
 */
public class ReleasableBytesStreamOutput extends BytesStreamOutput
    implements Releasable {

    private Releasable releasable;

    public ReleasableBytesStreamOutput(BigArrays bigarrays) {
        this(BigArrays.PAGE_SIZE_IN_BYTES, bigarrays);
    }

    public ReleasableBytesStreamOutput(int expectedSize, BigArrays bigArrays) {
        super(expectedSize, bigArrays);
        this.releasable = Releasables.releaseOnce(this.bytes);
    }

    /**
     * Returns a {@link Releasable} implementation of a
     * {@link BytesReference} that represents the current state of
     * the bytes in the stream.
     */
    @Override
    public ReleasablePagedBytesReference bytes() {
        return new ReleasablePagedBytesReference(bigArrays, bytes, count, releasable);
    }

    @Override
    public void close() {
        Releasables.close(releasable);
    }

    @Override
    void ensureCapacity(long offset) {
        final ByteArray prevBytes = this.bytes;
        super.ensureCapacity(offset);
        if (prevBytes != this.bytes) {
            // re-create the releasable with the new reference
            releasable = Releasables.releaseOnce(this.bytes);
        }
    }

    @Override
    public void reset() {
        final ByteArray prevBytes = this.bytes;
        super.reset();
        if (prevBytes != this.bytes) {
            // re-create the releasable with the new reference
            releasable = Releasables.releaseOnce(this.bytes);
        }
    }
}
