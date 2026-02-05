/**
 * title: Peer Server
 * description: A server daemon that listens on a port for incoming connections and provides requested
 *              files provided they exist in the configured share directory.
 * @author Dominic Evans
 * @date January 29, 2026
 * @version 1.0
 * @copyright 2026 Dominic Evans
 */

package catalog_application.client;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class PeerServer implements Runnable {
	private final int port;
	private final Path shareDir;
	private ExecutorService threadPool;
	
	public PeerServer(int port, Path shareDir, int maxConnections) {
		this.port = port;
		this.shareDir = shareDir;
		this.threadPool = Executors.newFixedThreadPool(maxConnections);
	} //ctor
	
	/**
	 * Implement the run method required by the Runnable interface. This method listens on the share port
	 * as configured via clients.properties and starts a new FileTransferHandler when a request is received
	 * on a separate thread. The listener then returns to listening. The maximum number of file
	 * transfer threads that may be active at any one time is configured via max_simultaneous_connections
	 * in client.properties.
	 */
	@Override
	public void run() {
		try (ServerSocket serverSock = new ServerSocket(port)) {
			System.out.println("[P2P] Peer Server listening on port " + this.port);
			
			// Ensure that the server socket does not become bound in TIME_WAIT during
			// rapid shutdown and restart
			serverSock.setReuseAddress(true);			
			
			// Listen on the server socket for incoming connection 
			while (!Thread.currentThread().isInterrupted()) {
				try {
					Socket clientSock = serverSock.accept();
					this.threadPool.execute(new FileTransferHandler(clientSock, this.shareDir));
				} catch (IOException ioe) {
					System.err.println("[P2P] Connection accept error: " + ioe.getMessage());
				}
			}
		} catch (IOException ioe) {
			System.err.println("[P2P] Peer Server error: " + ioe.getMessage());
		} finally {
			shutdownPool();
		}
	} // run
	
	/**
	 * Shuts down the thread pool of Executors when the listener is shutdown. Gives a 5 second grace period
	 * to allow for nice shutdown before forcibly ending the pool.
	 */
	private void shutdownPool() {
		this.threadPool.shutdown();
		try {
			if (!this.threadPool.awaitTermination(5, TimeUnit.SECONDS)) {
				this.threadPool.shutdownNow();
			}
		} catch (InterruptedException ie) {
			this.threadPool.shutdownNow();
		}
	} //shutdownPool
}
