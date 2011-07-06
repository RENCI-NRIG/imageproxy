package orca.imageproxy;

public class Entry implements Comparable<Entry>{
	public String getHashcode() {
		return hashcode;
	}
	public void setHashcode(String hashcode) {
		this.hashcode=hashcode;
	}
	public long getFilesize() {
		return filesize;
	}
	public void setFilesize(long filesize) {
		this.filesize=filesize;
	}
	public int getReference() {
		return reference;
	}
	public String getFilePath(){
		return this.filepath;
	}
	public void setFilePath(String filepath){
		this.filepath=filepath;
	}
	private String hashcode;
	private long filesize;//if the reference is same, the smaller the filesize is, the more prior it should be replaced(closer to the bottom)
	private int reference;
	private String filepath;
	public Entry(String hashcode, long filesize, int reference, String filepath)
	{
		this.hashcode=hashcode;
		this.filesize=filesize;
		this.reference=reference;
		this.filepath=filepath;
	}
	public Entry(){;}

	public int compareTo(Entry entry) {
		if(this.hashcode.startsWith("downloading")&&entry.hashcode.startsWith("downloading"))
			return 0;
		else if(this.hashcode.startsWith("downloading")&&!entry.hashcode.startsWith("downloading"))
			return -1;
		if(this.reference>entry.getReference())
			return 1;
		else if(this.reference<entry.getReference())
			return -1;
		else
		{
			if(this.filesize<entry.getFilesize())
				return 1;
			else if(this.filesize>entry.getFilesize())
				return -1;
			return 0;
		}
	}
	public void setReference(int i) {
		this.reference=i;
	}
}