/**
 * title: Share Manager
 * description: Tracks, lists and manages the sharing of local files 
 * @author Dominic Evans
 * @date January 29, 2026
 * @version 1.0
 * @copyright 2026 Dominic Evans
 */

package catalog_application.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import catalog_api.FileTracker;

/*
 * The ShareManager is responsible for managing the local share directory and the files that it contains.
 * Its methods are used to both perform initial registration of files in the share directory as well as 
 * periodically scanning that directory for changes, either new files being added or files being removed.
 * Any changes to the share directory are reflected through updates to the share server.
 */

public class ShareManager {
	
	private final Path shareDir;
	private final FileTracker tracker;
	private final String peerID;
	private final String localAddress;
	private final int localPort;
	private final Set<String> registeredFiles = new HashSet<>();
	
	public ShareManager(Path shareDir, FileTracker tracker, String peerID, String localAddress, int localPort) {
		this.shareDir = shareDir;
		this.tracker = tracker;
		this.peerID = peerID;
		this.localAddress = localAddress;
		this.localPort = localPort;
	} // ctor

	/**
	 * Creates a new scheduled thread to periodically scan the share directory for changes.
	 * 
	 * @param int The number of seconds between heartbeats and updates of the share directory.
	 */
	public void startMonitoring(int period) {
		ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
		scheduler.scheduleAtFixedRate(this::syncDirectory, 0, period, TimeUnit.SECONDS);
	}
	
	/**
	 * Scans each of the files in the shared directory and processes them so long as they
	 * are regular files and they are not hidden. Additionally responsible for sending a 
	 * heartbeat signal to the server to indicate that the client is still present and providing
	 * files.
	 */
	private void syncDirectory() {
		// Track the number of attempted retries and the need for another one
		int retries = 0;
		boolean needsRetry = false;
		
		/* 
		 * This loop enables immediate retries of the synchronization. This may be necessary if
		 * the server does not have any records of the files that the client expects to have
		 * registered with the server.  
		 * 
		 * The call to keepAlive() that constitutes the heartbeat may return false which indicates
		 * that the server has not updated any listings that are associated with the client. If
		 * the client assumes that it has at least one file registered with the server for sharing
		 * then a false return on the heartbeat indicates that there is some issue; either the files
		 * have been reaped by the server or listing of the files failed. In either case, the
		 * client should immediately attempt to re-register its entire share directory.
		 * 
		 * The number of retries are limited to three per invocation of this method to avoid
		 * having a client overwhelm the server with retries in the event of a malfunction.
		 */
		do {
			// Reset
			needsRetry = false;
			try {
				// Get the files that are current present in the share directory
				Set<String> currDiskFiles = new HashSet<>();
				try (Stream<Path> stream = Files.list(this.shareDir)) {
					// No files in the directory; immediately exit the loop/method
					if (stream.count() == 0) { break; }

					stream.filter(Files::isRegularFile).filter(p -> {
						try {
							return !Files.isHidden(p);
						} catch (IOException ioe) {
							return false;
						}
					}).forEach(p -> currDiskFiles.add(p.getFileName().toString()));
				}

				// Identify any new files; register them with the share server
				for (String fileName : currDiskFiles) {
					if (!registeredFiles.contains(fileName)) {
						try {
							tracker.listFile(this.peerID, fileName, this.localAddress, this.localPort);
							registeredFiles.add(fileName);
							System.out.println("[SHARE] Registered: " + fileName);
						} catch (Exception e) {
							System.err.println("[SHARE] Registration failed: " + fileName);
						}
					}
				}

				/*
				 * Identify any files that have been removed from the directory
				 * If any exist, delist them from the share server
				 */
				Iterator<String> it = registeredFiles.iterator();
				while (it.hasNext()) {
					String knownFile = it.next();
					if (!currDiskFiles.contains(knownFile)) {
						try {
							tracker.delistFile(knownFile, this.peerID);
							it.remove();
							System.out.println("[SHARE] Delisted: " + knownFile);
						} catch (Exception e) {
							System.err.println("[SHARE] Delisting failed: " + knownFile);
						}
					}
				}
				
				/* Send the heartbeat and detect if the server has lost the file listings.
				   If the server has reaped the listed files between heartbeats due to a delay, then
				   this check will cause an immediate refresh of the share directory */
				if (!tracker.keepAlive(this.peerID)) {
					System.out.println("[SHARE] Session lost. Attempting immediate re-sync...");
					registeredFiles.clear();
					// Limit the number of retries to 3
					needsRetry = (retries++ < 3);
				}

			} catch (IOException ioe) {
				System.err.println("[SHARE] Directory syn error " + ioe.getMessage());
			} 
		} while (needsRetry);
	} // syncDirectory
	
	/**
	 * Wrapper method for disconnecting from the server. Ensures that the ShareManager provides the total
	 * interface for all functions related to sharing files.
	 * 
	 * Disconnecting involves alerting the server to delist all files that are associated with this client.
	 */
	public void disconnect() {
		tracker.disconnect(this.peerID);
	}
}




















