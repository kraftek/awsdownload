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

package ro.cs.products.base;

/**
 * The download behavior enumeration
 *
 * @author Cosmin Cara
 */
public enum DownloadMode {
    /**
     * Product will be downloaded from the remote site and the corresponding local product,
     * if exists, it will be overwritten
     */
    OVERWRITE,
    /**
     * Product will be downloaded from the remote site and, if a corresponding local product exists,
     * the download will be resumed from the current length of the local product
     */
    RESUME,
    /**
     * The product will be copied from a local (or shared) folder into the output folder.
     * No remote download will be performed.
     * This works only in conjunction with the --input command line parameter.
     */
    COPY,
    /**
     * Only a symlink to the product file system location, into the output folder, will be created.
     * No remote download will be performed.
     * This works only in conjunction with the --input command line parameter.
     */
    SYMLINK
}
