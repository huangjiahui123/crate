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
package io.crate.es.test.disruption;

import io.crate.es.test.InternalTestCluster;
import org.apache.logging.log4j.core.util.Throwables;
import io.crate.es.cluster.ClusterState;
import io.crate.es.cluster.ClusterStateUpdateTask;
import io.crate.es.cluster.service.ClusterService;
import io.crate.es.common.Priority;
import io.crate.es.common.unit.TimeValue;

import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public class BlockMasterServiceOnMaster extends SingleNodeDisruption {

    AtomicReference<CountDownLatch> disruptionLatch = new AtomicReference<>();


    public BlockMasterServiceOnMaster(Random random) {
        super(random);
    }


    @Override
    public void startDisrupting() {
        disruptedNode = cluster.getMasterName();
        final String disruptionNodeCopy = disruptedNode;
        if (disruptionNodeCopy == null) {
            return;
        }
        ClusterService clusterService = cluster.getInstance(ClusterService.class, disruptionNodeCopy);
        if (clusterService == null) {
            return;
        }
        logger.info("blocking master service on node [{}]", disruptionNodeCopy);
        boolean success = disruptionLatch.compareAndSet(null, new CountDownLatch(1));
        assert success : "startDisrupting called without waiting on stopDisrupting to complete";
        final CountDownLatch started = new CountDownLatch(1);
        clusterService.getMasterService().submitStateUpdateTask("service_disruption_block", new ClusterStateUpdateTask() {
            @Override
            public Priority priority() {
                return Priority.IMMEDIATE;
            }

            @Override
            public ClusterState execute(ClusterState currentState) throws Exception {
                started.countDown();
                CountDownLatch latch = disruptionLatch.get();
                if (latch != null) {
                    try {
                        latch.await();
                    } catch (InterruptedException e) {
                        Throwables.rethrow(e);
                    }
                }
                return currentState;
            }

            @Override
            public void onFailure(String source, Exception e) {
                logger.error("unexpected error during disruption", e);
            }
        });
        try {
            started.await();
        } catch (InterruptedException e) {
        }
    }

    @Override
    public void stopDisrupting() {
        CountDownLatch latch = disruptionLatch.get();
        if (latch != null) {
            latch.countDown();
        }

    }

    @Override
    public void removeAndEnsureHealthy(InternalTestCluster cluster) {
        removeFromCluster(cluster);
    }

    @Override
    public TimeValue expectedTimeToHeal() {
        return TimeValue.timeValueMinutes(0);
    }
}
