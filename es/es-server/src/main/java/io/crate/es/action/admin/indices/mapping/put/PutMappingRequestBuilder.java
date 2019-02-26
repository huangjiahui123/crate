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

package io.crate.es.action.admin.indices.mapping.put;

import io.crate.es.action.support.IndicesOptions;
import io.crate.es.action.support.master.AcknowledgedRequestBuilder;
import io.crate.es.action.support.master.AcknowledgedResponse;
import io.crate.es.client.ElasticsearchClient;
import io.crate.es.common.xcontent.XContentBuilder;
import io.crate.es.common.xcontent.XContentType;
import io.crate.es.index.Index;

import java.util.Map;

/**
 * Builder for a put mapping request
 */
public class PutMappingRequestBuilder
    extends AcknowledgedRequestBuilder<PutMappingRequest, AcknowledgedResponse, PutMappingRequestBuilder> {

    public PutMappingRequestBuilder(ElasticsearchClient client, PutMappingAction action) {
        super(client, action, new PutMappingRequest());
    }

    public PutMappingRequestBuilder setIndices(String... indices) {
        request.indices(indices);
        return this;
    }

    public PutMappingRequestBuilder setConcreteIndex(Index index) {
        request.setConcreteIndex(index);
        return this;
    }

    /**
     * Specifies what type of requested indices to ignore and wildcard indices expressions.
     * <p>
     * For example indices that don't exist.
     */
    public PutMappingRequestBuilder setIndicesOptions(IndicesOptions options) {
        request.indicesOptions(options);
        return this;
    }

    /**
     * The type of the mappings.
     */
    public PutMappingRequestBuilder setType(String type) {
        request.type(type);
        return this;
    }

    /**
     * The mapping source definition.
     */
    public PutMappingRequestBuilder setSource(XContentBuilder mappingBuilder) {
        request.source(mappingBuilder);
        return this;
    }

    /**
     * The mapping source definition.
     */
    public PutMappingRequestBuilder setSource(Map mappingSource) {
        request.source(mappingSource);
        return this;
    }

    /**
     * The mapping source definition.
     */
    public PutMappingRequestBuilder setSource(String mappingSource, XContentType xContentType) {
        request.source(mappingSource, xContentType);
        return this;
    }

    /**
     * A specialized simplified mapping source method, takes the form of simple properties definition:
     * ("field1", "type=string,store=true").
     */
    public PutMappingRequestBuilder setSource(Object... source) {
        request.source(source);
        return this;
    }

    /** True if all fields that span multiple types should be updated, false otherwise
     * @deprecated useless with 6.x indices which may only have one type */
    @Deprecated
    public PutMappingRequestBuilder setUpdateAllTypes(boolean updateAllTypes) {
        request.updateAllTypes(updateAllTypes);
        return this;
    }

}
