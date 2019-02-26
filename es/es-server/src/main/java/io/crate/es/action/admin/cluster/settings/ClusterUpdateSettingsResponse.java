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

package io.crate.es.action.admin.cluster.settings;

import io.crate.es.action.support.master.AcknowledgedResponse;
import io.crate.es.common.ParseField;
import io.crate.es.common.io.stream.StreamInput;
import io.crate.es.common.io.stream.StreamOutput;
import io.crate.es.common.settings.Settings;
import io.crate.es.common.xcontent.ConstructingObjectParser;
import io.crate.es.common.xcontent.XContentParser;

import java.io.IOException;
import java.util.Objects;

/**
 * A response for a cluster update settings action.
 */
public class ClusterUpdateSettingsResponse extends AcknowledgedResponse {

    private static final ParseField PERSISTENT = new ParseField("persistent");
    private static final ParseField TRANSIENT = new ParseField("transient");

    private static final ConstructingObjectParser<ClusterUpdateSettingsResponse, Void> PARSER = new ConstructingObjectParser<>(
            "cluster_update_settings_response", true, a -> new ClusterUpdateSettingsResponse((boolean) a[0]));
    static {
        declareAcknowledgedField(PARSER);
        PARSER.declareObject((r, p) -> r.persistentSettings = p, (p, c) -> Settings.fromXContent(p), PERSISTENT);
        PARSER.declareObject((r, t) -> r.transientSettings = t, (p, c) -> Settings.fromXContent(p), TRANSIENT);
    }

    Settings transientSettings;
    Settings persistentSettings;

    ClusterUpdateSettingsResponse() {
        this.persistentSettings = Settings.EMPTY;
        this.transientSettings = Settings.EMPTY;
    }

    ClusterUpdateSettingsResponse(boolean acknowledged) {
        super(acknowledged);
    }

    ClusterUpdateSettingsResponse(boolean acknowledged, Settings transientSettings, Settings persistentSettings) {
        super(acknowledged);
        this.persistentSettings = persistentSettings;
        this.transientSettings = transientSettings;
    }

    @Override
    public void readFrom(StreamInput in) throws IOException {
        super.readFrom(in);
        transientSettings = Settings.readSettingsFromStream(in);
        persistentSettings = Settings.readSettingsFromStream(in);
    }

    public Settings getTransientSettings() {
        return transientSettings;
    }

    public Settings getPersistentSettings() {
        return persistentSettings;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        Settings.writeSettingsToStream(transientSettings, out);
        Settings.writeSettingsToStream(persistentSettings, out);
    }

    public static ClusterUpdateSettingsResponse fromXContent(XContentParser parser) {
        return PARSER.apply(parser, null);
    }

    @Override
    public boolean equals(Object o) {
        if (super.equals(o)) {
            ClusterUpdateSettingsResponse that = (ClusterUpdateSettingsResponse) o;
            return Objects.equals(transientSettings, that.transientSettings) &&
                    Objects.equals(persistentSettings, that.persistentSettings);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), transientSettings, persistentSettings);
    }
}
