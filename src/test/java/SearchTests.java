import org.junit.Assert;
import org.junit.Test;
import ro.cs.products.Executor;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * @author Cosmin Cara
 */
public class SearchTests extends TestBase {

    @Test
    public void sciHubSearchTest() {
        Path path = getOutputFolder();
        String cmd = String.format("--out %s --tiles T35TLK --startdate 2017-07-01 --enddate 2017-07-14 --user %s --password %s --query",
                                   path.toString(), getSciHubUser(), getSciHubPwd());
        Path expectedFile = path.resolve("results.txt");
        try {
            Executor.execute(cmd.split(" "));
            Assert.assertTrue(Files.exists(expectedFile));
        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }
    }

    @Test
    public void awsSentinel2SearchTest() {
        Path path = getOutputFolder();
        String cmd = String.format("--out %s --tiles T35TLK --startdate 2017-07-01 --enddate 2017-07-14 --aws --user %s --password %s --query",
                                   path.toString(), getSciHubUser(), getSciHubPwd());
        Path expectedFile = path.resolve("results.txt");
        try {
            Executor.execute(cmd.split(" "));
            Assert.assertTrue(Files.exists(expectedFile));
        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }
    }

    @Test
    public void awsLandsat8SearchTest() {
        Path path = getOutputFolder();
        String cmd = String.format("--out %s --sensor L8 --tiles 139045 --startdate 2017-07-09 --enddate 2017-07-11 --l8col C1 --l8pt RT --query",
                                   path.toString());
        Path expectedFile = path.resolve("results.txt");
        try {
            Executor.execute(cmd.split(" "));
            Assert.assertTrue(Files.exists(expectedFile));
            Assert.assertTrue(Files.lines(expectedFile).anyMatch("LC08_L1GT_139045_20170710_20170710_01_RT"::equals));
        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }
    }

}
