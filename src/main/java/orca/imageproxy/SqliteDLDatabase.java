package orca.imageproxy;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class SqliteDLDatabase extends SqliteBase{

	public final static String filestable = "FILE";
	public final static String moststaleview = "MOSTSTALEVIEW";
	
	private static SqliteDLDatabase dldatabase;
	
	protected SqliteDLDatabase() throws Exception {
		super();
		determineBootMode();
		initialize();
	}
	
	public synchronized static SqliteDLDatabase getInstance() throws Exception{
		if(dldatabase==null)
			dldatabase=new SqliteDLDatabase();
		return dldatabase;
	}
	
    private void determineBootMode() throws IOException {
        File file = new File(Globals.DLSuperblockLocation);
        logger.debug("Checking if this container is recovering. Looking for: " + Globals.DLSuperblockLocation);
        if (file.exists()) {
        	logger.debug("Found download superblock file. This container is recovering");
        	resetState = false;
        } else {
        	logger.debug("Download superblock file does not exist. This is a fresh container");
        	resetState = true;
        }
    }
    
    private void createSuperblock() throws IOException {
        logger.debug("Creating download superblock");
        File f = new File(Globals.DLSuperblockLocation);
        FileOutputStream os = new FileOutputStream(f);
        Properties p = new Properties();
        p.store(os, "This file tells the image proxy container to maintain its state on recovery. " +
        		"Hence, removing this file will make the container discard and reset its state.");
        os.close();
        logger.debug("Download superblock created successfully");
    }

    /**
     * Function to check if an entry for a given signature already exists. 
     * Also, in case mark = true, creates a new entry if one does not exist.
     * @param signature
     * @param mark
     * @return location of the file, if the file exists
     * @return null, if the file does not exist
     * @throws SQLException
     */
    public synchronized String checkDownloadList(String signature, boolean mark, String url, String downloadType) throws SQLException{
		String query = "SELECT * FROM " + filestable + " WHERE SIGNATURE = " + dbString(signature);
		Connection connection = getConnection();
		try {
			Statement statement = connection.createStatement();
			try {
				statement.setQueryTimeout(Globals.JDBC_OPERATION_TIMEOUT);
				ResultSet rs = statement.executeQuery(query);
				try {
					if(rs.next()) {
						String path = rs.getString("FILEPATH");
						if(mark){
							int newRef = rs.getInt("REF") + 1;
							int newActiveRef = rs.getInt("ACTIVEREF") + 1;
							query = "UPDATE " + filestable + " SET REF = " + newRef + ", ACTIVEREF = " + newActiveRef + ", LASTREF = " + System.currentTimeMillis() + " WHERE SIGNATURE = " + dbString(signature);
							statement.executeUpdate(query);
						}
						return path;
					}else{
						if(mark) {
							query = "INSERT INTO " + filestable + " VALUES (" + 
							dbString(signature) + ", " + dbString(Globals.IMAGE_INPROGRESS) + ", 0, 1, 1, 0, 0, " + System.currentTimeMillis() + ", " + dbString(downloadType) + ", " + dbString(url) + ", null)";
							//SIGNATURE, FILEPATH, FILESIZE, REF, ACTIVEREF, SEEDING, STATUS, LASTREF, DOWNLOADTYPE, URL, TORRENTFILEPATH
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
    
    public synchronized void updateFileSize(String signature, long fileSize) throws SQLException{
		String query = "UPDATE " + filestable + " SET FILESIZE = " + fileSize + " WHERE SIGNATURE = " + dbString(signature);
		executeUpdate(query);
	}
    
    public synchronized void updateFilePath(String signature, String path) throws SQLException{
		String query = "UPDATE " + filestable + " SET FILEPATH = " + dbString(path) + " WHERE SIGNATURE = " + dbString(signature);
		executeUpdate(query);
	}
    
    public synchronized void updateDownloadStatus(String signature, int status) throws SQLException{
		String query = "UPDATE " + filestable + " SET STATUS = " + status + " WHERE SIGNATURE = " + dbString(signature);
		executeUpdate(query);
	}
    
    public synchronized void updateTorrentFilePath(String signature, String torrentFilePath) throws SQLException{
		String query = "UPDATE " + filestable + " SET TORRENTFILEPATH = " + dbString(torrentFilePath) + " WHERE SIGNATURE = " + dbString(signature);
		executeUpdate(query);
	}
    
    /**
	 * Delete entry for the given signature
	 * @param signature
	 * @throws SQLException
	 */
	public synchronized void deleteEntry(String signature) throws SQLException{
		String query = "DELETE FROM " + filestable + " WHERE SIGNATURE = " + dbString(signature);
		executeUpdate(query);
	}
	
	/**
     * Calculate the size of existing data
     * @param size
     * @return
     * @throws SQLException
     */
    public synchronized long getExistingDataSize() throws SQLException
    {
    	long result = 0;
    	
    	String query = "SELECT SUM(FILESIZE) FROM " + filestable;

    	Connection connection = getConnection();
		try {
			Statement statement = connection.createStatement();
			try {
				statement.setQueryTimeout(Globals.JDBC_OPERATION_TIMEOUT);
				ResultSet rs = statement.executeQuery(query);
				try {
					while(rs.next())
					{
						result += rs.getLong(1);
					}
				}
				finally { rs.close(); }
			}
			finally { statement.close(); }
		}
		finally { connection.close(); }
	
		return result;
    }
    
    /**
     * Finds if an entry with the given signature exists
     * @param fileSignature
     * @return
     * @throws SQLException
     */
	public synchronized boolean isExisting(String fileSignature) throws SQLException
    {
    	String query="SELECT * FROM " + filestable + " WHERE SIGNATURE = " + dbString(fileSignature);
    	Connection connection = getConnection();
		try {
			Statement statement = connection.createStatement();
			try {
				statement.setQueryTimeout(Globals.JDBC_OPERATION_TIMEOUT);
				ResultSet rs = statement.executeQuery(query);
				try {
					if(!rs.next()) return false;
					return true;
				}
				finally { rs.close(); }
			}
			finally { statement.close(); }
		}
		finally { connection.close(); }
    }
   
	/**
	 * This function is called in case space needs to be freed up.
	 * @return the entry corresponding to the file that should be deleted.
	 * @throws SQLException
	 */
	public synchronized Entry getMostStaleEntry() throws SQLException{
		String query = "SELECT * FROM " + moststaleview;

		Connection connection = getConnection();
		try {
			Statement statement = connection.createStatement();
			try {
				statement.setQueryTimeout(Globals.JDBC_OPERATION_TIMEOUT);
				ResultSet rs = statement.executeQuery(query);
				try {
					if (rs.next())
					{
						Entry e = new Entry();
						e.setFilePath(rs.getString("FILEPATH"));
						e.setFilesize(rs.getLong("FILESIZE"));
						e.setSignature(rs.getString("SIGNATURE"));
						e.setDownloadType(rs.getString("DOWNLOADTYPE"));
						e.setTorrentFilePath(rs.getString("TORRENTFILEPATH"));
						return e;
					}
					return null;
				}
				finally { rs.close(); }
			}
			finally { statement.close(); }
		}
		finally { connection.close(); }
	}
	
	/**
	 * Fetches the list of files in downloading status
	 * @return
	 * @throws Exception
	 */
	public synchronized List<Entry> getDownloadingFiles() throws Exception{
		
		List<Entry> result = new ArrayList<Entry>();
		
		String query="SELECT * FROM " + filestable + " WHERE STATUS = 0";

		Connection connection = getConnection();
		try {
			Statement statement = connection.createStatement();
			try {
				statement.setQueryTimeout(Globals.JDBC_OPERATION_TIMEOUT);
				ResultSet rs = statement.executeQuery(query);
				try {
					while(rs.next())
					{
						Entry e = new Entry();
						e.setFilePath(rs.getString("FILEPATH"));
						e.setFilesize(rs.getLong("FILESIZE"));
						e.setSignature(rs.getString("SIGNATURE"));
						e.setDownloadType(rs.getString("DOWNLOADTYPE"));
						e.setTorrentFilePath(rs.getString("TORRENTFILEPATH"));
						result.add(e);
					}
				}
				finally { rs.close(); }
			}
			finally { statement.close(); }
		}
		finally { connection.close(); }

		return result;
	}
	
	/**
	 * Reduces the active reference count by one, for the entry with the given signature
	 * @param signature
	 * @throws SQLException
	 */
	public synchronized void removeReference(String signature) throws SQLException
	{
		String query = "SELECT * FROM " + filestable + " WHERE SIGNATURE = " + dbString(signature);
		Connection connection = getConnection();
		try {
			connection.setAutoCommit(false);
			Statement statement = connection.createStatement();
			try {
				statement.setQueryTimeout(Globals.JDBC_OPERATION_TIMEOUT);
				ResultSet rs = statement.executeQuery(query);
				try {
					if(rs.next()) {
						int newActiveRef = rs.getInt("ACTIVEREF") - 1;
						query = "UPDATE " + filestable + " SET ACTIVEREF = " + newActiveRef + " WHERE SIGNATURE = " + dbString(signature);
						statement.executeUpdate(query);
					}
				}
				finally { rs.close(); }
			}
			finally { statement.close(); }
		}
		finally {
			connection.commit();
			connection.setAutoCommit(true);
			connection.close();
		}
	}
	
	@Override
    public void resetDB() throws SQLException, IOException{
		Connection connection = getConnection();
		try {
			connection.setAutoCommit(false);
			Statement statement = connection.createStatement();
			try {
				statement.setQueryTimeout(Globals.JDBC_OPERATION_TIMEOUT);
				statement.executeUpdate("DROP TABLE IF EXISTS " + filestable);
				statement.executeUpdate("DROP VIEW IF EXISTS " + moststaleview);
				statement.executeUpdate("CREATE TABLE " + filestable +" " +
						"(SIGNATURE STRING, FILEPATH STRING, FILESIZE UNSIGNED BIG INT, REF UNSIGNED INT, ACTIVEREF UNSIGNED INT, SEEDING SMALLINT,  STATUS SMALLINT, LASTREF BIG INT, DOWNLOADTYPE STRING, URL STRING, TORRENTFILEPATH STRING, PRIMARY KEY(SIGNATURE))");      
				statement.executeUpdate("CREATE VIEW " + moststaleview + " AS SELECT * FROM " + filestable + " WHERE STATUS = 1 AND ACTIVEREF = 0 ORDER BY LASTREF");
	            
				createSuperblock();
			}
			finally { statement.close(); }
		}
		finally {
			connection.commit();
			connection.setAutoCommit(true);
			connection.close();
		}
    }
}
