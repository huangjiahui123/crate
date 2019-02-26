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

package io.crate.es.test;

import io.crate.es.common.xcontent.XContent;
import io.crate.es.common.Strings;
import io.crate.es.common.io.stream.Writeable;
import io.crate.es.common.xcontent.ToXContent;
import io.crate.es.common.xcontent.XContentBuilder;
import io.crate.es.common.xcontent.XContentParser;

import java.io.IOException;
import java.util.function.Predicate;

public abstract class AbstractSerializingTestCase<T extends ToXContent & Writeable> extends AbstractWireSerializingTestCase<T> {

    /**
     * Generic test that creates new instance from the test instance and checks
     * both for equality and asserts equality on the two instances.
     */
    public final void testFromXContent() throws IOException {
        AbstractXContentTestCase.testFromXContent(
                NUMBER_OF_TEST_RUNS,
                this::createTestInstance,
                supportsUnknownFields(),
                getShuffleFieldsExceptions(),
                getRandomFieldsExcludeFilter(),
                this::createParser,
                this::doParseInstance,
                this::assertEqualInstances,
                assertToXContentEquivalence(),
                getToXContentParams());
    }

    /**
     * Parses to a new instance using the provided {@link XContentParser}
     */
    protected abstract T doParseInstance(XContentParser parser) throws IOException;

    /**
     * Indicates whether the parser supports unknown fields or not. In case it does, such behaviour will be tested by
     * inserting random fields before parsing and checking that they don't make parsing fail.
     */
    protected boolean supportsUnknownFields() {
        return false;
    }

    /**
     * Returns a predicate that given the field name indicates whether the field has to be excluded from random fields insertion or not
     */
    protected Predicate<String> getRandomFieldsExcludeFilter() {
        return field -> false;
    }

    /**
     * Fields that have to be ignored when shuffling as part of testFromXContent
     */
    protected String[] getShuffleFieldsExceptions() {
        return Strings.EMPTY_ARRAY;
    }

    /**
     * Params that have to be provided when calling calling {@link ToXContent#toXContent(XContentBuilder, ToXContent.Params)}
     */
    protected ToXContent.Params getToXContentParams() {
        return ToXContent.EMPTY_PARAMS;
    }

    /**
     * Whether or not to assert equivalence of the {@link XContent} of the test instance and the instance
     * parsed from the {@link XContent} of the test instance.
     *
     * @return true if equivalence should be asserted, otherwise false
     */
    protected boolean assertToXContentEquivalence() {
        return true;
    }

}
