/**
 * title: Directory Server
 * description: Server that communicates with the database in order to create new file listings, return
 * 				data relevant to a requested file listing, and purging stale file listings.
 * @author Dominic Evans
 * @date January 22, 2026
 * @version 1.0
 * @copyright 2026 Dominic Evans
 */
package catalog_application.server;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import catalog_utils.ConfigLoader;

public class DirectoryServer {

	/**
	 * The database reaper periodically scans the database to determine if there are 
	 * any files that have not received a heartbeat signal in the last number of minutes
	 * corresponding to the period.
	 * 
	 * @param period indicates the number of minutes that the reaper should wait between invocations
	 */
	private static void startReaper(long period) {
		ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
		
		scheduler.scheduleAtFixedRate(() -> {
			String sql = "DELETE FROM file_entries WHERE last_seen < NOW() - INTERVAL '2 minutes'";
			try (Connection conn = DatabaseConfig.getConnection();
				 Statement stmt = conn.createStatement()) {
				
				int deletedRows = stmt.executeUpdate(sql);
				if (deletedRows > 0) {
					System.out.println("[REAPER] Purged " + deletedRows + " stale files.");
				}
			} catch (SQLException sqle) {
				System.err.println("[REAPER] Error during cleanup: " + sqle.getMessage());
			}
		}, 0, period, TimeUnit.MINUTES);
	}

	public static void main(String[] args) {
		DatabaseConfig.initialize("server.properties");
	}
}
