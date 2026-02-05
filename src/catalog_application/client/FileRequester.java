/**
 * title: File Requester
 * description: Retrieves files from other peers when supplied with a file name, IP address, and port
 * @author Dominic Evans
 * @date January 29, 2026
 * @version 1.0
 * @copyright 2026 Dominic Evans
 */

package catalog_application.client;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class FileRequester {
	
	public static void downloadFile (catalog_api.FileInfo info, Path downloadDir) {
		System.out.println("[P2P] Attempting to download: " + info.fileName);
		
		// Be sure that the download directory exists
		try {
			if (!Files.exists(downloadDir)) {
				Files.createDirectories(downloadDir);
			}
		} catch (IOException ioe) {
			System.err.println("[P2P] Could not create download directory: " + ioe.getMessage());
			return;
		}
		
		// Create the connection to peer
		try (Socket sock = new Socket(info.ownerIP, (int)info.port);
			 DataOutputStream out = new DataOutputStream(sock.getOutputStream());
			 DataInputStream in = new DataInputStream(sock.getInputStream())) {
			
			// Request the file by its name
			out.writeUTF(info.fileName);
			out.flush();

			// FileTransferHandler will provide an 8-byte header that indicates the size of the
			// incoming file prior to the file's bytes
			long fileSize = in.readLong();
			System.out.println("[P2P] " + info.fileName + ": peer reported file size = " + fileSize + " bytes");
			
			// Prepare the local file path
			Path downloadPath = downloadDir.resolve(info.fileName);
			
			// Stream data from the socket into the destination file
			System.out.println("[P2P] " + info.fileName + ": receiving data...");
			long bytesCopied = Files.copy(in, downloadPath, StandardCopyOption.REPLACE_EXISTING);
			
			if (bytesCopied != fileSize) {
				System.err.println("[WARN] " + info.fileName + " transfer mismatch! Expected " 
									+ fileSize + " bytes; received " 
									+ bytesCopied + " bytes.");
			} else {
				System.out.println("[P2P] " + info.fileName + " successfully transferred. Saved to: " + downloadPath);
			}
			
		} catch (IOException ioe) {
			System.err.println("[P2P] Download failed: " + ioe.getMessage());
		}
	}

}
