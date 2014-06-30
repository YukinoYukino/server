/*
 * Copyright (c) 2012-2014 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 * The Eclipse Public License is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * The Apache License v2.0 is available at
 * http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */
package org.dna.mqtt.moquette.server;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.dna.mqtt.moquette.proto.messages.AbstractMessage.QOSType;
import org.dna.mqtt.moquette.proto.messages.ConnectMessage;
import org.dna.mqtt.moquette.testclient.Client;
import org.fusesource.mqtt.client.BlockingConnection;
import org.fusesource.mqtt.client.MQTT;
import org.fusesource.mqtt.client.Message;
import org.fusesource.mqtt.client.QoS;
import org.fusesource.mqtt.client.Topic;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author andrea
 */
public class ServerLowlevelMessagesIntegrationTests {
    private static final Logger LOG = LoggerFactory.getLogger(ServerLowlevelMessagesIntegrationTests.class);
    
    Server m_server;
    Client m_client;
    MQTT m_subscriberDef;
    
    protected void startServer() throws IOException {
        m_server = new Server();
        m_server.startServer();
    }

    @Before
    public void setUp() throws Exception {
        startServer();
        m_client = new Client("localhost");
        m_subscriberDef = new MQTT();
        m_subscriberDef.setHost("localhost", 1883);
        m_subscriberDef.setClientId("Subscriber");
    }

    @After
    public void tearDown() throws Exception {
        m_client.close();
        
        m_server.stopServer();
        File dbFile = new File(Server.STORAGE_FILE_PATH);
        if (dbFile.exists()) {
            dbFile.delete();
        }
    }
    
    @Test
    public void elapseKeepAliveTime() throws InterruptedException {
        int keepAlive = 2; //secs
        ConnectMessage connectMessage = new ConnectMessage();
        connectMessage.setProcotolVersion((byte)3);
        connectMessage.setClientID("FAKECLNT");
        connectMessage.setKeepAlive(keepAlive);
        m_client.sendMessage(connectMessage);
        
        //wait 2 times the keepAlive
        Thread.sleep(keepAlive * 2 * 1000);
        
        assertTrue(m_client.isConnectionLost());
    }
    
    @Test
    public void checkWillMessageIsWiredOnClientKeepAliveExpiry() throws Exception {
        LOG.info("*** checkWillMessageIsWiredOnClientKeepAliveExpiry ***");
        String willTestamentTopic = "/will/test";
        String willTestamentMsg = "Bye bye";
        
        BlockingConnection willSubscriber = m_subscriberDef.blockingConnection();
        willSubscriber.connect();
        Topic[] topics = new Topic[]{new Topic(willTestamentTopic, QoS.AT_MOST_ONCE)};
        willSubscriber.subscribe(topics);
        
        int keepAlive = 2; //secs
        ConnectMessage connectMessage = new ConnectMessage();
        connectMessage.setProcotolVersion((byte)3);
        connectMessage.setClientID("FAKECLNT");
        connectMessage.setKeepAlive(keepAlive);
        connectMessage.setWillFlag(true);
        connectMessage.setWillMessage(willTestamentMsg);
        connectMessage.setWillTopic(willTestamentTopic);
        connectMessage.setWillQos((byte) QOSType.MOST_ONE.ordinal());
        
        //Execute
        m_client.sendMessage(connectMessage);
        long connectTime = System.currentTimeMillis();
        
        //but after the 2 KEEP ALIVE timeut expires it gets fired, 
        //NB it's 1,5 * KEEP_ALIVE so 3 secs and some millis to propagate the message
        Message msg = willSubscriber.receive(3100, TimeUnit.MILLISECONDS);
        long willMessageReceiveTime = System.currentTimeMillis();
        assertNotNull("the will message should be fired after keep alive!", msg);
        msg.ack();
        //the will message hasn't to be received before the elapsing of Keep Alive timeout
        assertTrue(willMessageReceiveTime - connectTime  > 3000);
        
        assertEquals(willTestamentMsg, new String(msg.getPayload()));
        willSubscriber.disconnect();
    }
}