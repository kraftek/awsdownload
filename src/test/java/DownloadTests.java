import org.junit.Assert;
import org.junit.Test;
import ro.cs.products.Executor;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * @author Cosmin Cara
 */
public class DownloadTests extends TestBase {

    @Test
    public void sciHubDownloadTest() {
        Path path = getOutputFolder();
        String cmd = String.format("--out %s --tiles T35TLK --startdate 2017-07-01 --enddate 2017-07-14 --user %s --password %s --store SCIHUB",
                                   path.toString(), getSciHubUser(), getSciHubPwd());
        Path expectedFile = path.resolve("S2A_MSIL1C_20170705T090551_N0205_R050_T35TLK_20170705T090814.SAFE");
        try {
            Executor.execute(cmd.split(" "));
            Assert.assertTrue(Files.exists(expectedFile));
            Assert.assertTrue(Files.isDirectory(expectedFile));
            String[] list = expectedFile.toFile().list();
            Assert.assertTrue(list != null && list.length > 0);
        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }
    }

    public void awsSentinel2DownloadTest() {
        Path path = getOutputFolder();
        String cmd = String.format("--out %s --tiles T35TLK --startdate 2017-07-01 --enddate 2017-07-14 --store AWS --aws",
                                   path.toString());
        Path expectedFile = path.resolve("S2A_MSIL1C_20170705T090551_N0205_R050_T35TLK_20170705T090814.SAFE");
        try {
            Executor.execute(cmd.split(" "));
            Assert.assertTrue(Files.exists(expectedFile));
            Assert.assertTrue(Files.isDirectory(expectedFile));
            String[] list = expectedFile.toFile().list();
            Assert.assertTrue(list != null && list.length > 0);
        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }
    }

    public void awsLandsat8DownloadTest() {
        Path path = getOutputFolder();
        String cmd = String.format("--out %s --sensor L8 --tiles 139045 --startdate 2017-07-09 --enddate 2017-07-11 --l8col C1 --l8pt RT",
                                   path.toString());
        Path expectedFile = path.resolve("LC08_L1GT_139045_20170710_20170710_01_RT");
        try {
            Executor.execute(cmd.split(" "));
            Assert.assertTrue(Files.exists(expectedFile));
            Assert.assertTrue(Files.isDirectory(expectedFile));
            String[] list = expectedFile.toFile().list();
            Assert.assertTrue(list != null && list.length > 0);
        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }
    }

    public void sciHubSentinel2BDownloadTest() {
        Path path = getOutputFolder();
        String cmd = String.format("--out %s --tiles T35TLK --startdate 2017-07-12 --enddate 2017-07-14 --user %s --password %s --preops",
                                   path.toString(), getSciHubUser(), getSciHubPwd());
        Path expectedFile = path.resolve("S2B_MSIL1C_20170713T092029_N0205_R093_T34TGQ_20170713T092028.SAFE");
        try {
            Executor.execute(cmd.split(" "));
            Assert.assertTrue(Files.exists(expectedFile));
            Assert.assertTrue(Files.isDirectory(expectedFile));
            String[] list = expectedFile.toFile().list();
            Assert.assertTrue(list != null && list.length > 0);
        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }
    }
}
