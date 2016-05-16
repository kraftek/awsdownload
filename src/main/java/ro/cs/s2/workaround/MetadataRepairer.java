package ro.cs.s2.workaround;

import ro.cs.s2.util.Constants;
import ro.cs.s2.util.Logger;
import ro.cs.s2.util.Utilities;

import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Utility class for attempting filling missing metadata angles.
 *
 */
public class MetadataRepairer {

    private FillAnglesMethod fillMissingAnglesMethod;

    private MetadataRepairer(FillAnglesMethod method) {
        fillMissingAnglesMethod = method;
    }

    public static List<String> parse(Path metadataFile, FillAnglesMethod fillMethod) throws IOException {
        MetadataRepairer instance = new MetadataRepairer(fillMethod);
        return instance.parse(metadataFile);
    }

    private List<String> parse(Path metadataFile) throws IOException {
        List<String> tileMetadataLines = Files.readAllLines(metadataFile);
        int gridCount = Utilities.filter(tileMetadataLines, "<Viewing_Incidence_Angles_Grids").size();
        if (gridCount != 13 * 12) {
            Logger.getRootLogger().warn("Metadata %s doesn't contain one or more angles grids!", metadataFile.getFileName());
            if (!FillAnglesMethod.NONE.equals(fillMissingAnglesMethod)) {
                Map<String, MetaGrid> angleGridMap = XmlAnglesReader.parse(metadataFile);
                List<ViewingIncidenceAngleGrid> missingAngles = computeMissingAngles(angleGridMap);
                StringBuilder lines = new StringBuilder();
                String message = "Angle grids have been computed for ";
                Map<Integer, Set<Integer>> missingBandIds = new TreeMap<>();
                for (ViewingIncidenceAngleGrid missingGrid : missingAngles) {
                    lines.append(missingGrid.toString());
                    int bandId = missingGrid.getBandId();
                    if (!missingBandIds.containsKey(bandId)) {
                        missingBandIds.put(bandId, new TreeSet<Integer>());
                    }
                    missingBandIds.get(bandId).add(missingGrid.getDetectorId());
                }
                if (missingBandIds.isEmpty()) {
                    message += String.valueOf(13*12-gridCount) + " missing detectors";
                } else {
                    for (Map.Entry<Integer, Set<Integer>> e : missingBandIds.entrySet()) {
                        message += "band " + String.valueOf(e.getKey()) + " [detectors: " + Utilities.join(e.getValue(), ",") + "]; ";
                    }
                }
                String[] tokens = lines.toString().split("\n");
                if(!insertAngles(metadataFile, tileMetadataLines, Arrays.asList(tokens), meansToXml(computeMeanAngles(angleGridMap, missingAngles, true), computeMeanAngles(angleGridMap, missingAngles, false)))) {
                    Logger.getRootLogger().warn("Metadata for tile %s has not been updated!", metadataFile.getFileName());
                } else {
                    Logger.getRootLogger().info(message);
                }
            }
        }
        return tileMetadataLines;
    }

    private List<ViewingIncidenceAngleGrid> computeMissingAngles(Map<String, MetaGrid> angleGridMap) {
        MetaGrid zeniths = angleGridMap.get("Zenith");
        MetaGrid azimuths = angleGridMap.get("Azimuth");
        zeniths.setFillMethod(fillMissingAnglesMethod);
        azimuths.setFillMethod(fillMissingAnglesMethod);
        List<ViewingIncidenceAngleGrid> computedGrids = new ArrayList<>();
        Set<Integer[]> missingPairs = zeniths.fillGaps();
        azimuths.fillGaps();
        for (Integer[] pair : missingPairs) {
            int bandId = pair[0];
            int detectorId = pair[1];
            ViewingIncidenceAngleGrid grid = new ViewingIncidenceAngleGrid(bandId, detectorId - 1);
            grid.setZenith(zeniths.getGrid(bandId, detectorId));
            grid.setAzimuth(azimuths.getGrid(bandId, detectorId));
            computedGrids.add(grid);
        }

        return computedGrids;
    }

    private Map<Integer, Double> computeMeanAngles(Map<String, MetaGrid> angleGridMap, List<ViewingIncidenceAngleGrid> missingGrids, boolean isZenith) {
        Map<Integer, Double> means = new HashMap<>();
        Map<Integer, Integer> nonNaNCounts = new HashMap<>();
        Map<Integer, MeanBandAngle> bandMeanAngles = angleGridMap.get("Zenith").getBandMeanAngles();
        for (ViewingIncidenceAngleGrid grid : missingGrids) {
            int bandId = grid.getBandId();
            if (!bandMeanAngles.containsKey(bandId)) {
                double meanValue = (isZenith ? grid.getZenith() : grid.getAzimuth()).meanValue();
                if (!Double.isNaN(meanValue)) {
                    if (!means.containsKey(bandId)) {
                        means.put(bandId, meanValue);
                    } else {
                        means.put(bandId, means.get(bandId) + meanValue);
                    }

                    if (!nonNaNCounts.containsKey(bandId)) {
                        nonNaNCounts.put(bandId, 1);
                    } else {
                        nonNaNCounts.put(bandId, nonNaNCounts.get(bandId) + 1);
                    }
                }
            }
        }
        for (int bandId = 0; bandId < 13; bandId++) {
            if (!bandMeanAngles.containsKey(bandId)) {
                means.put(bandId, means.containsKey(bandId) ?
                        (means.get(bandId) / (double) (nonNaNCounts.containsKey(bandId) ? nonNaNCounts.get(bandId) : 1)) :
                        Double.NaN);
            }
        }

        return means;
    }

    private List<String> meansToXml(Map<Integer, Double> zenithMeans, Map<Integer, Double> azimuthMeans) {
        StringBuilder buffer = new StringBuilder();
        if(zenithMeans.size() == azimuthMeans.size() && zenithMeans.size() > 0) {
            for (Integer bandId : zenithMeans.keySet()) {
                buffer.append(Constants.LEVEL_2).append("<Mean_Viewing_Incidence_Angle bandId=\"").append(bandId).append("\">\n");
                buffer.append(Constants.LEVEL_3).append("<ZENITH_ANGLE unit=\"deg\">").append(zenithMeans.get(bandId)).append("</ZENITH_ANGLE>\n");
                buffer.append(Constants.LEVEL_3).append("<AZIMUTH_ANGLE unit=\"deg\">").append(azimuthMeans.containsKey(bandId)?(Serializable)azimuthMeans.get(bandId):"NaN").append("</AZIMUTH_ANGLE>\n");
                buffer.append(Constants.LEVEL_2).append("</Mean_Viewing_Incidence_Angle>\n");
            }
            Logger.getRootLogger().info("Mean angles have been computed for bands " + Utilities.join(zenithMeans.keySet(), ","));
        }
        if (zenithMeans.size() == 0) {
            Logger.getRootLogger().info("No mean angle has been computed");
        }
        return Arrays.asList(buffer.toString().split("\n"));
    }

    private boolean insertAngles(Path metaFile, List<String> originalLines, List<String> gridLines, List<String> meanLines) throws IOException {
        boolean gridUpdated = false;
        boolean meansUpdated = meanLines.isEmpty();
        int lineCount = originalLines.size();

        for(int idx = 0; idx < lineCount; ++idx) {
            String line = originalLines.get(idx);
            if(line.contains("<Viewing_Incidence_Angles_Grids") && !gridUpdated) {
                gridUpdated = originalLines.addAll(idx, gridLines);
                idx += gridLines.size();
                lineCount += gridLines.size();
            }

            if(line.contains("<Mean_Viewing_Incidence_Angle ") && !meansUpdated) {
                meansUpdated = originalLines.addAll(idx, meanLines);
                idx = lineCount;
            }
        }

        if(gridUpdated && meansUpdated) {
            Files.copy(metaFile, Paths.get(metaFile.toAbsolutePath().toString() + ".bkp"));
            Files.write(metaFile, originalLines, StandardCharsets.UTF_8);
        }

        return gridUpdated && meansUpdated;
    }
}
