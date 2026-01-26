/**
 * title: Catalog Client
 * description: Main program of operating a client that interfaces with the catalog server and other clients
 * 				to perform P2P file sharing.
 * @author Dominic Evans
 * @date January 25, 2026
 * @version 1.0
 * @
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
		
		initializeConnection(args, config);
		
	} // main
	
	private static void initializeConnection(String[] args, ConfigLoader config) {
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
			
			System.out.println("[SYS] Successfully connected to catalog server at " + 
								serverHost + ":" + serverPort);
			
		} catch (org.omg.CORBA.COMM_FAILURE cf) {
			System.err.println("[CONN] Server unreachable. Check host/port in client.properties");
		} catch (Exception e) {
			System.err.println("[ERROR] Connection failed: " + e.getMessage());
		} 
	} // initializeConnection
}
