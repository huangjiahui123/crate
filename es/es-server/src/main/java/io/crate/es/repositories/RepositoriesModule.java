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

import io.crate.es.common.inject.AbstractModule;
import io.crate.es.common.inject.multibindings.MapBinder;
import io.crate.es.common.xcontent.NamedXContentRegistry;
import io.crate.es.env.Environment;
import io.crate.es.plugins.RepositoryPlugin;
import io.crate.es.repositories.fs.FsRepository;
import io.crate.es.snapshots.RestoreService;
import io.crate.es.snapshots.SnapshotShardsService;
import io.crate.es.snapshots.SnapshotsService;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sets up classes for Snapshot/Restore.
 */
public class RepositoriesModule extends AbstractModule {

    private final Map<String, Repository.Factory> repositoryTypes;

    public RepositoriesModule(Environment env, List<RepositoryPlugin> repoPlugins, NamedXContentRegistry namedXContentRegistry) {
        Map<String, Repository.Factory> factories = new HashMap<>();
        factories.put(FsRepository.TYPE, (metadata) -> new FsRepository(metadata, env, namedXContentRegistry));

        for (RepositoryPlugin repoPlugin : repoPlugins) {
            Map<String, Repository.Factory> newRepoTypes = repoPlugin.getRepositories(env, namedXContentRegistry);
            for (Map.Entry<String, Repository.Factory> entry : newRepoTypes.entrySet()) {
                if (factories.put(entry.getKey(), entry.getValue()) != null) {
                    throw new IllegalArgumentException("Repository type [" + entry.getKey() + "] is already registered");
                }
            }
        }
        repositoryTypes = Collections.unmodifiableMap(factories);
    }

    @Override
    protected void configure() {
        bind(RepositoriesService.class).asEagerSingleton();
        bind(SnapshotsService.class).asEagerSingleton();
        bind(SnapshotShardsService.class).asEagerSingleton();
        bind(RestoreService.class).asEagerSingleton();
        MapBinder<String, Repository.Factory> typesBinder = MapBinder.newMapBinder(binder(), String.class, Repository.Factory.class);
        repositoryTypes.forEach((k, v) -> typesBinder.addBinding(k).toInstance(v));
    }
}
