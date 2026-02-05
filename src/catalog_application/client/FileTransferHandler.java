/**
 * title: File Transfer Handler
 * description: Serializes and transfers files from the file system over the designated socket.
 * @author Dominic Evans
 * @date January 29, 2026
 * @version 1.0
 * @copyright 2026 Dominic Evans
 */

package catalog_application.client;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileTransferHandler implements Runnable {
	
	private final Socket shareSock;
	private final Path shareDir;
	
	public FileTransferHandler(Socket sock, Path shareDir) {
		this.shareSock = sock;
		this.shareDir = shareDir;
	} //ctor

	@Override
	public void run() {

		try (Socket s = this.shareSock;
			 DataInputStream in = new DataInputStream(s.getInputStream());
			 OutputStream out = s.getOutputStream()) {
				 
			// Receive the filename from the requesting peer
			String fileName = in.readUTF();
			
			// Resolve the Path ensuring that there are no unsafe character sequences
			// and that the file request has sharDir as a prefix
			Path targetFile = shareDir.resolve(fileName).normalize();
			if (!targetFile.startsWith(shareDir)) {
				System.err.println("[P2P] SECURITY ALERT: Blocked access to " + targetFile.toString());
				return;
			}
			
			// Ensure that file exists before streaming the file over the socket
			if (Files.exists(targetFile) && !Files.isDirectory(targetFile)) {
				System.out.println("[P2P] Sending " + fileName + " to " + s.getInetAddress());

				// Send the file size before the file to alert peer of incoming 
				// transfer and its size
				long size = Files.size(targetFile);
				new DataOutputStream(out).writeLong(size);
				
				Files.copy(targetFile, out);
				out.flush();
			} else {
				System.err.println("[P2P] File not found or inaccessible: " + fileName);
			}
			
		} catch (IOException ioe) {
			System.err.println("[P2P] Transfer error: " + ioe.getMessage());
		}
	} // run
}
