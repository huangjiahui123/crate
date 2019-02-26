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

import io.crate.es.common.io.stream.StreamInput;
import io.crate.es.common.io.stream.Writeable;

import java.io.IOException;

public interface TransportResponseHandler<T extends TransportResponse> extends Writeable.Reader<T> {

    /**
     * @deprecated Implement {@link #read(StreamInput)} instead.
     */
    @Deprecated
    default T newInstance() {
        throw new UnsupportedOperationException();
    }

    /**
     * deserializes a new instance of the return type from the stream.
     * called by the infra when de-serializing the response.
     *
     * @return the deserialized response.
     */
    @SuppressWarnings("deprecation")
    @Override
    default T read(StreamInput in) throws IOException {
        T instance = newInstance();
        instance.readFrom(in);
        return instance;
    }

    void handleResponse(T response);

    void handleException(TransportException exp);

    String executor();
}
