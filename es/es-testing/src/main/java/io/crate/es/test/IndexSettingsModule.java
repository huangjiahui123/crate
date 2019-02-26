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

import io.crate.es.Version;
import io.crate.es.cluster.metadata.IndexMetaData;
import io.crate.es.common.inject.AbstractModule;
import io.crate.es.common.settings.IndexScopedSettings;
import io.crate.es.common.settings.Setting;
import io.crate.es.common.settings.Settings;
import io.crate.es.index.Index;
import io.crate.es.index.IndexSettings;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class IndexSettingsModule extends AbstractModule {

    private final Index index;
    private final Settings settings;

    public IndexSettingsModule(Index index, Settings settings) {
        this.settings = settings;
        this.index = index;

    }

    @Override
    protected void configure() {
        bind(IndexSettings.class).toInstance(newIndexSettings(index, settings));
    }

    public static IndexSettings newIndexSettings(String index, Settings settings, Setting<?>... setting) {
        return newIndexSettings(new Index(index, settings.get(IndexMetaData.SETTING_INDEX_UUID, IndexMetaData.INDEX_UUID_NA_VALUE)),
                settings, setting);
    }

    public static IndexSettings newIndexSettings(Index index, Settings settings, Setting<?>... setting) {
        return newIndexSettings(index, settings, Settings.EMPTY, setting);
    }

    public static IndexSettings newIndexSettings(Index index, Settings indexSetting, Settings nodeSettings, Setting<?>... setting) {
        Settings build = Settings.builder().put(IndexMetaData.SETTING_VERSION_CREATED, Version.CURRENT)
            .put(IndexMetaData.SETTING_NUMBER_OF_REPLICAS, 1)
            .put(IndexMetaData.SETTING_NUMBER_OF_SHARDS, 1)
            .put(indexSetting)
            .build();
        IndexMetaData metaData = IndexMetaData.builder(index.getName()).settings(build).build();
        Set<Setting<?>> settingSet = new HashSet<>(IndexScopedSettings.BUILT_IN_INDEX_SETTINGS);
        if (setting.length > 0) {
            settingSet.addAll(Arrays.asList(setting));
        }
        return new IndexSettings(metaData, nodeSettings, new IndexScopedSettings(Settings.EMPTY, settingSet));
    }

    public static IndexSettings newIndexSettings(Index index, Settings settings, IndexScopedSettings indexScopedSettings) {
        Settings build = Settings.builder().put(IndexMetaData.SETTING_VERSION_CREATED, Version.CURRENT)
                .put(IndexMetaData.SETTING_NUMBER_OF_REPLICAS, 1)
                .put(IndexMetaData.SETTING_NUMBER_OF_SHARDS, 1)
                .put(settings)
                .build();
        IndexMetaData metaData = IndexMetaData.builder(index.getName()).settings(build).build();
        return new IndexSettings(metaData, Settings.EMPTY, indexScopedSettings);
    }

    public static IndexSettings newIndexSettings(final IndexMetaData indexMetaData, Setting<?>... setting) {
        Set<Setting<?>> settingSet = new HashSet<>(IndexScopedSettings.BUILT_IN_INDEX_SETTINGS);
        if (setting.length > 0) {
            settingSet.addAll(Arrays.asList(setting));
        }
        return new IndexSettings(indexMetaData, Settings.EMPTY, new IndexScopedSettings(Settings.EMPTY, settingSet));
    }
}
