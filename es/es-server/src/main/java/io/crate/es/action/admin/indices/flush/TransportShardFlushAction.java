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

package io.crate.es.action.admin.indices.flush;

import io.crate.es.action.support.replication.ReplicationResponse;
import io.crate.es.action.support.replication.TransportReplicationAction;
import io.crate.es.cluster.action.shard.ShardStateAction;
import io.crate.es.cluster.metadata.IndexNameExpressionResolver;
import io.crate.es.cluster.service.ClusterService;
import io.crate.es.common.inject.Inject;
import io.crate.es.common.settings.Settings;
import io.crate.es.index.shard.IndexShard;
import io.crate.es.indices.IndicesService;
import io.crate.es.threadpool.ThreadPool;
import io.crate.es.transport.TransportService;

public class TransportShardFlushAction extends TransportReplicationAction<ShardFlushRequest, ShardFlushRequest, ReplicationResponse> {

    public static final String NAME = FlushAction.NAME + "[s]";

    @Inject
    public TransportShardFlushAction(Settings settings, TransportService transportService, ClusterService clusterService,
                                     IndicesService indicesService, ThreadPool threadPool, ShardStateAction shardStateAction,
                                     IndexNameExpressionResolver indexNameExpressionResolver) {
        super(settings, NAME, transportService, clusterService, indicesService, threadPool, shardStateAction,
            indexNameExpressionResolver, ShardFlushRequest::new, ShardFlushRequest::new, ThreadPool.Names.FLUSH);
    }

    @Override
    protected ReplicationResponse newResponseInstance() {
        return new ReplicationResponse();
    }

    @Override
    protected PrimaryResult shardOperationOnPrimary(ShardFlushRequest shardRequest, IndexShard primary) {
        primary.flush(shardRequest.getRequest());
        logger.trace("{} flush request executed on primary", primary.shardId());
        return new PrimaryResult<>(shardRequest, new ReplicationResponse());
    }

    @Override
    protected ReplicaResult shardOperationOnReplica(ShardFlushRequest request, IndexShard replica) {
        replica.flush(request.getRequest());
        logger.trace("{} flush request executed on replica", replica.shardId());
        return new ReplicaResult();
    }
}
