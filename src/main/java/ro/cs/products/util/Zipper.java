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
