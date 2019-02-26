/*
 * Licensed to CRATE Technology GmbH ("Crate") under one or more contributor
 * license agreements.  See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.  Crate licenses
 * this file to you under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.  You may
 * obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * However, if you have executed another commercial license agreement
 * with Crate these terms will supersede the license and you may use the
 * software solely pursuant to the terms of the relevant commercial agreement.
 */

package io.crate.action.sql.parser;

import io.crate.es.common.xcontent.XContentParser;

/**
 * This interface is for the {@link SQLRequestParser}
 * The {@link SQLRequestParser} receives a JSON structured body in the form of:
 * <p>
 * {
 * "stmt": "...",
 * "other_property": "..."
 * }
 * <p>
 * it then utilizes a {@link SQLParseElement} for each property in that structure.
 * <p>
 * E.g. for "stmt" property the {@link io.crate.action.sql.parser.SQLStmtParseElement} is used.
 */
interface SQLParseElement {

    void parse(XContentParser parser, SQLRequestParseContext context) throws Exception;
}
