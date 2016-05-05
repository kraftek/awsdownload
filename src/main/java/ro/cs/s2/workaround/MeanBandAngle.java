package ro.cs.s2.workaround;

import ro.cs.s2.util.Constants;

/**
 * Created by kraftek on 5/5/2016.
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
