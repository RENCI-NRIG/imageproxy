package orca.imageproxy;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import java.util.concurrent.locks.ReentrantLock;

public class SqliteDLDatabase extends SqliteBase{

	public final static String imagestable="FILE";
	
	public final static String moststaleview="MOSTSTALEVIEW";

	private static final ReentrantLock lock = new ReentrantLock();
	
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

    public void lock() {
	lock.lock();
    }

    public void unlock() {
	lock.unlock();
    }
    
    public synchronized boolean clearDownloadingImages() throws Exception{
		String query="SELECT * FROM "+imagestable+" WHERE STATUS=0";
		boolean flag=false;

		Connection connection = getConnection();
		try {
			Statement statement = connection.createStatement();
			try {
				statement.setQueryTimeout(Globals.JDBC_OPERATION_TIMEOUT);
				ResultSet rs = statement.executeQuery(query);
				try {
					while(rs.next())
					{
						String guid=rs.getString("GUID");
						String path=rs.getString("FILEPATH");
						if(!path.toLowerCase().startsWith("http://"))
							BTDownload.deleteDownloadingImage(guid, path);
						flag=true;
					}
					deleteDownloadingEntries(connection);
					BTDownload.deleteIncompleteImages();
				}
				finally { rs.close(); }
			}
			finally { statement.close(); }
		}
		finally { connection.close(); }

		return flag;
	}
    
    public synchronized boolean isExisted(String image_guid) throws SQLException
    {
    	String query="SELECT * FROM "+imagestable+" WHERE GUID="+dbString(image_guid);

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
    
    public synchronized long getExistingDataSize(long size) throws SQLException
    {
    	String query = "SELECT SUM(FILESIZE) FROM "+imagestable;

    	Connection connection = getConnection();
	try {
		Statement statement = connection.createStatement();
		try {
			statement.setQueryTimeout(Globals.JDBC_OPERATION_TIMEOUT);
			ResultSet rs = statement.executeQuery(query);
			try {
				while(rs.next())
				{
					size+=rs.getLong(1);
				}
			}
			finally { rs.close(); }
		}
		finally { statement.close(); }
	}
	finally { connection.close(); }

	return size;
    }
    
	public synchronized int checkDownloadList(String hashcode) throws SQLException{
		String query = "SELECT * FROM "+imagestable;
		int flag=0;

		Connection connection = getConnection();
		try {
			Statement statement = connection.createStatement();
			try {
				statement.setQueryTimeout(Globals.JDBC_OPERATION_TIMEOUT);
				ResultSet rs = statement.executeQuery(query);
				try {
					while(rs.next()) {
						int newRef=rs.getShort("REF")+1;
						String guid=rs.getString("GUID");
						int status=rs.getShort("STATUS");

						if(guid.equals(hashcode)){
							if(status==0) flag = -1; //downloading
							else if(status==1) flag = 1; //downloaded
						}
					}
				}
				finally { rs.close(); }
			}
			finally { statement.close(); }
		}
		finally { connection.close(); }

		return flag;
	}
	
	public synchronized Entry getMostStaleEntry() throws SQLException{
		String query = "SELECT * FROM "+moststaleview;

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
						e.setHashcode(rs.getString("GUID"));
						e.setReference(rs.getShort("REF"));
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
	
	private synchronized void updateRefNum(short newRef, String guid, Statement stmt)
	throws SQLException{
		String query = "UPDATE "+imagestable+" SET REF="+newRef+" WHERE GUID="+dbString(guid);
		stmt.executeUpdate(query);
	}
	
	public synchronized void deleteEntry(String guid) throws SQLException
	{
		String query = "DELETE FROM "+imagestable+" WHERE GUID="+dbString(guid);
		executeUpdate(query);
	}
	
	public synchronized void insertEntry(Entry e) throws SQLException, NullPointerException{
		if (e == null)
			throw new NullPointerException("a valid entry should be inserted into the database");

		String query = "INSERT INTO "+imagestable+" VALUES ("+dbString(e.getHashcode())+", "+0+", "+
					e.getFilesize()+", "+e.getReference()+", "+dbString(e.getFilePath())+", null, 0)";

		executeUpdate(query);
	}
	
	public synchronized boolean updateDownloadStatus(Entry entry, BTDownload.Type type) throws SQLException
	{
		String query=null;

		switch (type) {
			case HTTP:
				query="UPDATE "+imagestable+" SET STATUS=1, UNBUNDLING=1, FILESIZE="+entry.getFilesize()+
					", FILEPATH="+dbString(entry.getFilePath())+
					" WHERE GUID="+dbString(entry.getHashcode())+" AND STATUS=0";
				break;
			case BT:
				query="UPDATE "+imagestable+" SET STATUS=1, UNBUNDLING=1, FILESIZE="+entry.getFilesize()+
					", FILEPATH="+dbString(entry.getFilePath())+
					", SEEDING=0"+" WHERE GUID="+dbString(entry.getHashcode())+" AND STATUS=0";
				break;
		}

		int count = executeUpdate(query);

                return (!(count == 0));
	}
	
	public synchronized void updateDownloadStatus(Entry entry, String correctGUID, BTDownload.Type type) throws SQLException
	{
		String query = "DELETE FROM "+imagestable+" WHERE GUID="+dbString(entry.getHashcode());
		executeUpdate(query);

		query = "SELECT * FROM "+imagestable+" WHERE GUID="+dbString(correctGUID);

		Connection connection = getConnection();
		try {
			connection.setAutoCommit(false);
			Statement statement=connection.createStatement();
			try {
				statement.setQueryTimeout(Globals.JDBC_OPERATION_TIMEOUT);
				ResultSet rs = statement.executeQuery(query);
				try {
					if(!rs.next())
					{
						switch (type) {
							case HTTP:
								query="INSERT INTO "+imagestable+" VALUES ("+dbString(correctGUID)+
									", 1, "+entry.getFilesize()+", 0, "+dbString(entry.getFilePath())+
									", null, 1)";
								break;
							case BT:
								query="INSERT INTO "+imagestable+" VALUES ("+dbString(correctGUID)+
									", 1, "+entry.getFilesize()+", 0, "+dbString(entry.getFilePath())+
									", 0, 1)";
								break;
						}
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
	
	public synchronized void updateRegistrationStatus(String guid) throws SQLException
	{
		String query="UPDATE "+imagestable+" SET UNBUNDLING=0 WHERE GUID="+dbString(guid);
		executeUpdate(query);
	}
	
	public synchronized void deleteDownloadingEntries(Connection connection) throws SQLException{
		String query = "DELETE FROM "+imagestable+" WHERE STATUS=0";
		executeUpdate(query);
	}

	@Override
    public void resetDB() throws SQLException, IOException{
	Connection connection = getConnection();
	try {
		connection.setAutoCommit(false);
		Statement statement = connection.createStatement();
		try {
			statement.setQueryTimeout(Globals.JDBC_OPERATION_TIMEOUT);
			statement.executeUpdate("DROP TABLE IF EXISTS "+imagestable);
			statement.executeUpdate("DROP VIEW IF EXISTS "+moststaleview);
			statement.executeUpdate("CREATE TABLE "+imagestable+" " +
					"(GUID STRING, STATUS SMALLINT, FILESIZE UNSIGNED BIG INT, REF SMALLINT, FILEPATH STRING, SEEDING SMALLINT, UNBUNDLING SMALLINT, PRIMARY KEY(GUID))");    
			statement.executeUpdate("CREATE VIEW "+moststaleview+" AS SELECT * FROM "+imagestable+" WHERE FILESIZE " +
					"<=(SELECT FILESIZE FROM "+imagestable+" GROUP BY GUID HAVING REF=MAX(REF) AND STATUS=1 AND UNBUNDLING=0)");
            
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
