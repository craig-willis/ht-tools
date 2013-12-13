package edu.illinois.lis.ht.index.mpi;

import java.io.File;
import java.io.IOException;
import java.util.List;

import mpi.MPI;

import org.apache.commons.io.FileUtils;

/**
 * MPI wrapper for the indexing process. Run via MPJ using
 * 
 *   mpjboot machines
 *   mpjrun.sh -np 16 -dev niodev -Xmx1024m -jar lib/ht-indexer-0.0.1-SNAPSHOT-jar-with-dependencies.jar
 *	 mpjhalt machines
 *
 * Each process reads a common list of IDs and indexes all documents that are a factor of its rank.
 * 
 */
public class MPIWrapper 
{	
	public static void main(String[] args) 
	{
		MPI.Init(args);
		
		int rank = MPI.COMM_WORLD.Rank();
		int size = MPI.COMM_WORLD.Size();
	
		MPI.COMM_WORLD.hashCode();
		System.out.println("Starting process " + rank + "> of total <" + size + ">.");
		System.out.println("Start timestamp = " + System.currentTimeMillis());
		
		try
		{
			List<String> idList = readIDList();
			
			SolrCloudIndexer indexer = new SolrCloudIndexer();
			int numIds = idList.size();
			for(int i=0; i<numIds; i++){
				
				if((rank + size*i)<numIds)
				{
					indexer.index(idList.get(rank + size*i), rank);
				}else 
					break;
			}
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		System.out.println("indexing finished ");
		System.out.println("End timestamp = " + System.currentTimeMillis());
		
		MPI.Finalize();		
	}
	
	public static List<String> readIDList() throws IOException {

		String idListFile = PropertyManager.getProperty("id.file");
		
		List<String> idList = FileUtils.readLines(new File(idListFile));

		return idList;

	}
}
