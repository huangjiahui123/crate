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

package io.crate.expression.reference.sys.shard;

import io.crate.metadata.IndexParts;
import io.crate.es.action.admin.cluster.allocation.ClusterAllocationExplanation;
import io.crate.es.cluster.ClusterInfo;
import io.crate.es.cluster.ClusterInfoService;
import io.crate.es.cluster.ClusterState;
import io.crate.es.cluster.routing.RoutingNodes;
import io.crate.es.cluster.routing.ShardRouting;
import io.crate.es.cluster.routing.allocation.AllocateUnassignedDecision;
import io.crate.es.cluster.routing.allocation.MoveDecision;
import io.crate.es.cluster.routing.allocation.RoutingAllocation;
import io.crate.es.cluster.routing.allocation.ShardAllocationDecision;
import io.crate.es.cluster.routing.allocation.allocator.ShardsAllocator;
import io.crate.es.cluster.routing.allocation.decider.AllocationDeciders;
import io.crate.es.cluster.service.ClusterService;
import io.crate.es.common.inject.Inject;
import io.crate.es.common.inject.Singleton;
import io.crate.es.gateway.GatewayAllocator;

import java.util.Iterator;

@Singleton
public class SysAllocations implements Iterable<SysAllocation> {

    private final ClusterService clusterService;
    private final ClusterInfoService clusterInfoService;
    private final AllocationDeciders allocationDeciders;
    private final ShardsAllocator shardAllocator;
    private final GatewayAllocator gatewayAllocator;

    @Inject
    public SysAllocations(ClusterService clusterService,
                          ClusterInfoService clusterInfoService,
                          AllocationDeciders allocationDeciders,
                          ShardsAllocator shardAllocator,
                          GatewayAllocator gatewayAllocator) {
        this.clusterService = clusterService;
        this.clusterInfoService = clusterInfoService;
        this.allocationDeciders = allocationDeciders;
        this.shardAllocator = shardAllocator;
        this.gatewayAllocator = gatewayAllocator;
    }

    @Override
    public Iterator<SysAllocation> iterator() {
        final ClusterState state = clusterService.state();
        final RoutingNodes routingNodes = state.getRoutingNodes();
        final ClusterInfo clusterInfo = clusterInfoService.getClusterInfo();

        final RoutingAllocation allocation = new RoutingAllocation(
            allocationDeciders, routingNodes, state, clusterInfo, System.nanoTime());
        return allocation.routingTable().allShards().stream()
            .filter(shardRouting -> !IndexParts.isDangling(shardRouting.getIndexName()))
            .map(shardRouting -> new SysAllocation(
                explainShard(shardRouting, allocation, gatewayAllocator, shardAllocator))).iterator();
    }

    private static ClusterAllocationExplanation explainShard(ShardRouting shardRouting,
                                                             RoutingAllocation allocation,
                                                             GatewayAllocator gatewayAllocator,
                                                             ShardsAllocator shardAllocator) {
        allocation.setDebugMode(RoutingAllocation.DebugMode.EXCLUDE_YES_DECISIONS);

        ShardAllocationDecision shardDecision;
        if (shardRouting.initializing() || shardRouting.relocating()) {
            shardDecision = ShardAllocationDecision.NOT_TAKEN;
        } else {
            AllocateUnassignedDecision allocateDecision = shardRouting.unassigned() ?
                gatewayAllocator.decideUnassignedShardAllocation(shardRouting, allocation) : AllocateUnassignedDecision.NOT_TAKEN;
            if (allocateDecision.isDecisionTaken() == false) {
                shardDecision = shardAllocator.decideShardAllocation(shardRouting, allocation);
            } else {
                shardDecision = new ShardAllocationDecision(allocateDecision, MoveDecision.NOT_TAKEN);
            }
        }
        return new ClusterAllocationExplanation(
            shardRouting,
            shardRouting.currentNodeId() != null ? allocation.nodes().get(shardRouting.currentNodeId()) : null,
            shardRouting.relocatingNodeId() != null ? allocation.nodes().get(shardRouting.relocatingNodeId()) : null,
            null,
            shardDecision
        );
    }
}
