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

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import java.util.TimeZone;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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

	// Default values
	private static final String DEFAULT_SERVER_PORT = "1050";
	private static final String DEFAULT_SERVER_HOST = "localhost";
	private static final String DEFAULT_DB_URL = "jdbc:postgresql://localhost:5432/catalog_db";
	private static final String DEFAULT_DB_USER = "catalog_server";
	private static final String DEFAULT_DB_PASS = "";

	private static String serverPort;
	private static String serverHost;
	private static String dbURL;
	private static String dbUser;
	private static String dbPassword;

	public static void main(String[] args) {
		// Load the configuration and extract configured values
		ConfigLoader config = new ConfigLoader("server.properties");
		serverPort = config.getProperty("server.port", DEFAULT_SERVER_PORT);
		dbURL = config.getProperty("db.url", DEFAULT_DB_URL);
		dbUser = config.getProperty("db.user", DEFAULT_DB_USER);
		dbPassword = config.getProperty("db.password", DEFAULT_DB_PASS);
		
		// Find the local IPv4 host address for the server
		serverHost = findLocalIP();

		// Set time zone; this avoids any issues arising from the reaper and the system
		// times being at odds with one another potentially causing premature reaping
		String timezone = config.getProperty("app.timezone", "UTC");
		TimeZone.setDefault(TimeZone.getTimeZone(timezone));

		// Detect OS and start orbd automatically; this process will be null
		// if there is already an instance of orbd running on the system
		Process orbdProcess = startNamingService(serverPort);

		/*
		 * Create a shutdown hook if the server is configured to kill orbd on close and
		 * there is no existing orbd process (indicated by null above).
		 * 
		 * Note that orbdProcess == null indicates that some orbd process is alive via
		 * startNamingService() so that this check only needs to additionally ensure
		 * that the server is not configured to shutdown the ORB on exit.
		 * 
		 * There is another case in which the orbd simply failed to initialize, in which
		 * case orbdProcess == null but that cannot be detected definitively at this
		 * stage. Therefore, we rely on an error during ORB initialization to indicate
		 * this case. If this occurs, a [SYS] warning will print indicating that
		 * starting orbd failed in order to give a clue for debugging.
		 */
		if (config.getBooleanProperty("app.destroy_orb_on_shutdown", false) && orbdProcess != null) {
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
		DatabaseConfig.initialize(dbURL, dbUser, dbPassword);

		// Configure the ORB
		Properties orbProps = new Properties();
		orbProps.put("org.omg.CORBA.ORBInitialPort", serverPort);
		orbProps.put("org.omg.CORBA.ORBInitialHost", serverHost);

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

			/*
			 * Run the reaper immediately on server startup to allow for the case in which
			 * the server goes down and is then brought back up. In this case, there may be
			 * files that have expired in the time that the server was down. Running the
			 * reaper cleans up these files, which must then be re-listed by their owners,
			 * if they are still present and wishing to share them.
			 * 
			 * Once the reaper has run, start a background job that will periodically run
			 * the reaper again to clean up any stale files
			 */
			int reaperInterval = config.getIntProperty("server.reaper_interval_minutes");
			runReaper(reaperInterval);
			startReaper(reaperInterval);
			
			// Start the ORB
			orb.run();
		} catch (Exception e) {
			System.err.println("ERROR: " + e.getMessage());
			e.printStackTrace();
		}
	} // main
	
	/**
	 * Find the IP address of the host the server is running on.
	 * 
	 * @return String IPv4 address of the local host; null if there is any failure
	 *         to do so.
	 */
	private static String findLocalIP() {
		try (final DatagramSocket sock = new DatagramSocket()) {
			sock.connect(InetAddress.getByName("8.8.8.8"), 10002);
			return sock.getLocalAddress().getHostAddress();
		} catch (Exception e) {
			return DEFAULT_SERVER_HOST;
		}
	} // findLocalIP

	/**
	 * Starts the orbd naming service that will handle connections to the server
	 * from remote clients
	 * 
	 * @param port specifies the initial port of the ORB
	 */
	private static Process startNamingService(String port) {
		int numericPort = Integer.parseInt(port);

		// Check if orbd is already running by checking the provided ORB port and the
		// default
		// orbd activation port; return null if they are in use assuming that this means
		// orbd
		// is already active and healthy
		if (!isPortAvailable(numericPort) || !isPortAvailable(1049)) {
			return null;
		}

		try {
			ProcessBuilder pb = new ProcessBuilder("orbd", "-ORBInitialPort", port);

			// Ensure that errors or messages are printed on the server console
			pb.inheritIO();
			Process orbdProcess = pb.start();
			System.out.println("[SYS] Naming service (orbd) starting on " + serverHost + ":" + port);

			// Give orbd time to bind to a socket before server initialization proceeds
			Thread.sleep(2000);

			// It may be the case that orbd is already running from a prior server process
			// that did not close it
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
	} // startNamingService

	/**
	 * Performs a socket ping to determine if there a service running the given port
	 * at the server host address.
	 * 
	 * @param port the port that needs testing
	 * @return true if the socket is free
	 */
	private static boolean isPortAvailable(int port) {
		//
		try (Socket sock = new Socket()) {
			// If the ping is answered (no exception) then the port is not free
			sock.connect(new InetSocketAddress("localhost", port), 200);
			return false;
		} catch (IOException ioe) {
			// Connection refused so assume the port is free
			return true;
		}
	} // isPortAvailable

	/**
	 * The database reaper periodically scans the database to determine if there are
	 * any files that have not received a heartbeat signal in the last number of
	 * minutes corresponding to the period.
	 * 
	 * @param period indicates the number of minutes that the reaper should wait
	 *               between invocations
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
		scheduler.scheduleAtFixedRate(() -> runReaper(period), period, period, TimeUnit.MINUTES);
	} // startReaper

	/**
	 * Runs the reaper to cull any stale files from the database.
	 * 
	 * @param period the amount of time (in minutes) that a file may exist in the
	 *               database without receiving a heartbeat from its client
	 */
	private static void runReaper(long period) {
		// Reaper actually allows files to exist for period+1 minutes to allow for any
		// race conditions with a client that is sending a heartbeat at the exact same
		// moment the reaper is running.
		String sql = "DELETE FROM file_entries WHERE last_seen < NOW() - INTERVAL '" + (period + 1) + " minutes'";
		try (Connection conn = DatabaseConfig.getConnection(); Statement stmt = conn.createStatement()) {

			int deletedRows = stmt.executeUpdate(sql);
			if (deletedRows > 0) {
				System.out.println("[REAPER] Purged " + deletedRows + " stale file(s).");
			}
		} catch (SQLException sqle) {
			System.err.println("[REAPER] Error during cleanup: " + sqle.getMessage());
		}
	}
}