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

package io.crate.es.cluster.service;

import io.crate.es.common.Priority;
import io.crate.es.common.io.stream.StreamInput;
import io.crate.es.common.io.stream.StreamOutput;
import io.crate.es.common.io.stream.Streamable;
import io.crate.es.common.text.Text;
import io.crate.es.common.unit.TimeValue;

import java.io.IOException;

public class PendingClusterTask implements Streamable {

    private long insertOrder;
    private Priority priority;
    private Text source;
    private long timeInQueue;
    private boolean executing;

    public PendingClusterTask() {
    }

    public PendingClusterTask(long insertOrder, Priority priority, Text source, long timeInQueue, boolean executing) {
        assert timeInQueue >= 0 : "got a negative timeInQueue [" + timeInQueue + "]";
        assert insertOrder >= 0 : "got a negative insertOrder [" + insertOrder + "]";
        this.insertOrder = insertOrder;
        this.priority = priority;
        this.source = source;
        this.timeInQueue = timeInQueue;
        this.executing = executing;
    }

    public long getInsertOrder() {
        return insertOrder;
    }

    public Priority getPriority() {
        return priority;
    }

    public Text getSource() {
        return source;
    }

    public long getTimeInQueueInMillis() {
        return timeInQueue;
    }

    public TimeValue getTimeInQueue() {
        return new TimeValue(getTimeInQueueInMillis());
    }

    public boolean isExecuting() {
        return executing;
    }

    @Override
    public void readFrom(StreamInput in) throws IOException {
        insertOrder = in.readVLong();
        priority = Priority.readFrom(in);
        source = in.readText();
        timeInQueue = in.readLong();
        executing = in.readBoolean();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeVLong(insertOrder);
        Priority.writeTo(priority, out);
        out.writeText(source);
        out.writeLong(timeInQueue);
        out.writeBoolean(executing);
    }
}
