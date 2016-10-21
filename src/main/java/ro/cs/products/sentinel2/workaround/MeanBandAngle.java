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
 * Holder for the mean angle values of a band.
 *
 * @author Cosmin Cara
 */
public class MeanBandAngle {
    private int bandId;
    private double zenith;
    private double azimuth;

    public MeanBandAngle(int bandId) {
        this.bandId = bandId;
    }

    public double getAzimuth() {
        return azimuth;
    }

    public void setAzimuth(double azimuth) {
        this.azimuth = azimuth;
    }

    public int getBandId() {
        return bandId;
    }

    public double getZenith() {
        return zenith;
    }

    public void setZenith(double zenith) {
        this.zenith = zenith;
    }

    @Override
    public String toString() {
        return Constants.LEVEL_2 + "<Mean_Viewing_Incidence_Angle bandId=\"" + bandId + "\">\n" +
                Constants.LEVEL_3 + "<ZENITH_ANGLE unit=\"deg\">" + zenith + "</ZENITH_ANGLE>\n" +
                Constants.LEVEL_3 + "<AZIMUTH_ANGLE unit=\"deg\">" + azimuth + "</AZIMUTH_ANGLE>\n" +
                Constants.LEVEL_2 + "</Mean_Viewing_Incidence_Angle>\n";
    }
}
