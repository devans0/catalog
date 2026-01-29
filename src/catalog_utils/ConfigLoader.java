/**
 * title: Configuration Loader
 * description: Utility for parsing and returning values from ".properties" files
 * @author Dominic Evans
 * @date January 22 2026
 * @version 1.0
 * @copyright 2026 Dominic Evans 
 */

package catalog_utils;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class ConfigLoader {
	private Properties props = new Properties();
	
	/**
	 * Responsible for loading a properties file so that it may be parsed for values
	 * associated with different configuration properties.
	 * 
	 * @param fileName the name of the file containing properties; suffixed with the
	 * ".properties" file extension
	 */
	public ConfigLoader(String fileName) {
		try (InputStream input = new FileInputStream(fileName)) {
			props.load(input);
		} catch (IOException ioe) {
			System.err.println("Could not find configuration file: " + fileName);
			ioe.printStackTrace();
		}
	} // ctor
	
	/**
	 * Returns the value associated with the provided key
	 * @param key indicating which property to access
	 * @return String value associated with the desired property
	 */
	public String getProperty(String key) {
		return props.getProperty(key);
	} // getProperty
	
	/* Overload: provide an optional default configuration value */
	public String getProperty(String key, String defaultValue) {
		return props.getProperty(key, defaultValue);
	} // getProperty
	
	/**
	 * Fetches the integer value associated with a given key
	 * @param key indicating which property to access; must be associated with an
	 * integer-valued property field
	 * @return Integer value associated with the provided key
	 */
	public int getIntProperty(String key) {
		return Integer.parseInt(props.getProperty(key));
	} // getIntProperty
	
	/* Overload: provide an optional default configuration value */
	public int getIntProperty(String key, int defaultVal) {
		try {
			int result = Integer.parseInt(props.getProperty(key));
			return result;
		} catch (NumberFormatException nfe) { }
		return defaultVal;
	} // getIntProperty
	
	/**
	 * Fetches the boolean value associated with a given key and returns its value
	 * @param key indicating the property to be returned from the configuration file.
	 * @return boolean corresponding to desired key
	 */
	public boolean getBooleanProperty(String key) {
		return Boolean.parseBoolean(props.getProperty(key));
	} // getBooleanProperty  
	
	/* Overload: provide an optional default configuration value */
	public boolean getBooleanProperty(String key, boolean defaultValue) {
		String val = props.getProperty(key);
		if (val == null) {
			return defaultValue;
		}
		return Boolean.parseBoolean(val);
	} // getBooleanProperty
}




