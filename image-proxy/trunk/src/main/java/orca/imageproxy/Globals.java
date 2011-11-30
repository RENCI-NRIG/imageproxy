package orca.imageproxy;

import java.io.File;
import java.io.IOException;
import java.lang.Math;
import java.util.Properties;
import java.util.Random;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

// singleton containing logger, properties and other
// globally interesting stuff
public class Globals {
	private Logger l;
	private static final Globals i = new Globals();
	private Properties p;
        private String tmpDir;
	public static final String proxySettingPropertiesFile="imageproxy-settings.properties";
	public static final String proxySettingProperties="orca.imageproxy.imageproxy-settings";
	public static final String SuperblockLocation = "db_registry_state_recovery.lock";
	public static final String DLSuperblockLocation = "db_download_state_recovery.lock";
	public static final int JDBC_OPERATION_TIMEOUT = 30;
	
	public static final String FILE_SYSTEM_IMAGE_KEY = "FILESYSTEM";
	public static final String KERNEL_IMAGE_KEY = "KERNEL";
	public static final String RAMDISK_IMAGE_KEY = "RAMDISK";
	public static final String IMAGE_INPROGRESS = "INPROGRESS";
	public static final String ERROR_CODE = "ERROR";
	
        private static final String tmpDirBaseProperty = "imageproxy.tmpDirBase";
        private static final String DEFAULT_TMPDIRBASE = "/tmp";
        private static final String TMPDIR_PREFIX = "imageproxy-";

	protected Globals() {
		// instead of getting Classloader.getSystemClassloader()
		// for Tomcat we need to try to get the app classloader
		ClassLoader loader = this.getClass().getClassLoader();

		File f = new File(proxySettingPropertiesFile);
		boolean loadAsResource = true;
		String propsLocation = proxySettingProperties;

		if (f.exists()) {
			loadAsResource = false;
			propsLocation = proxySettingPropertiesFile;
		}
		p = PropertyLoader.loadProperties(propsLocation, loader, loadAsResource);

		PropertyConfigurator.configure(p);
		l = Logger.getLogger(this.getClass());

		if (loadAsResource) l.info("Global configuration loaded from defaults.");
		else l.info("Global configuration loaded from: " + f.getPath());

                String tmpDirBase = p.getProperty(tmpDirBaseProperty);
		if (tmpDirBase == null || tmpDirBase.length() == 0)
                    tmpDirBase = System.getProperty("java.io.tmpdir");
                if (tmpDirBase == null)
                    tmpDirBase = DEFAULT_TMPDIRBASE;

                File tmpDir = null;
                Random rn = new Random();
                int failCount = 0;
                do {
                    tmpDir = new File(tmpDirBase, TMPDIR_PREFIX + Math.abs(rn.nextInt()));
                    failCount++;
                }
                while (tmpDir.exists() && failCount < 10);

                if (!tmpDir.exists() && tmpDir.mkdir()) {
                    // Set permissions to rwx by owner *only*.
                    // We do this by first clearing permissions for *everyone*,
                    // then by adding them back for the owner only (second parameter is true).
                    tmpDir.setReadable(false, false);
                    tmpDir.setReadable(true, true);
                    tmpDir.setWritable(false, false);
                    tmpDir.setWritable(true, true);
                    tmpDir.setExecutable(false, false);
                    tmpDir.setExecutable(true, true);
                    l.info("Temp directory located in: " + tmpDir);
                }
                else {
                    l.error("Creation of unique temp directory failed; " +
                            "falling back to using " + tmpDirBase);
                    tmpDir = new File(tmpDirBase);
                }

                this.tmpDir = tmpDir.toString();
	}
	
	public static Globals getInstance() {
		return i;
	}
	
	public Properties getProperties() {
		return getInstance().p;
	}
	
	public Logger getLogger() {
		return getInstance().l;
	}

        public String getTmpDir() {
                return getInstance().tmpDir;
        }
}
