package orca.imageproxy;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

public class SqliteDatabase extends SqliteBase{

	private static SqliteDatabase imageProxyDB;

	public synchronized static SqliteDatabase getInstance() throws ClassNotFoundException, SQLException, IOException {
		if (imageProxyDB == null)
			imageProxyDB = new SqliteDatabase();
		return imageProxyDB;
	}
	
    protected SqliteDatabase() throws ClassNotFoundException, SQLException, IOException{
    	super();
    	determineBootMode();
    	initialize();
    }

    private void determineBootMode() throws IOException {
        File superBlockFile = new File(Globals.SuperblockLocation);
        File dbFile = new File(db);
        logger.debug("Checking if this container is recovering. Looking for: " + Globals.SuperblockLocation);
        if (superBlockFile.exists() && dbFile.exists()) {
        	logger.debug("Found superblock and database file. This container is recovering");
        	resetState = false;
        } else {
        	logger.debug("Superblock/Database file does not exist. This is a fresh container");
        	resetState = true;
        	createSuperblock();
        }
    }
    
    private void createSuperblock() throws IOException {
        logger.debug("Creating superblock");
        File f = new File(Globals.SuperblockLocation);
        FileOutputStream os = new FileOutputStream(f);
        Properties p = new Properties();
        p.store(os, "This file tells the image proxy container to maintain its state on recovery. Hence, removing this file will make the container discard and reset its state.");
        os.close();
        logger.debug("Superblock created successfully");
    }
    
    public synchronized Properties checkBundleGuid(String guid, boolean mark) throws SQLException{
    	String query = "SELECT * FROM BUNDLE WHERE GUID = " + dbString(guid);
    	Connection connection = getConnection();
    	Statement statement = connection.createStatement();
    	ResultSet rs = statement.executeQuery(query);
    	try{
    		if(rs.next()){
        		Properties imageIds = new Properties();
            	imageIds.put(Globals.FILE_SYSTEM_IMAGE_KEY, rs.getString("EMI"));
            	if(rs.getString("EKI") != null){
            		imageIds.put(Globals.KERNEL_IMAGE_KEY, rs.getString("EKI"));
            	}
            	if(rs.getString("ERI") != null){
            		imageIds.put(Globals.RAMDISK_IMAGE_KEY, rs.getString("ERI"));
            	}
            	return imageIds;
            }else{
            	if(mark){
            		query = "INSERT INTO BUNDLE VALUES ( " + dbString(guid) + " , " + dbString(Globals.IMAGE_INPROGRESS) + " , NULL, NULL)";
            		this.executeUpdate(query);
            	}
            	return null;
            }
    	}finally {
    		returnConnection(connection);
    	}
    }
    
    public synchronized int updateBundleGuid(String guid, Properties imageIds) throws SQLException{
    	String query = "UPDATE BUNDLE SET EMI = " + dbString(imageIds.getProperty(Globals.FILE_SYSTEM_IMAGE_KEY)) + 
			", EKI = " + dbString(imageIds.getProperty(Globals.KERNEL_IMAGE_KEY)) +
			", ERI = " + dbString(imageIds.getProperty(Globals.RAMDISK_IMAGE_KEY)) +
			" WHERE GUID = " + dbString(guid);
    	return this.executeUpdate(query);
    }
    
    public synchronized int removeBundleGuid(String guid) throws SQLException{
    	String query = "DELETE FROM BUNDLE WHERE GUID = " + dbString(guid);
    	return this.executeUpdate(query);
    }
    
    public synchronized String checkImageGuid(String guid, String type, boolean mark) throws SQLException{
    	String query = "SELECT * FROM IMAGE WHERE GUID = " + dbString(guid);
    	Connection connection = getConnection();
    	try{
    		Statement statement = connection.createStatement();
        	ResultSet rs = statement.executeQuery(query);
        	if(rs.next()){
            	return rs.getString("IMAGE_ID");
            }else{
            	if(mark){
            		query = "INSERT INTO IMAGE VALUES ( " + dbString(guid) + " , " + dbString(Globals.IMAGE_INPROGRESS) + " )";
                	this.executeUpdate(query);
            	}
            	return null;
            }
    	}finally {
    		returnConnection(connection);
    	}
    }
    
    public synchronized int updateImageGuid(String guid, String imageId, String type) throws SQLException{
    	String query = "UPDATE IMAGE SET IMAGE_ID = " + dbString(imageId) + " WHERE GUID = " + dbString(guid);
    	return this.executeUpdate(query);
    }
    
    public synchronized int removeImageGuid(String guid, String type) throws SQLException{
    	String query = "DELETE FROM IMAGE WHERE GUID = " + dbString(guid);
    	return this.executeUpdate(query);
    }
    
    @Override
    public void resetDB(Connection connection) throws SQLException, IOException{
    	
        	Statement statement = connection.createStatement();
            statement.setQueryTimeout(30);  // set timeout to 30 sec.
            
            statement.executeUpdate("DROP TABLE IF EXISTS BUNDLE");
            statement.executeUpdate("DROP TABLE IF EXISTS IMAGE");
            statement.executeUpdate("CREATE TABLE BUNDLE (GUID STRING, EMI STRING, EKI STRING, ERI STRING)");
            statement.executeUpdate("CREATE TABLE IMAGE (GUID STRING, IMAGE_ID STRING)");
            
            createSuperblock();
            
    }
}