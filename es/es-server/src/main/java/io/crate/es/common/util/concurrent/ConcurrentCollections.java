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

package io.crate.es.common.util.concurrent;

import java.util.Collections;
import java.util.Deque;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedTransferQueue;


public abstract class ConcurrentCollections {

    static final int aggressiveConcurrencyLevel;

    static {
        aggressiveConcurrencyLevel = Math.max(Runtime.getRuntime().availableProcessors() * 2, 16);
    }

    /**
     * Creates a new CHM with an aggressive concurrency level, aimed at high concurrent update rate long living maps.
     */
    public static <K, V> ConcurrentMap<K, V> newConcurrentMapWithAggressiveConcurrency() {
        return newConcurrentMapWithAggressiveConcurrency(16);
    }

    /**
     * Creates a new CHM with an aggressive concurrency level, aimed at high concurrent update rate long living maps.
     */
    public static <K, V> ConcurrentMap<K, V> newConcurrentMapWithAggressiveConcurrency(int initalCapacity) {
        return new ConcurrentHashMap<>(initalCapacity, 0.75f, aggressiveConcurrencyLevel);
    }

    public static <K, V> ConcurrentMap<K, V> newConcurrentMap() {
        return new ConcurrentHashMap<>();
    }

    public static <V> Set<V> newConcurrentSet() {
        return Collections.newSetFromMap(ConcurrentCollections.newConcurrentMap());
    }

    public static <T> Queue<T> newQueue() {
        return new ConcurrentLinkedQueue<>();
    }

    public static <T> Deque<T> newDeque() {
        return new ConcurrentLinkedDeque<>();
    }

    public static <T> BlockingQueue<T> newBlockingQueue() {
        return new LinkedTransferQueue<>();
    }

    private ConcurrentCollections() {

    }
}
