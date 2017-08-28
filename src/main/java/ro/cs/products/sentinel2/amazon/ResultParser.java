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
package ro.cs.products.sentinel2.amazon;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * Parser for AWS XML responses
 *
 * @author  Cosmin Cara
 */
public class ResultParser {

    public static Result parse(String text) {
        Result result = null;
        try (InputStream inputStream = new ByteArrayInputStream(text.getBytes())) {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            SAXParser parser = factory.newSAXParser();
            Handler handler = new Handler();
            parser.parse(inputStream, handler);
            result = handler.getResult();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    private static class Handler extends DefaultHandler {
        private Result result;
        private boolean isCollection;
        private StringBuilder buffer;

        Result getResult() {
            return result;
        }

        @Override
        public void startDocument() throws SAXException {
            try {
                result = new Result();
                buffer = new StringBuilder();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void endDocument() throws SAXException {
            super.endDocument();
        }

        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {
            buffer.append(new String(ch, start, length).replace("\n", ""));
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
            if (qName.indexOf(":") > 0) {
                qName = qName.substring(qName.indexOf(":") + 1);
            }
            if ("CommonPrefixes".equals(qName)) {
                isCollection = true;
            }
            buffer.setLength(0);
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            if (qName.indexOf(":") > 0) {
                qName = qName.substring(qName.indexOf(":") + 1);
            }
            switch (qName) {
                case "Name":
                    result.setName(buffer.toString());
                    break;
                case "Prefix":
                    if (isCollection) {
                        result.addPrefix(buffer.toString());
                    } else {
                        result.setPrefix(buffer.toString());
                    }
                    break;
                case "Marker":
                    result.setMarker(buffer.toString());
                    break;
                case "MaxKeys":
                    result.setMaxKeys(Integer.parseInt(buffer.toString()));
                    break;
                case "Delimiter":
                    result.setDelimiter(buffer.toString());
                    break;
                case "IsTruncated":
                    result.setTruncated(Boolean.parseBoolean(buffer.toString()));
                    break;
            }
            buffer.setLength(0);
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

