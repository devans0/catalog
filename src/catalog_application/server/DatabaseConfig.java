/**
 * title: Database Configuration
 * description: Wrapper that encapsulates the configuration of the database server; capable of
 * 				producing a connection to that server.
 * @author Dominic Evans
 * @date January 22, 2026
 * @version 1.0
 * @copyright 2026 Dominic Evans
 */

package catalog_application.server;

import catalog_utils.ConfigLoader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConfig {
	private static String url;
	private static String user;
	private static String pass;
	private static boolean initialized = false;

	/**
	 * Creates the static context for the database configuration. Whenever a new DatabaseConfig
	 * object is created it must be initialized using a properties file before it may be used
	 * to generate a connection to a database.
	 * 
	 * @param propFile containing the values required to create a valid database connection
	 */
	public static void initialize(String propFile) {
		ConfigLoader config = new ConfigLoader(propFile);
		url = config.getProperty("db.url");
		user = config.getProperty("db.user");
		pass = config.getProperty("db.password");
		initialized = true;
	} 
	
	/**
	 * Returns a java.sql.Conneciton to the database described by the properties file used to
	 * intialize the DatabaseConfig object.
	 * 
	 * @return java.sql.Connection object to the PostgreSQL server described by the properties file
	 * @throws SQLException in the case of the PostgreSQL driver not being found or when a connection
	 * is requested from an uninitialized DatabaseConfig object.
	 */
	public static Connection getConnection() throws SQLException {
		if (!initialized) {
			throw new SQLException("DatabaseConfig has not been initialized with a properties file.");
		}
		try {
			// Explicitly load the Driver to force an exception if it is not present; this enables
			// a more descriptive exception to be thrown
			Class.forName("org.postgresql.Driver");
			return DriverManager.getConnection(url, user, pass);
		} catch (ClassNotFoundException cnf) {
			throw new SQLException("PostgreSQL Driver not found in classpath.", cnf);
		}
	}
}
