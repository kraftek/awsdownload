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
package ro.cs.products.sentinel2.angles;

import ro.cs.products.util.Constants;

/**
 * Holder for a pair of view angle grids of a band & detector combination.
 *
 * @author Cosmin Cara
 */
public class ViewingIncidenceAngleGrid {
    private int bandId;
    private int detectorId;
    private AngleGrid zenith;
    private AngleGrid azimuth;

    public ViewingIncidenceAngleGrid(int bandId, int detectorId) {
        this.bandId = bandId;
        this.detectorId = detectorId;
    }

    /**
     * Returns the band identifier.
     */
    public int getBandId() {
        return this.bandId;
    }
    /**
     * Returns the detector identifier.
     */
    public int getDetectorId() {
        return this.detectorId;
    }
    /**
     * Returns the Zenith angles grid.
     */
    public AngleGrid getZenith() {
        return this.zenith;
    }
    /**
     * Sets the Zenith angles grid.
     */
    public void setZenith(AngleGrid grid) {
        this.zenith = grid;
    }
    /**
     * Returns the Azimuth angles grid.
     */
    public AngleGrid getAzimuth() {
        return this.azimuth;
    }
    /**
     * Sets the Azimuth angles grid.
     */
    public void setAzimuth(AngleGrid grid) {
        this.azimuth = grid;
    }
    @Override
    public String toString() {
        return Constants.LEVEL_1 + "<Viewing_Incidence_Angles_Grids bandId=\"" + this.bandId + "\" detectorId=\"" + (this.detectorId + 1) + "\">\n" + Constants.LEVEL_2 + "<Zenith>\n" + this.zenith.toString() + Constants.LEVEL_2 + "</Zenith>\n" + Constants.LEVEL_2 + "<Azimuth>\n" + this.azimuth.toString() + Constants.LEVEL_2 + "</Azimuth>\n" + Constants.LEVEL_1 + "</Viewing_Incidence_Angles_Grids>\n";
    }

}