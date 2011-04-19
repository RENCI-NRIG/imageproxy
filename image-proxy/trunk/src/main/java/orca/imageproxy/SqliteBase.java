package orca.imageproxy;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

public class SqliteBase {

    // configuration properties
    public static final String PropertyImageProxyDb = "db.imageproxy.db";

    protected boolean initialized = false;
    protected boolean resetState = false;
    
    /**
     * The logging tool.
     */
    protected Logger logger;
    
    /**
     * source URL
     */
    protected final String source = "jdbc:sqlite:";
    
    /**
     * The name of the database. Can be anything.
     */
    protected String db = "imageproxy.db";
    
    /**
     * JDBC drivers we are using.
     */
    protected final String driverPath = "org.sqlite.JDBC";

    /**
     * 
     */
    public static final String proxySettingProperties="orca.imageproxy.imageproxy-settings";
    
    
    /**
     * Creates a new instance.
     * @throws SQLException 
     * @throws ClassNotFoundException 
     */
    protected SqliteBase() throws ClassNotFoundException, SQLException
    {
    	ClassLoader loader = this.getClass().getClassLoader();
		Properties p = PropertyLoader.loadProperties(proxySettingProperties, loader);
		PropertyConfigurator.configure(p);
		logger = Logger.getLogger(this.getClass().getCanonicalName());
		
		this.configure(p);
		
		//this.initialize();
    }

    /**
     * Configures the database from the specified properties list.
     * @param p
     * @throws Exception
     */
    public void configure(Properties p)
    {
        db = p.getProperty(PropertyImageProxyDb);
        
        if (db == null) {
            db = "imageproxy.db";
        }
    }

    /**
     * Get a connection from the pool.
     * @return connection
     * @throws SQLException
     */
    public Connection getConnection() throws SQLException
    {
        return DriverManager.getConnection(source + db);
    }

    /**
     * Return the connection to the pool
     * @param connection
     * @throws SQLException
     */
    public void returnConnection(Connection connection) throws SQLException
    {
        connection.close();
    }
    
    public void initialize() throws ClassNotFoundException, SQLException, IOException
    {
        if (!initialized) {
            
            loadDrivers();

            checkDb();
            
            initialized = true;
        }
    }

    /**
     * Load the JDBC driver.
     * @throws ClassNotFoundException 
     */
    protected void loadDrivers() throws ClassNotFoundException
    {
    	// load the sqlite-JDBC driver using the current class loader
    	try {
			Class.forName(driverPath);
		} catch (ClassNotFoundException classNotFoundException) {
			logger.error("Could not find sqlite-JDBC driver " + driverPath);
			throw classNotFoundException;
		}
    }

    /**
     * Checks the database and resets it if needed.
     * @throws Exception
     */
    protected void checkDb() throws SQLException, IOException
    {
        Connection connection = getConnection();
        try {
            if (resetState) {
                resetDB(connection);
            }
        } finally {
            returnConnection(connection);
        }
    }
    
    protected int executeUpdate(String query) throws SQLException{
    	Connection connection = getConnection();
    	Statement statement;
    	try{
    		statement = connection.createStatement();
    		return statement.executeUpdate(query);
    	}finally {
    		returnConnection(connection);
    	}
    }
    
    protected int executeUpdate(String query, Connection connection) throws SQLException{
    	Statement statement;
    	statement = connection.createStatement();
    	return statement.executeUpdate(query);
    }
    
    /**
     * Resets the database to a clean state.
     * @param connection
     * @throws SQLException
     */
    protected void resetDB(Connection connection) throws SQLException, IOException
    {
    }
    
    public String getDb()
    {
        return db;
    }

    public Logger getLogger()
    {
        return logger;
    }

    /**
     * Helper function to construct a query. Constructs a query of the form
     * key1='value1', key2='value2' etc from the Properties list it is passed
     * @param p Properties list
     * @return query string
     */
    public static String constructQueryPartial(Properties p)
    {
        StringBuffer query = new StringBuffer("");
        Iterator<?> i = p.entrySet().iterator();

        while (i.hasNext()) {
            if (query.length() > 0) {
                query.append(", ");
            }

            Map.Entry<?, ?> entry = (Map.Entry<?, ?>) i.next();
            String name = (String) entry.getKey();
            String value = (String) entry.getValue();
            query.append(name + "='" + value + "'");
        }

        return query.toString();
    }
    
    public static String dbString(String value)
    {
    	if(value == null) return null;
        return "'" + value + "'";
    }
}
