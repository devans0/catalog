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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import catalog_api.FileInfo;
import catalog_api.FileTrackerPOA;
import catalog_api.SearchResult;
import catalog_utils.ConfigLoader;

/**
 * FileTrackerImpl implements the interface for the Catalog Server as defined in the IDL specification for
 * the server. This interface provides a means for clients to list files on the server as available for
 * sharing, keep the server aware that these files are still actively being shared, to de-list those files, 
 * to search for specific files, and to retrieve the information necessary to set up a peer connection with
 * another client to download files from them.
 */

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
	public void listFile(String ownerID, String fileName, String ownerIP, int ownerPort) {
		String sql = "INSERT INTO file_entries (peer_id, file_name, owner_ip, owner_port, last_seen) " +
					 "VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP) " +
					 "ON CONFLICT (file_name, owner_ip, owner_port) " +
					 "DO UPDATE SET last_seen = CURRENT_TIMESTAMP";
		
		try (Connection conn = DatabaseConfig.getConnection();
			PreparedStatement pstmt = conn.prepareStatement(sql)) {
			
			pstmt.setString(1, ownerID);
			pstmt.setString(2, fileName);
			pstmt.setString(3,  ownerIP);
			pstmt.setInt(4, ownerPort);
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

	/**
	 * This method allows for searching the database for any listings that match a provided file name.
	 * Each matching file, along with its database-side ID are returned to the caller in an array
	 * of type SearchResult
	 * 
	 * @param query the file name that constitutes the search term for the 
	 * @return SearchResult[] containing the search result values for all the files found that correspond
	 * to the provided query.
	 */
	@Override
	public SearchResult[] searchFiles(String query) {
		String sql = "SELECT id, file_name FROM file_entries " +
					 "WHERE file_name ILIKE ?";
		
		List<SearchResult> fileList = new ArrayList<>();

		try (Connection conn = DatabaseConfig.getConnection(); 
			 PreparedStatement pstmt = conn.prepareStatement(sql)) {
			
			pstmt.setString(1, "%" + query + "%");
			
			// Iterate over the results and add new FileInfo objects to the list
			try (ResultSet rs = pstmt.executeQuery()) {
				while (rs.next()) {
					fileList.add(new SearchResult(
							rs.getInt("id"),
							rs.getString("file_name")
					));
				}
			}
		} catch (SQLException sqle) {
			System.err.println("[DB] Error in searchFiles: " + sqle.getMessage());
		}
		
		// CORBA expects an array type per the IDL interface specification
		return fileList.toArray(new SearchResult[0]);
	} // searchFiles

	/**
	 * Finds the FileInfo for an exact file name.
	 * 
	 * @param fileName the name of the desired file; only an exact match will be returned.
	 * @return FileInfo the information required to download an exact file.
	 */
	@Override
	public FileInfo getFileOwner(int fileID) {
		// This search should benefit from indexing; searchFiles() is not used to avoid
		// duplicating the searches when gathering data vs. getting particular file info
		String sql = "SELECT file_name, owner_ip, owner_port FROM file_entries " +
					 "WHERE id = ? LIMIT 1";
		
		try (Connection conn = DatabaseConfig.getConnection();
			 PreparedStatement pstmt = conn.prepareStatement(sql)) {
			
			// Construct the prepared statement and execute it
			pstmt.setInt(1, fileID);
			try (ResultSet rs = pstmt.executeQuery()) {
				// There is only one row or no rows in the ResultSet
				if (rs.next()) {
					// Package the database return into a FileInfo object and return it
					return new FileInfo (
							fileID,
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
	 * Provides the caller with the configured period of the file entry reaper
	 * 
	 * @return int The length of the reaper period in minutes
	 */
	@Override
	public int getTTL() {
		ConfigLoader config = new ConfigLoader("server.properties");
		return config.getIntProperty("server.reaper_interval_minutes");
	}

	/**
	 * Enables updating the last_seen attribute of a file that is listed in the catalog server. This
	 * stops the file from becoming stale and being removed by the reaper. The client is identified
	 * through the use of a UUID which is used to update all files that that client has listed
	 * with the server.
	 * 
	 * The boolean return for this function aims to signal to a client that calls it that the update
	 * actually performed an update in the database. If it returns false, this means that the client
	 * does not actually have any files listed in the database. This is used to signal that the client
	 * must refresh their shared files with the server, as they have all been reaped or delisted in some
	 * other way.
	 * 	  
	 * @param clientID UUID of the client sending the heartbeat
	 * @param clientPort the port that the client is using to share the file
	 * @return true if the signal updated any records in the database; false otherwise, indicating that
	 *         the client does not have any files listed on the server
	 */
	@Override
	public boolean keepAlive(String clientID) {
		// Sanity check
		// TODO REMOVE
		if (clientID == null || clientID.trim().isEmpty()) {
			System.err.println("[AUTH] Rejected null/empty heartbeat ID.");
			return false;
		}
		
		String sql = "UPDATE file_entries SET last_seen = CURRENT_TIMESTAMP " +
					 "WHERE peer_id = ?";
		
		int rows = 0;
		try (Connection conn = DatabaseConfig.getConnection();
			 PreparedStatement pstmt = conn.prepareStatement(sql)) {
			
			// Construct the prepared statement and execute it against the database
			pstmt.setString(1, clientID);
			rows = pstmt.executeUpdate();
			
			// Report any heartbeats that do not correspond to a clientIP and port pair
			if (rows == 0) {
				System.out.println("[AUTH] Failed heartbeat for ID: " + clientID);
			}
		} catch (SQLException sqle) {
			System.err.println("[DB] Error in keepAlive: " + sqle.getMessage());
		}
		return rows > 0;
	} // keepAlive

	/**
	 * This method disconnects a client identified by its ownerID. A disconnection involves removing all
	 * files listed by the client. After sending this signal, a client is expected to immediately cease
	 * listing any files with the server.
	 * 
	 * NOTE: This method highlights the imperative that ownerIDs remain a secret held by the only the
	 * server and a given client; if this information is ever discovered by another client, they become
	 * capable of arbitrarily disconnecting any other user for which the ownerID is known.
	 * 
	 * @param ownerID the String UUID that uniquely identifies a given client.
	 */
	@Override
	public void disconnect(String ownerID) {
		String sql = "DELETE FROM file_entries WHERE peer_id = ?";
		
		try (Connection conn = DatabaseConfig.getConnection(); 
			 PreparedStatement pstmt = conn.prepareStatement(sql)) {
			
			pstmt.setString(1, ownerID);
			pstmt.execute();

		} catch (SQLException sqle) {
			System.err.println("[DB] Error in disconnect: Database operation failed.");
			sqle.printStackTrace();
		}
	}
}
