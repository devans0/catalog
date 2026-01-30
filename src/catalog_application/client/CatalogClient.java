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
import java.util.Properties;
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
	
	// Globals
	private static FileTracker tracker;
	private static String peerID;
	private static String localAddress;
	private static int localPort;
	private static Thread serverThread;
	private static Path shareDir;
	private static Path downloadDir;
	
	public static void main(String args[]) {
		// Load the configuration file which controls the behaviour of the client
		ConfigLoader config = new ConfigLoader("client.properties");
		
		// Set the client peer identity
		IdentityManager IDManager = new IdentityManager();
		peerID = IDManager.getPeerID();
		localAddress = getLocalIP();
		localPort = config.getIntProperty("client.share_port", 2050);	
		
		// Set the time zone
		String timezone = config.getProperty("app.timezone", "UTC");
		TimeZone.setDefault(TimeZone.getTimeZone(timezone));
		
		// Set the share and download directories
		shareDir = Paths.get(config.getProperty("client.share_dir", "./share"));
		downloadDir = Paths.get(config.getProperty("client.download_dir", "./downloads"));

		// Shutdown hook to ensure graceful shutdown of the background file server
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			System.out.println("[SYS] Shutdown initiated...");
			if (serverThread != null) {
				serverThread.interrupt();
			}
		}));
		
		// Start the GUI
		SwingUtilities.invokeLater(() -> {
			CatalogGUI gui = new CatalogGUI(peerID, downloadDir);
			gui.setVisible(true);
			
			// Create new thread for connection initialization to avoid freezing the GUI
			new Thread(() -> {
				// Attempt connection to the server
				if (initializeConnection(args, config)) {
					// Perform initial registration of the local files
					listLocalFiles(shareDir);

					// Pass the now-instantiated tracker to the GUI 
					SwingUtilities.invokeLater(() -> gui.setTracker(tracker));

					// Start the listener thread for incoming connections and handling file transfers 
					PeerServer listener = new PeerServer(config);
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
	 * Determines the client's local IP address which is necessary for allowing other clients to connect
	 * and download files once they have been found on the share server.
	 * 
	 * Note: this method for finding the local address was found via Baeldung:
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
	
	/**
	 * This method scans the share directory and lists each file found there provided it is a regular file
	 * and it is not a hidden file. This method is to be used for initial share directory scanning and listing
	 * whereas periodic updates to the listings are handled by updateLocalFileList.
	 * 
	 * @param sharePath the path corresponding to the directory that is to be shared with other peers
	 */
	public static void listLocalFiles(Path sharePath) {
		// Generate a new thread to avoid hanging the GUI while this method executes
		new Thread(() -> {
			/*
			 * This stream expression first filters out all files in the share directory that are not regular
			 * files. It then filters the resulting stream and removes any files that are hidden. Finally,
			 * each of the resulting files that made it through each of these filters is listed with the 
			 * share server via the FileTracker.
			 */
			try (Stream<Path> stream = Files.list(sharePath)) {
				stream.filter(Files::isRegularFile)
					  .filter(p -> {
						  try {
							  return !Files.isHidden(p);
						  } catch (IOException ioe) {
							  return false;
						  }
					  })
					  .forEach(p -> {
						  try {
							  tracker.listFile(peerID, p.getFileName().toString(), localAddress, localPort);
						  } catch (Exception e) {
							  System.err.println("[ERROR] Failed to register: " + p.getFileName());
						  }
					  });
			} catch (IOException ioe) {
				System.err.println("[SYS] Could not read share directory: " + ioe.getMessage());
			}
		}).start();
	} // listLocalFiles
}















