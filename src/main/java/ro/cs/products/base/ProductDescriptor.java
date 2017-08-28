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

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Simple product descriptor to hold attributes needed for download.
 *
 * @author Cosmin Cara
 */
public abstract class ProductDescriptor {
    protected String name;
    protected String id;
    protected double cloudsPercentage;
    protected String sensingDate;
    protected String version;

    public ProductDescriptor() {}

    public ProductDescriptor(String name) {
        setName(name);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        if (!verifyProductName(name)) {
            throw new IllegalArgumentException(String.format("The product name %s doesn't match the expected pattern", name));
        }
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getVersion() { return version; }

    public void setVersion(String version) { this.version = version; }

    public double getCloudsPercentage() {
        return cloudsPercentage;
    }

    public void setCloudsPercentage(double cloudsPercentage) {
        this.cloudsPercentage = cloudsPercentage;
    }

    public String getSensingDate() { return sensingDate; }

    public void setSensingDate(String date) { this.sensingDate = date; }

    public abstract String getProductRelativePath();

    @Override
    public String toString() {
        return this.name;
    }

    protected abstract boolean verifyProductName(String name);

    protected String[] getTokens(Pattern pattern, String input, Map<Integer, String> replacements) {
        String[] tokens = null;
        Matcher matcher = pattern.matcher(input);
        if (matcher.matches()) {
            int count = matcher.groupCount();
            tokens = new String[count];
            for (int i = 0; i < tokens.length; i++) {
                if (replacements != null && replacements.containsKey(i)) {
                    tokens[i] = replacements.get(i);
                } else {
                    tokens[i] = matcher.group(i + 1);
                }
            }
        } else {
            throw new RuntimeException("Name doesn't match the specifications");
        }
        return tokens;
    }
}
