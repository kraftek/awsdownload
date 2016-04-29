package ro.cs.s2.workaround;

import java.util.*;

/**
 * Created by kraftek on 4/26/2016.
 */
public class MetaGrid {

    private AngleGrid[][] angleGrids;
    private Map<Integer, Integer> bandIndices;
    private int rows;
    private int cols;
    private FillAnglesMethod method;

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
        /*for (int i = 0; i < angleGrids.length; i++) {
            for (int j = 0; j < angleGrids[0].length; j++) {
                angleGrids[i][j] = createEmpty();
            }
        }*/
    }

    public void setFillMethod(FillAnglesMethod method) {
        this.method = method;
    }

    public void addGrid(int detectorId, int bandId, AngleGrid grid) {
        checkValidIndices(detectorId - 1, bandId);
        if (grid == null || grid.getRowsCount() != rows || grid.getColsCount() != cols) {
            throw new IllegalArgumentException("grid");
        }
        int bandIndex = bandIndices.get(bandId);
        angleGrids[detectorId - 1][bandIndex] = grid;
    }

    public AngleGrid getGrid(int bandId, int detectorId) {
        checkValidIndices(detectorId - 1, bandId);
        return angleGrids[detectorId - 1][bandIndices.get(bandId)];
    }

    public List<AngleGrid> getBandGrids(int bandId) {
        checkValidIndices(0, bandId);
        int bandIdx = bandIndices.get(bandId);
        List<AngleGrid> grids = new ArrayList<>();
        for (AngleGrid[] angleGrid : angleGrids) {
            grids.add(angleGrid[bandIdx]);
        }
        return grids;
    }

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

    public Set<Integer> fillGaps() {
        Set<Integer> missingBands = new TreeSet<>();
        if (angleGrids.length > 0) {
            for (int i = 0; i < angleGrids[0].length; i++) {
                if (angleGrids[0][i] == null) {
                    missingBands.add(getBandIdFromIndex(i));
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
