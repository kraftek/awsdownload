package ro.cs.s2.workaround;

import ro.cs.s2.util.Constants;

import java.util.Arrays;

/**
 * Created by kraftek on 4/26/2016.
 */
public class AngleGrid {

    private final double[][] values;
    private int rows;
    private int cols;

    public AngleGrid() {
        this(23, 23);
    }

    public AngleGrid(int rows, int cols) {
        this.rows = rows;
        this.cols = cols;
        values = new double[this.rows][this.cols];
    }

    public int getRowsCount() { return rows; }

    public int getColsCount() { return cols; }

    public double[] getRowValues(int row) {
        return this.isIndexValid(row) ? this.values[row] : null;
    }

    public double getValueAt(int row, int col) {
        return this.isIndexValid(row) && this.isIndexValid(col) ? this.values[row][col] : Double.NaN;
    }

    public void setRowValues(int row, String values) {
        if(values != null && this.isIndexValid(row)) {
            String[] split = values.split(" ");

            for(int i = 0; i < split.length; ++i) {
                this.setValueAt(row, i, Double.parseDouble(split[i]));
            }
        }
    }

    public void setRowValues(int row, double[] values) {
        this.values[row] = values;
    }

    public void setValueAt(int row, int col, double value) {
        if(this.isIndexValid(row) && this.isIndexValid(col)) {
            this.values[row][col] = value;
        }
    }

    public double meanValue() {
        double sum = 0.0D;
        int count = 0;
        for (int row = 0; row < this.values.length; row++) {
            for (int col = 0; col < this.values[0].length; col++) {
                if (!Double.isNaN(this.values[row][col])) {
                    sum += this.values[row][col];
                    count++;
                }
            }
        }

        return count > 0 ? sum / (double) count : Double.NaN;
    }

    public String toString() {
        StringBuilder buffer = new StringBuilder();
        buffer.append(Constants.LEVEL_3).append("<COL_STEP unit=\"m\">5000</COL_STEP>\n").append(Constants.LEVEL_3).append("<ROW_STEP unit=\"m\">5000</ROW_STEP>\n").append(Constants.LEVEL_3).append("<Values_List>\n");

        for(int i = 0; i < rows; ++i) {
            buffer.append(Constants.LEVEL_4).append("<VALUES>");
            buffer.append(this.asString(this.getRowValues(i)));
            buffer.append("</VALUES>\n");
        }

        buffer.append(Constants.LEVEL_3).append("</Values_List>\n");
        return buffer.toString();
    }

    private boolean isIndexValid(int index) {
        return index >= 0 && index < 23;
    }

    private String asString(double[] doubles) {
        String result = "";
        if (doubles == null) {
            doubles = new double[rows];
            Arrays.fill(doubles, Double.NaN);
        }

        for (double aDouble : doubles) {
            result += (Double.isNaN(aDouble) ? "NaN" : String.format("%.3f", aDouble)) + " ";
        }

        return result.trim();
    }
}
