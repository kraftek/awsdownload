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

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;

/**
 * Reads the angle grids from xml metadata.
 *
 * @author Cosmin Cara
 */
public class XmlAnglesReader {
    public XmlAnglesReader() {
    }

    /**
     * Parses the given input file and returns the meta grids of the granule, grouped by azimuth and zenith.
     * The grids are ordered by the order of the detectors. Thus, the band order is the following:
     * <code>1, 7, 2, 3, 4, 5, 6, 12, 0, 8, 9, 10, 11</code>
     *
     * @param inputFile The metadata file of a granule
     */
    public static Map<String, MetaGrid> parse(Path inputFile) {
        Map<String, MetaGrid> result = null;
        InputStream inputStream = null;
        try {
            inputStream = Files.newInputStream(inputFile, StandardOpenOption.READ);
            SAXParserFactory factory = SAXParserFactory.newInstance();
            SAXParser parser = factory.newSAXParser();
            XmlAnglesReader.Handler handler = new XmlAnglesReader.Handler();
            parser.parse(inputStream, handler);
            result = handler.getResult();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (Exception ignored) { }
            }
        }

        return result;
    }

    protected static class Handler extends DefaultHandler {
        private Map<String, MetaGrid> result;
        private AngleGrid currentGrid;
        private int currentBandId;
        private int currentDetectorId;
        private int currentRow;
        private MeanBandAngle currentMean;
        private StringBuilder buffer;

        protected Handler() {
        }

        public Map<String, MetaGrid> getResult() {
            return this.result;
        }

        @Override
        public void startDocument() throws SAXException {
            try {
                this.result = new HashMap<String, MetaGrid>() {{
                    put("Zenith", new MetaGrid(new int[] { 1, 7, 2, 3, 4, 5, 6, 12, 0, 8, 9, 10, 11 }, 23, 23));
                    put("Azimuth", new MetaGrid(new int[] { 1, 7, 2, 3, 4, 5, 6, 12, 0, 8, 9, 10, 11 }, 23, 23));
                }};
                this.buffer = new StringBuilder();
                this.currentDetectorId = -1;
                this.currentBandId = -1;
            } catch (Exception var2) {
                var2.printStackTrace();
            }

        }

        @Override
        public void endDocument() throws SAXException {
            super.endDocument();
        }

        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {
            this.buffer.append((new String(ch, start, length)).replace("\n", ""));
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
            if(qName.indexOf(":") > 0) {
                qName = qName.substring(qName.indexOf(":") + 1);
            }

            this.buffer.setLength(0);

            switch(qName) {
                case "Viewing_Incidence_Angles_Grids":
                    this.currentBandId = Integer.parseInt(attributes.getValue("bandId"));
                    this.currentDetectorId = Integer.parseInt(attributes.getValue("detectorId"));
                    break;
                case "Zenith":
                case "Azimuth":
                    if(this.currentBandId >= 0) {
                        this.currentGrid = new AngleGrid();
                        this.currentRow = 0;
                    }
                    break;
                case "Mean_Viewing_Incidence_Angle":
                    this.currentMean = new MeanBandAngle(Integer.parseInt(attributes.getValue("bandId")));
                    break;
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            if(qName.indexOf(":") > 0) {
                qName = qName.substring(qName.indexOf(":") + 1);
            }

            switch(qName) {
                case "Viewing_Incidence_Angles_Grids":
                    break;
                case "Zenith":
                    if (currentBandId >= 0) {
                        this.result.get("Zenith").addGrid(currentDetectorId, currentBandId, currentGrid);
                    }
                    break;
                case "Azimuth":
                    if (currentBandId >= 0) {
                        this.result.get("Azimuth").addGrid(currentDetectorId, currentBandId, currentGrid);
                    }
                    break;
                case "VALUES":
                    if(this.currentGrid != null) {
                        this.currentGrid.setRowValues(this.currentRow++, this.buffer.toString());
                    }
                    break;
                case "ZENITH_ANGLE":
                    if (this.currentMean != null) {
                        this.currentMean.setZenith(Double.parseDouble(this.buffer.toString()));
                    }
                    break;
                case "AZIMUTH_ANGLE":
                    if (this.currentMean != null) {
                        this.currentMean.setAzimuth(Double.parseDouble(this.buffer.toString()));
                    }
                    break;
                case "Mean_Viewing_Incidence_Angle":
                    this.result.get("Zenith").setBandMeanAngles(this.currentMean);
                    break;
            }

            this.buffer.setLength(0);
        }

        @Override
        public void error(SAXParseException e) throws SAXException {
            String error = e.getMessage();
            if (!error.contains("no grammar found")) {
                e.printStackTrace();
            }
        }
    }
}
