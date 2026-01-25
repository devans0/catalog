/**
 * title: File Tracker Implementation
 * description: Implementation of the IDL-generated object interface that defines the database operations
 * 				for the catalog server.
 * @author Dominic Evans
 * @date January 22, 2026
 * @version 1.0
 * @copyright 2026 Dominic Evans
 */

package catalog_application.server;

import catalog_api.FileInfo;
import catalog_api.FileTrackerPOA;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class FileTrackerImpl extends FileTrackerPOA {

	/**
	 * Makes a file available in the directory database for other clients to discover.
	 * 
	 * @param fileName the name of the file.
	 * @param ownerID UUID of the client; generated client side and used to uniquely identify a 
	 * 				  client on subsequent accesses to the same file listing.
	 * @param ownerIP the IP address of the owner of the file.
	 * @param ownerPort the port that the owner of the file wishes to accept connections on.
	 */
	@Override
	public void listFile(String fileName, String ownerID, String ownerIP, int ownerPort) {
		String sql = "INSERT INTO file_entries (file_name, peer_id, owner_ip, owner_port, last_seen) " +
					 "VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP) " +
					 "ON CONFLICT (file_name, owner_ip, owner_port) " +
					 "DO UPDATE SET last_seen = CURRENT_TIMESTAMP";
		
		try (Connection conn = DatabaseConfig.getConnection();
			PreparedStatement pstmt = conn.prepareStatement(sql)) {
			
			pstmt.setString(1, fileName);
			pstmt.setString(2, ownerID);
			pstmt.setString(3,  ownerIP);
			pstmt.setInt(4,  ownerPort);
			pstmt.executeUpdate();
			
			System.out.println("[DB] Listing updated: " + fileName + " from " + ownerIP);
		} catch (SQLException sqle) {
			System.err.println("[DB] Error in listFile: " + sqle.getMessage());
		}
	} // listFile

	@Override
	/**
	 * Indicates that the owner of a file wishes to remove a file from the database of available files.
	 * May occur when the client is shutting down or when the user decides they wish to stop sharing a 
	 * given file.
	 * 
	 * @param fileName the name of the file that the user wishes to stop sharing.
	 * @param ownerID the UUID of the owner of the file; supplied during file listing.
	 */
	public void delistFile(String fileName, String ownerID) {
		String sql = "DELETE FROM file_entries WHERE file_name = ? AND owner_id = ?";
		
		try (Connection conn = DatabaseConfig.getConnection();
			 PreparedStatement pstmt = conn.prepareStatement(sql)) {
			
			// Construct the prepared statement and execute it against the database
			pstmt.setString(1, fileName);
			pstmt.setString(2, ownerID);
			int rows = pstmt.executeUpdate();

			// Report results to stdout
			if (rows == 0) {
				System.out.println("[AUTH] Unauthorized delist attempt for: " + fileName);
			} else {
				System.out.println("[DB] delisting file '" + fileName + "'");
			}
		} catch (SQLException sqle) {
			System.err.println("[DB] Error in delistFile: " + sqle.getMessage());
		}
	} // delistFile

	@Override
	public FileInfo[] searchFiles(String query) {
		String sql = "SELECT file_name, owner_IP, owner_port FROM file_entries " +
					 "WHERE file_name ILIKE ?";
		
		List<FileInfo> fileList = new ArrayList<>();

		try (Connection conn = DatabaseConfig.getConnection(); 
			 PreparedStatement pstmt = conn.prepareStatement(sql)) {
			
			pstmt.setString(1, "%" + query + "%");
			
			// Iterate over the results and add new FileInfo objects to the list
			try (ResultSet rs = pstmt.executeQuery()) {
				while (rs.next()) {
					fileList.add(new FileInfo(
							rs.getString("file_name"),
							rs.getString("owner_ip"),
							rs.getInt("owner_port")
					));
				}
			}
		} catch (SQLException sqle) {
			System.err.println("[DB] Error in searchFiles: " + sqle.getMessage());
		}
		
		// CORBA expects an array type per the IDL interface specification
		return fileList.toArray(new FileInfo[0]);
	} // searchFiles

	/**
	 * Finds the FileInfo for an exact file name.
	 * 
	 * @param fileName the name of the desired file; only an exact match will be returned.
	 * @return FileInfo the information required to download an exact file.
	 */
	@Override
	public FileInfo getFileOwner(String fileName) {
		// This search should benefit from indexing; searchFiles() is not used to avoid
		// duplicating the searches when gathering data vs. getting particular file info
		String sql = "SELECT file_name, owner_ip, owner_port FROM file_entries " +
					 "WHERE file_name = ? LIMIT 1";
		
		try (Connection conn = DatabaseConfig.getConnection();
			 PreparedStatement pstmt = conn.prepareStatement(sql)) {
			
			// Construct the prepared statement and execute it
			pstmt.setString(1,  fileName);
			try (ResultSet rs = pstmt.executeQuery()) {
				// There is only one row or no rows in the ResultSet
				if (rs.next()) {
					// Package the database return into a FileInfo object and return it
					return new FileInfo (
							rs.getString("file_name"),
							rs.getString("owner_ip"),
							rs.getInt("owner_port")
					);
				}
			}
		} catch (SQLException sqle) {
			System.err.println("[DB] Error in getFileOwner: " + sqle.getMessage());
		}
		
		// No exact match was found for the file
		return null;
	} // getFileOwner

	/**
	 * Enables updating the last_seen attribute of a file that is listed in the catalog server. This
	 * stops the file from becoming stale and being removed by the reaper. The address/port combination
	 * is used to identify the owner of the file in the database.
	 * 
	 * @param clientID UUID of the client sending the heartbeat
	 * @param clientPort the port that the client is using to share the file
	 */
	@Override
	public void keepAlive(String clientID, int clientPort) {
		String sql = "UPDATE file_entries SET last_seen = CURRENT_TIMESTAMP " +
					 "WHERE owner_ip = ? AND owner_port = ?";
		
		try (Connection conn = DatabaseConfig.getConnection();
			 PreparedStatement pstmt = conn.prepareStatement(sql)) {
			
			// Construct the prepared statement and execute it against the database
			pstmt.setString(1, clientID);
			pstmt.setInt(2, clientPort);
			int rows = pstmt.executeUpdate();
			
			// Report any heartbeats that do not correspond to a clientIP and port pair
			if (rows == 0) {
				System.out.println("[AUTH] Failed heartbeat for ID: " + clientID);
			}
		} catch (SQLException sqle) {
			System.err.println("[DB] Error in keepAlive: " + sqle.getMessage());
		}
	} // keepAlive
}
