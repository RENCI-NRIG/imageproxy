package orca.imageproxy;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.concurrent.Callable;

import javax.management.AttributeNotFoundException;

public class DownloadRegister extends RegistrationScript implements Callable<String>{
	
	private Map<String, Pair<String, String>> imageInfo; 
	private String type;
	
	public DownloadRegister() throws Exception {
		super();
	}
	
	public String call() throws Exception{
		return downloadAndRegister(imageInfo, type);
	}

	/**
	 * 
	 * @param imageInfo
	 * @param type
	 * @return
	 * @throws Exception
	 */
	private String downloadAndRegister(Map<String, Pair<String, String>> imageInfo, String type) throws Exception{
		
		String guid = imageInfo.get(type).getFirst();
		String url = imageInfo.get(type).getSecond();
		
		l.info("Download and register, Guid: " + guid + ", Url: " + url + ", Type: " + type);
		
		try{
			
			// see if this guid is present in registry
			String imageId = db.checkImageGuid(guid, type, true);

			// null means we get to load it, otherwise wait and return eki
			if (imageId != null) {
				// wait for other threads to load the image
				while (Globals.IMAGE_INPROGRESS.equals(imageId)) {
					l.info("Someone is downloading. Waiting for image " + guid + " to load");
					try {
						// TODO: should replace this with condition variables instead of
						// polling
						Thread.sleep(1000);
					} catch (InterruptedException e) {
						;
					}
					imageId = db.checkImageGuid(guid, type, false);
					if(imageId == null){
						throw new Exception("Exception while downloading/registering image");
					}
				}
			}else{
				boolean[] downloadingflag = new boolean[1];
				String imagePath;
				
				try{
					imagePath = download(guid, url, downloadingflag);
				}catch(Exception exception){
					l.error("Exception while downloading image. Url: " + url);
					throw exception;
				}
								
				if (!downloadingflag[0]){// if this is not a concurrent duplicate request
					try{
						imageId = register(imagePath, type);
					}catch(Exception exception){
						l.error("Exception while registering image.");
						throw exception;
					}
				}
			}
			
			l.info("Image Id: " + imageId);
			
			db.updateImageGuid(guid, imageId, type);
			
			return imageId;
			
		}catch(Exception exception){
			db.removeImageGuid(guid, type);
			throw exception;
		}
	}
	
	/**
	 * Calls the functions to download the required file
	 * @param guid
	 * @param url
	 * @param downloadingflag
	 * @return path of the downloaded file
	 * @throws IOException 
	 * @throws InterruptedException 
	 */
	private String download(String guid, String url, boolean[] downloadingflag) throws Exception {
		
		l.info("Downloading file with guid: " + guid + " from url: " + url);
		
		// calling function to download file
		String imagePath = btDownload.Download(url, guid, downloadingflag);
		
		l.info("File downloaded. Path: " + imagePath);

		return imagePath;
	}

	/**
	 * function to download and register an image
	 * @param imagePath
	 * @return registered image id
	 * @throws AttributeNotFoundException
	 * @throws IOException 
	 * @throws InterruptedException 
	 */
	private String register(String imagePath, String type) throws AttributeNotFoundException, IOException, InterruptedException {

		l.info("Registering downloaded image.");
		l.info("Image location: " + imagePath);
		l.info("Image type: " + type);
		
		String emi = new String();
			
		String registerScript = Globals.getInstance().getProperties().getProperty(registerScriptProperty);
		if ( registerScript == null || registerScript.length() == 0)
			registerScript = DEFAULT_REGISTER_SCRIPT;
		
		String bukkitName = Globals.getInstance().getProperties().getProperty(bukkitNameProperty);
		if ( bukkitName == null || bukkitName.length() == 0)
			bukkitName = DEFAULT_BUKKIT_NAME;
		
		// registration script
		String command = BTDownload.imageproxyHome + File.separator + registerScript + " " + imagePath + " " + bukkitName + " " + type;
		
		l.info("Invoking registration script");
		l.debug(command);
		
		// invoking registration script
		Process process = Runtime.getRuntime().exec(command);
		
		// checking if script ran successfully
		if (process.waitFor() == 0) {
			BufferedReader stdInput = new BufferedReader(
					new InputStreamReader(process.getInputStream()));
			String s;
			if((s = stdInput.readLine()) != null) {
				l.info(s);
				emi = s;
			}else{
				throw new RuntimeException("Exception while registering image.");
			}
		} else {
			BufferedReader stdInput = new BufferedReader(
					new InputStreamReader(process.getInputStream()));
			
			StringBuffer buffer = new StringBuffer();
			String s;
			
			while ((s = stdInput.readLine()) != null)
				buffer.append(s);
			
			l.error(buffer.toString());
			throw new RuntimeException("Exception while registering image.\n" + buffer.toString());
		}

		l.info("Image registered successfully.");

		return emi;
	}
	
	public void setImageInfo(Map<String, Pair<String, String>> imageInfo) {
		this.imageInfo = imageInfo;
	}

	public void setType(String type) {
		this.type = type;
	}
}
