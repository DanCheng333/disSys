import java.util.LinkedList;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MRU -----------------LRU
 * @author danc
 *
 */
public class LRU {
	public ConcurrentHashMap<String,CacheInfo> cacheMap;
	public LinkedList<String> cacheList;
	public int cacheSize;
	public int cacheSizeLimit;
	
	public ConcurrentHashMap<String,CacheInfo> getMap() {
		return this.cacheMap;
	}
	
	private int evict(int currCacheSize) {
		System.err.println("LRU evict ...");
		int listIndex = cacheList.size() - 1;
		while (currCacheSize > this.cacheSizeLimit) {
			System.err.println("curSize:"+currCacheSize+" limit:"+this.cacheSizeLimit);
			
			if (listIndex < 0) { //cannot make enough space
				System.err.println("Cannot make enough space");
				return -1;
			}
			String lruCache = cacheList.get(listIndex);
			CacheInfo cInfo = cacheMap.get(lruCache);
			System.err.println("Try to evict index " +listIndex + " path" +lruCache);
			if (!cInfo.isUsing) { //not in use we can evict it
				System.err.println("Remove cache path:"+lruCache);
				currCacheSize = currCacheSize - cInfo.size;
				System.err.println("curr size now"+currCacheSize);
				cacheList.remove(listIndex);
				cacheMap.remove(lruCache);
			}
			listIndex--;
			
		}
		return currCacheSize;
	}
	
	//For Open
	public boolean add(String path,CacheInfo cInfo) {
		//isUsing true
		System.err.println("Adding path:" + path);
		int currCacheSize = this.cacheSize+cInfo.size;
		//Evict to make space for new cache
		if (currCacheSize > this.cacheSizeLimit) {
			int size = evict(currCacheSize);
			if (size == -1) {
				return false; //all cache in use, cannot make enough space
			}
			this.cacheSize = size;
		}
		
		cInfo.isUsing = true;
		this.cacheSize = currCacheSize;
		this.cacheList.add(path);
		this.cacheMap.put(path, cInfo);
		return true;
	}

	//For Close
	public boolean move2MRU(String path, CacheInfo cInfo) {
		if (cacheList.contains(path)) {
			cInfo.isUsing = false;
			cacheList.remove(path);
			cacheMap.remove(path);			
		}
		else {
			System.err.println(" should never get here");
			return false;
		}
		cacheList.addFirst(path);
		cacheMap.put(path, cInfo);
		return true;
	}
	
	public LRU(int limit) {
		this.cacheMap = new ConcurrentHashMap<String,CacheInfo>();
		this.cacheList = new LinkedList<String>();
		this.cacheSize = 0;
		this.cacheSizeLimit = limit;
	}
	
	/*public static void main(String[] args) {
		LRU l = new LRU(10);

		CacheInfo a = new CacheInfo("ac", 0, false,true,3);
		CacheInfo b = new CacheInfo("bc", 0, false,true,3);
		CacheInfo c = new CacheInfo("cc", 0, false,false,3);
		CacheInfo d = new CacheInfo("dc", 0, false,true,3);
		l.add("a",a);
		//l.move2MRU("a");
		l.add("b",b);
		l.move2MRU("b",b);
		l.add("c",c);
		l.move2MRU("c",c);
		l.add("d",d);	
		l.move2MRU("d",d);
	}*/
	
}
