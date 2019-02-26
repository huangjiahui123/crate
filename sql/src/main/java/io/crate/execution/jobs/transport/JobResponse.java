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

package io.crate.execution.jobs.transport;

import io.crate.Streamer;
import io.crate.execution.engine.distribution.StreamBucket;
import io.crate.es.common.io.stream.StreamInput;
import io.crate.es.common.io.stream.StreamOutput;
import io.crate.es.transport.TransportResponse;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class JobResponse extends TransportResponse {

    private List<StreamBucket> directResponse = Collections.emptyList();

    public JobResponse() {
    }

    public JobResponse(@Nonnull List<StreamBucket> buckets) {
        this.directResponse = buckets;
    }

    public List<StreamBucket> getDirectResponses(Streamer<?>[] streamers) {
        for (StreamBucket bucket : directResponse) {
            bucket.streamers(streamers);
        }
        return directResponse;
    }

    public boolean hasDirectResponses() {
        return !directResponse.isEmpty();
    }

    @Override
    public void readFrom(StreamInput in) throws IOException {
        super.readFrom(in);
        int size = in.readVInt();
        directResponse = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            StreamBucket bucket = new StreamBucket(in);
            directResponse.add(bucket);
        }
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeVInt(directResponse.size());
        for (StreamBucket bucket : directResponse) {
            bucket.writeTo(out);
        }
    }
}
