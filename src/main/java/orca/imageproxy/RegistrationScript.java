package orca.imageproxy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class RegistrationScript {

	private static final String testModeProperty = "impTestMode";

	protected static final String registerScriptProperty = "registerScriptProperty";
	
	protected static final String DEFAULT_REGISTER_SCRIPT = "scripts/register.sh";
	
	protected static final String bukkitNameProperty = "eucaBukkitName";
	
	protected static final String DEFAULT_BUKKIT_NAME = "imageproxy.bukkit";
	
	private static final String spacesizeProperty = "spacesize";
	
	protected BTDownload btDownload;

	protected Logger l;
	
	private boolean testMode = false;
	
	private int testModeSleep = 30000; // in milliseconds
	
	protected SqliteDatabase db;

	public RegistrationScript() throws Exception {
		try{
			db = SqliteDatabase.getInstance();
			l = Logger.getLogger(this.getClass());
			Properties p = Globals.getInstance().getProperties();
			if (p.containsKey(spacesizeProperty)) {
				String spacesizeString=p.getProperty(spacesizeProperty);
				if(spacesizeString!=null) {
					int factor = 1;
					try{
						factor = Integer.parseInt(spacesizeString);
					}catch(NumberFormatException e){
						throw new NumberFormatException("can't recognize the number format of property spacesize.");
					}
					if(factor > 0)
						BTDownload.SPACESIZE = BTDownload.SPACESIZE * factor;
					else
						throw new NumberFormatException("the spacesize should be larger than 0.");
				}
			}
			if (p.containsKey(testModeProperty)) {
				String testModeString = p.getProperty(testModeProperty);
				if (testModeString.toLowerCase().equals("true") || 
						testModeString.toLowerCase().equals("yes")) {
					testMode = true;
					if (p.containsKey("testModeSleep"))
						testModeSleep = Integer.parseInt(p.getProperty("testModeSleep")) * 1000;
				}
			}
			btDownload = BTDownload.getInstance();
		}catch(Exception exception){
			l.error("Exception during initialization.");
			l.error(exception.toString(), exception);
			throw exception;
		}
	}

	/**
	 * Method to register images
	 * @param url  url for image metadata
	 * @param guid hash of the image, to uniquely identify it
	 * @return registered image ids, ERROR in case of any exception
	 */
	public String RegisterImage(String url) {

		try{

			Properties imageIds = new Properties();
			
			if (testMode) {
				l.info("Test mode enabled. Sleeping " + testModeSleep + " msec.");
				try {
					Thread.sleep(testModeSleep);
				} catch (InterruptedException e) {
					;
				}
				l.info("Awake");
				// fake cheap unique image ids
				Double uniq = Math.floor(Math.random() * 1000);
				imageIds.put(Globals.FILE_SYSTEM_IMAGE_KEY, url + uniq.intValue());
				uniq = Math.floor(Math.random() * 1000);
				imageIds.put(Globals.KERNEL_IMAGE_KEY, url + uniq.intValue());
				uniq = Math.floor(Math.random() * 1000);
				imageIds.put(Globals.RAMDISK_IMAGE_KEY, url + uniq.intValue());
				
			} else {
				
				String emi, eki, eri;
				
				try{
					l.info("Entering download and registration process");
					
					Map<String, Pair<String, String>> imageInfo = parseMetadata(url);
					
					if(!imageInfo.containsKey(Globals.FILE_SYSTEM_IMAGE_KEY)){
						l.error("Valid filesystem image information could not be found in the metadata.");
						return Globals.ERROR_CODE;
					}
						
					HashMap<String, Future<String>> downloadRegisterTasks = new HashMap<String, Future<String>>();
					
					//
					downloadRegisterTasks.put(Globals.FILE_SYSTEM_IMAGE_KEY, downloadAndRegister(imageInfo, Globals.FILE_SYSTEM_IMAGE_KEY));
					
					if(imageInfo.containsKey(Globals.KERNEL_IMAGE_KEY)){
						l.info("Kernel image information available");
						downloadRegisterTasks.put(Globals.KERNEL_IMAGE_KEY, downloadAndRegister(imageInfo, Globals.KERNEL_IMAGE_KEY));
					}
					
					if(imageInfo.containsKey(Globals.RAMDISK_IMAGE_KEY)){
						l.info("Ramdisk image information available");
						downloadRegisterTasks.put(Globals.RAMDISK_IMAGE_KEY, downloadAndRegister(imageInfo, Globals.RAMDISK_IMAGE_KEY));
					}
					
					//
					emi = downloadRegisterTasks.get(Globals.FILE_SYSTEM_IMAGE_KEY).get();
					imageIds.put(Globals.FILE_SYSTEM_IMAGE_KEY, emi);
					
					if(imageInfo.containsKey(Globals.KERNEL_IMAGE_KEY)){
						eki = downloadRegisterTasks.get(Globals.KERNEL_IMAGE_KEY).get();
						imageIds.put(Globals.KERNEL_IMAGE_KEY, eki);
					}
					
					if(imageInfo.containsKey(Globals.RAMDISK_IMAGE_KEY)){
						eri = downloadRegisterTasks.get(Globals.RAMDISK_IMAGE_KEY).get();
						imageIds.put(Globals.RAMDISK_IMAGE_KEY, eri);
					}
					
				}catch(Exception exception){
					l.error(exception.toString(), exception);
					return Globals.ERROR_CODE;
				}
				
			}
			
			l.info("Filesystem Image id: " + imageIds.get(Globals.FILE_SYSTEM_IMAGE_KEY));
			if(imageIds.get(Globals.KERNEL_IMAGE_KEY) != null){
				l.info("Kernel Image id: " + imageIds.get(Globals.KERNEL_IMAGE_KEY));
			}
			if(imageIds.get(Globals.RAMDISK_IMAGE_KEY) != null){
				l.info("Ramdisk Image id: " + imageIds.get(Globals.RAMDISK_IMAGE_KEY));
			}
			
			return toString(imageIds);
			
		}catch(Exception exception){
			l.error(exception.toString(), exception);
			return Globals.ERROR_CODE;
		}
	}
	
	private Future<String> downloadAndRegister(Map<String, Pair<String, String>> imageInfo, String type) throws Exception {
		
		DownloadRegister downloadRegister = new DownloadRegister();
		downloadRegister.setImageInfo(imageInfo);
		downloadRegister.setType(type);
		
		ExecutorService executorService = Executors.newSingleThreadExecutor();
		Future<String> downloadRegisterTask = executorService.submit(downloadRegister);
		executorService.shutdown();
		
		return downloadRegisterTask;
	}
	
	/**
	 * 
	 * @param metadataUrl
	 * @return
	 * @throws Exception
	 */
	private Map<String, Pair<String, String>> parseMetadata(String metadataUrl) throws Exception{
		
		List<String> validImageTypes = new ArrayList<String>();
		validImageTypes.add(Globals.FILE_SYSTEM_IMAGE_KEY);
		validImageTypes.add(Globals.KERNEL_IMAGE_KEY);
		validImageTypes.add(Globals.RAMDISK_IMAGE_KEY);
		
		Map<String, Pair<String, String>> imageInfo = new HashMap<String, Pair<String,String>>();
		
		try {

			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			DocumentBuilder db = dbf.newDocumentBuilder();
		  
			Document doc = db.parse(new URL(metadataUrl).openStream());
			doc.getDocumentElement().normalize();
		  
			l.info("Root element " + doc.getDocumentElement().getNodeName());
		  
			l.info("Information of all images");
			
			NodeList imgNodeList = doc.getElementsByTagName("image");
			
			String type, guid, url;
			
			for (int itr = 0; itr < imgNodeList.getLength(); itr++) {

				Node imgNode = imgNodeList.item(itr);
		    
				if (imgNode.getNodeType() == Node.ELEMENT_NODE) {
		  
					Element imgElmnt = (Element) imgNode;
					
					NodeList imgTypeElmntLst = imgElmnt.getElementsByTagName("type");
					Element imgTypeElmnt = (Element) imgTypeElmntLst.item(0);
					NodeList imageType = imgTypeElmnt.getChildNodes();
					if(imageType.getLength()!=0 && validImageTypes.contains(((Node) imageType.item(0)).getNodeValue().trim())){
						type = ((Node) imageType.item(0)).getNodeValue();
						l.info("Image Type : "  + type);
					}else{
						l.info("Invalid image type");
						continue;
					}
					
					NodeList imgGuidElmntLst = imgElmnt.getElementsByTagName("guid");
					Element imgGuidElmnt = (Element) imgGuidElmntLst.item(0);
					NodeList imgGuid = imgGuidElmnt.getChildNodes();
					if(imgGuid.getLength()!=0 && !((Node) imgGuid.item(0)).getNodeValue().trim().equals("")){
						guid = ((Node) imgGuid.item(0)).getNodeValue();
						l.info("Guid : " + guid);
					}else{
						l.info("Invalid Guid");
						continue;
					}
					
					NodeList imgUrlElmntLst = imgElmnt.getElementsByTagName("url");
					Element imgUrlElmnt = (Element) imgUrlElmntLst.item(0);
					NodeList imgUrl = imgUrlElmnt.getChildNodes();
					if(imgUrl.getLength()!=0 && !((Node) imgUrl.item(0)).getNodeValue().trim().equals("")){
						url = ((Node) imgUrl.item(0)).getNodeValue();
						l.info("Url : " + url);
					}else{
						l.info("Invalid Url");
						continue;
					}
					
					imageInfo.put(type, new Pair<String, String>(guid, url));
				}
			}
			
		}catch(Exception exception){
			l.error("Exception while parsing metadata.");
			throw exception;
		}
		
		return imageInfo;
	}
	
	/**
     * Serializes a properties list to a string
     * @param properties The properties list
     * @return The string representation of this properties list
	 * @throws IOException 
     */
    private static String toString(Properties properties) throws IOException
    {
        if ((properties != null) && (properties.size() > 0)) {
                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                properties.store(stream, null);
                return stream.toString();
        } else {
            return "";
        }
    }
}