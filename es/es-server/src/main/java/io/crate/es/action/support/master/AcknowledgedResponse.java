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
package io.crate.es.action.support.master;

import io.crate.es.action.ActionResponse;
import io.crate.es.common.ParseField;
import io.crate.es.common.io.stream.StreamInput;
import io.crate.es.common.io.stream.StreamOutput;
import io.crate.es.common.xcontent.ConstructingObjectParser;
import io.crate.es.common.xcontent.ObjectParser;

import java.io.IOException;
import java.util.Objects;

import static io.crate.es.common.xcontent.ConstructingObjectParser.constructorArg;

/**
 * A response that indicates that a request has been acknowledged
 */
public class AcknowledgedResponse extends ActionResponse {

    private static final ParseField ACKNOWLEDGED = new ParseField("acknowledged");

    protected static <T extends AcknowledgedResponse> void declareAcknowledgedField(ConstructingObjectParser<T, Void> objectParser) {
        objectParser.declareField(constructorArg(), (parser, context) -> parser.booleanValue(), ACKNOWLEDGED,
            ObjectParser.ValueType.BOOLEAN);
    }

    protected boolean acknowledged;

    public AcknowledgedResponse() {
    }

    public AcknowledgedResponse(boolean acknowledged) {
        this.acknowledged = acknowledged;
    }

    /**
     * Returns whether the response is acknowledged or not
     * @return true if the response is acknowledged, false otherwise
     */
    public final boolean isAcknowledged() {
        return acknowledged;
    }

    @Override
    public void readFrom(StreamInput in) throws IOException {
        super.readFrom(in);
        acknowledged = in.readBoolean();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeBoolean(acknowledged);
    }

    /**
     * A generic parser that simply parses the acknowledged flag
     */
    private static final ConstructingObjectParser<Boolean, Void> ACKNOWLEDGED_FLAG_PARSER = new ConstructingObjectParser<>(
            "acknowledged_flag", true, args -> (Boolean) args[0]);

    static {
        ACKNOWLEDGED_FLAG_PARSER.declareField(constructorArg(), (parser, context) -> parser.booleanValue(), ACKNOWLEDGED,
                ObjectParser.ValueType.BOOLEAN);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AcknowledgedResponse that = (AcknowledgedResponse) o;
        return isAcknowledged() == that.isAcknowledged();
    }

    @Override
    public int hashCode() {
        return Objects.hash(isAcknowledged());
    }
}
