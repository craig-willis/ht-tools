package edu.illinois.lis.ht.index.mpi;

import java.io.File;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

public class PropertyManager
{	
	private static Properties properties = new Properties();
	static{
		try {
			properties.load(new FileInputStream(new File("conf/ht-indexer.properties")));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public static String getProperty(String key){
		return properties.getProperty(key);
	}	
	
	public static List<String> getPropertyAsList(String key) {
		String value = properties.getProperty(key);
		String[] values = value.split(",");
		return Arrays.asList(values);
	}
}
