package orca.imageproxy;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.LinkedList;

import javax.management.AttributeNotFoundException;

import org.apache.log4j.Logger;

public class BTDownload {
	
	public static enum Type {
		BT, HTTP;
	};
	
	Logger l;

	// hold the images which are still downloading
	private LinkedList<Entry> downloadinglist;
	private long totalsize;
	private String errors;
	
	public final static String imageproxyHome = System.getenv("IMAGEPROXY_HOME");
	
	// the reserved size of associative files (torrent file, status file...)
	public final static long SETTINGSIZE = (long) (Math.pow(2, 20));

	// the directory the image are downloaded to
	public final static String DOWNLOADFOLDER = imageproxyHome + File.separator + "download";

	// the total space size to cache images
	public static long SPACESIZE = (long) Math.pow(2, 30);

	private static BTDownload btdownload = null;
	private SqliteDLDatabase sqliteDLDatabase;

	public synchronized static BTDownload getInstance()  throws Exception{
		if (btdownload == null)
			btdownload = new BTDownload();
		return btdownload;
	}

	public String getErrorMsg() {
		return this.errors;
	}

	public void setErrorMsg(String errors) {
		this.errors = errors;
	}

	private BTDownload() throws Exception{
		
		l = Logger.getLogger(this.getClass());
		
		if(imageproxyHome == null){
			throw new AttributeNotFoundException(
			"Please set environment variable: IMAGEPROXY_HOME");
		}
		sqliteDLDatabase=SqliteDLDatabase.getInstance();
		downloadinglist = new LinkedList<Entry>();
		totalsize = SETTINGSIZE;
		this.errors = "";
		new File(BTDownload.DOWNLOADFOLDER).mkdir();
		//clear the downloading images
		l.info("attempting to clear the downloading images left by the last nasty crash");
		if(!sqliteDLDatabase.clearDownloadingImages())
			l.info("no downloading image needs to be cleared.");
		initSession(imageproxyHome);
		
	}
	
	synchronized public static void deleteDownloadingImage(String hash, String filepath) throws Exception{
		if(filepath.endsWith(".torrent")){
			deleteImageBT(hash, filepath);
		}else{
			File file=new File(DOWNLOADFOLDER+File.separator+hash);
			file.delete();
		}
	}
	
	synchronized public static void deleteIncompleteImages(){
		File downloadDir=new File(DOWNLOADFOLDER);
		File[] filelist=downloadDir.listFiles();
		for(int i=0;i<filelist.length;i++)
		{
			if(filelist[i].isFile()&&filelist[i].getName().endsWith(".part"))
				filelist[i].delete();
		}
	}
	
	private static native void deleteImageBT(String hash, String filepath) throws Exception;
	
	/**
	 * check download list and update the reference number
	 * if the image is downloading, the entry will be added into the downloading list
	 * @return 1  when the image is already downloaded
	 * @return -1 when the image is being downloaded
	 * @return 0 otherwise
	 */

	/**
	 * Method to add the downloading image to the downloading list if it's not in yet
	 * @param entry
	 */
	private void addToDLlist(Entry entry)
	{
		Iterator<Entry> iterator = downloadinglist.iterator();
		while (iterator.hasNext()) {
			Entry e = iterator.next();
			if (e.getHashcode().equals(entry.getHashcode()))
				return;
		}
		downloadinglist.add(entry);
	}

	private Entry removeFromDLlist(String hashcode)
	// remove the downloaded image from the downloading list if it's found
	// in normal case it should be found in downloading list
	{
		Iterator<Entry> iterator = downloadinglist.iterator();
		while (iterator.hasNext()) {
			Entry entry = iterator.next();
			if (entry.getHashcode().equals(hashcode)) {
				iterator.remove();
				return entry;
			}
		}
		return null;
	}

	/**
	 * get file length of image downloaded by bt protocol
	 * @param bt_url
	 * @param download_folder
	 * @return file length
	 * @throws IOException
	 */
	public native String getFileLength(String bt_url, String download_folder)
			throws Exception;

	private synchronized int controller(long spacesize, String hash, String surl,
			boolean[] isDownloading) throws Exception
	// check if a file is still cached
	// return 1 if it's not cached and can be downloaded, return 0 if it's still
	// cached
	// if this file needs to be downloaded again, make enough space for the
	// ensuing download
	// return -1 if it's not cached and the disk space is not large enough to
	// store it
	{
		Entry downloadingentry = new Entry();
		int value = this.sqliteDLDatabase.checkDownloadList(hash, downloadingentry);
		if (value != 0)// downloading or downloaded
		{
			if (value == 1)// downloaded
				return 0;
			else {
				addToDLlist(downloadingentry);
				synchronized (downloadingentry) {
					l.info(hash + " is downloading");
					isDownloading[0] = true;
					downloadingentry.wait();
				}
				return 0;
			}
		} else {
				URL url = new URL(surl);
				String filename = url.getFile();
				String type="";
				
				if(filename.lastIndexOf(".")>=0)
					type= filename.substring(filename.lastIndexOf("."));
				
				long newfilesize = 0;
				if (type.equals(".torrent")) {
					String result=getFileLength(surl, BTDownload.DOWNLOADFOLDER);
					int len_start=result.lastIndexOf(".torrent")+".torrent".length();
					long length=Long.parseLong(result.substring(len_start));
					try{
						surl=result.substring(0, len_start);
						newfilesize += length;
					}catch(Exception exception){
						throw exception;
					}
					
				} else {
					File downloadfile = new File(surl);
					newfilesize += downloadfile.length();
				}
				l.info(hash + " image's size is "+newfilesize+"B");
				
				if (totalsize + newfilesize > spacesize) {
					do {
						Entry e = this.sqliteDLDatabase.getMostStaleEntry();
						if(e==null)
							return -1;
						l.info("image "+e.getHashcode()+"("+e.getFilesize()+ "B) is going to be deleted");
						
						File file = new File(BTDownload.DOWNLOADFOLDER + File.separator + hash);
						boolean result = file.delete();
						if (!result){
							throw new IOException("Couldn't delete file " + e.getFilePath());
						}
						totalsize -= e.getFilesize();
						l.info("image "+e.getHashcode()+"("+e.getFilesize()+ "B) is deleted");
						
					} while (totalsize + newfilesize > spacesize);
				}
				Entry entry = new Entry(hash, newfilesize, 0, surl);
				this.sqliteDLDatabase.insertEntry(entry);
			return 1;
		}
	}

	public boolean downloadfromURL(String surl, String path, String hash) throws Exception
	// download directly if the file is image; if the file is bt file, when
	// download by using bt protocol
	// return true if the file is downloaded in a normal way, which indicates it
	// can be deleted after register
	// return false if it's downloaded in bt way, which indicates it can't be
	// deleted after register.
	// after downloading, append a new line to the tablefile
	{
		
		URI uri = new URI(surl);
		URL url = null;
		String filename;
		try{
			url =uri.toURL();
			filename=url.getFile();
		}catch(Exception e){
			filename=uri.getPath();
		}
		
			String type = "";
			
			if(filename.lastIndexOf(".") >= 0)
				type = filename.substring(filename.lastIndexOf("."));
			
			if (type.equals(".torrent")) {
				// download by bt protocol
				this.btdownloadfromURL(surl, path, hash);
				return false;
			} else {
				BufferedInputStream bis;
				// download file directly
				try{
				HttpURLConnection connection = (HttpURLConnection) url
						.openConnection();
				 bis = new BufferedInputStream(connection
						.getInputStream());
				}catch(IOException ioException){
					throw new IOException("Exception while downloading file through http connection.");
				}
				
				File newfile = new File(path + File.separator
						+ hash);
				
				try{
					BufferedOutputStream fos = new BufferedOutputStream(
							new FileOutputStream(newfile));
					int b;
					while ((b = bis.read()) != -1) {
						fos.write(b);
					}
					fos.flush();
					fos.close();
				}catch(IOException ioException){
					throw new IOException("Error while writing data to file " + newfile);
				}
				Entry e = new Entry(hash, newfile.length(), -1, newfile.getPath());
				boolean flag = this.sqliteDLDatabase.updateDownloadStatus(e, Type.HTTP);
				if (!flag)
					throw new SQLException(
							"The downloading image should have been logged");
				return true;
			}
	}

	// this method will be invoked when bt downloading completes
	public void callbackComplete(String entry) throws SQLException, ClassNotFoundException
	{
		String hash = "";
		String filesize = "";
		String reference = "";
		String filepath = "";

		if (!entry.contains("#"))// the same file with different hash
		{
			hash = entry;
			this.sqliteDLDatabase.deleteEntry(hash);
			throw new RuntimeException("The image "+hash+" already exists with a different hash ");
			
		} else {
			String[] items = entry.split("#");
			hash = items[0];
			filesize = items[1];
			reference = items[2];
			filepath = items[3];
		}

		Entry e=new Entry(hash, Long.parseLong(filesize), Integer.parseInt(reference), filepath);
		boolean flag = this.sqliteDLDatabase.updateDownloadStatus(e, Type.BT);

		if (!flag)
			throw new SQLException("The downloading image should have been logged");
	}

	public native void initSession(String rootfolder);

	public native void btdownloadfromURL(String surl, String path, String hash)
			throws Exception;

	static {
		System.loadLibrary("btclient"); // the bt c library we are hooking up
	}

	public String Download(String surl, String hash, boolean[] isDownloading) throws Exception
	// download image with given url and hash if it's not been cached now
	// return the image path
	{
		int isDeleted = this.controller(BTDownload.SPACESIZE, hash, surl,
				isDownloading);
		if (isDeleted == 1) {
			this.downloadfromURL(surl, BTDownload.DOWNLOADFOLDER, hash);
			l.info("Image " + hash + " finished downloading");
			// wake up the waiting threads for accomplishment of downloading
			Entry removed = removeFromDLlist(hash);
			if (removed != null) {
				synchronized (removed) {
					removed.notifyAll();
				}
			}
		} else if (isDeleted == -1){
			throw new OutOfMemoryError(
					"There isn't enough local space to download image " + hash);
		} else{
			l.info("Image " + hash + " is already cached");
		}

		return BTDownload.DOWNLOADFOLDER + File.separator + hash;
	}
}