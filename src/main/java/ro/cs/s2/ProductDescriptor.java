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
package ro.cs.s2;

/**
 * Simple product descriptor to hold attributes needed for download.
 *
 * @author Cosmin Cara
 */
public class ProductDescriptor {
    private String name;
    private String id;
    private double cloudsPercentage;
    private String sensingDate;

    public ProductDescriptor() {}

    public ProductDescriptor(String name) {
        this.name = name;
        String[] tokens = this.name.split("_");
        if (tokens.length != 9) {
            throw new IllegalArgumentException(String.format("The product name %s doesn't match the expected pattern", name));
        }
        this.sensingDate = tokens[7].substring(1, tokens[7].indexOf("T"));
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public double getCloudsPercentage() {
        return cloudsPercentage;
    }

    public void setCloudsPercentage(double cloudsPercentage) {
        this.cloudsPercentage = cloudsPercentage;
    }

    public String getSensingDate() { return sensingDate; }

    @Override
    public String toString() {
        return this.name;
    }
}
