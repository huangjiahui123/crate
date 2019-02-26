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

package io.crate.es.transport;

import io.crate.es.ElasticsearchWrapperException;
import io.crate.es.common.io.stream.StreamInput;
import io.crate.es.common.transport.TransportAddress;

import java.io.IOException;

/**
 * A remote exception for an action. A wrapper exception around the actual remote cause and does not fill the
 * stack trace.
 *
 *
 */
public class RemoteTransportException extends ActionTransportException implements ElasticsearchWrapperException {

    public RemoteTransportException(String msg, Throwable cause) {
        super(msg, null, null, cause);
    }

    public RemoteTransportException(String name, TransportAddress address, String action, Throwable cause) {
        super(name, address, action, cause);
    }

    public RemoteTransportException(StreamInput in) throws IOException {
        super(in);
    }

    @Override
    public Throwable fillInStackTrace() {
        // no need for stack trace here, we always have cause
        return null;
    }
}
