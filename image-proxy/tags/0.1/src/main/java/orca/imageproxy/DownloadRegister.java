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
		
		String signature = imageInfo.get(type).getFirst();
		String url = imageInfo.get(type).getSecond();
		
		l.info("Download and register, Signature: " + signature + ", Url: " + url + ", Type: " + type);
		
		try{
			
			// see if this file with given signature is present in registry
			String imageId = db.checkImageSign(signature, type, true);

			// null means we get to load it, otherwise wait and return eki
			if (imageId != null) {
				// wait for other threads to load the image
				synchronized(db) {
					while (Globals.IMAGE_INPROGRESS.equals(imageId)) {
						l.info("Image download in progress; awaiting completion.");
						try {
							db.wait();
						} catch (InterruptedException e) {
							;
						}
						l.info("Received signal that image download is complete.");
						imageId = db.checkImageSign(signature, type, false);
						if(imageId == null){
							//throw new Exception("Exception while downloading/registering image");
							return downloadAndRegister(imageInfo, type);
						}
					}
				}
			}else{
				boolean[] downloadingflag = new boolean[1];
				String imagePath, hash;
				
				try{
					Pair<String, String> downloadInfo = download(signature, url, downloadingflag);
					imagePath = downloadInfo.getFirst();
					hash = downloadInfo.getSecond();
					
					if(!signature.equals(hash)){
						throw new Exception("Signature mismatch for image file.");
					}
					
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
					}finally{
						// change the registration status to finished so the image can be replaced by an incoming one
						SqliteDLDatabase.getInstance().updateRegistrationStatus(signature);
					}
				}
				db.updateImageInfo(signature, imageId, type);
				synchronized (db) {
					db.notifyAll();
				}
			}
			
			l.info("Image Id: " + imageId);
			
			return imageId;
			
		}catch(Exception exception){
			db.removeImageInfo(signature, type);
			throw exception;
		}
	}
	
	/**
	 * Calls the functions to download the required file
	 * @param signature
	 * @param url
	 * @param downloadingflag
	 * @return path and hash of the downloaded file
	 * @throws IOException 
	 * @throws InterruptedException 
	 */
	private Pair<String, String> download(String signature, String url, boolean[] downloadingflag) throws Exception {
		
		l.info("Downloading file with Signature: " + signature + " from url: " + url);
		
		// calling function to download file
		Pair<String, String> downloadInfo = btDownload.Download(url, signature, downloadingflag);
		
		l.info("File downloaded. Path: " + downloadInfo.getFirst() + " , Hash: " + downloadInfo.getSecond());

		return downloadInfo;
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
