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

package io.crate.es.repositories;

import io.crate.es.ElasticsearchException;
import io.crate.es.common.io.stream.StreamInput;
import io.crate.es.common.io.stream.StreamOutput;

import java.io.IOException;

/**
 * Generic repository exception
 */
public class RepositoryException extends ElasticsearchException {
    private final String repository;

    public RepositoryException(String repository, String msg) {
        this(repository, msg, null);
    }

    public RepositoryException(String repository, String msg, Throwable cause) {
        super("[" + (repository == null ? "_na" : repository) + "] " + msg, cause);
        this.repository = repository;
    }

    /**
     * Returns repository name
     *
     * @return repository name
     */
    public String repository() {
        return repository;
    }

    public RepositoryException(StreamInput in) throws IOException{
        super(in);
        repository = in.readOptionalString();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeOptionalString(repository);
    }
}
