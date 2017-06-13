/*
 * Copyright (C) 2016 Cosmin Cara
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 *  with this program; if not, see http://www.gnu.org/licenses/
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
