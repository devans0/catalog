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

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.Properties;
import java.util.TimeZone;

import org.omg.CORBA.ORB;
import org.omg.CosNaming.NameComponent;
import org.omg.CosNaming.NamingContextExt;
import org.omg.CosNaming.NamingContextExtHelper;
import org.omg.PortableServer.POA;
import org.omg.PortableServer.POAHelper;

import catalog_api.FileTracker;
import catalog_api.FileTrackerHelper;
import catalog_utils.ConfigLoader;

public class CatalogServer {

	public static void main(String[] args) {
		// Load the configuration and initialize the DatabaseConfig
		ConfigLoader config = new ConfigLoader("server.properties");

		// Set time zone; this avoids any issues arising from the reaper and the system
		// times being at odds with one another potentially causing premature reaping
		String timezone = config.getProperty("app.timezone", "UTC");
		TimeZone.setDefault(TimeZone.getTimeZone(timezone));
		
		// Detect OS and start orbd automatically; this process will be null
		// if there is already an instance of orbd running on the system
		Process orbdProcess = startNamingService(config);
		
		/*
		 * Create a shutdown hook if the server is configured to kill orbd on close and there is
		 * no existing orbd process (indicated by null above).
	     *	
		 * Note that orbdProcess == null indicates that some orbd process is alive via startNamingService()
		 * so that this check only needs to additionally ensure that the server is not configured to
		 * shutdown the ORB on exit.
		 * 
		 * There is another case in which the orbd simply failed to initialize, in which case orbdProcess == null
		 * but that cannot be detected definitively at this stage. Therefore, we rely on an error during
		 * ORB initialization to indicate this case. If this occurs, a [SYS] warning will print indicating
		 * that starting orbd failed in order to give a clue for debugging.
		 */
		if(config.getBooleanProperty("app.destroy_orb_on_shutdown", false) && orbdProcess != null) {
			Runtime.getRuntime().addShutdownHook(new Thread(() -> {
				System.out.println("[SYS] Catalog Server shutting down...");
				orbdProcess.destroy();
				System.out.println("[SYS] Cleanup complete.");
			}));
		}
		
		// Log the result of creating the orbd process to aid in troubleshooting
		if (orbdProcess == null) {
			System.out.println("[SYS] Using existing Naming Service or external daemon.");
		} else {
			System.out.println("[SYS] Local orbd instance started successfully.");
		}
		
		// Initialize the database configuration
		DatabaseConfig.initialize(config);

		// Configure the ORB
		Properties orbProps = new Properties();
		// Properties are pulled from the server.properties file or a default value is used
		orbProps.put("org.omg.CORBA.ORBInitialPort", config.getProperty("server.port", "1050"));
		orbProps.put("org.omg.CORBA.ORBInitialHost", config.getProperty("server.host", "localhost"));

		try {
			// Initialize the ORB
			ORB orb = ORB.init(args, orbProps);

			// Initialize the POA and activate it
			POA rootPOA = POAHelper.narrow(orb.resolve_initial_references("RootPOA"));
			rootPOA.the_POAManager().activate();
			
			// Create the servant and get object reference from it 
			FileTrackerImpl trackerImpl = new FileTrackerImpl();
			org.omg.CORBA.Object ref = rootPOA.servant_to_reference(trackerImpl);			
			FileTracker href = FileTrackerHelper.narrow(ref);
			
			// Create naming service context
			org.omg.CORBA.Object objRef = orb.resolve_initial_references("NameService");
			NamingContextExt ncRef = NamingContextExtHelper.narrow(objRef);
			
			// Bind the object reference to the naming service
			String name = "CatalogService";
			NameComponent path[] = ncRef.to_name(name);
			ncRef.rebind(path, href);
			System.out.println("Catalog Server registered as '" + name + "'");
			
			// Activate the database reaper and start the ORB
			startReaper(config.getIntProperty("server.reaper_interval_minutes"));
			orb.run();
		} catch (Exception e) {
			System.err.println("ERROR: " + e.getMessage());
			e.printStackTrace();
		}
	}

	/**
	 * Starts the orbd naming service that will handle connections to the server from remote clients
	 * 
	 * @param config ConfigLoader object generated from a properties file used to configure the server.
	 */
	private static Process startNamingService(ConfigLoader config) {
		try {
			String port = config.getProperty("server.port", "1050");
			ProcessBuilder pb = new ProcessBuilder("orbd", "-ORBInitialPort", port);

			// Ensure that errors or messages are printed on the server console
			pb.inheritIO();
			Process orbdProcess = pb.start();
			
			System.out.println("[SYS] Naming service (orbd) starting on port " + port);
			
			// Give orbd time to bind to a socket before server initialization proceeds
			Thread.sleep(2000);
			
			// It may be the case that orbd is already running from a prior server process that did not close it
			if (!orbdProcess.isAlive() && orbdProcess.exitValue() != 0) {
				System.err.println("[SYS] orbd failed to start. Check if port " + port + " is available.");
				return null;
			}
			
			return orbdProcess;
		} catch (Exception e) {
			System.err.println("[SYS] Warning: could not start orbd automatically. "
							   + "It may already be running or not in your PATH.");
		}
		// Process creation failed
		return null;
	}

	/**
	 * The database reaper periodically scans the database to determine if there are 
	 * any files that have not received a heartbeat signal in the last number of minutes
	 * corresponding to the period.
	 * 
	 * @param period indicates the number of minutes that the reaper should wait between invocations
	 */
	private static void startReaper(long period) {
		// Create a reaper daemon thread
		ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(reaper -> {
			Thread t = new Thread(reaper);
			t.setDaemon(true);
			t.setName("DatabaseReaper-Thread");
			return t;
		});
		
		// Execute the reaper every period minutes
		scheduler.scheduleAtFixedRate(() -> {
			String sql = "DELETE FROM file_entries WHERE last_seen < NOW() - INTERVAL '" + period + " minutes'";
			try (Connection conn = DatabaseConfig.getConnection();
				 Statement stmt = conn.createStatement()) {
				
				int deletedRows = stmt.executeUpdate(sql);
				if (deletedRows > 0) {
					System.out.println("[REAPER] Purged " + deletedRows + " stale file(s).");
				} 
			} catch (SQLException sqle) {
				System.err.println("[REAPER] Error during cleanup: " + sqle.getMessage());
			}
		}, 1, period, TimeUnit.MINUTES);
	}
}


