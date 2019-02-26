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

package io.crate.es.common.inject;

import io.crate.es.common.inject.internal.Errors;
import io.crate.es.common.inject.spi.PrivateElements;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles {@link Binder#newPrivateBinder()} elements.
 *
 * @author jessewilson@google.com (Jesse Wilson)
 */
class PrivateElementProcessor extends AbstractProcessor {

    private final Stage stage;
    private final List<InjectorShell.Builder> injectorShellBuilders = new ArrayList<>();

    PrivateElementProcessor(Errors errors, Stage stage) {
        super(errors);
        this.stage = stage;
    }

    @Override
    public Boolean visit(PrivateElements privateElements) {
        InjectorShell.Builder builder = new InjectorShell.Builder()
                .parent(injector)
                .stage(stage)
                .privateElements(privateElements);
        injectorShellBuilders.add(builder);
        return true;
    }

    public List<InjectorShell.Builder> getInjectorShellBuilders() {
        return injectorShellBuilders;
    }
}
