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

package io.crate.es.node;

import io.crate.es.common.settings.Settings;
import io.crate.es.common.util.BigArrays;
import io.crate.es.common.util.MockBigArrays;
import io.crate.es.env.Environment;
import io.crate.es.plugins.Plugin;
import io.crate.es.test.ESTestCase;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MockNodeTests extends ESTestCase {
    /**
     * Test that we add the appropriate mock services when their plugins are added. This is a very heavy test for a testing component but
     * we've broken it in the past so it is important.
     */
    public void testComponentsMockedByMarkerPlugins() throws IOException {
        Settings settings = Settings.builder() // All these are required or MockNode will fail to build.
                .put(Environment.PATH_HOME_SETTING.getKey(), createTempDir())
                .put("transport.type", getTestTransportType())
                .build();
        List<Class<? extends Plugin>> plugins = new ArrayList<>();
        plugins.add(getTestTransportPlugin());
        boolean useMockBigArrays = randomBoolean();
        if (useMockBigArrays) {
            plugins.add(NodeMocksPlugin.class);
        }
        try (MockNode node = new MockNode(settings, plugins)) {
            BigArrays bigArrays = node.injector().getInstance(BigArrays.class);
            if (useMockBigArrays) {
                assertSame(bigArrays.getClass(), MockBigArrays.class);
            } else {
                assertSame(bigArrays.getClass(), BigArrays.class);
            }
        }
    }
}
