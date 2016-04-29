package ro.cs.s2.workaround;

/**
 * Created by kraftek on 4/29/2016.
 */
public enum FillAnglesMethod {
    /*
     * Nothing to do
     */
    NONE,
    /*
     * Missing angle value replaced with NaN
     */
    NAN,
    /*
     * Missing angle value interpolated from existing values
     */
    INTERPOLATE
}
