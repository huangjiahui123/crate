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

package io.crate.es.index.cache.query;

import org.apache.lucene.search.QueryCachingPolicy;
import org.apache.lucene.search.Weight;
import io.crate.es.index.AbstractIndexComponent;
import io.crate.es.index.IndexSettings;
import io.crate.es.index.cache.query.QueryCache;

public class DisabledQueryCache extends AbstractIndexComponent implements QueryCache {

    public DisabledQueryCache(IndexSettings indexSettings) {
        super(indexSettings);
        logger.debug("Using no query cache");
    }

    @Override
    public void close() {
        // nothing to do here
    }

    @Override
    public Weight doCache(Weight weight, QueryCachingPolicy policy) {
        return weight;
    }

    @Override
    public void clear(String reason) {
        // nothing to do here
    }
}
