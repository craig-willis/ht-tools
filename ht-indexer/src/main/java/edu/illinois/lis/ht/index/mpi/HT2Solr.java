package edu.illinois.lis.ht.index.mpi;

import gov.loc.repository.pairtree.Pairtree;



import java.io.BufferedReader;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.lang.StringUtils;
import org.apache.solr.common.SolrInputDocument;
import org.marc4j.MarcReader;
import org.marc4j.MarcXmlReader;
import org.marc4j.marc.ControlField;
import org.marc4j.marc.DataField;
import org.marc4j.marc.Record;
import org.marc4j.marc.Subfield;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;


/** 
 * Utility class to convert a set of HT zip, mets, and marc xml files 
 * in to a list of SolrInputDocuments.
 */
public class HT2Solr 
{
	String ocrBasePath = "";
	String marcBasePath = "";

	public static void main(String[] args) throws Exception {
	
		HT2Solr h = new HT2Solr("test", "test");
		h.getSolrDocuments("miua.0048030.1838.001");
		
	}

	public HT2Solr(String ocrBasePath, String marcBasePath)
	{
		this.ocrBasePath = ocrBasePath;
		this.marcBasePath = marcBasePath;
	}
	
	public List<SolrInputDocument> getSolrDocuments(String volumeId) throws Exception
	{
		List<SolrInputDocument> solrDocs = new ArrayList<SolrInputDocument>();
		
		volumeId = URLDecoder.decode(volumeId, "UTF-8");
		Pairtree pt = new Pairtree();
		
        String sourcePart = volumeId.substring(0, volumeId.indexOf("."));
        String volumePart = volumeId.substring(volumeId.indexOf(".")+1, volumeId.length());
        String uncleanId = pt.uncleanId(volumePart);
        String path = pt.mapToPPath(uncleanId);
        String cleanId = pt.cleanId(volumePart);
        
        String zipPath = ocrBasePath + File.separator + sourcePart 
        		+ File.separator + "pairtree_root" 
        		+ File.separator + path 
        		+ File.separator + cleanId
        		+ File.separator + cleanId + ".zip";

        File metsFile = new File( ocrBasePath + File.separator + sourcePart 
        		+ File.separator + "pairtree_root" 
        		+ File.separator + path 
        		+ File.separator + cleanId
        		+ File.separator + cleanId + ".mets.xml" );
      
        File marcFile = new File( marcBasePath + File.separator + sourcePart 
        		+ File.separator + "pairtree_root" 
        		+ File.separator + path 
        		+ File.separator + cleanId
        		+ File.separator + cleanId + ".marc.xml" );

	    String lang = getLanguage(marcFile);
	    if (!lang.equals("eng"))
	    {
	    	System.out.println("Skipping non-English volume");
	    	// Skip non-english documents
	    	return solrDocs;
	    }
	    
		String marc = getMarcData(marcFile);
        
        ZipFile zipFile = new ZipFile(zipPath);
        			
		DocumentBuilderFactory domFactory = 
				DocumentBuilderFactory.newInstance();
		domFactory.setNamespaceAware(true); 
	    DocumentBuilder builder = domFactory.newDocumentBuilder();
	    Document doc = builder.parse(metsFile);
	    XPath xpath = XPathFactory.newInstance().newXPath();
	    xpath.setNamespaceContext(new HTNamespaceContext());	    
	
		    
	    Map<String, MetsFile> metsFileMap = getMetsFileMap(doc, xpath);
	    Map<String, String> pageLabels = getPageLabels(doc, xpath, metsFileMap);
		    

		    
		
		Enumeration<? extends ZipEntry> entries = zipFile.entries();
	
	    while(entries.hasMoreElements()){
	        ZipEntry entry = entries.nextElement();
	
	    	if (entry.isDirectory())
	        	continue;
		    		   
	        String fileName = entry.getName();
	        String dirPart = fileName.substring(0, fileName.indexOf("/"));
	        String namePart = fileName.substring( fileName.indexOf("/") + 1, fileName.length());
	        // Skip the concatenated version of the file
	        if (namePart.equals(dirPart + ".txt")) 
	        	continue;
	        
	        MetsFile mf = metsFileMap.get(namePart);
	        
	        String pageId = mf.getId();
	        String seq = mf.getSeq();
	        
	        String pageNum = namePart.replaceAll("\\..*", "");
	        String documentId = volumeId + "-" + pageId;
		        
	        String text = "";
	        InputStream is = zipFile.getInputStream(entry);
	        
	        BufferedReader br = new BufferedReader(new InputStreamReader(is));
	        String line;
	        while ((line = br.readLine()) != null) 
	        	text += line + "\n";

			        
	        // Skip blank pages
	        if (StringUtils.isBlank(text))
	        	continue;
		      
	        //System.out.println("id=" + documentId + ", volumeId=" + volumeId + ", pageNum=" + pageNum);
	        SolrInputDocument solrDoc = new SolrInputDocument();
	        solrDoc.addField("id", documentId);
	        solrDoc.addField("volumeId", volumeId);
	        solrDoc.addField("pageNum", pageNum);
	        solrDoc.addField("label", pageLabels.get(pageId));
	        solrDoc.addField("marc", marc);
	        solrDoc.addField("text", text);
	        solrDoc.addField("seq", seq);

	        solrDocs.add(solrDoc);
	
	    }
	    return solrDocs;
	}
	

	public static Map<String, String> getPageIdMap(Document doc, XPath xpath) 
			throws XPathExpressionException
	{
	    XPathExpression expr = xpath.compile("/METS:mets/METS:fileSec/METS:fileGrp[@USE='ocr']");

	    Map<String, String> getPageIdMap = new HashMap<String, String>();
	    
	    Object result = expr.evaluate(doc, XPathConstants.NODESET);
	    NodeList nodes = (NodeList) result;
	    for (int i = 0; i < nodes.getLength(); i++) {
	    	Node node = nodes.item(i);
	    	NodeList files = node.getChildNodes();
	    	for (int j = 0; j < files.getLength(); j++) {
	    		Node file = files.item(j);
	    		NamedNodeMap fileAttr = file.getAttributes();
	    		
	    		if (fileAttr != null)
	    		{
	    			NodeList locations = file.getChildNodes();
	    			Node fLocat = locations.item(1);
		    		NamedNodeMap fLocatAttr = fLocat.getAttributes();
	    			String id = fileAttr.getNamedItem("ID").getTextContent();
	    			String seq = fileAttr.getNamedItem("SEQ").getTextContent();		    		
	    			String href = fLocatAttr.getNamedItem("xlink:href").getTextContent();
	    			getPageIdMap.put(id, href);
	    			getPageIdMap.put(href, id);
	    		}
	    	}
	    }
	    return getPageIdMap;		
	}
	
	public static Map<String, MetsFile> getMetsFileMap(Document doc, XPath xpath) 
			throws XPathExpressionException
	{
	    XPathExpression expr = xpath.compile("/METS:mets/METS:fileSec/METS:fileGrp[@USE='ocr']");

	    Map<String, MetsFile> metsFileMap = new HashMap<String, MetsFile>();
	    
	    Object result = expr.evaluate(doc, XPathConstants.NODESET);
	    NodeList nodes = (NodeList) result;
	    for (int i = 0; i < nodes.getLength(); i++) {
	    	Node node = nodes.item(i);
	    	NodeList files = node.getChildNodes();
	    	for (int j = 0; j < files.getLength(); j++) {
	    		Node file = files.item(j);
	    		NamedNodeMap fileAttr = file.getAttributes();
	    		
	    		if (fileAttr != null)
	    		{
	    			NodeList locations = file.getChildNodes();
	    			Node fLocat = locations.item(1);
		    		NamedNodeMap fLocatAttr = fLocat.getAttributes();
	    			String id = fileAttr.getNamedItem("ID").getTextContent();
	    			String seq = fileAttr.getNamedItem("SEQ").getTextContent();		    		
	    			String href = fLocatAttr.getNamedItem("xlink:href").getTextContent();
	    			MetsFile f = new MetsFile();
	    			f.setId(id);
	    			f.setSeq(seq);
	    			f.setHref(href);
	    			
	    			metsFileMap.put(id, f);
	    			metsFileMap.put(href, f);
	    		}
	    	}
	    }
	    return metsFileMap;		
	}
	public static Map<String, String> getPageLabels(Document doc, XPath xpath, Map<String, MetsFile> metsFileMap) 
			throws XPathExpressionException
	{
	    Map<String, String> pageLabelMap = new HashMap<String, String>();
	    XPathExpression expr = xpath.compile("/METS:mets/METS:structMap[@TYPE='physical']/METS:div[@TYPE='volume']");
	    Object result = expr.evaluate(doc, XPathConstants.NODESET);
	    NodeList nodes = (NodeList) result;
	    for (int i = 0; i < nodes.getLength(); i++) {
	    	Node node = nodes.item(i);
	    	NodeList divisions = node.getChildNodes();
	    	for (int j = 0; j < divisions.getLength(); j++) {
	    		Node div = divisions.item(j);
	    		NamedNodeMap divAttr = div.getAttributes();
	    		
	    		if (divAttr != null)
	    		{
	    			//String order = divAttr.getNamedItem("ORDER").getTextContent();	
	    			Node labelNode = divAttr.getNamedItem("LABEL");
	    			String label = "";
	    			if (labelNode != null) 
	    				label = labelNode.getTextContent();	
	    			//String type = divAttr.getNamedItem("TYPE").getTextContent();	
	    			//String orderlabel = divAttr.getNamedItem("ORDERLABEL").getTextContent();	
	    			NodeList pointers = div.getChildNodes();
	    			
	    	    	for (int k = 0; k < pointers.getLength(); k++) {
	    	    		Node ptr = pointers.item(k);
	    	    	
	    	    		NamedNodeMap ptrAttr = ptr.getAttributes();
	    	    		if (ptrAttr != null)
	    	    		{
		    	    		String fileId = ptrAttr.getNamedItem("FILEID").getTextContent();
		    	    		if (metsFileMap.get(fileId) != null) {
		    	    			pageLabelMap.put(fileId, label);
		    	    		}
	    	    		}
	    	    	}
	    		}
	    	}	    	
	    }
	    return pageLabelMap;
	}
	
	public static String getVolumeId(Document doc, XPath xpath) 
			throws XPathExpressionException
	{
		String volumeId = null;
		
	    XPathExpression expr = xpath.compile("/METS:mets");
	    Object result = expr.evaluate(doc, XPathConstants.NODESET);
	    NodeList nodes = (NodeList) result;
	    for (int i = 0; i < nodes.getLength(); i++) {
	    	Node node = nodes.item(i);
	    	NamedNodeMap attr = node.getAttributes();
    		
    		if (attr != null)
    			volumeId = attr.getNamedItem("OBJID").getTextContent();	
    	}
	    return volumeId;
	}
	
	static String getMarcData(File marcXml)
	{		
		StringBuffer marcData = new StringBuffer();
		try
		{
			MarcReader reader = new MarcXmlReader(new FileInputStream(marcXml));
			if (reader.hasNext()) {
				Record record = reader.next();
				String title = getTitle(record);
				String author = getAuthor(record);
				String pubinfo = getPubInfo(record);
				String ids = getIdentifiers(record);
				marcData.append(author);
				marcData.append(title);
				marcData.append(pubinfo);
				marcData.append(ids);
			}
		} catch (IOException e) {
			System.out.println("Warning: unable to read MARC, skipping");
			e.printStackTrace();
		}
		return marcData.toString();
	}
	
	static public String getFields(Record record, Map<String, char[]> fields) {
		StringBuffer fieldData = new StringBuffer();
        @SuppressWarnings("unchecked")
		List<DataField> datafields = (List<DataField>)record.getDataFields();
        for (DataField datafield: datafields)
        {
        	String tag2 = datafield.getTag();
        	for (String tag1: fields.keySet()) 
        	{
        		if (tag2.equals(tag1))
        		{
        			char[] subfields = fields.get(tag1);
        			for (char subfield: subfields) {
        				@SuppressWarnings("unchecked")
						List<Subfield> values = (List<Subfield>)datafield.getSubfields(subfield);
        				for (Subfield value: values) {
        					fieldData.append(" " + value.getData());
        				}
        			}	
        			fieldData.append("\n");
        		}
        		
        	}
        }
        return fieldData.toString();
	}
	
	
	static String getLanguage(File marcXml)
	{		
		String lang = "";
		try
		{
			MarcReader reader = new MarcXmlReader(new FileInputStream(marcXml));
			if (reader.hasNext()) {
				Record record = reader.next();
				List<ControlField> fields = record.getControlFields();
				for (ControlField field: fields) {
					if (field.getTag().equals("008")) {
						String data = field.getData();
						//<controlfield tag="008">940210s1993    dcu      br  f001 0 eng d</controlfield>
						lang = data.substring(35, 38);
					}
				}
			}

		} catch (IOException e) {
			System.out.println("Warning: unable to read MARC, skipping");
			e.printStackTrace();
		}		
		return lang;
	}
	
	
	
	
	public static String getAuthor(Record record)  {
		Map<String, char[]> fields = new HashMap<String, char[]>();
		
		fields.put("100", "abcdegqu".toCharArray());
		fields.put("110", "abcdegnu".toCharArray());
		fields.put("111", "acdegjnqu".toCharArray());
		//fields.put("700", "abcegqu".toCharArray());
		//fields.put("710", "abcdegnu".toCharArray());
		//fields.put("711", "acdegjnqu".toCharArray());
		
		return getFields(record, fields);
	}
	
	public static String getTitle(Record record)  {
		Map<String, char[]> fields = new HashMap<String, char[]>();
		
		fields.put("130", "adfghklmnoprst".toCharArray());
		fields.put("210", "ab".toCharArray());
		fields.put("222", "ab".toCharArray());
		fields.put("240", "adfghklmnoprs".toCharArray());
		fields.put("242", "abnp".toCharArray());
		fields.put("243", "adfghklmnoprs".toCharArray());
		fields.put("245", "abcnps".toCharArray());
		fields.put("246", "abfgnp".toCharArray());
		fields.put("247", "abfgnp".toCharArray());
		fields.put("440", "anpv".toCharArray());
		fields.put("490", "av".toCharArray());
		fields.put("730", "adfgklmnoprst".toCharArray());
		fields.put("740", "anp".toCharArray());
		
		return getFields(record, fields);
	}
	
	public static String getPubInfo(Record record)  {
		Map<String, char[]> fields = new HashMap<String, char[]>();
		
		fields.put("250", "ab".toCharArray());
		fields.put("260", "abcefg".toCharArray());
		
		return getFields(record, fields);
	}
	
	public static String getIdentifiers(Record record)  {
		Map<String, char[]> fields = new HashMap<String, char[]>();
		
		//ISBN
		fields.put("020", "az".toCharArray());
		// ISSN
		fields.put("022", "almyz".toCharArray());
		// Call number
		fields.put("050", "ab".toCharArray());
		// SUDOC
		fields.put("086", "az".toCharArray()); 
		//Call number
		fields.put("090", "ab".toCharArray());
		
		return getFields(record, fields);
	}
	
}

class HTNamespaceContext implements NamespaceContext
{
    public String getNamespaceURI(String prefix)
    {

        if (prefix.equals("METS"))
            return "http://www.loc.gov/METS/";
        else if (prefix.equals("xsi"))
            return "http://www.w3.org/2001/XMLSchema-instance";
        else if (prefix.equals("xlink"))
            return "http://www.w3.org/1999/xlink";
        else if (prefix.equals("PREMIS"))
            return "http://www.loc.gov/standards/premis";
        else
            return XMLConstants.NULL_NS_URI;
    }
    
    public String getPrefix(String namespace)
    {
        if (namespace.equals("http://www.loc.gov/METS/"))
            return "METS";
        else if (namespace.equals("http://www.w3.org/2001/XMLSchema-instance"))
            return "xsi";
        else if (namespace.equals("http://www.w3.org/1999/xlink"))
            return "xlink";
        else if (namespace.equals("http://www.loc.gov/standards/premis"))
            return "PREMIS"; 
        else
            return null;
    }

    @SuppressWarnings("rawtypes")
	public Iterator getPrefixes(String namespace)
    {
        return null;
    }
} 
