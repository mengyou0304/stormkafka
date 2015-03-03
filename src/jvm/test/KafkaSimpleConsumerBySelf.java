package test;

//General
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

//Kafka
import kafka.consumer.Consumer;
//import kafka.consumer.ConsumerConfig;
//import kafka.consumer.ConsumerIterator;
import kafka.consumer.KafkaStream;
//import kafka.javaapi.consumer.ConsumerConnector;
import kafka.consumer.ConsumerConfig;
import kafka.consumer.ConsumerIterator;
import kafka.consumer.KafkaStream;
import kafka.javaapi.consumer.ConsumerConnector;
import kafka.message.Message;
import backtype.storm.spout.SpoutOutputCollector;
import backtype.storm.task.TopologyContext;
import backtype.storm.topology.OutputFieldsDeclarer;
import backtype.storm.topology.base.BaseRichSpout;
import backtype.storm.tuple.Fields;
import backtype.storm.tuple.Values;

import kafka.javaapi.consumer.SimpleConsumer;
import kafka.javaapi.message.ByteBufferMessageSet;
import kafka.message.MessageAndOffset;
import kafka.api.OffsetRequest;
import kafka.api.FetchRequest;
import kafka.api.FetchRequestBuilder;
import kafka.javaapi.*;

//Storm
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

import com.chinacache.stormkafka.*;
import org.apache.zookeeper.ZooKeeper;


public class KafkaSimpleConsumerBySelf extends BaseRichSpout {
	private String topic;
	private SpoutOutputCollector collector;
	Map<String, List<KafkaStream<byte[], byte[]>>> consumerStreams;
	List<KafkaStream<byte[], byte[]>> streams;
	private Properties props;
	private long offset;
	public static final Logger LOG = LoggerFactory.getLogger(KafkaSimpleConsumerBySelf.class);
	private ZooKeeper zk;

	public void open(Map conf, TopologyContext context,SpoutOutputCollector collector)
	{
		this.collector = collector;
		this.topic = "zeus";
		this.offset = -1;
		String connectionString = "10.20.72.132:2181,10.20.72.133:2181,10.20.72.134:2181,10.20.72.135:2181,10.20.72.136:2181";
		try {
			 zk = new ZooKeeper(connectionString, 5000, null);
		} catch (IOException e) {
			 e.printStackTrace();
		}
	}

	// returns logsize current offset - this is NOT clients offset
	private long getOffset(SimpleConsumer consumer, String topic, int partation)
	{
	    long startOffsetTime = kafka.api.OffsetRequest.LatestTime();
	    long offset = KafkaUtils.getOffset(consumer, "zeus", 0, startOffsetTime);
	    //Logger.info("Offset is: "+ offset);
	    return offset;
	}

	public void nextTuple() 
	{
	    // todo - find leader
	    SimpleConsumer consumer = new SimpleConsumer("10.20.72.145", 9092, 100000, 64 * 1024, topic);

	    if (this.offset == -1 )
	     {
		//this.offset = getOffset(consumer, topic, 0);
		this.offset = ZookeeperBySelf.getKafkaOffset(zk, "/consumers/kafkaspout/offsets/kafkademo1/0");
		System.out.println("Offset is: "+ offset);
          }
	
	    FetchRequest req = new FetchRequestBuilder()
                 .clientId("KafkaSpout")
                 .addFetch(topic, 0, offset, 1024 * 1024)
                 .build();
         FetchResponse fetchResponse = consumer.fetch(req);	

	    if (fetchResponse.hasError()) {
	    
	    	short code = fetchResponse.errorCode(topic, 0);
		System.out.println("Error fetching data from the Broker:" + "vm37" + " Reason: " + code);
	    }

	    long numRead = 0;
         for (MessageAndOffset messageAndOffset : fetchResponse.messageSet(topic, 0)) {
             long currentOffset = messageAndOffset.offset();
             if (currentOffset < offset) {
                 System.out.println("Found an old offset: " + currentOffset + " Expecting: " + offset);
                 continue;
             }
             offset = messageAndOffset.nextOffset();
             ByteBuffer payload = messageAndOffset.message().payload();

             byte[] bytes = new byte[payload.limit()];
             payload.get(bytes);
		try {
			LOG.info("Message... "+ String.valueOf(messageAndOffset.offset()) +" : "+ new String(bytes, "UTF-8"));
			this.collector.emit(new Values(new String(bytes, "UTF-8")), String.valueOf(messageAndOffset.offset()));
			ZookeeperBySelf.setKafkaOffset(zk, "/consumers/kafkaspout/offsets/kafkademo1/0", offset);
		}catch (UnsupportedEncodingException e) {
        	 	System.out.println("Oops:" + e);
     	}
		numRead++;
         }
	    

	   if (numRead == 0) {
             try {
                 Thread.sleep(1000);
             } catch (InterruptedException ie) {
             }
         }
		
	     if (consumer != null)
	    	consumer.close();	
	}
	
	public void ack(Object id) 
	{
             
		System.out.println("OK:"+id);
	}
		  
	public void fail(Object id) {
		System.out.println("FAIL:"+id);
	}
		  
	public void declareOutputFields(OutputFieldsDeclarer declarer) {
		declarer.declare(new Fields("kafkademo1"));
	}

     public void close() {
		 try {
			zk.close();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
     }

}