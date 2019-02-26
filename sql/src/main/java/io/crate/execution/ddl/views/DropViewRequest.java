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

package io.crate.execution.ddl.views;

import io.crate.metadata.RelationName;
import io.crate.es.action.ActionRequestValidationException;
import io.crate.es.action.support.master.MasterNodeRequest;
import io.crate.es.cluster.ack.AckedRequest;
import io.crate.es.common.io.stream.StreamInput;
import io.crate.es.common.io.stream.StreamOutput;
import io.crate.es.common.unit.TimeValue;

import java.io.IOException;
import java.util.List;

import static io.crate.es.action.support.master.AcknowledgedRequest.DEFAULT_ACK_TIMEOUT;

public class DropViewRequest extends MasterNodeRequest<DropViewRequest> implements AckedRequest {

    private List<RelationName> names;
    private boolean ifExists;

    public DropViewRequest(List<RelationName> names, boolean ifExists) {
        this.names = names;
        this.ifExists = ifExists;
    }

    DropViewRequest() {
    }

    @Override
    public ActionRequestValidationException validate() {
        return null;
    }

    @Override
    public TimeValue ackTimeout() {
        return DEFAULT_ACK_TIMEOUT;
    }

    public List<RelationName> names() {
        return names;
    }

    public boolean ifExists() {
        return ifExists;
    }

    @Override
    public void readFrom(StreamInput in) throws IOException {
        super.readFrom(in);
        names = in.readList(RelationName::new);
        ifExists = in.readBoolean();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeList(names);
        out.writeBoolean(ifExists);
    }
}
