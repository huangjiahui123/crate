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

import io.crate.es.ElasticsearchGenerationException;
import io.crate.es.action.ActionRequestValidationException;
import io.crate.es.action.support.master.AcknowledgedRequest;
import io.crate.es.common.ParseField;
import io.crate.es.common.Strings;
import io.crate.es.common.io.stream.StreamInput;
import io.crate.es.common.io.stream.StreamOutput;
import io.crate.es.common.settings.Settings;
import io.crate.es.common.xcontent.ObjectParser;
import io.crate.es.common.xcontent.ToXContentObject;
import io.crate.es.common.xcontent.XContentBuilder;
import io.crate.es.common.xcontent.XContentFactory;
import io.crate.es.common.xcontent.XContentParser;
import io.crate.es.common.xcontent.XContentType;

import java.io.IOException;
import java.util.Map;

import static io.crate.es.action.ValidateActions.addValidationError;
import static io.crate.es.common.settings.Settings.readSettingsFromStream;
import static io.crate.es.common.settings.Settings.writeSettingsToStream;
import static io.crate.es.common.settings.Settings.Builder.EMPTY_SETTINGS;

/**
 * Request for an update cluster settings action
 */
public class ClusterUpdateSettingsRequest extends AcknowledgedRequest<ClusterUpdateSettingsRequest> implements ToXContentObject {

    private static final ParseField PERSISTENT = new ParseField("persistent");
    private static final ParseField TRANSIENT = new ParseField("transient");

    private static final ObjectParser<ClusterUpdateSettingsRequest, Void> PARSER = new ObjectParser<>("cluster_update_settings_request",
            false, ClusterUpdateSettingsRequest::new);

    static {
        PARSER.declareObject((r, p) -> r.persistentSettings = p, (p, c) -> Settings.fromXContent(p), PERSISTENT);
        PARSER.declareObject((r, t) -> r.transientSettings = t, (p, c) -> Settings.fromXContent(p), TRANSIENT);
    }

    private Settings transientSettings = EMPTY_SETTINGS;
    private Settings persistentSettings = EMPTY_SETTINGS;

    public ClusterUpdateSettingsRequest() {
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException validationException = null;
        if (transientSettings.isEmpty() && persistentSettings.isEmpty()) {
            validationException = addValidationError("no settings to update", validationException);
        }
        return validationException;
    }

    public Settings transientSettings() {
        return transientSettings;
    }

    public Settings persistentSettings() {
        return persistentSettings;
    }

    /**
     * Sets the transient settings to be updated. They will not survive a full cluster restart
     */
    public ClusterUpdateSettingsRequest transientSettings(Settings settings) {
        this.transientSettings = settings;
        return this;
    }

    /**
     * Sets the transient settings to be updated. They will not survive a full cluster restart
     */
    public ClusterUpdateSettingsRequest transientSettings(Settings.Builder settings) {
        this.transientSettings = settings.build();
        return this;
    }

    /**
     * Sets the source containing the transient settings to be updated. They will not survive a full cluster restart
     */
    public ClusterUpdateSettingsRequest transientSettings(String source, XContentType xContentType) {
        this.transientSettings = Settings.builder().loadFromSource(source, xContentType).build();
        return this;
    }

    /**
     * Sets the transient settings to be updated. They will not survive a full cluster restart
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public ClusterUpdateSettingsRequest transientSettings(Map source) {
        try {
            XContentBuilder builder = XContentFactory.contentBuilder(XContentType.JSON);
            builder.map(source);
            transientSettings(Strings.toString(builder), builder.contentType());
        } catch (IOException e) {
            throw new ElasticsearchGenerationException("Failed to generate [" + source + "]", e);
        }
        return this;
    }

    /**
     * Sets the persistent settings to be updated. They will get applied cross restarts
     */
    public ClusterUpdateSettingsRequest persistentSettings(Settings settings) {
        this.persistentSettings = settings;
        return this;
    }

    /**
     * Sets the persistent settings to be updated. They will get applied cross restarts
     */
    public ClusterUpdateSettingsRequest persistentSettings(Settings.Builder settings) {
        this.persistentSettings = settings.build();
        return this;
    }

    /**
     * Sets the source containing the persistent settings to be updated. They will get applied cross restarts
     */
    public ClusterUpdateSettingsRequest persistentSettings(String source, XContentType xContentType) {
        this.persistentSettings = Settings.builder().loadFromSource(source, xContentType).build();
        return this;
    }

    /**
     * Sets the persistent settings to be updated. They will get applied cross restarts
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public ClusterUpdateSettingsRequest persistentSettings(Map source) {
        try {
            XContentBuilder builder = XContentFactory.contentBuilder(XContentType.JSON);
            builder.map(source);
            persistentSettings(Strings.toString(builder), builder.contentType());
        } catch (IOException e) {
            throw new ElasticsearchGenerationException("Failed to generate [" + source + "]", e);
        }
        return this;
    }

    @Override
    public void readFrom(StreamInput in) throws IOException {
        super.readFrom(in);
        transientSettings = readSettingsFromStream(in);
        persistentSettings = readSettingsFromStream(in);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        writeSettingsToStream(transientSettings, out);
        writeSettingsToStream(persistentSettings, out);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.startObject(PERSISTENT.getPreferredName());
        persistentSettings.toXContent(builder, params);
        builder.endObject();
        builder.startObject(TRANSIENT.getPreferredName());
        transientSettings.toXContent(builder, params);
        builder.endObject();
        builder.endObject();
        return builder;
    }

    public static ClusterUpdateSettingsRequest fromXContent(XContentParser parser) throws IOException {
        return PARSER.apply(parser, null);
    }
}
