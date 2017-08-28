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
