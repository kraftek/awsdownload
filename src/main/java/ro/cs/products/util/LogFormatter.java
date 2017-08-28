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
package ro.cs.products.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

/**
 * Formatter for log messages.
 *
 * @author  Cosmin Cara
 */
public class LogFormatter extends Formatter {
    @Override
    public String format(LogRecord record) {
        String level = record.getLevel().getName();
        return formatTime(record.getMillis()) +
                "\t" +
                "[" + level + "]" +
                "\t" + (level.length() < 6 ? "\t" : "") +
                record.getMessage() +
                "\n";
    }

    private String formatTime(long millis) {
        SimpleDateFormat date_format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Date resultDate = new Date(millis);
        return date_format.format(resultDate);
    }
}
