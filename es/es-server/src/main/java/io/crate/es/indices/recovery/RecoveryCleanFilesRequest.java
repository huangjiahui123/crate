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

package io.crate.es.indices.recovery;

import io.crate.es.common.io.stream.StreamInput;
import io.crate.es.common.io.stream.StreamOutput;
import io.crate.es.index.shard.ShardId;
import io.crate.es.index.store.Store;
import io.crate.es.transport.TransportRequest;

import java.io.IOException;

public class RecoveryCleanFilesRequest extends TransportRequest {

    private long recoveryId;
    private ShardId shardId;

    private Store.MetadataSnapshot snapshotFiles;
    private int totalTranslogOps = RecoveryState.Translog.UNKNOWN;

    public RecoveryCleanFilesRequest() {
    }

    RecoveryCleanFilesRequest(long recoveryId, ShardId shardId, Store.MetadataSnapshot snapshotFiles, int totalTranslogOps) {
        this.recoveryId = recoveryId;
        this.shardId = shardId;
        this.snapshotFiles = snapshotFiles;
        this.totalTranslogOps = totalTranslogOps;
    }

    public long recoveryId() {
        return this.recoveryId;
    }

    public ShardId shardId() {
        return shardId;
    }

    @Override
    public void readFrom(StreamInput in) throws IOException {
        super.readFrom(in);
        recoveryId = in.readLong();
        shardId = ShardId.readShardId(in);
        snapshotFiles = new Store.MetadataSnapshot(in);
        totalTranslogOps = in.readVInt();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeLong(recoveryId);
        shardId.writeTo(out);
        snapshotFiles.writeTo(out);
        out.writeVInt(totalTranslogOps);
    }

    public Store.MetadataSnapshot sourceMetaSnapshot() {
        return snapshotFiles;
    }

    public int totalTranslogOps() {
        return totalTranslogOps;
    }
}
