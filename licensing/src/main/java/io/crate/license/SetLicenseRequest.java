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

package io.crate.license;

import io.crate.es.action.ActionRequestValidationException;
import io.crate.es.action.ValidateActions;
import io.crate.es.action.support.master.MasterNodeRequest;
import io.crate.es.common.io.stream.StreamInput;
import io.crate.es.common.io.stream.StreamOutput;

import java.io.IOException;

public class SetLicenseRequest extends MasterNodeRequest<SetLicenseRequest> {

    private LicenseKey licenseKey;

    public SetLicenseRequest() {
    }

    public SetLicenseRequest(LicenseKey licenseKey) {
        this.licenseKey = licenseKey;
    }

    public LicenseKey licenseMetaData() {
        return licenseKey;
    }

    @Override
    public ActionRequestValidationException validate() {
        if (licenseKey == null) {
            return ValidateActions.addValidationError("licenseMetaData is missing", null);
        }
        return null;
    }

    @Override
    public void readFrom(StreamInput in) throws IOException {
        super.readFrom(in);
        licenseKey = new LicenseKey(in);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        licenseKey.writeTo(out);
    }
}
