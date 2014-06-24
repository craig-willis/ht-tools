package edu.illinois.lis.ht.index.mpi;

import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.UnknownHostException;
import java.util.List;

import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.CloudSolrServer;
import org.apache.solr.common.SolrInputDocument;

import edu.illinois.lis.ht.index.mpi.HT2Solr.Unit;

/**
 * Simple indexer that relies on SolrCloud for document routing. 
 *
 */
public class SolrCloudIndexer 
{
	CloudSolrServer cloudClient;
	HT2Solr ht2solr;
	String hostname = "unknown";
	int commitPages = 0;
	
	public SolrCloudIndexer() throws MalformedURLException, UnknownHostException
	{
		/* Read the zookeeper hostname from the config */
		String zkHost = PropertyManager.getProperty("zkHost");
		/* Read the OCR basepath from the config. Assumes all OCR is under a common directory */
		String ocrBasePath = PropertyManager.getProperty("ocrBasePath");	
		/* Read the MARC basepath from the config. Assumes one MARC XML file per volume. */
		String marcBasePath = PropertyManager.getProperty("marcBasePath");
		/* Limit indexing to a set of languages */
		List<String> languages = PropertyManager.getPropertyAsList("languages");
		/* Indexing level: page or volume */
		String indexingUnit = PropertyManager.getProperty("indexingUnit");
		
		/* Hostname for logging */
		hostname	 = InetAddress.getLocalHost().getHostName();
		
		/* Create a connection to SolrCloud */
		this.cloudClient = new CloudSolrServer(zkHost);
		cloudClient.setDefaultCollection("htrc");
		
		this.ht2solr = new HT2Solr(ocrBasePath, marcBasePath, languages, Unit.valueOf(indexingUnit));
	}
	
	/**
	 * Indexes the specified volume.  Given the volume Id, read the OCR text, 
	 * create a set of SolrInputDocuments and post to SolrCloud.
	 * 
	 * @param volumeId
	 * @param rank 		ID of this node for logging purposes.
	 */
	public void index(String volumeId, int nodeId)
	{
		try
		{
			System.out.println("Indexing started for " + volumeId + "," + nodeId);
			
			// Read the volume into one or more SolrInputDocuments
			long t1 = System.currentTimeMillis();
			System.out.println("Reading pages from " + volumeId + "," + nodeId);
			List<SolrInputDocument> docs = ht2solr.getSolrDocuments(volumeId);	
			
			int numPages = docs.size();
			
			if (numPages == 0) {
				System.out.println("Empty " + volumeId + " skipped");
				return;
			}
			System.out.println("Read " + numPages + " from " + volumeId + "," + nodeId);
			
			// Add the document
			int retry = 0;
			long t2 = System.currentTimeMillis();
			do
			{
				try {
					cloudClient.add(docs);			
					break;
				} catch (SolrServerException e) {
					
					retry++;
					try {
						Thread.sleep(1000);
					} catch (Exception ie) { }
					
					System.out.println("Retrying due to server error " + volumeId + ":" + e.getMessage() );	
				}
			} while (retry < 2);
			long t3 = System.currentTimeMillis();
	
			System.out.println("Indexing finished," + hostname + "," + nodeId + "," + volumeId +  "," + numPages + "," + (t2-t1) + "," + (t3-t2));
		} catch (Exception e) {
			System.out.println("Error indexing volume " + volumeId + ":" + e.getMessage());	
			e.printStackTrace();
		}
	}
	
}
