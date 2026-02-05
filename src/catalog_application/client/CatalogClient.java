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

import java.io.File;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.TimeZone;
import java.util.stream.Stream;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import org.omg.CORBA.ORB;
import org.omg.CosNaming.NamingContextExt;
import org.omg.CosNaming.NamingContextExtHelper;

import catalog_api.FileTracker;
import catalog_api.FileTrackerHelper;
import catalog_utils.ConfigLoader;

public class CatalogClient {
	// Default values
	private static final int DEFAULT_PORT = 2050;
	private static final String DEFAULT_TZ = "UTC";
	private static final String DEFAULT_SHARE_DIR = "./share";
	private static final String DEFAULT_DOWNLOAD_DIR = "./downloads";
	
	// Globals
	private static FileTracker tracker;
	private static String peerID;
	private static String localAddress;
	private static int localPort;
	private static Thread serverThread;
	private static Path shareDir;
	private static Path downloadDir;
	private static ShareManager shareManager;
	
	public static void main(String args[]) {

		// Load the configuration file which controls the behaviour of the client
		ConfigLoader config = new ConfigLoader("client.properties");
		
		// Set the client peer identity
		IdentityManager IDManager = new IdentityManager();
		peerID = IDManager.getPeerID();
		localAddress = getLocalIP();
		localPort = config.getIntProperty("client.share_port", DEFAULT_PORT);	
		
		// Set the time zone
		String timezone = config.getProperty("app.timezone", DEFAULT_TZ);
		TimeZone.setDefault(TimeZone.getTimeZone(timezone));
		
		// Set the share and download directories
		shareDir = Paths.get(config.getProperty("client.share_dir", DEFAULT_SHARE_DIR));
		downloadDir = Paths.get(config.getProperty("client.download_dir", DEFAULT_DOWNLOAD_DIR));

		/*
		 * Command-line runtime customization. When run from the command line, arguments
		 * must be supplied to the Catalog Client in the following order:
		 * 
		 * [port] [share directory] [download directory]
		 * 
		 * Each of these arguments are required if command-line customization is to be
		 * used. If these arguments are supplied then they will override the properties
		 * found in client.properties. This is to be used to run multiple instances of
		 * the client on the same host for testing and debugging purposes.
		 */
		if (args.length != 0) {
			localPort = Integer.parseInt(args[0]);
			shareDir = Paths.get(args[1]);
			downloadDir = Paths.get(args[2]);
		}

		// Set shutdown tasks
		setShutdownHooks();
		
		// Start the GUI
		SwingUtilities.invokeLater(() -> {
			CatalogGUI gui = new CatalogGUI(peerID, downloadDir);
			gui.setVisible(true);
			
			/*
			 * This thread is responsible for initializing the connection to the server and starting the
			 * Share Manager and Peer Server. These tasks occur serially but must be relegated to a new
			 * thread in order to keep the GUI responsive even if these tasks hang.
			 */
			new Thread(() -> {
				// Attempt connection to the server
				if (initializeConnection(args, config)) {
					// Invoke the share manager to periodically scan the share directory and list or delist
					// any files that are added to or removed from that directory
					shareManager = new ShareManager(shareDir, tracker, peerID, localAddress, localPort);
					int heartbeatPeriodSeconds = Math.max(10, (int) (60 * 0.75 * tracker.getTTL()));
					shareManager.startMonitoring(heartbeatPeriodSeconds);

					// Pass the now-instantiated tracker to the GUI 
					SwingUtilities.invokeLater(() -> gui.setTracker(tracker));

					// Start the listener thread for incoming connections and handling file transfers 
					int maxConnections = config.getIntProperty("client.max_simultaneous_connections", 4);
					PeerServer listener = new PeerServer(localPort, shareDir, maxConnections);
					serverThread = new Thread(listener);
					serverThread.setDaemon(true);
					serverThread.start();
			
				} else {
					// Show error dialog when the server is not found 
					SwingUtilities.invokeLater(() -> {
						JOptionPane.showMessageDialog(gui, 
								"Fatal Error: Could not connect to the Catalog Server.\n" + 
								"Please check your network settings and server status.",
								"Connection Failed",
								JOptionPane.ERROR_MESSAGE);
					});
				}
			}).start();
		});
		
	} // main
	
	/**
	 * Sets the shutdown hook
	 */
	private static void setShutdownHooks() {
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			System.out.println("[SYS] Shutdown initiated...");
			// Alert the server that sharing is ending
			if (shareManager != null) {
				shareManager.disconnect();
			}
			// Shut down the peer server
			if (serverThread != null) {
				serverThread.interrupt();
			}
		}));
	} // setShutdownHook
	
	/**
	 * Determines the client's local IP address which is necessary for allowing other clients to connect
	 * and download files once they have been found on the share server.
	 * 
	 * Note: this method for finding the local address was found via a Baeldung article:
	 * https://www.baeldung.com/java-get-ip-address
	 * 
	 * @return a String representing the local IP address of the client program.
	 */
	private static String getLocalIP() {
		try (final DatagramSocket sock = new DatagramSocket()) {
			sock.connect(InetAddress.getByName("8.8.8.8"), 10002);
			return sock.getLocalAddress().getHostAddress();
		} catch (Exception e) {
			// If offline, default to the loopback device
			return "127.0.0.1";
		}
	}
	
	/**
	 * Creates a connection to the catalog server. 
	 * 
	 * @param args Any command line argument that may have been passed to CatalogClient for configuration.
	 * @param config The ConfigLoader object that parses the properties file containing persistent configuration.
	 * @return true if the connection successfully initialized; false otherwise
	 */
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