/*
 * Licensed to CRATE Technology GmbH ("Crate") under one or more contributor
 * license agreements.  See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.  Crate licenses
 * this file to you under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.  You may
 * obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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

package io.crate.breaker;

import io.crate.es.common.breaker.CircuitBreaker;

import javax.annotation.Nullable;

/**
 * Component to estimate the memory-requirement for a value.
 * This is mainly used for circuit-breaking.
 * See
 *  - {@link RamAccountingContext} and
 *  - {@link CircuitBreaker}
 *
 * This is best effort. the Actual size varies from JVM to JVM (32-bit vs 64-bit, compressed oop, etc.).
 * To get accurate values we'd have to integrate something like http://openjdk.java.net/projects/code-tools/jol/
 * but it's GPL licensed.
 *
 */
public abstract class SizeEstimator<T> {

    public abstract long estimateSize(@Nullable T value);

    public long estimateSizeDelta(@Nullable T oldValue, @Nullable T newValue) {
        return estimateSize(newValue) - estimateSize(oldValue);
    }
}
