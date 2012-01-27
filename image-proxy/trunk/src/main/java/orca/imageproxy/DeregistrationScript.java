package orca.imageproxy;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Properties;

import org.apache.log4j.Logger;

public class DeregistrationScript {
    protected static final String deregisterScriptProperty = "deregisterScriptProperty";
    protected static final String DEFAULT_DEREGISTER_SCRIPT = "scripts/deregister.sh";

    protected Logger l;
    protected SqliteDatabase db;

    public DeregistrationScript() throws Exception {
        try {
            db = SqliteDatabase.getInstance();
            l = Logger.getLogger(this.getClass());
            Properties p = Globals.getInstance().getProperties();
        }
        catch (Exception exception){
            l.error("Exception during initialization.");
            l.error(exception.toString(), exception);
            throw exception;
        }
    }

    /**
     * GC an image from Eucalyptus/OpenStack
     * @param signature
     * @return true if success, false if failed
     * @throws SQLException
     * @throws IOException 
     * @throws InterruptedException 
     */
    public boolean deregister(String signature)
        throws IOException, InterruptedException, SQLException {

        l.info("Deregistering image.");
        l.info("Image signature: " + signature);
		
        String deregisterScript = Globals.getInstance().getProperties().getProperty(deregisterScriptProperty);
        if (deregisterScript == null || deregisterScript.length() == 0)
            deregisterScript = DEFAULT_DEREGISTER_SCRIPT;
        
        // Type isn't needed. We'll fake it.
        String imageId = db.checkImageSignature(signature, "foo", false);

        if (imageId == null) {
            // Image doesn't exist in the database; already deleted?
            l.error("Deletion attempt for image with signature " + signature +
                " failed, because image was not found in database.");
            return false;
        }

        // deregistration script
        String command =
            BTDownload.imageproxyHome + File.separator + deregisterScript + " " + imageId;
		
        l.info("Invoking deregistration script");
        l.debug(command);
		
        // invoking registration script
        Process process = Runtime.getRuntime().exec(command);
		
        // checking if script ran successfully
        if (process.waitFor() != 0) {
            l.error("Error encountered while attempting to deregister image with ID " +
                imageId + " and signature " + signature);
            return false;
        }

        // Again, fake the type, since it isn't needed.
        db.removeImageInfo(signature, "foo");

        l.info("Image deregistered successfully.");

        return true;
    }
}
