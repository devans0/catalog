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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseConfig {
	private static String url;
	private static String user;
	private static String pass;
	private static boolean initialized = false;

	/**
	 * Creates the static context for the database configuration. Whenever a new DatabaseConfig
	 * object is created it must be initialized using a ConfigLoader generated from a properties 
	 * file before it may be used to generate a connection to a database.
	 * 
	 * @param config ConfigLoader object containing the configuration obtained from a properties file;
	 * this object must be instantiated prior to initializing the DatabaseConfig object. 
	 */
	public static void initialize(String dbUrl, String dbUser, String dbPass) {
		url = dbUrl;
		user = dbUser;
		pass = dbPass;
		
		/*
		 * Initialized must be true before database configuration or getConnection(), called from
		 * within verifyDatabase(), will throw an error assuming that a connection is being made 
		 * against an uninitialized database
		 */
		initialized = true;
		verifyDatabase();
	} // initialize
	
	/**
	 * Verifies that the database exists, is accepting connections, and contains the tables needed
	 * for the server to function properly. If a database cannot be reached, this constitutes a
	 * critical error and the server exits with error code 1.
	 */
	private static void verifyDatabase() {
		try (Connection conn = getConnection();
			 Statement stmt = conn.createStatement()) {
			
			String sql = "SELECT EXISTS (SELECT FROM information_schema.tables " +
						 "WHERE table_name = 'file_entries')";
			
			boolean tableExists = false;
			try (ResultSet rs = stmt.executeQuery(sql)) {
				if (rs.next()) {
					tableExists = rs.getBoolean(1);
				}
			}
			
			if (!tableExists) {
				System.out.println("[DB] Table 'file_entries' not found. Initializing table from setup.sql...");
				runSetupScript(conn);
				System.out.println("[DB] Database initialization complete.");
			} else {
				System.out.println("[DB] Database schema verfied.");
			}
		} catch (SQLException sqle) {
			System.err.println("[DB] Critical Database Error: " + sqle.getMessage());
			System.exit(1);
		}
	} // verifyDatabase
	
	/**
	 * verifyDatabase() helper. Runs the setup.sql script to initialize the table schema required by the server.
	 * 
	 * @param conn A connection to the database server establishes prior to method call.
	 * @throws SQLException If the setup script is not found then this method cannot complete its setup tasks.
	 */
	private static void runSetupScript(Connection conn) throws SQLException {
		try {
			String sqlInit = new String(Files.readAllBytes(Paths.get("setup.sql")));
			
			try (Statement stmt = conn.createStatement()) {
				stmt.executeUpdate(sqlInit);
			}
		} catch (IOException ioe) {
			System.err.println("[DB] Could not find setup.sql in root directory.");
			throw new SQLException("Initialization script missing.", ioe);
		}
	} // runSetupScript
	
	/**
	 * Returns a java.sql.Conneciton to the database described by the properties file used to
	 * initialize the DatabaseConfig object.
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
	} // getConnection
}
