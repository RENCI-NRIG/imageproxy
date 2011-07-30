package orca.imageproxy; 

import java.util.concurrent.ConcurrentHashMap;

// This is a singleton instance of ConcurrentHashMap,
// intended to provide a fine-grained means for
// blocking/waking threads waiting on downloads to complete.

class ActiveDownloadMap extends ConcurrentHashMap {
	private static final ActiveDownloadMap activeMap = new ActiveDownloadMap();

	private ActiveDownloadMap() {
	}
	
	public static ActiveDownloadMap getInstance() {
		return activeMap;
	}
}
