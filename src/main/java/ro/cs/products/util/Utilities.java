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

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Utility class for various operations.
 *
 * @author  Cosmin Cara
 */
public class Utilities {

    private static Boolean supportsPosix;

    public static String join(Iterable collection, String separator) {
        String result = "";
        if (collection != null) {
            boolean hasElements = false;
            for (Object aCollection : collection) {
                hasElements = true;
                result += (aCollection != null ? aCollection.toString() : "null") + separator;
            }
            if (hasElements) {
                result = result.substring(0, result.length() - separator.length());
            }
        }
        return result;
    }

    public static List<String> filter(List<String> input, String filter) {
        List<String> result = new ArrayList<>();
        if (input != null) {
            for (String item : input) {
                if (item.contains(filter)) {
                    result.add(item);
                }
            }
        }
        return result;
    }

    public static String find(List<String> input, String filter) {
        String value = null;
        String granuleName;
        for (String line : input) {
            granuleName = getAttributeValue(line, "granuleIdentifier");
            if (granuleName.contains(filter)) {
                value = granuleName;
                break;
            }
        }
        return value;
    }

    public static String formatTime(long millis) {
        return String.format("%02dh:%02dm:%02ds",
                TimeUnit.MILLISECONDS.toHours(millis),
                TimeUnit.MILLISECONDS.toMinutes(millis) % TimeUnit.HOURS.toMinutes(1),
                TimeUnit.MILLISECONDS.toSeconds(millis) % TimeUnit.MINUTES.toSeconds(1));
    }

    public static String getAttributeValue(String xmlLine, String name) {
        String value = null;
        int idx = xmlLine.indexOf(name);
        if (idx > 0) {
            int start = idx + name.length() + 2;
            value = xmlLine.substring(start, xmlLine.indexOf("\"", start));
        }
        return value;
    }

    public static Path ensureExists(Path folder) throws IOException {
        if (folder != null && !Files.exists(folder)) {
            if (isPosixFileSystem()) {
                Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rwxr-xr-x");
                FileAttribute<Set<PosixFilePermission>> attrs = PosixFilePermissions.asFileAttribute(perms);
                folder = Files.createDirectory(folder, attrs);
            } else {
                folder = Files.createDirectory(folder);
            }

        }
        return folder;
    }

    public static Path ensurePermissions(Path file) {
        try {
            if (file != null && Files.exists(file)) {
                if (isPosixFileSystem()) {
                    Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rwxr-xr-x");
                    file = Files.setPosixFilePermissions(file, perms);
                }
            }
        } catch (IOException ex) {
            Logger.getRootLogger().warn("Cannot set permissions for %s", file.toString());
        }
        return file;
    }

    private static boolean isPosixFileSystem() {
        if (supportsPosix == null) {
            supportsPosix = Boolean.FALSE;
            FileSystem fileSystem = FileSystems.getDefault();
            Iterable<FileStore> fileStores = fileSystem.getFileStores();
            for (FileStore fs : fileStores) {
                supportsPosix = fs.supportsFileAttributeView(PosixFileAttributeView.class);
                if (supportsPosix) {
                    break;
                }
            }
        }
        return supportsPosix;
    }
}
