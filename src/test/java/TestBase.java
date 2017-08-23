import org.junit.After;
import org.junit.Before;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * @author Cosmin Cara
 */
public abstract class TestBase {
    private final Path outputFolder = Paths.get(System.getProperty("java.io.tmpdir"), "product_download_tests");
    private final String sciHubUser = "scitest";
    private final String sciHubPwd = "scihubtest";
    private final String preOpsUser = "s2bguest";
    private final String preOpsPwd = "s2bguest";

    @Before
    public void setUp() {
        if (!Files.exists(outputFolder)) {
            try {
                Files.createDirectory(outputFolder);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @After
    public void tearDown() throws Exception {
        try {
            Files.walkFileTree(outputFolder, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    try {
                        Files.delete(file);
                    } catch (IOException ignored) {}
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    try {
                        Files.delete(dir);
                    } catch (IOException ignored) {}
                    return super.postVisitDirectory(dir, exc);
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    Path getOutputFolder() { return outputFolder; }

    String getSciHubUser() {
        return sciHubUser;
    }

    String getSciHubPwd() {
        return sciHubPwd;
    }

    String getPreOpsUser() {
        return preOpsUser;
    }

    String getPreOpsPwd() {
        return preOpsPwd;
    }
}
