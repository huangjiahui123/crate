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

package io.crate.es.index.fielddata;

import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.util.NumericUtils;

import java.io.IOException;

/**
 * {@link NumericDoubleValues} instance that wraps a {@link NumericDocValues}
 * and converts the doubles to sortable long bits using
 * {@link NumericUtils#sortableLongToDouble(long)}.
 */
final class SortableLongBitsToNumericDoubleValues extends NumericDoubleValues {

    private final NumericDocValues values;

    SortableLongBitsToNumericDoubleValues(NumericDocValues values) {
        this.values = values;
    }

    @Override
    public double doubleValue() throws IOException {
        return NumericUtils.sortableLongToDouble(values.longValue());
    }

    @Override
    public boolean advanceExact(int doc) throws IOException {
        return values.advanceExact(doc);
    }

    /** Return the wrapped values. */
    public NumericDocValues getLongValues() {
        return values;
    }

}
