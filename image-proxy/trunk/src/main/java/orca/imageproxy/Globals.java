package orca.imageproxy;

import java.io.File;

import java.util.Properties;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

// singleton containing logger, properties and other
// globally interesting stuff
public class Globals {
	private Logger l;
	private static final Globals i = new Globals();
	private Properties p;
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
}
