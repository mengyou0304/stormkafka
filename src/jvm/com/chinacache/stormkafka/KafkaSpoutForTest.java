/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.chinacache.stormkafka;

import backtype.storm.Config;
import backtype.storm.metric.api.IMetric;
import backtype.storm.spout.SpoutOutputCollector;
import backtype.storm.task.TopologyContext;
import backtype.storm.topology.OutputFieldsDeclarer;
import backtype.storm.topology.base.BaseRichSpout;
import kafka.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.qos.logback.core.db.dialect.MsSQLDialect;

import com.chinacache.stormkafka.KafkaSpout.EmitState;
import com.chinacache.stormkafka.PartitionManager.KafkaMessageId;

import java.util.*;

// TODO: need to add blacklisting
// TODO: need to make a best effort to not re-emit messages if don't have to
public class KafkaSpoutForTest extends BaseRichSpout {
    public static class MessageAndRealOffset {
        public Message msg;
        public long offset;

        public MessageAndRealOffset(Message msg, long offset) {
            this.msg = msg;
            this.offset = offset;
        }
    }

   

    public static final Logger LOG = LoggerFactory.getLogger(KafkaSpoutForTest.class);

    String _uuid = UUID.randomUUID().toString();
    SpoutConfig _spoutConfig;
    SpoutOutputCollector _collector;
    PartitionCoordinator _coordinator;
    DynamicPartitionConnections _connections;
    ZkState _state;

    long _lastUpdateMs = 0;

    int _currPartitionIndex = 0;
    
    //sleep when start spout, to give time for download config
    public int INIT_SLEEP_TIME = 30000;
    int initSleepTime = INIT_SLEEP_TIME;
    
    //control the max messages a spout can get from kafka
    int maxMsgToSleep = Integer.MAX_VALUE;
    int currentMsgCount = 0;
    int sleepTime = 0;

    public KafkaSpoutForTest(SpoutConfig spoutConf) {
        _spoutConfig = spoutConf;
        //_spoutConfig.zkPort = 2181;
        //_spoutConfig.zkServers = new ArrayList<String>(Arrays.asList(new String[]{"180.97.185.37","180.97.185.38","180.97.185.39","153.99.250.34","153.99.250.35"}));

    }       
    
    public KafkaSpoutForTest(SpoutConfig _spoutConfig, int initSleepTime) {
		this._spoutConfig = _spoutConfig;
		this.initSleepTime = initSleepTime;
	}
    
	public KafkaSpoutForTest(SpoutConfig spoutConf,int maxMsgToSleep, int sleepTime) {
    	this.maxMsgToSleep = maxMsgToSleep;
    	this._spoutConfig = spoutConf;
    	this.sleepTime = sleepTime;
    	
    }	

	public KafkaSpoutForTest(SpoutConfig spoutConf, int initSleepTime, int maxMsgToSleep, int sleepTime) {
    	this.maxMsgToSleep = maxMsgToSleep;
    	this.initSleepTime = initSleepTime;
    	this._spoutConfig = spoutConf;
    	this.sleepTime = sleepTime;
    	
    }	

    public int getInitSleepTime() {
		return initSleepTime;
	}

	public void setInitSleepTime(int initSleepTime) {
		this.initSleepTime = initSleepTime;
	}

	public int getMaxMsgToSleep() {
		return maxMsgToSleep;
	}

	public void setMaxMsgToSleep(int maxMsgToSleep) {
		this.maxMsgToSleep = maxMsgToSleep;
	}

	public int getSleepTime() {
		return sleepTime;
	}

	public void setSleepTime(int sleepTime) {
		this.sleepTime = sleepTime;
	}

	@Override
    public void open(Map conf, final TopologyContext context, final SpoutOutputCollector collector) {
        _collector = collector;
        
        /*
        try {
			Thread.sleep(initSleepTime);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		*/

        Map stateConf = new HashMap(conf);
        List<String> zkServers = _spoutConfig.zkServers;
        if (zkServers == null) {
        	
            zkServers = (List<String>) conf.get(Config.STORM_ZOOKEEPER_SERVERS);
        }
        Integer zkPort = _spoutConfig.zkPort;
        if (zkPort == null) {
            zkPort = ((Number) conf.get(Config.STORM_ZOOKEEPER_PORT)).intValue();
        }
        stateConf.put(Config.TRANSACTIONAL_ZOOKEEPER_SERVERS, zkServers);
        stateConf.put(Config.TRANSACTIONAL_ZOOKEEPER_PORT, zkPort);
        stateConf.put(Config.TRANSACTIONAL_ZOOKEEPER_ROOT, _spoutConfig.zkRoot);
     
        _state = new ZkState(stateConf);

        _connections = new DynamicPartitionConnections(_spoutConfig, KafkaUtils.makeBrokerReader(conf, _spoutConfig));

        // using TransactionalState like this is a hack
        if (_spoutConfig.hosts instanceof StaticHosts) {
        	System.out.println("static coordinator");
            _coordinator = new StaticCoordinator(_connections, conf, _spoutConfig, _state, 1, 1, _uuid);
        } else {
        	System.out.println("zk coordinator");
            _coordinator = new ZkCoordinator(_connections, conf, _spoutConfig, _state, 1, 1, _uuid);
        }

       
    }

    @Override
    public void close() {
        _state.close();
    }

    @Override
    public void nextTuple() {
    	System.out.println("begin nextTuple");
    	
        List<PartitionManager> managers = _coordinator.getMyManagedPartitions();
        for (int i = 0; i < managers.size(); i++) {
            try {
                // in case the number of managers decreased
                _currPartitionIndex = _currPartitionIndex % managers.size();
                EmitState state = managers.get(_currPartitionIndex).next(_collector);
                if (state != EmitState.EMITTED_MORE_LEFT) {
                    _currPartitionIndex = (_currPartitionIndex + 1) % managers.size();
                }
                if (state != EmitState.NO_EMITTED) {
                    break;
                }
            } catch (FailedFetchException e) {
                LOG.warn("Fetch failed", e);
                _coordinator.refresh();
            }
        }

        long now = System.currentTimeMillis();
        if ((now - _lastUpdateMs) > _spoutConfig.stateUpdateIntervalMs) {
            commit();
        }
    }

    @Override
    public void ack(Object msgId) {
        KafkaMessageId id = (KafkaMessageId) msgId;
        PartitionManager m = _coordinator.getManager(id.partition);
        if (m != null) {
            m.ack(id.offset);
        }
    }

    @Override
    public void fail(Object msgId) {
        KafkaMessageId id = (KafkaMessageId) msgId;
        PartitionManager m = _coordinator.getManager(id.partition);
        if (m != null) {
            m.fail(id.offset);
        }
    }

    @Override
    public void deactivate() {
        commit();
    }
    

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(_spoutConfig.scheme.getOutputFields());
    }

    private void commit() {
        _lastUpdateMs = System.currentTimeMillis();
        for (PartitionManager manager : _coordinator.getMyManagedPartitions()) {
            manager.commit();
        }
    }

}
