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

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
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

    public static String find(List<String> input, String filter, String psdVersion) {
        String value = null;
        String granuleIdentifier;
        switch (psdVersion) {
            case Constants.PSD_13 :
                for (String line : input) {
                    granuleIdentifier = getAttributeValue(line, "granuleIdentifier");
                    if (granuleIdentifier.contains(filter)) {
                        value = granuleIdentifier;
                        break;
                    }
                }
                break;
            case Constants.PSD_14:
                String datastripIdentifier;
                for (String line : input) {
                    granuleIdentifier = getAttributeValue(line, "granuleIdentifier");
                    if (granuleIdentifier.contains(filter)) {
                        datastripIdentifier = getAttributeValue(line, "datastripIdentifier");
                        value = granuleIdentifier.substring(13, 16) + "_" +
                                granuleIdentifier.substring(49, 55) + "_" +
                                granuleIdentifier.substring(41, 48) + "_" +
                                datastripIdentifier.substring(42, 57);
                        break;
                    }
                }
                break;
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
                folder = Files.createDirectories(folder, attrs);
            } else {
                folder = Files.createDirectories(folder);
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
