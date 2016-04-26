package ro.cs.s2.workaround;

import ro.cs.s2.util.Constants;

public class ViewingIncidenceAngleGrid {
    private int bandId;
    private int detectorId;
    private AngleGrid zenith;
    private AngleGrid azimuth;

    public ViewingIncidenceAngleGrid(int bandId, int detectorId) {
        this.bandId = bandId;
        this.detectorId = detectorId;
    }

    public int getBandId() {
        return this.bandId;
    }

    public int getDetectorId() {
        return this.detectorId;
    }

    public AngleGrid getZenith() {
        return this.zenith;
    }

    public void setZenith(AngleGrid grid) {
        this.zenith = grid;
    }

    public AngleGrid getAzimuth() {
        return this.azimuth;
    }

    public void setAzimuth(AngleGrid grid) {
        this.azimuth = grid;
    }

    public String toString() {
        return Constants.LEVEL_1 + "<Viewing_Incidence_Angles_Grids bandId=\"" + this.bandId + "\" detectorId=\"" + (this.detectorId + 1) + "\">\n" + Constants.LEVEL_2 + "<Zenith>\n" + this.zenith.toString() + Constants.LEVEL_2 + "</Zenith>\n" + Constants.LEVEL_2 + "<Azimuth>\n" + this.azimuth.toString() + Constants.LEVEL_2 + "</Azimuth>\n" + Constants.LEVEL_1 + "</Viewing_Incidence_Angles_Grids>\n";
    }

}