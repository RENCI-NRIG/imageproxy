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
	if (!resetState) cleanupInProgressImages();
    }

    private void determineBootMode() throws IOException {
        File superBlockFile = new File(Globals.SuperblockLocation);
        File dbFile = new File(db);
        logger.debug("Checking if this container is recovering. Looking for: " +
			Globals.SuperblockLocation);
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

    private synchronized void cleanupInProgressImages() throws SQLException {
        logger.debug("Removing image IDs left in \"IN PROGRESS\" state at shutdown.");
    	String query = "DELETE FROM IMAGE WHERE IMAGE_ID = " + dbString(Globals.IMAGE_INPROGRESS);
    	executeUpdate(query);
    }
    
    public synchronized String checkImageSignature(String signature, String type, boolean mark) throws SQLException{
    	String query = "SELECT * FROM IMAGE WHERE SIGNATURE = " + dbString(signature);

    	Connection connection = getConnection();
		try {
			Statement statement = connection.createStatement();
			try {
				statement.setQueryTimeout(Globals.JDBC_OPERATION_TIMEOUT);
				ResultSet rs = statement.executeQuery(query);
				try {
					if(rs.next()) {
						return rs.getString("IMAGE_ID");
					} else {
						if(mark) {
							query = "INSERT INTO IMAGE VALUES ( " + dbString(signature) + " , " + dbString(Globals.IMAGE_INPROGRESS) + " )";
							statement.executeUpdate(query);
						}
						return null;
					}
				}
				finally { rs.close(); }
			}
			finally { statement.close(); }
		}
		finally { connection.close(); }
    }
    
    public synchronized int updateImageInfo(String signature, String imageId, String type) throws SQLException{
    	String query = "UPDATE IMAGE SET IMAGE_ID = " + dbString(imageId) + " WHERE SIGNATURE = " + dbString(signature);
    	return executeUpdate(query);
    }
    
    public synchronized int removeImageInfo(String signature, String type) throws SQLException{
    	String query = "DELETE FROM IMAGE WHERE SIGNATURE = " + dbString(signature);
    	return executeUpdate(query);
    }
    
    @Override
    public void resetDB() throws SQLException, IOException{
	Connection connection = getConnection();
	try {
		Statement statement = connection.createStatement();
		try {
			statement.setQueryTimeout(Globals.JDBC_OPERATION_TIMEOUT);
			statement.executeUpdate("DROP TABLE IF EXISTS IMAGE");
			statement.executeUpdate("CREATE TABLE IMAGE (SIGNATURE STRING, IMAGE_ID STRING)");

			createSuperblock();
		}
		finally { statement.close(); }
	}
	finally { connection.close(); }
    }
}
