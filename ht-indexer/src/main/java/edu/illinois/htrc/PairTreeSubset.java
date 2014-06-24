package edu.illinois.htrc;

import java.io.File;
import java.io.IOException;
import java.net.URLDecoder;
import java.util.List;

import org.apache.commons.io.FileUtils;

import gov.loc.repository.pairtree.Pairtree;

public class PairTreeSubset {

	public static void main(String[] args) throws IOException
	{
		String volumeFile = args[0];
		String sourceRoot = args[1];
		String destRoot = args[2];
		
		List<String> volumeList = FileUtils.readLines(new File(volumeFile));
		for (String volumeId: volumeList)
		{
			volumeId = URLDecoder.decode(volumeId, "UTF-8");
			Pairtree pt = new Pairtree();
			
		    String sourcePart = volumeId.substring(0, volumeId.indexOf("."));
		    String volumePart = volumeId.substring(volumeId.indexOf(".")+1, volumeId.length());
		    String uncleanId = pt.uncleanId(volumePart);
		    String path = pt.mapToPPath(uncleanId);
		    String cleanId = pt.cleanId(volumePart);
		    
		    String sourceVolume = sourceRoot + File.separator + sourcePart 
		    		+ File.separator + "pairtree_root" 
		    		+ File.separator + path 
		    		+ File.separator + cleanId;

		    String destVolume = destRoot + File.separator + sourcePart 
		    		+ File.separator + "pairtree_root" 
		    		+ File.separator + path 
		    		+ File.separator + cleanId;

		    System.out.println("Copying " + sourceVolume + " to " + destVolume);
		    FileUtils.copyDirectory(new File(sourceVolume), new File(destVolume));
		}		
	}
}
