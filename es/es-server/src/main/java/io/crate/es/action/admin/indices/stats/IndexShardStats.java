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

package io.crate.es.action.admin.indices.stats;

import io.crate.es.common.io.stream.StreamInput;
import io.crate.es.common.io.stream.StreamOutput;
import io.crate.es.common.io.stream.Streamable;
import io.crate.es.index.shard.ShardId;

import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;

public class IndexShardStats implements Iterable<ShardStats>, Streamable {

    private ShardId shardId;

    private ShardStats[] shards;

    private IndexShardStats() {}

    public IndexShardStats(ShardId shardId, ShardStats[] shards) {
        this.shardId = shardId;
        this.shards = shards;
    }

    public ShardId getShardId() {
        return this.shardId;
    }

    public ShardStats[] getShards() {
        return shards;
    }

    public ShardStats getAt(int position) {
        return shards[position];
    }

    @Override
    public Iterator<ShardStats> iterator() {
        return Arrays.stream(shards).iterator();
    }

    private CommonStats total = null;

    public CommonStats getTotal() {
        if (total != null) {
            return total;
        }
        CommonStats stats = new CommonStats();
        for (ShardStats shard : shards) {
            stats.add(shard.getStats());
        }
        total = stats;
        return stats;
    }

    private CommonStats primary = null;

    public CommonStats getPrimary() {
        if (primary != null) {
            return primary;
        }
        CommonStats stats = new CommonStats();
        for (ShardStats shard : shards) {
            if (shard.getShardRouting().primary()) {
                stats.add(shard.getStats());
            }
        }
        primary = stats;
        return stats;
    }

    @Override
    public void readFrom(StreamInput in) throws IOException {
        shardId = ShardId.readShardId(in);
        int shardSize = in.readVInt();
        shards = new ShardStats[shardSize];
        for (int i = 0; i < shardSize; i++) {
            shards[i] = ShardStats.readShardStats(in);
        }
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        shardId.writeTo(out);
        out.writeVInt(shards.length);
        for (ShardStats stats : shards) {
            stats.writeTo(out);
        }
    }

    public static IndexShardStats readIndexShardStats(StreamInput in) throws IOException {
        IndexShardStats indexShardStats = new IndexShardStats();
        indexShardStats.readFrom(in);
        return indexShardStats;
    }

}
