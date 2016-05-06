package ro.cs.s2.workaround;

import ro.cs.s2.util.Logger;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;
import java.util.List;

/**
 * Utility class for inspecting (and modifying if needed) a tree of products.
 *
 */
public class ProductInspector {

    private Path root;
    private FillAnglesMethod method;

    public ProductInspector(String path, FillAnglesMethod fillAnglesMethod) throws FileNotFoundException{
        this.root = Paths.get(path);
        if (!Files.exists(this.root)) {
            throw new FileNotFoundException(path);
        }
        this.method = fillAnglesMethod;
    }

    public void traverse() throws IOException {
        Files.walkFileTree(root, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE,
                new FileVisitor<Path>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                        String dirName = dir.getFileName().toString();
                        if ("AUX_DATA".equals(dirName) ||
                                "DATASTRIP".equals(dirName)) {
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        String fileName = file.getFileName().toString();
                        if (fileName.startsWith("S2A_OPER_MTD_L1C_TL_") && fileName.endsWith(".xml")) {
                            if (!Files.exists(file.resolveSibling(fileName.replace(".xml", ".orig")))) {
                                Path previous = file.resolveSibling(fileName + ".bkp");
                                List<String> originalLines;
                                if (Files.exists(previous)) {
                                    originalLines = MetadataRepairer.parse(previous, method);
                                    Files.move(Paths.get(previous.toAbsolutePath().toString() + ".bkp"), Paths.get(file.toAbsolutePath().toString().replace(".xml", ".orig")));
                                    Files.delete(previous);
                                    Files.move(file, previous);
                                } else {
                                    originalLines = MetadataRepairer.parse(file, method);
                                }
                                Files.write(file, originalLines, StandardCharsets.UTF_8);
                            } else {
                                Logger.info(String.format("%s already processed", fileName));
                            }
                            return FileVisitResult.SKIP_SIBLINGS;
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                        return FileVisitResult.CONTINUE;
                    }
                });
    }
}
