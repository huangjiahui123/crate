/*
 * Licensed to CRATE Technology GmbH ("Crate") under one or more contributor
 * license agreements.  See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.  Crate licenses
 * this file to you under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.  You may
 * obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * However, if you have executed another commercial license agreement
 * with Crate these terms will supersede the license and you may use the
 * software solely pursuant to the terms of the relevant commercial agreement.
 */

package io.crate.execution.jobs.kill;

import com.google.common.collect.ImmutableList;
import io.crate.execution.jobs.TasksService;
import io.crate.test.integration.CrateDummyClusterServiceUnitTest;
import io.crate.es.Version;
import io.crate.es.common.settings.Settings;
import io.crate.es.test.transport.MockTransportService;
import org.junit.Test;
import org.mockito.Answers;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class TransportKillJobsNodeActionTest extends CrateDummyClusterServiceUnitTest {

    @Test
    public void testKillIsCalledOnJobContextService() throws Exception {
        TasksService tasksService = mock(TasksService.class, Answers.RETURNS_MOCKS.get());
        TransportKillJobsNodeAction transportKillJobsNodeAction = new TransportKillJobsNodeAction(
            Settings.EMPTY,
            tasksService,
            clusterService,
            MockTransportService.createNewService(
                Settings.EMPTY, Version.CURRENT, THREAD_POOL, clusterService.getClusterSettings())
        );

        List<UUID> toKill = ImmutableList.of(UUID.randomUUID(), UUID.randomUUID());

        transportKillJobsNodeAction.nodeOperation(new KillJobsRequest(toKill)).get(5, TimeUnit.SECONDS);
        verify(tasksService, times(1)).killJobs(toKill);
    }
}
