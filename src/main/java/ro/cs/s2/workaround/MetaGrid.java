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
package ro.cs.s2.workaround;

import java.util.*;

/**
 * This class manipulates the angle grids of a product.
 *
 * @author Cosmin Cara
 */
public class MetaGrid {

    private AngleGrid[][] angleGrids;
    private Map<Integer, Integer> bandIndices;
    private int rows;
    private int cols;
    private FillAnglesMethod method;
    private Map<Integer, MeanBandAngle> bandMeanAngles;

    /**
     * Constructs a meta grid having the given number of rows and columns, and for which
     * the bands order is specified.
     */
    public MetaGrid(int[] bandsOrder, int rows, int cols) {
        int bands = bandsOrder != null ? bandsOrder.length : 13;
        angleGrids = new AngleGrid[12][bands];
        bandIndices = new HashMap<>();
        if (bandsOrder != null) {
            for (int b = 0; b < bandsOrder.length; b++) {
                bandIndices.put(bandsOrder[b], b);
            }
        } else {
            for (int b = 0; b < 13; b++) {
                bandIndices.put(b, b);
            }
        }
        this.rows = rows;
        this.cols = cols;
        this.method = FillAnglesMethod.NONE;
        this.bandMeanAngles = new HashMap<>();
    }
    /**
     * Sets the fill method for missing angles.
     */
    public void setFillMethod(FillAnglesMethod method) {
        this.method = method;
    }

    /**
     * Adds an angles grid to this meta grid, for the given band and detector.
     *
     * @param detectorId    The detector identifier
     * @param bandId        The band identifier
     * @param grid          The angles grid
     */
    public void addGrid(int detectorId, int bandId, AngleGrid grid) {
        checkValidIndices(detectorId - 1, bandId);
        if (grid == null || grid.getRowsCount() != rows || grid.getColsCount() != cols) {
            throw new IllegalArgumentException("grid");
        }
        int bandIndex = bandIndices.get(bandId);
        angleGrids[detectorId - 1][bandIndex] = grid;
    }

    /**
     * Returns the angles grid for the given band and detector.
     *
     * @param bandId        The band identifier
     * @param detectorId    The detector identifier
     */
    public AngleGrid getGrid(int bandId, int detectorId) {
        checkValidIndices(detectorId - 1, bandId);
        return angleGrids[detectorId - 1][bandIndices.get(bandId)];
    }
    /**
     * Returns all the angles grids for a given band.
     *
     * @param bandId    The band identifier
     */
    public List<AngleGrid> getBandGrids(int bandId) {
        checkValidIndices(0, bandId);
        int bandIdx = bandIndices.get(bandId);
        List<AngleGrid> grids = new ArrayList<>();
        for (AngleGrid[] angleGrid : angleGrids) {
            grids.add(angleGrid[bandIdx]);
        }
        return grids;
    }

    /**
     * Returns the band identifier for the band at the given index.
     * The order of the bands may not be given by the identifiers of the bands.
     * @param bandIdx  The band index
     */
    public int getBandIdFromIndex(int bandIdx) {
        int bandId = -1;
        for (Map.Entry<Integer, Integer> entry : bandIndices.entrySet()) {
            if (entry.getValue() == bandIdx) {
                bandId = entry.getKey();
                break;
            }
        }
        return bandId;
    }
    /**
     * Sets the mean angles values for a band.
     *
     * @param meanAngles    The mean band angle values holder.
     */
    public void setBandMeanAngles(MeanBandAngle meanAngles) {
        if (meanAngles != null) {
            this.bandMeanAngles.put(meanAngles.getBandId(), meanAngles);
        }
    }
    /**
     * Returns the mean angles values for all bands.
     */
    public Map<Integer, MeanBandAngle> getBandMeanAngles() { return this.bandMeanAngles; }

    /**
     * Returns the mean value for a band.
     *
     * @param bandId    The band identifier
     */
    public double getBandMeanValue(int bandId) {
        checkValidIndices(0, bandId);
        int bandIdx = bandIndices.get(bandId);
        double sum = 0.0;
        int count = 0;
        for (int detectorId = 0; detectorId < angleGrids.length; detectorId++) {
            if (angleGrids[detectorId][bandIdx] != null) {
                double mean = angleGrids[detectorId][bandIdx].meanValue();
                if (!Double.isNaN(mean)) {
                    sum += mean;
                    count++;
                }
            }
        }
        return count > 0 ? sum / (double) count : Double.NaN;
    }

    /**
     * Fills the missing angles values / grids.
     *
     * @return  A set of band identifiers for the modified band grids.
     */
    public Set<Integer[]> fillGaps() {
        Set<Integer[]> missingBands = new HashSet<>();
        if (angleGrids.length > 0) {
            for (int j = 0; j < angleGrids.length; j++) {
                for (int i = 0; i < angleGrids[0].length; i++) {
                    if (angleGrids[j][i] == null) {
                        missingBands.add(new Integer[] { getBandIdFromIndex(i), j + 1 });
                    }
                }
            }
        }
        for (int i = 0; i < angleGrids.length; i++) {
            fillDetectorGaps(i);
        }
        return missingBands;
    }

    private void fillDetectorGaps(int detectorId) {
        if (!FillAnglesMethod.NONE.equals(method)) {
            AngleGrid[] detGrids = angleGrids[detectorId];
            for (int row = 0; row < rows; row++) {
                double[] values = new double[detGrids.length];
                for (int col = 0; col < cols; col++) {
                    for (int b = 0; b < values.length; b++) {
                        if (detGrids[b] == null) {
                            detGrids[b] = createEmpty();
                        }
                        values[b] = detGrids[b].getValueAt(row, col);
                    }
                    if (FillAnglesMethod.INTERPOLATE.equals(method)) {
                        values = interpolate(values);
                        for (int b = 0; b < values.length; b++) {
                            detGrids[b].setValueAt(row, col, values[b]);
                        }
                    }
                }
            }
        }
    }

    private void checkValidIndices(int detectorId, int bandId) {
        if (detectorId >= angleGrids.length || bandId >= angleGrids[detectorId].length) {
            throw new IllegalArgumentException("Out of bounds");
        }
    }

    private AngleGrid createEmpty() {
        AngleGrid newGrid = new AngleGrid(rows, cols);
        for (int i = 0; i < rows; i++) {
            double[] doubles = new double[cols];
            Arrays.fill(doubles, Double.NaN);
            newGrid.setRowValues(i, doubles);
        }
        return newGrid;
    }

    public static double[] interpolate(double[] data) {
        int startIdx = -1;
        double startValue = Double.NaN;
        double element;
        for (int i = 0; i < data.length - 1; i++) {
            element = data[i];
            if (!Double.isNaN(element)) {
                if (startIdx != -1) {
                    doInterpolate(startValue, element, startIdx + 1, i - startIdx - 1, data);
                }
                startValue = element;
                startIdx = i;
            }
        }
        return data;
    }

    private static void doInterpolate(double start, double end, int startIdx, int count, double[] data) {
        double delta = (end - start) / (count + 1);
        for (int i = startIdx; i < startIdx + count; i++) {
            data[i] = start + delta * (i - startIdx + 1);
        }
    }
}
