package orca.imageproxy; 

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Properties;

import javax.management.AttributeNotFoundException;

import org.apache.log4j.Logger;

public class BTDownload {
	
	public static enum Type {
		BT, HTTP;
	};
	
	Logger l;

	// hold the images which are still downloading
	private LinkedList<Entry> downloadinglist;
	private long existingdatasize;
	private String errors;
	
	public final static String imageproxyHome = System.getenv("IMAGEPROXY_HOME");
	
	// the reserved size of associative files (torrent file, status file...)
	public final static long SETTINGSIZE = (long) (Math.pow(2, 20));

	// the directory the image are downloaded to
	public final static String DOWNLOADFOLDER = imageproxyHome + File.separator + "download";

	// the total space size to cache images
	public static long SPACESIZE = (long) Math.pow(2, 30);
	public static String spacesizeProperty="spacesize";

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
		existingdatasize = SETTINGSIZE;
		this.errors = "";
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
		l.info("the local space size is "+SPACESIZE+"B.");
		new File(BTDownload.DOWNLOADFOLDER).mkdir();
		initSession(imageproxyHome);
		//clear the downloading images
		l.info("attempting to clear the downloading images left by the last nasty crash");
		if(!sqliteDLDatabase.clearDownloadingImages())
			l.info("no downloading image needs to be cleared.");

		
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
	
	private Entry getEntryFromDLList(String hash)
	{
		Iterator<Entry> iterator = downloadinglist.iterator();
		while(iterator.hasNext()){
			Entry e = iterator.next();
			if(e.getHashcode().equals(hash))
				return e;
		}
		return null;
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

		int value = this.sqliteDLDatabase.checkDownloadList(hash);
		if (value != 0)// downloading or downloaded
		{
			if (value == 1)// downloaded
				return 0;
			else {
				Entry downloadingentry=getEntryFromDLList(hash);
				if(downloadingentry==null)
					throw new NullPointerException("the downloading entry should have been added to the downloading list");
				synchronized (downloadingentry) {
					l.info(hash + " is downloading");
					isDownloading[0] = true;
					downloadingentry.wait();
				}
				return controller(spacesize, hash, surl, isDownloading);
			}
		} else {
				Entry downloadingentry = new Entry();
				downloadingentry.setHashcode(hash);
				addToDLlist(downloadingentry);
				
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
					HttpURLConnection urlcon=(HttpURLConnection)url.openConnection();
					String fileLength=urlcon.getHeaderField("content-Length");
					long length=Long.parseLong(fileLength);
					if(length<=0)
						throw new Exception("fail on parsing the length of the image file");
					newfilesize += length;
					urlcon.disconnect();
				}
				l.info(hash + " image's size is "+newfilesize+"B");
				
				existingdatasize=this.sqliteDLDatabase.getExistingDataSize(existingdatasize);
				l.info("the existing data size in local space is "+ existingdatasize);
				l.info("the total space available for downloading images is "+spacesize);
				
				while (existingdatasize + newfilesize > spacesize) {
					Entry e = this.sqliteDLDatabase.getMostStaleEntry();
					if(e==null)
						return -1;
					l.info("image "+e.getHashcode()+"("+e.getFilesize()+ "B) is going to be deleted");
					if(e.getFilePath().endsWith(".torrent"))
					{
						deleteImageBT(e.getHashcode(), e.getFilePath());
					}
					else
					{
						File file = new File(BTDownload.DOWNLOADFOLDER + File.separator + e.getHashcode());
						boolean result = file.delete();
						if (!result){
							throw new IOException("Couldn't delete file " + e.getFilePath());
						}
					}
					this.sqliteDLDatabase.deleteEntry(e.getHashcode());
					existingdatasize -= e.getFilesize();
					l.info("image "+e.getHashcode()+"("+e.getFilesize()+ "B) is deleted");
				}
				Entry entry = new Entry(hash, newfilesize, 0, surl);
				this.sqliteDLDatabase.insertEntry(entry);
			return 1;
		}
	}

	public String downloadfromURL(String surl, String path, String hash) throws Exception
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
				String correctHash=this.btdownloadfromURL(surl, path, hash);
				return correctHash;
			} else {
				// download file directly
				String correctHash=this.httpdownloadfromURL(url, path, hash);
				return correctHash;
			}
	}
	
	public String httpdownloadfromURL(URL url, String path, String hash) throws SQLException, IOException, NoSuchAlgorithmException
	{
		BufferedInputStream bis;
		try{
			HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			bis = new BufferedInputStream(connection.getInputStream());
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
		
		String correctHash=Util.getFileHash(newfile.getPath());
		Entry e = new Entry(hash, newfile.length(), -1, newfile.getPath());
		
		if(!correctHash.equals(hash))
		//the user-provided hash is incorrect, correct the hash in database and update its download status
		{
			l.warn("the provided hash "+hash+" is incorrect");
			if(this.sqliteDLDatabase.isExisted(correctHash))//need to delete the downloaded image
			{
				this.sqliteDLDatabase.deleteEntry(hash);
				boolean deleteresult=newfile.delete();
				if(!deleteresult)
					throw new IOException("fail on deleting the redundant file "+newfile.getPath());
			}		
			this.sqliteDLDatabase.updateDownloadStatus(e, correctHash, Type.HTTP);
			if(newfile.exists())
				newfile.renameTo(new File(path + File.separator
						+ correctHash));
		}
		else//the user-provided hash is correct, update download status
		{
			boolean flag = this.sqliteDLDatabase.updateDownloadStatus(e, Type.HTTP);
			if(!flag)
				throw new SQLException(
					"The downloading image should have been logged");
		}
		return correctHash;
	}

	// this method will be invoked when bt downloading completes
	public String callbackComplete(String entry) throws Exception
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
		
		String correctHash=Util.getFileHash(BTDownload.DOWNLOADFOLDER+File.separator+hash);
		Entry e=new Entry(hash, Long.parseLong(filesize), Integer.parseInt(reference), filepath);
		
		if(!correctHash.equals(hash) )//the user-provided hash is incorrect, correct the hash in database and update its download status
		{
			l.warn("the provided hash "+hash+" is incorrect");
			if(this.sqliteDLDatabase.isExisted(correctHash))//need to delete the downloaded image
			{
				this.sqliteDLDatabase.deleteEntry(hash);
				deleteImageBT(hash, filepath);
			}
			this.sqliteDLDatabase.updateDownloadStatus(e, correctHash, Type.BT);
			File newfile=new File(BTDownload.DOWNLOADFOLDER+File.separator+hash);
			if(newfile.exists())
				newfile.renameTo(new File(BTDownload.DOWNLOADFOLDER + File.separator
						+ correctHash));
		}
		else//the user-provided hash is correct, update download status
		{
			boolean flag = this.sqliteDLDatabase.updateDownloadStatus(e, Type.BT);
			if(!flag)
				throw new SQLException(
					"The downloading image should have been logged");
		}
		return correctHash;
	}

	public native void initSession(String rootfolder);

	public native String btdownloadfromURL(String surl, String path, String hash)
			throws Exception;

	static {
		System.loadLibrary("btclient"); // the bt c library we are hooking up
	}

	public Pair<String, String> Download(String surl, String hash, boolean[] isDownloading) throws Exception
	// download image with given url and hash if it's not been cached now
	// return a Pair of the image path and the image's correct hash
	// if the correct hash is null, it means the image with correct hash is already cached
	{
		String correctHash=null;
		try{
			int isDeleted = this.controller(BTDownload.SPACESIZE, hash, surl,
				isDownloading);
			if (isDeleted == 1) {
				correctHash=this.downloadfromURL(surl, BTDownload.DOWNLOADFOLDER, hash);
				l.info("Image " + hash + " finished downloading");
			
				// wake up the waiting threads for accomplishment of downloading
				Entry removed = removeFromDLlist(hash);
				if (removed != null) {
					synchronized (removed) {
						removed.notifyAll();
					}
				}
			} else if (isDeleted == -1){
				throw new IOException("There isn't enough local space to download image " + hash);
			} else{
				l.info("Image " + hash + " is already cached");
				correctHash=hash;
			}
		}catch(Exception e){
			this.sqliteDLDatabase.deleteEntry(hash);
			throw e;
		}
		return new Pair<String, String>(BTDownload.DOWNLOADFOLDER + File.separator + correctHash, correctHash);
	}
}
