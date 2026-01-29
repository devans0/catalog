/**
 * title: Catalog Client
 * description: Main program of operating a client that interfaces with the catalog server and other clients
 * 				to perform P2P file sharing.
 * @author Dominic Evans
 * @date January 25, 2026
 * @version 1.0
 * @copyright 2026 Dominic Evans
 */

package catalog_application.client;

import java.util.Properties;
import java.util.TimeZone;

import org.omg.CORBA.ORB;
import org.omg.CosNaming.NamingContextExt;
import org.omg.CosNaming.NamingContextExtHelper;

import catalog_api.FileTracker;
import catalog_api.FileTrackerHelper;
import catalog_utils.ConfigLoader;

public class CatalogClient {
	
	// Globals
	private static FileTracker tracker;
	private static String peerID;
	
	public static void main(String args[]) {
		// Load the configuration file which controls the behaviour of the client
		ConfigLoader config = new ConfigLoader("client.properties");
		
		// Set the client peer identity
		IdentityManager IDManager = new IdentityManager();
		peerID = IDManager.getPeerID();
		
		// Set the time zone
		String timezone = config.getProperty("app.timezone", "UTC");
		TimeZone.setDefault(TimeZone.getTimeZone(timezone));
		
		// Load GUI
		
		// Connect to the server, failure to do so is a critical failure
		if (initializeConnection(args, config)) {
			// Start the listener thread for incoming connections and handling file transfers 
			PeerServer listener = new PeerServer(config);
			Thread serverThread = new Thread(listener);
			serverThread.setDaemon(true);
			serverThread.start();
			
			// Shutdown thread to ensure graceful and clean shutdown
			Runtime.getRuntime().addShutdownHook(new Thread(() -> {
				System.out.println("[SYS] Shutdown initiated...");
				serverThread.interrupt();
			}));
		
			// Application loop listening for user input and performing actions (non-blocking)
			while (!Thread.currentThread().isInterrupted()) {
				
			}
		} else {
			// TODO Gracefully handle this condition within the GUI
			System.err.println("Server unreachable");
			System.exit(1);
		}
	} // main
	
	private static boolean initializeConnection(String[] args, ConfigLoader config) {
		String serverHost = config.getProperty("server.nameservice_host", "localhost");
		String serverPort = config.getProperty("server.port", "1050");

		// Fetch the location of the server and the port it is accepting connections on
		Properties orbProps = new Properties();
		orbProps.put("org.omg.CORBA.ORBInitialHost", serverHost);		
		orbProps.put("org.omg.CORBA.ORBInitialPort", serverPort);
		
		try {
			// Initialize the ORB
			ORB orb = ORB.init(args, orbProps);

			// Discover the Catalog Server via name service
			org.omg.CORBA.Object objRef = orb.resolve_initial_references("NameService");
			NamingContextExt ncRef = NamingContextExtHelper.narrow(objRef);
			
			// Bind the remote FileTracker object
			tracker = FileTrackerHelper.narrow(ncRef.resolve_str("CatalogService"));
			
			// Connection succeeded
			System.out.println("[SYS] Successfully connected to catalog server at " + 
								serverHost + ":" + serverPort);
			return true;
			
		} catch (org.omg.CORBA.COMM_FAILURE cf) {
			System.err.println("[CONN] Server unreachable. Check host/port in client.properties");
		} catch (Exception e) {
			System.err.println("[ERROR] Connection failed: " + e.getMessage());
		} 
		// Connection failed due to exception above
		return false;
	} // initializeConnection
}
