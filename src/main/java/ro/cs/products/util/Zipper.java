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
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Iterator;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Simple utility class for zipping downloaded products
 *
 * @author Cosmin Cara
 */
public class Zipper {

    public static void compress(Path sourceFolder, String archiveName, boolean deleteFolder) throws IOException {
        Path zipFile = sourceFolder.getParent().resolve(archiveName + ".zip");
        Files.deleteIfExists(zipFile);
        zipFile = Files.createFile(zipFile);
        try (ZipOutputStream outputStream = new ZipOutputStream(Files.newOutputStream(zipFile))) {
            try (Stream<Path> files = Files.walk(sourceFolder)) {
                Iterator<Path> pathIterator = files.iterator();
                while (pathIterator.hasNext()) {
                    Path path = pathIterator.next();
                    String sp = sourceFolder.relativize(path).toString();
                    try {
                        ZipEntry entry = new ZipEntry(sp);
                        outputStream.putNextEntry(entry);
                        if (!Files.isDirectory(path)) {
                            outputStream.write(Files.readAllBytes(path));
                        }
                        outputStream.closeEntry();
                    } catch (Exception e) {
                        System.out.println(e.getMessage());
                    }
                }
            }
        }
        if (deleteFolder) {
            delete(sourceFolder);
        }
    }

    private static boolean delete(Path path) {
        if (!Files.exists(path)) {
            return false;
        }
        boolean retVal = false;
        try {
            if (Files.isDirectory(path)) {
                Files.walkFileTree(path, new FileVisitor<Path>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        Files.delete(file);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                        Files.delete(dir);
                        return FileVisitResult.CONTINUE;
                    }
                });
            } else {
                retVal = Files.deleteIfExists(path);
            }
        } catch (IOException e) {
            System.err.println(e.getMessage());
            retVal = false;
        }
        return retVal;
    }

}
