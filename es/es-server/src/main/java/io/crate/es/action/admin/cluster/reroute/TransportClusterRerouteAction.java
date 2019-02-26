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

package io.crate.es.action.admin.cluster.reroute;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.ParameterizedMessage;
import io.crate.es.action.ActionListener;
import io.crate.es.action.support.master.TransportMasterNodeAction;
import io.crate.es.cluster.AckedClusterStateUpdateTask;
import io.crate.es.cluster.ClusterState;
import io.crate.es.cluster.block.ClusterBlockException;
import io.crate.es.cluster.block.ClusterBlockLevel;
import io.crate.es.cluster.metadata.IndexNameExpressionResolver;
import io.crate.es.cluster.routing.allocation.AllocationService;
import io.crate.es.cluster.routing.allocation.RoutingExplanations;
import io.crate.es.cluster.service.ClusterService;
import io.crate.es.common.Priority;
import io.crate.es.common.inject.Inject;
import io.crate.es.common.settings.Settings;
import io.crate.es.threadpool.ThreadPool;
import io.crate.es.transport.TransportService;

public class TransportClusterRerouteAction extends TransportMasterNodeAction<ClusterRerouteRequest, ClusterRerouteResponse> {

    private final AllocationService allocationService;

    @Inject
    public TransportClusterRerouteAction(Settings settings, TransportService transportService, ClusterService clusterService, ThreadPool threadPool,
                                         AllocationService allocationService, IndexNameExpressionResolver indexNameExpressionResolver) {
        super(settings, ClusterRerouteAction.NAME, transportService, clusterService, threadPool, indexNameExpressionResolver, ClusterRerouteRequest::new);
        this.allocationService = allocationService;
    }

    @Override
    protected String executor() {
        // we go async right away
        return ThreadPool.Names.SAME;
    }

    @Override
    protected ClusterBlockException checkBlock(ClusterRerouteRequest request, ClusterState state) {
        return state.blocks().globalBlockedException(ClusterBlockLevel.METADATA_WRITE);
    }

    @Override
    protected ClusterRerouteResponse newResponse() {
        return new ClusterRerouteResponse();
    }

    @Override
    protected void masterOperation(final ClusterRerouteRequest request, final ClusterState state, final ActionListener<ClusterRerouteResponse> listener) {
        ActionListener<ClusterRerouteResponse> logWrapper = ActionListener.wrap(
            response -> {
                if (request.dryRun() == false) {
                    response.getExplanations().getYesDecisionMessages().forEach(logger::info);
                }
                listener.onResponse(response);
            },
            listener::onFailure
        );

        clusterService.submitStateUpdateTask("cluster_reroute (api)", new ClusterRerouteResponseAckedClusterStateUpdateTask(logger,
            allocationService, request, logWrapper));
    }

    static class ClusterRerouteResponseAckedClusterStateUpdateTask extends AckedClusterStateUpdateTask<ClusterRerouteResponse> {

        private final ClusterRerouteRequest request;
        private final ActionListener<ClusterRerouteResponse> listener;
        private final Logger logger;
        private final AllocationService allocationService;
        private volatile ClusterState clusterStateToSend;
        private volatile RoutingExplanations explanations;

        ClusterRerouteResponseAckedClusterStateUpdateTask(Logger logger, AllocationService allocationService, ClusterRerouteRequest request,
                                                          ActionListener<ClusterRerouteResponse> listener) {
            super(Priority.IMMEDIATE, request, listener);
            this.request = request;
            this.listener = listener;
            this.logger = logger;
            this.allocationService = allocationService;
        }

        @Override
        protected ClusterRerouteResponse newResponse(boolean acknowledged) {
            return new ClusterRerouteResponse(acknowledged, clusterStateToSend, explanations);
        }

        @Override
        public void onAckTimeout() {
            listener.onResponse(new ClusterRerouteResponse(false, clusterStateToSend, new RoutingExplanations()));
        }

        @Override
        public void onFailure(String source, Exception e) {
            logger.debug(() -> new ParameterizedMessage("failed to perform [{}]", source), e);
            super.onFailure(source, e);
        }

        @Override
        public ClusterState execute(ClusterState currentState) {
            AllocationService.CommandsResult commandsResult =
                allocationService.reroute(currentState, request.getCommands(), request.explain(), request.isRetryFailed());
            clusterStateToSend = commandsResult.getClusterState();
            explanations = commandsResult.explanations();
            if (request.dryRun()) {
                return currentState;
            }
            return commandsResult.getClusterState();
        }
    }
}
