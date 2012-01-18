package orca.imageproxy;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
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
         * @param signature hash of the image, to uniquely identify it
         * @return registered image ids, ERROR in case of any exception
         */
        public String RegisterImage(String url, String signature) throws Exception{

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
                                
                                Pair<String, String> downloadInfo = download(signature, url);
                                String imagePath = downloadInfo.getFirst();
                                String hash = downloadInfo.getSecond();
                                
                                if(!hash.equals(signature)){
                                        throw new Exception("Provided signature " + signature +
                                                        " does not match computed signature " +
                                                        hash + " for metadata file at URL: " + url);
                                }
                                
                                String emi, eki, eri;
                                
                                try{
                                        l.info("Entering download and registration process");
                                        
                                        Map<String, Pair<String, String>> imageInfo = parseMetadata(imagePath);
                                        
                                        SqliteDLDatabase.getInstance().removeReference(signature);
                                        
                                        HashMap<String, Future<String>> downloadRegisterTasks = new HashMap<String, Future<String>>();

                                        if (imageInfo.containsKey(Globals.FILE_SYSTEM_IMAGE_KEY)) {
                                            downloadRegisterTasks.put(Globals.FILE_SYSTEM_IMAGE_KEY, downloadAndRegister(imageInfo, Globals.FILE_SYSTEM_IMAGE_KEY));
                                        }
                                        else if (imageInfo.containsKey(Globals.ZFILE_SYSTEM_IMAGE_KEY)) {
                                            downloadRegisterTasks.put(Globals.ZFILE_SYSTEM_IMAGE_KEY, downloadAndRegister(imageInfo, Globals.ZFILE_SYSTEM_IMAGE_KEY));
                                        }
                                        else {
                                            l.error("Valid filesystem image information could not be found in the metadata.");
                                            //return Globals.ERROR_CODE;
                                            throw new Exception("Valid filesystem image information could not be found in the metadata.");
                                        }
                                                
                                        if(imageInfo.containsKey(Globals.KERNEL_IMAGE_KEY)){
                                                l.info("Kernel image information available");
                                                downloadRegisterTasks.put(Globals.KERNEL_IMAGE_KEY, downloadAndRegister(imageInfo, Globals.KERNEL_IMAGE_KEY));
                                        }
                                        
                                        if(imageInfo.containsKey(Globals.RAMDISK_IMAGE_KEY)){
                                                l.info("Ramdisk image information available");
                                                downloadRegisterTasks.put(Globals.RAMDISK_IMAGE_KEY, downloadAndRegister(imageInfo, Globals.RAMDISK_IMAGE_KEY));
                                        }
                                        
					//
                                        Future<String> futStr = downloadRegisterTasks.get(Globals.FILE_SYSTEM_IMAGE_KEY);
                                        if (futStr == null)
                                            futStr = downloadRegisterTasks.get(Globals.ZFILE_SYSTEM_IMAGE_KEY);
                                        emi = futStr.get();
                                        imageIds.put(Globals.FILE_SYSTEM_IMAGE_KEY, emi);
                                        
                                        if (imageInfo.containsKey(Globals.KERNEL_IMAGE_KEY)) {
                                                eki = downloadRegisterTasks.get(Globals.KERNEL_IMAGE_KEY).get();
                                                imageIds.put(Globals.KERNEL_IMAGE_KEY, eki);
                                        }
                                        
                                        if (imageInfo.containsKey(Globals.RAMDISK_IMAGE_KEY)) {
                                                eri = downloadRegisterTasks.get(Globals.RAMDISK_IMAGE_KEY).get();
                                                imageIds.put(Globals.RAMDISK_IMAGE_KEY, eri);
                                        }
                                        
                                } catch (Exception exception) {
                                        l.error(exception.toString(), exception);
                                        throw exception;
                                }
                                
                        }
                        
                        l.info("Filesystem Image id: " + imageIds.get(Globals.FILE_SYSTEM_IMAGE_KEY));
                        if (imageIds.get(Globals.KERNEL_IMAGE_KEY) != null)
                                l.info("Kernel Image id: " + imageIds.get(Globals.KERNEL_IMAGE_KEY));
                        if (imageIds.get(Globals.RAMDISK_IMAGE_KEY) != null)
                                l.info("Ramdisk Image id: " + imageIds.get(Globals.RAMDISK_IMAGE_KEY));
                        
                        return toString(imageIds);
                        
                } catch (Exception exception) {
                        l.error(exception.toString(), exception);
                        throw exception;
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
	 * @param metadataFilePath
	 * @return
	 * @throws Exception
	 */
	private Map<String, Pair<String, String>> parseMetadata(String metadataFilePath) throws Exception {
		
		List<String> validImageTypes = new ArrayList<String>();
		validImageTypes.add(Globals.FILE_SYSTEM_IMAGE_KEY);
		validImageTypes.add(Globals.ZFILE_SYSTEM_IMAGE_KEY);
		validImageTypes.add(Globals.KERNEL_IMAGE_KEY);
		validImageTypes.add(Globals.RAMDISK_IMAGE_KEY);
		
		Map<String, Pair<String, String>> imageInfo = new HashMap<String, Pair<String,String>>();
		
		try {

			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			DocumentBuilder db = dbf.newDocumentBuilder();
		  
			Document doc = db.parse(new File(metadataFilePath));
			doc.getDocumentElement().normalize();
		  
			l.info("Root element " + doc.getDocumentElement().getNodeName());
		  
			l.info("Information of all images");
			
			NodeList imgNodeList = doc.getElementsByTagName("image");
			
			String type, signature, url;
			
			for (int itr = 0; itr < imgNodeList.getLength(); itr++) {

				Node imgNode = imgNodeList.item(itr);
		    
				if (imgNode.getNodeType() == Node.ELEMENT_NODE) {
		  
					Element imgElmnt = (Element) imgNode;

					NodeList imgUrlElmntLst = imgElmnt.getElementsByTagName("url");
					Element imgUrlElmnt = (Element) imgUrlElmntLst.item(0);
					NodeList imgUrl = imgUrlElmnt.getChildNodes();
					if (imgUrl.getLength() != 0) {
						url = ((Node) imgUrl.item(0)).getNodeValue().trim();
						if (url.equals("")) {
							l.info("URL element cannot be empty.");
							continue;
						}
						else {
							l.info("URL: " + url);
						}
					}
					else {
						l.info("URL element missing.");
						continue;
					}
					
					NodeList imgTypeElmntLst = imgElmnt.getElementsByTagName("type");
					Element imgTypeElmnt = (Element) imgTypeElmntLst.item(0);
					NodeList imgType = imgTypeElmnt.getChildNodes();
					if (imgType.getLength() != 0) {
						type = ((Node) imgType.item(0)).getNodeValue().trim();
						if (validImageTypes.contains(type)) {
							l.info("Image Type: "  + type);
						}
						else {
							l.info("Invalid image type: \"" + type +
								"\" for image URL: " + url);
							continue;
						}
					}
					else {
						l.info("Type element missing for image URL: " + url);
						continue;
					}
					
					NodeList imgSignElmntLst =
						imgElmnt.getElementsByTagName("signature");
					Element imgSignElmnt = (Element) imgSignElmntLst.item(0);
					NodeList imgSign = imgSignElmnt.getChildNodes();
					if (imgSign.getLength() != 0) {
						signature = ((Node) imgSign.item(0)).getNodeValue().trim();
						if (signature.equals("")) {
							l.info("Signature element cannot be empty " +
								"for image URL: " + url);
							continue;
						}
						else {
							l.info("Signature: " + signature);
						}
					}
					else {
						l.info("Signature element missing for image URL: " + url);
						continue;
					}
					
					imageInfo.put(type, new Pair<String, String>(signature, url));
				}
			}
			
		}
		catch (Exception exception){
			l.error("Exception while parsing metadata.");
			throw exception;
		}
		
		return imageInfo;
	}
	
	/**
	 * Calls the functions to download the required file
	 * @param signature
	 * @param url
	 * @return signature and url of the file to be downloaded
	 * @throws IOException 
	 * @throws InterruptedException 
	 */
        Pair<String, String> download(String signature, String url) throws Exception {
		
		l.info("Downloading file with signature: " + signature + " from url: " + url);
		
		// calling function to download file
		Pair<String, String> downloadInfo = btDownload.downloadFile(url, signature);
		
		l.info("File downloaded. Path: " + downloadInfo.getFirst() + " , Signature: " + downloadInfo.getSecond());

		return downloadInfo;
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
