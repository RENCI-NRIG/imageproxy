package orca.imageproxy; 

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import javax.management.AttributeNotFoundException;

import org.apache.log4j.Logger;

public class BTDownload {
	
	Logger l;

	public final static String imageproxyHome = System.getenv("IMAGEPROXY_HOME");
	
	// the directory the files are downloaded to
	private final static String DOWNLOADFOLDER = imageproxyHome + File.separator + "download";

	// the total available space to cache files
	private static long CACHE_SIZE = (long) Math.pow(2, 30);
	private static String cacheSizeProperty = "spacesize";

	private static BTDownload btdownload = null;
	private SqliteDLDatabase sqliteDLDatabase;

	private static final String DOWNLOADTYPE_BT = "BT";
	private static final String DOWNLOADTYPE_HTTP = "HTTP";

	public synchronized static BTDownload getInstance()  throws Exception{
		if (btdownload == null)
			btdownload = new BTDownload();
		return btdownload;
	}

	private BTDownload() throws Exception{
		
		l = Logger.getLogger(this.getClass());
		
		if(imageproxyHome == null){
			throw new AttributeNotFoundException(
			"Please set environment variable: IMAGEPROXY_HOME");
		}
		
		sqliteDLDatabase = SqliteDLDatabase.getInstance();
		
		Properties p = Globals.getInstance().getProperties();
		if (p.containsKey(cacheSizeProperty)) {
			String spacesizeString=p.getProperty(cacheSizeProperty);
			if(spacesizeString!=null) {
				long newCacheSize;
				float factor;
				try {
					factor = Float.parseFloat(spacesizeString);
				}
				catch (NumberFormatException e) {
					l.warn("Hrm. " + cacheSizeProperty + " is specified " +
						"in an unrecognized numbering system; " +
						"using the default of " + BTDownload.CACHE_SIZE + 
						" until user decides simple decimals are a valid option.");
					factor = 1;
				}
				newCacheSize = (long) (BTDownload.CACHE_SIZE * factor);
				if (newCacheSize > 0) {
					BTDownload.CACHE_SIZE = newCacheSize;
				}
				else {
					l.warn("Requested " + cacheSizeProperty + " would result " +
						"in a cache size of 0 or less. Defaulting to " +
						BTDownload.CACHE_SIZE +
						" until user can quit fooling around.");
				}
			}
		}
		
		l.info("Available space for downloads = "+ CACHE_SIZE + " bytes");
		
		new File(BTDownload.DOWNLOADFOLDER).mkdir();
		initSession(imageproxyHome);
		
		//clear the files in downloading status
		l.info("attempting to clear the downloading files left by the last nasty crash");
		if(!this.recover())
			l.info("no downloading file needs to be cleared.");
		
	}
	
	/**
	 * function to download file with given url and signature, if the same is not already cached
	 * @param surl
	 * @param signature
	 * @return a Pair of the file path and the file's correct signature (SHA-1 hash)
	 * @throws Exception
	 */
        public Pair<String, String> downloadFile(String surl, String signature) throws Exception
	{
		ActiveDownloadMap dlMap = ActiveDownloadMap.getInstance();
		String wakeObject;
		String methodName = Thread.currentThread().getStackTrace()[1].getMethodName();

		try {
			URL url = new URL(surl);
			String filename = url.getFile();
			
			String downloadType = DOWNLOADTYPE_HTTP;
			if (filename.endsWith(".torrent")) 
				downloadType = DOWNLOADTYPE_BT;

			String filePath = sqliteDLDatabase.checkDownloadList(signature, true,
									surl, downloadType);
			wakeObject = (String) dlMap.get(signature+methodName);
			if (wakeObject == null) {
				String newWakeObject;
				if (filePath == null)
					newWakeObject = new String(Globals.IMAGE_INPROGRESS);
				else
					newWakeObject = new String(filePath);
				wakeObject = (String) dlMap.putIfAbsent(signature+methodName,
									newWakeObject);
				if (wakeObject == null) wakeObject = newWakeObject;
			}
			
			// null means we get to load it, otherwise wait and return file information
			if (filePath != null) {
				synchronized(wakeObject) {
					while (Globals.IMAGE_INPROGRESS.equals(filePath)) {
						l.info("File download for URL: " + surl +
							" in progress; awaiting completion.");
						try {
							// wait some other thread to download the file
							wakeObject.wait();
						} catch (InterruptedException e) {
							;
						}
						l.info("Awakened while waiting for file to download; " +
							"checking to see if complete...");
						filePath = sqliteDLDatabase.checkDownloadList(signature, 
											false, null, null);
						
						//Exception while downloading file
						if(filePath == null) {
                                                    return downloadFile(surl, signature);
						}
					}
					l.info("File download from URL: " + surl + " complete.");
					return new Pair<String, String>(filePath, signature);
				}
			} else {
				l.info("Downloading file from URL: " + surl);
				Pair<String, String> fileInfo = controller(signature, surl, downloadType);
				l.info("File downloaded from URL: " + surl);
				return fileInfo;
			}
			
		} catch(Exception e) {
			sqliteDLDatabase.deleteEntry(signature);
			throw e;
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
	 * Controls the download of a given file
	 * @param fileSignature
	 * @param surl
	 * @return downloaded file path and signature
	 * @throws Exception
	 */
        private Pair<String, String> controller(String fileSignature, String surl, String downloadType) throws Exception
	{
		findFreeStorage(fileSignature, surl, downloadType);
		
		String correctSign = downloadfromURL(surl, fileSignature, downloadType);
		
		if (fileSignature.equals(correctSign)) {
			l.info("File finished downloading; signature verified.");
			return new Pair<String, String>(BTDownload.DOWNLOADFOLDER + 
							File.separator + fileSignature, fileSignature);
		}
		else {
			l.info("File finished downloading, but failed signature verification.");
			return new Pair<String, String>("INVALID SIGNATURE", correctSign);
		}
		
	}
	
	/**
	 * Checks if enough storage is available to download the file.
	 * If not, it tries to allocate sufficient storage to store the file
	 * by emptying the storage cache according to an LRU policy.
	 * If there is still insufficient storage after trying to clear the
	 * storage cache, an exception will be thrown.
	 * @param fileSignature
	 * @param surl
         * @param downloadType
	 * @throws Exception
	 */
        private synchronized void findFreeStorage (String fileSignature, String surl, String downloadType)
	throws Exception {
		//fetching the size of the file to be downloaded
		long fileSize = getFileSize(surl, downloadType);
		l.info("File (" + fileSignature + ") size is " + fileSize + "bytes");
		
		//calculating space used up by existing data
		long existingdatasize = sqliteDLDatabase.getExistingDataSize();
		
		l.info("Downloaded + downloading files size = " + existingdatasize);
		l.info("Total space reserved for downloading files is " + BTDownload.CACHE_SIZE);
		
		//deleting unused files to make space
		while (fileSize > (BTDownload.CACHE_SIZE - existingdatasize)) {
			Entry e = sqliteDLDatabase.getMostStaleEntry();
			
			if (e == null) {
				throw new IOException("Insufficient storage to download file: " +
							fileSignature + " from URL: " + surl);
			}
			l.info("File " + e.getSignature() + "(" + e.getFilesize() +
				" bytes) is going to be deleted");
			
			if (DOWNLOADTYPE_BT.equals(e.getDownloadType())) {
				try{
					deleteImageBT(e.getSignature(), e.getTorrentFilePath());
				}catch(Exception exception){
					throw new IOException("Couldn't delete file " + e.getFilePath());
				}
			}
			else {
				File file = new File(e.getFilePath());
				boolean result = file.delete();
				if (!result) {
					throw new IOException("Couldn't delete file " + e.getFilePath());
				}
			}
			
			//delete entry (for the deleted file) from database
			sqliteDLDatabase.deleteEntry(e.getSignature());
			
			l.info("File (" + e.getSignature() + ") (" +
				e.getFilesize() + " bytes) is deleted");
			
			//reset existing data size
			existingdatasize -= e.getFilesize();
		}
		
		sqliteDLDatabase.updateFileSize(fileSignature, fileSize);
	}
	
	/**
	 * Function of fetch size of file corresponding to the given URL
	 * @param surl
         * @param downloadType
	 * @return file size
	 * @throws Exception
	 */
	private long getFileSize(String surl, String downloadType) throws Exception{
	
		long fileSize = 0;
		
		URL url = new URL(surl);
		
		if (downloadType.equals(DOWNLOADTYPE_BT)) {
			fileSize = getFileLength(surl);
		}else {
			HttpURLConnection urlcon = (HttpURLConnection)url.openConnection();
			try{
				urlcon.connect();
			}catch(IOException ioException){
				throw new IOException("Unable to connect to " + url);
			}
			
			try{
				String fileLength = urlcon.getHeaderField("Content-Length");
				long length = Long.parseLong(fileLength);
				if(length <= 0)
					throw new IOException("Failure while attempting to fetch file size");
				fileSize = length;
			}catch(Exception exception){
				throw new IOException("Could not fetch file size for " + url);
			}
			
			urlcon.disconnect();
		}
		
		return fileSize;
	}
	
	/**
	 * If the file is bittorrent file, then download by using bt protocol, else download using http 
	 * @param surl
	 * @param signature
	 * @return correct file signature
	 * @throws Exception
	 */
	private String downloadfromURL(String surl, String signature, String downloadType) throws Exception{
		
		if (downloadType.equals(DOWNLOADTYPE_BT)) {
			// download by bt protocol
			return btdownloadfromURL(surl, signature);
		} else {
			// download file directly
			return httpdownloadfromURL(surl, signature);
		}
	}
	
	/**
	 * Download file using http
	 * @param url
	 * @param signature
	 * @return correct signature
	 * @throws SQLException
	 * @throws IOException
	 * @throws NoSuchAlgorithmException
	 * @throws URISyntaxException 
	 */
	private String httpdownloadfromURL(String surl, String signature) throws SQLException, IOException, NoSuchAlgorithmException, URISyntaxException
	{
		
		URL url = new URL(surl);
		
		BufferedInputStream bis;
		try {
			HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			bis = new BufferedInputStream(connection.getInputStream());
		}
		catch (IOException ioException) {
			throw new IOException("Error encountered while attempting to " +
						"establish HTTP connection to URL: " +
						surl + " ; reason was: " + ioException.getMessage());
		}
		
		File newfile = new File(BTDownload.DOWNLOADFOLDER + File.separator + signature);
		
		try {
			BufferedOutputStream fos = new BufferedOutputStream(new FileOutputStream(newfile));
			int b;
			while ((b = bis.read()) != -1) fos.write(b);
			fos.flush();
			fos.close();
		}
		catch (IOException ioException) {
			throw new IOException("Error encountered while writing to file: " +
						newfile.getPath() + " ; reason was: " +
						ioException.getMessage());
		}
		
		String correctHash = Util.getFileHash(newfile.getPath());
		if (correctHash.equals(signature)) {
			sqliteDLDatabase.updateFilePath(correctHash, newfile.getPath());
			sqliteDLDatabase.updateDownloadStatus(correctHash, 1);
		}
		else {
			// Uh-oh. Somebody made a mistake with the hash they
			// provided, or else tampering may have occurred.
			// Clean up, and fail loudly.
			l.warn("The provided signature " + signature +
				" does not match the computed signature " + correctHash);
			sqliteDLDatabase.deleteEntry(signature);
			if(!newfile.delete())
				l.warn("An error occurred while trying to delete: " +
					newfile.getPath() + " ; manual cleanup may be required.");
		}
		
		return correctHash;
	}

	public native void initSession(String rootfolder);
	
	/**
	 * get length of a file to be downloaded by bt protocol
	 * @param btUrl
	 * @param downloadFolder
	 * @return file length
	 * @throws Exception
	 */
	public native long getFileLength(String btUrl)
			throws Exception;

	private static native void deleteImageBT(String signature, String torrentFilePath) throws Exception;
	
	public native String btdownloadfromURL(String surl, String signature)
	throws Exception;
	
	
	/**
	 * This method gets invoked when bittorrent download completes
	 * @param entry
	 * @return
	 * @throws Exception
	 */
	public String callbackComplete(String entry) throws Exception
	{
		String[] items = entry.split("#");
		String signature = items[0];
		String url = items[1];
		String torrentFilePath = items[2];
		
		String correctHash = Util.getFileHash(BTDownload.DOWNLOADFOLDER + File.separator +
							signature);
		if (correctHash.equals(signature)) {
			sqliteDLDatabase.updateFilePath(correctHash,
							BTDownload.DOWNLOADFOLDER + File.separator +
							correctHash);
			sqliteDLDatabase.updateTorrentFilePath(correctHash, torrentFilePath);
			sqliteDLDatabase.updateDownloadStatus(correctHash, 1);
		}
		else {
			// Uh-oh. Somebody made a mistake with the hash they
			// provided, or else tampering may have occurred.
			// Clean up, and fail loudly.
			l.warn("The provided signature " + signature +
				" does not match the computed signature " + correctHash);
			sqliteDLDatabase.deleteEntry(signature);
			deleteImageBT(signature, torrentFilePath);
		}
		
		return correctHash;
	}
	
	/**
	 * Function to be called once the downloaded file is no longer referenced.
	 * This is put the current image into the pool of image that can be, if required, deleted.
	 * @param signature
	 * @throws SQLException 
	 */
	public void removeReference(String signature) throws SQLException{
		sqliteDLDatabase.removeReference(signature);
	}
	
	/**
	 * This function clears all the incomplete files and corresponding database entries.
	 * @return true - if anything needed to be done
	 * @return false - otherwise
	 * @throws Exception
	 */
	private boolean recover() throws Exception{
		List<Entry> downloadingFiles = sqliteDLDatabase.getDownloadingFiles();
		Iterator<Entry> itr = downloadingFiles.iterator();
		
		if(!itr.hasNext())
			return false;
		
		Entry e;
		while(itr.hasNext()){
			
			e = itr.next();
			
			if(DOWNLOADTYPE_BT.equals(e.getDownloadType())){
				try{
					deleteImageBT(e.getSignature(), e.getTorrentFilePath());
				}catch(Exception exception){
					l.error("Couldn't delete file " + e.getFilePath());
				}
			}else{
				File file = new File(e.getFilePath());
				boolean result = file.delete();
				if (!result){
					l.error("Couldn't delete file " + e.getFilePath());
				}
			}
			
			//delete entry (for the deleted file) from database
			sqliteDLDatabase.deleteEntry(e.getSignature());
			
			l.info("File (" + e.getSignature() + ") (" + e.getFilesize() + " bytes) is deleted");
		}
		
		deleteIncompleteFiles();
		
		l.info("Recovery Complete");
		
		return true;
	}
	
	/**
	 * This function deletes all the incomplete files.
	 */
	private void deleteIncompleteFiles(){
		File downloadDir = new File(DOWNLOADFOLDER);
		File[] filelist = downloadDir.listFiles();
		for(int i=0;i<filelist.length;i++){
			if(filelist[i].isFile()&&filelist[i].getName().endsWith(".part"))
				filelist[i].delete();
		}
	}

	static {
		System.loadLibrary("btclient"); // the bt c library we are hooking up
	}
}
