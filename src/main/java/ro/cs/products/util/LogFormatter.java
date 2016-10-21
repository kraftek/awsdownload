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
