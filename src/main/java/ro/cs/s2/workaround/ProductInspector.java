package ro.cs.s2.workaround;

import ro.cs.s2.util.Logger;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Utility class for inspecting (and modifying if needed) a tree of products.
 *
 */
public class ProductInspector {

    private Path root;
    private FillAnglesMethod method;
    private Set<String> products;

    public ProductInspector(String path, FillAnglesMethod fillAnglesMethod, Set<String> products) throws FileNotFoundException{
        this.root = Paths.get(path);
        if (!Files.exists(this.root)) {
            throw new FileNotFoundException(path);
        }
        this.products = products;
        this.method = fillAnglesMethod;
    }

    public void traverse() throws IOException {
        Files.walkFileTree(root, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE,
                new FileVisitor<Path>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                        if (dir.equals(root)) {
                            return FileVisitResult.CONTINUE;
                        }
                        if (!acceptPath(dir)) {
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                        String dirName = dir.getFileName().toString();
                        if ("AUX_DATA".equals(dirName) || "DATASTRIP".equals(dirName)) {
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
                                Logger.getRootLogger().info(String.format("%s already processed", fileName));
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

    private boolean acceptPath(Path path) {
        boolean result = true;
        if (products != null) {
            String fullPath = path.toAbsolutePath().toString();
            boolean found = false;
            for (String product : products) {
                if (fullPath.contains(product)) {
                    found = true;
                    break;
                }
            }
            result = found;
        }
        return result;
    }
}
