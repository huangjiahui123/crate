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

package io.crate.es.action.admin.cluster.tasks;

import io.crate.es.action.ActionListener;
import io.crate.es.action.support.master.TransportMasterNodeReadAction;
import io.crate.es.cluster.ClusterState;
import io.crate.es.cluster.block.ClusterBlockException;
import io.crate.es.cluster.block.ClusterBlockLevel;
import io.crate.es.cluster.metadata.IndexNameExpressionResolver;
import io.crate.es.cluster.service.ClusterService;
import io.crate.es.cluster.service.PendingClusterTask;
import io.crate.es.common.inject.Inject;
import io.crate.es.common.settings.Settings;
import io.crate.es.threadpool.ThreadPool;
import io.crate.es.transport.TransportService;

import java.util.List;

public class TransportPendingClusterTasksAction extends TransportMasterNodeReadAction<PendingClusterTasksRequest, PendingClusterTasksResponse> {

    private final ClusterService clusterService;

    @Inject
    public TransportPendingClusterTasksAction(Settings settings, TransportService transportService, ClusterService clusterService,
                                              ThreadPool threadPool, IndexNameExpressionResolver indexNameExpressionResolver) {
        super(settings, PendingClusterTasksAction.NAME, transportService, clusterService, threadPool, indexNameExpressionResolver, PendingClusterTasksRequest::new);
        this.clusterService = clusterService;
    }

    @Override
    protected String executor() {
        // very lightweight operation in memory, no need to fork to a thread
        return ThreadPool.Names.SAME;
    }

    @Override
    protected ClusterBlockException checkBlock(PendingClusterTasksRequest request, ClusterState state) {
        return state.blocks().globalBlockedException(ClusterBlockLevel.METADATA_READ);
    }

    @Override
    protected PendingClusterTasksResponse newResponse() {
        return new PendingClusterTasksResponse();
    }

    @Override
    protected void masterOperation(PendingClusterTasksRequest request, ClusterState state, ActionListener<PendingClusterTasksResponse> listener) {
        logger.trace("fetching pending tasks from cluster service");
        final List<PendingClusterTask> pendingTasks = clusterService.getMasterService().pendingTasks();
        logger.trace("done fetching pending tasks from cluster service");
        listener.onResponse(new PendingClusterTasksResponse(pendingTasks));
    }
}
