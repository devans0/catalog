package catalog_application.server;

import catalog_api.FileInfo;
import catalog_api.FileTrackerPOA;
import java.sql.*;

public class FileTrackerImpl extends FileTrackerPOA {

	public FileTrackerImpl() {
		// TODO Auto-generated constructor stub
	}

	@Override
	public void listFile(String fileName, String ownerIP, int port) {
		// TODO Auto-generated method stub

	}

	@Override
	public void delistFile(String fileName, String ownerIP) {
		// TODO Auto-generated method stub

	}

	@Override
	public String[] searchFiles(String query) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public FileInfo getFileOwner(String fileName) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void keepAlive(String clientIP) {
		// TODO Auto-generated method stub

	}

}
