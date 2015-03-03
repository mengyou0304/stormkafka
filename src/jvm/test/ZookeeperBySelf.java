package test;

import java.io.ByteArrayOutputStream;
import java.util.logging.Logger;

import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;


public class ZookeeperBySelf {
	
	private final static Logger LOGGER = Logger.getLogger(ZookeeperBySelf.class.getName()); 
	
	/** 
	 * Returns znode(holds kafka consumers offset) data
	 * 
	 * @author margus@roo.ee
	 * @param zk ZooKeeper connection object
	 * @param zkNodePath path to znode
	 * @return offset
	 */
	public static long getKafkaOffset(ZooKeeper zk, String zkNodePath)
	{
		long offset = -1;
		
		Stat stat = null;
		try {
			byte[] b = zk.getData(zkNodePath, false, stat);
			 String s = new String(b);
			 //System.out.println(s);
			 
			 offset = Long.valueOf(s);
			 
			 //zk.close();
			 
			 return offset;
		} catch (KeeperException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
				
		return offset;
	}
	
	
	/** 
	 * Sets Kafka topic-partation offset to zookeeper znode
	 * @author margus@roo.ee
	 * @param zk ZooKeeper connection object
	 * @param zkNodePath path to znode
	 * @param newOffset new offset to set in zookeeper znode
	 * @return void
	 * 
	 */
	public static void setKafkaOffset (ZooKeeper zk, String zkNodePath, long newOffset)
	{
		
		LOGGER.info("got offset: "+ newOffset);
		
		ByteArrayOutputStream bOutput = new ByteArrayOutputStream();
		
		String s = String.valueOf(newOffset);
		char arr[]=s.toCharArray();
		for(int i=0;i<arr.length;i++){
		    //System.out.println("Data at ["+i+"]="+arr[i]);
		    bOutput.write(arr[i]);
		}
		byte b [] = bOutput.toByteArray();
			
		try {
			zk.setData(zkNodePath, b, -1);
		} catch (KeeperException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
}
