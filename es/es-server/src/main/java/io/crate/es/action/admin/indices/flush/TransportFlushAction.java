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

import io.crate.es.action.support.DefaultShardOperationFailedException;
import io.crate.es.action.support.replication.ReplicationResponse;
import io.crate.es.action.support.replication.TransportBroadcastReplicationAction;
import io.crate.es.cluster.metadata.IndexNameExpressionResolver;
import io.crate.es.cluster.service.ClusterService;
import io.crate.es.common.inject.Inject;
import io.crate.es.common.settings.Settings;
import io.crate.es.index.shard.ShardId;
import io.crate.es.threadpool.ThreadPool;
import io.crate.es.transport.TransportService;

import java.util.List;

/**
 * Flush Action.
 */
public class TransportFlushAction extends TransportBroadcastReplicationAction<FlushRequest, FlushResponse, ShardFlushRequest, ReplicationResponse> {

    @Inject
    public TransportFlushAction(Settings settings, ThreadPool threadPool, ClusterService clusterService,
                                TransportService transportService,
                                IndexNameExpressionResolver indexNameExpressionResolver,
                                TransportShardFlushAction replicatedFlushAction) {
        super(FlushAction.NAME, FlushRequest::new, settings, threadPool, clusterService, transportService, indexNameExpressionResolver, replicatedFlushAction);
    }

    @Override
    protected ReplicationResponse newShardResponse() {
        return new ReplicationResponse();
    }

    @Override
    protected ShardFlushRequest newShardRequest(FlushRequest request, ShardId shardId) {
        return new ShardFlushRequest(request, shardId);
    }

    @Override
    protected FlushResponse newResponse(int successfulShards, int failedShards, int totalNumCopies, List
            <DefaultShardOperationFailedException> shardFailures) {
        return new FlushResponse(totalNumCopies, successfulShards, failedShards, shardFailures);
    }
}
