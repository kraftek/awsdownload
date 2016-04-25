package ro.cs.s2.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

/**
 * Created by kraftek on 3/10/2016.
 */
public class LogFormatter extends Formatter {
    @Override
    public String format(LogRecord record) {
        String level = record.getLevel().getName();
        String buffer = formatTime(record.getMillis()) +
                "\t" +
                "[" + level + "]" +
                "\t" + (level.length() < 6 ? "\t" : "") +
                record.getMessage() +
                "\n";
        return buffer;
    }

    private String formatTime(long millis) {
        SimpleDateFormat date_format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Date resultDate = new Date(millis);
        return date_format.format(resultDate);
    }
}
