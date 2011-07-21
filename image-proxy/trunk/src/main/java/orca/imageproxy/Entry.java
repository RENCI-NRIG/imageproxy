package orca.imageproxy;

public class Entry{
	
	private String signature;
	private long filesize;//if the reference is same, the smaller the filesize is, the more prior it should be replaced(closer to the bottom)
	private String filePath;
	private String downloadType;
	private String torrentFilePath;
	
	public Entry(String hashcode, long filesize, int reference, String filepath, String downloadType, String torrentFilePath)
	{
		this.signature = hashcode;
		this.filesize = filesize;
		this.filePath = filepath;
		this.downloadType = downloadType;
		this.torrentFilePath = torrentFilePath;
	}
	
	public Entry(){;}

	public String getSignature() {
		return signature;
	}
	public void setSignature(String signature) {
		this.signature = signature;
	}
	public long getFilesize() {
		return filesize;
	}
	public void setFilesize(long filesize) {
		this.filesize = filesize;
	}
	public String getFilePath(){
		return this.filePath;
	}
	public void setFilePath(String filepath){
		this.filePath = filepath;
	}
	public String getDownloadType() {
		return downloadType;
	}
	public void setDownloadType(String downloadType) {
		this.downloadType = downloadType;
	}
	public String getTorrentFilePath(){
		return this.torrentFilePath;
	}
	public void setTorrentFilePath(String torrentFilePath){
		this.torrentFilePath = torrentFilePath;
	}
}