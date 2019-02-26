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

package io.crate.es.common.component;

import io.crate.es.common.logging.DeprecationLogger;
import io.crate.es.common.settings.Settings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import io.crate.es.node.Node;

public abstract class AbstractComponent {

    protected final Logger logger;
    protected final DeprecationLogger deprecationLogger;
    protected final Settings settings;

    public AbstractComponent(Settings settings) {
        this.logger = LogManager.getLogger(getClass());
        this.deprecationLogger = new DeprecationLogger(logger);
        this.settings = settings;
    }

    /**
     * Returns the nodes name from the settings or the empty string if not set.
     */
    public final String nodeName() {
        return Node.NODE_NAME_SETTING.get(settings);
    }
}
