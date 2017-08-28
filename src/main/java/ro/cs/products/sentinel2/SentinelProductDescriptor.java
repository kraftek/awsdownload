/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package ro.cs.products.sentinel2;

import ro.cs.products.base.ProductDescriptor;

/**
 * @author Cosmin Cara
 */
public abstract class SentinelProductDescriptor extends ProductDescriptor {

    public SentinelProductDescriptor() { }

    public SentinelProductDescriptor(String name) {
        super(name);
    }

    abstract PlatformType getPlatform();

    abstract String getTileIdentifier();

    abstract String getMetadataFileName();

    abstract String getDatastripMetadataFileName(String datastripIdentifier);

    abstract String getDatastripFolder(String datastripIdentifier);

    abstract String getGranuleFolder(String datastripIdentifier, String granuleIdentifier);

    abstract String getGranuleMetadataFileName(String granuleIdentifier);

    abstract String getBandFileName(String granuleIdentifier, String band);

    abstract String getEcmWftFileName(String granuleIdentifier);

}
