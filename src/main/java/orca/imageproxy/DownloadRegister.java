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
	private String downloadAndRegister(Map<String, Pair<String, String>> imageInfo, String type) throws Exception {
		
		ActiveDownloadMap dlMap = ActiveDownloadMap.getInstance();
		String wakeObject;
		String methodName = Thread.currentThread().getStackTrace()[1].getMethodName();
		String signature = imageInfo.get(type).getFirst();
		String url = imageInfo.get(type).getSecond();
		
		l.info("Download and register, Signature: " + signature + 
			", Url: " + url + ", Type: " + type);
		
		try {
			// see if this file with given signature is present in registry
			String imageId = db.checkImageSignature(signature, type, true);
			wakeObject = (String) dlMap.get(signature+methodName);
			if (wakeObject == null) {
				String newWakeObject;
				if (imageId == null)
					newWakeObject = new String(Globals.IMAGE_INPROGRESS);
				else
					newWakeObject = new String(imageId);
				wakeObject = (String) dlMap.putIfAbsent(signature+methodName,
									newWakeObject);
				if (wakeObject == null) wakeObject = newWakeObject;
			}

			// null means we get to load it, otherwise wait and return image id
			if (imageId != null) {
				// wait for other threads to load the image
				synchronized(wakeObject) {
					while (Globals.IMAGE_INPROGRESS.equals(imageId)) {
						l.info("Image download from URL: " + url +
							" in progress; awaiting completion.");
						try {
							wakeObject.wait();
						} catch (InterruptedException e) {
							;
						}
						l.info("Awakened while waiting" +
							" for image to download; " +
							"checking to see if complete...");
						imageId = db.checkImageSignature(signature, type, false);
						if(imageId == null)
							return downloadAndRegister(imageInfo, type);
					}
				}
			} else {
				String imagePath, hash;
				try {
					Pair<String, String> downloadInfo = download(signature, url);
					imagePath = downloadInfo.getFirst();
					hash = downloadInfo.getSecond();
					
					if (!signature.equals(hash)) {
						throw new Exception("Provided signature " + signature +
								" does not match computed signature " +
								hash + " for image at URL: " + url);
					}
					
				}
				catch (Exception exception) {
					l.error("Exception while downloading image from URL: " + url);
					throw exception;
				}
								
				try {
					imageId = register(imagePath, type);
				}
				catch (Exception exception){
					l.error("Exception encountered while attempting " +
						"to register image.");
					throw exception;
				}
				finally {
					// change the registration status to finished so the image,
					// if required, can be replaced by an incoming one
					btDownload.removeReference(signature);
				}
				
				db.updateImageInfo(signature, imageId, type);
			}
			
			l.info("Image Id: " + imageId);
			
			return imageId;
			
		}
		catch (Exception exception) {
			db.removeImageInfo(signature, type);
			throw exception;
		}
		finally {
			wakeObject = (String) dlMap.get(signature+methodName);
			if (wakeObject != null) {
				synchronized (wakeObject) {
					wakeObject.notifyAll();
				}
				dlMap.remove(signature+methodName);
			}
		}
	}
	
	/**
	 * function to download and register an image
	 * @param imagePath
	 * @return registered image id
	 * @throws AttributeNotFoundException
	 * @throws IOException 
	 * @throws InterruptedException 
	 */
	private String register(String imagePath, String type)
            throws AttributeNotFoundException, IOException, InterruptedException {

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
		String command =
                    BTDownload.imageproxyHome + File.separator + registerScript + " " +
                    imagePath + " " + bukkitName + " " + type + " " + Globals.getInstance().getTmpDir();
		
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
