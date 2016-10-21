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
package ro.cs.products.sentinel2.workaround;

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