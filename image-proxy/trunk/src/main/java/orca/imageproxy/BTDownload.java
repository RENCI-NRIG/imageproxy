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
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

import javax.management.AttributeNotFoundException;

import org.apache.log4j.Logger;

public class BTDownload {
	
	Logger l;

	//list of files that are still downloading
	private LinkedList<String> activeDownloadList;
	
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
		
		activeDownloadList = new LinkedList<String>();
		
		Properties p = Globals.getInstance().getProperties();
		if (p.containsKey(cacheSizeProperty)) {
			String spacesizeString=p.getProperty(cacheSizeProperty);
			if(spacesizeString!=null) {
				int factor = 1;
				try{
					factor = Integer.parseInt(spacesizeString);
				}catch(NumberFormatException e){
					throw new NumberFormatException("can't recognize the number format of property spacesize.");
				}
				if(factor > 0)
					BTDownload.CACHE_SIZE = BTDownload.CACHE_SIZE * factor;
				else
					throw new NumberFormatException("the spacesize should be larger than 0.");
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
		try{
			
			URL url = new URL(surl);
			String filename = url.getFile();
			
			String downloadType;
			if (filename.endsWith(".torrent")) {
				downloadType = DOWNLOADTYPE_BT;
			}else{
				downloadType = DOWNLOADTYPE_HTTP;
			}
			
			String filePath = sqliteDLDatabase.checkDownloadList(signature, true, surl, downloadType);
			
			// null means we get to load it, otherwise wait and return file information
			if (filePath != null) {
				
				synchronized(sqliteDLDatabase) {
					while (Globals.IMAGE_INPROGRESS.equals(filePath)) {
						l.info("File download in progress; awaiting completion.");
						try {
							// wait for other threads to download the file
							sqliteDLDatabase.wait();
						} catch (InterruptedException e) {
							;
						}
						l.info("Awakened while waiting for file to download; checking to see if complete...");
						filePath = sqliteDLDatabase.checkDownloadList(signature, false, null, null);
						
						//Exception while downloading file
						if(filePath == null){
							return downloadFile(surl, signature);
						}
					}
					l.info("File downloaded.");
					return new Pair<String, String>(filePath, signature);
				}
			}else{
				l.info("Downloading file");
				Pair<String, String> fileInfo = controller(signature, surl, downloadType);
				synchronized (sqliteDLDatabase) {
					sqliteDLDatabase.notifyAll();
				}
				l.info("File downloaded.");
				return fileInfo;
			}
			
		}catch(Exception e){
			sqliteDLDatabase.deleteEntry(signature);
			synchronized (sqliteDLDatabase) {
				sqliteDLDatabase.notifyAll();
			}
			throw e;
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
		checkSpace(fileSignature, surl, downloadType);
		
		String correctSign = downloadfromURL(surl, fileSignature, downloadType);
		l.info("File finished downloading");
		
		if(fileSignature.equals(correctSign)){
			return new Pair<String, String>(BTDownload.DOWNLOADFOLDER + File.separator + fileSignature, fileSignature);
		}else{
			return new Pair<String, String>(null, correctSign);
		}
		
	}
	
	/**
	 * Checks if enough space is available to download the file. If not, tries to make space.
	 * @param fileSignature
	 * @param surl
	 * @throws Exception
	 */
	private synchronized void checkSpace(String fileSignature, String surl, String downloadType) throws Exception{
		
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
			
			if(e==null) {
				throw new IOException("There isn't enough space to download file " + fileSignature);
			}
			l.info("File " + e.getSignature() + "(" + e.getFilesize() + " bytes) is going to be deleted");
			
			if(DOWNLOADTYPE_BT.equals(e.getDownloadType())){
				try{
					deleteImageBT(e.getSignature(), e.getTorrentFilePath());
				}catch(Exception exception){
					throw new IOException("Couldn't delete file " + e.getFilePath());
				}
			}else{
				File file = new File(e.getFilePath());
				boolean result = file.delete();
				if (!result){
					throw new IOException("Couldn't delete file " + e.getFilePath());
				}
			}
			
			//delete entry (for the deleted file) from database
			sqliteDLDatabase.deleteEntry(e.getSignature());
			
			l.info("File (" + e.getSignature() + ") (" + e.getFilesize() + " bytes) is deleted");
			
			//reset existing data size
			existingdatasize -= e.getFilesize();
		}
		
		sqliteDLDatabase.updateFileSize(fileSignature, fileSize);
	}
	
	/**
	 * Function of fetch size of file corresponding to the given URL
	 * @param surl
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
		try{
			HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			bis = new BufferedInputStream(connection.getInputStream());
		}catch(IOException ioException){
			throw new IOException("Exception while downloading file through http connection.");
		}
		
		File newfile = new File(BTDownload.DOWNLOADFOLDER + File.separator + signature);
		
		try{
			BufferedOutputStream fos = new BufferedOutputStream(new FileOutputStream(newfile));
			int b;
			while ((b = bis.read()) != -1) {
				fos.write(b);
			}
			fos.flush();
			fos.close();
		}catch(IOException ioException){
			throw new IOException("Error while writing data to file " + newfile);
		}
		
		String correctHash = Util.getFileHash(newfile.getPath());
		
		//the user-provided hash is incorrect, correct the hash in database and update its download status
		if(!correctHash.equals(signature)){
			l.warn("The provided signature " + signature + " is incorrect");
			
			//check if another entry with the correct signature exists, if so delete the downloaded file
			if(sqliteDLDatabase.checkDownloadList(correctHash, true, url.toURI().toString(), DOWNLOADTYPE_HTTP) != null){
				sqliteDLDatabase.deleteEntry(signature);
				boolean deleteresult = newfile.delete();
				if(!deleteresult)
					throw new IOException("Failed to delete the redundant file " + newfile.getPath());
			}else{
				newfile.renameTo(new File(BTDownload.DOWNLOADFOLDER + File.separator + correctHash));
				sqliteDLDatabase.updateFilePath(correctHash, newfile.getPath());
				sqliteDLDatabase.updateDownloadStatus(correctHash, 1);
			}
				
		}else{
			sqliteDLDatabase.updateFilePath(correctHash, newfile.getPath());
			sqliteDLDatabase.updateDownloadStatus(correctHash, 1);
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
		
		String correctHash = Util.getFileHash(BTDownload.DOWNLOADFOLDER + File.separator + signature);
		
		//the user-provided hash is incorrect, correct the hash in database and update its download status
		if(!correctHash.equals(signature)){
			l.warn("The provided signature " + signature + " is incorrect");
			
			//check if another entry with the correct signature exists, if so delete the downloaded image
			if(sqliteDLDatabase.checkDownloadList(correctHash, true, url, DOWNLOADTYPE_BT) != null){
				sqliteDLDatabase.deleteEntry(signature);
				deleteImageBT(signature, torrentFilePath);
			}else{
				File file = new File(BTDownload.DOWNLOADFOLDER + File.separator + signature);
				file.renameTo(new File(BTDownload.DOWNLOADFOLDER + File.separator + correctHash));
				sqliteDLDatabase.updateFilePath(correctHash, file.getPath());
				sqliteDLDatabase.updateTorrentFilePath(correctHash, torrentFilePath);
				sqliteDLDatabase.updateDownloadStatus(correctHash, 1);
			}
				
		}else{
			sqliteDLDatabase.updateFilePath(correctHash, BTDownload.DOWNLOADFOLDER + File.separator + correctHash);
			sqliteDLDatabase.updateTorrentFilePath(correctHash, torrentFilePath);
			sqliteDLDatabase.updateDownloadStatus(correctHash, 1);
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
