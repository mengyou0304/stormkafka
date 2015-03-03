package com.chinacache.stormkafka;

import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import backtype.storm.Config;
import backtype.storm.LocalCluster;
import backtype.storm.StormSubmitter;
import backtype.storm.generated.StormTopology;
import backtype.storm.spout.SchemeAsMultiScheme;
import backtype.storm.topology.BasicOutputCollector;
import backtype.storm.topology.OutputFieldsDeclarer;
import backtype.storm.topology.TopologyBuilder;
import backtype.storm.topology.base.BaseBasicBolt;
import backtype.storm.tuple.Tuple;




public class KafkaSpoutTestTopology {
    public static final Logger LOG = LoggerFactory.getLogger(KafkaSpoutTestTopology.class);   
 
    public static class PrinterBolt extends BaseBasicBolt {
        @Override
        public void declareOutputFields(OutputFieldsDeclarer declarer) {
        }

        @Override
        public void execute(Tuple tuple, BasicOutputCollector collector) {
            LOG.info(tuple.toString());
        }

    }


    private final BrokerHosts brokerHosts;

    public KafkaSpoutTestTopology(String kafkaZookeeper) {
        brokerHosts = new ZkHosts(kafkaZookeeper,"/YRFS/BJ-TONGZHOU/8BGP-0/brokers");
    }

    public StormTopology buildTopology() {
        SpoutConfig kafkaConfig = new SpoutConfig(brokerHosts, "final", "", "withinner1222");
        kafkaConfig.scheme = new SchemeAsMultiScheme(new StringScheme());
        TopologyBuilder builder = new TopologyBuilder();
        builder.setSpout("words", new KafkaSpout(kafkaConfig), 50);
        builder.setBolt("print", new PrinterBolt(),50).shuffleGrouping("words");
        return builder.createTopology();
    }

    public static void main(String[] args) throws Exception {

        String kafkaZk = args[0];
        KafkaSpoutTestTopology kafkaSpoutTestTopology = new KafkaSpoutTestTopology(kafkaZk);
        Config config = new Config();
        config.put(Config.TOPOLOGY_TRIDENT_BATCH_EMIT_INTERVAL_MILLIS, 2000);
        config.put(Config.STORM_ZOOKEEPER_SESSION_TIMEOUT, 20000);
        config.put(Config.STORM_ZOOKEEPER_RETRY_TIMES, 3);
        config.put(Config.STORM_ZOOKEEPER_RETRY_INTERVAL, 30);
        
        StormTopology stormTopology = kafkaSpoutTestTopology.buildTopology();
        if (args != null && args.length > 1) {
            String name = args[1];
            //String dockerIp = args[2];
            config.setNumWorkers(100);
            config.setMaxTaskParallelism(100);
            //config.put(Config.NIMBUS_HOST, dockerIp);
            config.put(Config.NIMBUS_THRIFT_PORT, 6627);
            config.put(Config.STORM_ZOOKEEPER_PORT, 2181);
            config.put(Config.STORM_ZOOKEEPER_SERVERS, Arrays.asList(new String[]{"223.202.46.151","223.202.46.152","223.202.46.153"}));
            config.put("zookeeper.connect", "223.202.46.151:2181,223.202.46.152:2181,223.202.46.153:2181/YRFS/BJ-TONGZHOU/8BGP-0/brokers");
            StormSubmitter.submitTopology(name, config, stormTopology);
        } else {
            config.setNumWorkers(40);
            config.setMaxTaskParallelism(40);
            LocalCluster cluster = new LocalCluster();
            cluster.submitTopology("kafka", config, stormTopology);
            System.out.println("hehe");
        } 
    }
}
