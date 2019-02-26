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

package io.crate.es.repositories;

import com.carrotsearch.hppc.ObjectContainer;
import com.carrotsearch.hppc.cursors.ObjectCursor;
import org.apache.logging.log4j.message.ParameterizedMessage;
import io.crate.es.action.ActionListener;
import io.crate.es.cluster.node.DiscoveryNode;
import io.crate.es.cluster.node.DiscoveryNodes;
import io.crate.es.cluster.service.ClusterService;
import io.crate.es.common.component.AbstractComponent;
import io.crate.es.common.io.stream.StreamInput;
import io.crate.es.common.io.stream.StreamOutput;
import io.crate.es.common.settings.Settings;
import io.crate.es.repositories.RepositoriesService.VerifyResponse;
import io.crate.es.threadpool.ThreadPool;
import io.crate.es.transport.EmptyTransportResponseHandler;
import io.crate.es.transport.TransportChannel;
import io.crate.es.transport.TransportException;
import io.crate.es.transport.TransportRequest;
import io.crate.es.transport.TransportRequestHandler;
import io.crate.es.transport.TransportResponse;
import io.crate.es.transport.TransportService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class VerifyNodeRepositoryAction  extends AbstractComponent {
    public static final String ACTION_NAME = "internal:admin/repository/verify";

    private final TransportService transportService;

    private final ClusterService clusterService;

    private final RepositoriesService repositoriesService;

    public VerifyNodeRepositoryAction(Settings settings, TransportService transportService, ClusterService clusterService, RepositoriesService repositoriesService) {
        super(settings);
        this.transportService = transportService;
        this.clusterService = clusterService;
        this.repositoriesService = repositoriesService;
        transportService.registerRequestHandler(ACTION_NAME, VerifyNodeRepositoryRequest::new, ThreadPool.Names.SNAPSHOT, new VerifyNodeRepositoryRequestHandler());
    }

    public void verify(String repository, String verificationToken, final ActionListener<VerifyResponse> listener) {
        final DiscoveryNodes discoNodes = clusterService.state().nodes();
        final DiscoveryNode localNode = discoNodes.getLocalNode();

        final ObjectContainer<DiscoveryNode> masterAndDataNodes = discoNodes.getMasterAndDataNodes().values();
        final List<DiscoveryNode> nodes = new ArrayList<>();
        for (ObjectCursor<DiscoveryNode> cursor : masterAndDataNodes) {
            DiscoveryNode node = cursor.value;
            nodes.add(node);
        }
        final CopyOnWriteArrayList<VerificationFailure> errors = new CopyOnWriteArrayList<>();
        final AtomicInteger counter = new AtomicInteger(nodes.size());
        for (final DiscoveryNode node : nodes) {
            if (node.equals(localNode)) {
                try {
                    doVerify(repository, verificationToken, localNode);
                } catch (Exception e) {
                    logger.warn(() -> new ParameterizedMessage("[{}] failed to verify repository", repository), e);
                    errors.add(new VerificationFailure(node.getId(), e));
                }
                if (counter.decrementAndGet() == 0) {
                    finishVerification(listener, nodes, errors);
                }
            } else {
                transportService.sendRequest(node, ACTION_NAME, new VerifyNodeRepositoryRequest(repository, verificationToken), new EmptyTransportResponseHandler(ThreadPool.Names.SAME) {
                    @Override
                    public void handleResponse(TransportResponse.Empty response) {
                        if (counter.decrementAndGet() == 0) {
                            finishVerification(listener, nodes, errors);
                        }
                    }

                    @Override
                    public void handleException(TransportException exp) {
                        errors.add(new VerificationFailure(node.getId(), exp));
                        if (counter.decrementAndGet() == 0) {
                            finishVerification(listener, nodes, errors);
                        }
                    }
                });
            }
        }
    }

    public void finishVerification(ActionListener<VerifyResponse> listener, List<DiscoveryNode> nodes, CopyOnWriteArrayList<VerificationFailure> errors) {
        listener.onResponse(new RepositoriesService.VerifyResponse(nodes.toArray(new DiscoveryNode[nodes.size()]), errors.toArray(new VerificationFailure[errors.size()])));
    }

    private void doVerify(String repositoryName, String verificationToken, DiscoveryNode localNode) {
        Repository repository = repositoriesService.repository(repositoryName);
        repository.verify(verificationToken, localNode);
    }

    public static class VerifyNodeRepositoryRequest extends TransportRequest {

        private String repository;
        private String verificationToken;

        public VerifyNodeRepositoryRequest() {
        }

        VerifyNodeRepositoryRequest(String repository, String verificationToken) {
            this.repository = repository;
            this.verificationToken = verificationToken;
        }

        @Override
        public void readFrom(StreamInput in) throws IOException {
            super.readFrom(in);
            repository = in.readString();
            verificationToken = in.readString();
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            super.writeTo(out);
            out.writeString(repository);
            out.writeString(verificationToken);
        }
    }

    class VerifyNodeRepositoryRequestHandler implements TransportRequestHandler<VerifyNodeRepositoryRequest> {
        @Override
        public void messageReceived(VerifyNodeRepositoryRequest request, TransportChannel channel) throws Exception {
            DiscoveryNode localNode = clusterService.state().nodes().getLocalNode();
            try {
                doVerify(request.repository, request.verificationToken, localNode);
            } catch (Exception ex) {
                logger.warn(() -> new ParameterizedMessage("[{}] failed to verify repository", request.repository), ex);
                throw ex;
            }
            channel.sendResponse(TransportResponse.Empty.INSTANCE);
        }
    }

}
