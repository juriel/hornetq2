/*
 * Copyright 2005-2014 Red Hat, Inc.
 * Red Hat licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */
package org.hornetq.tests.integration.jms;
import org.hornetq.api.core.SimpleString;
import org.hornetq.core.client.impl.ServerLocatorImpl;
import org.hornetq.core.remoting.impl.invm.InVMConnectorFactory;
import org.hornetq.tests.integration.jms.serializables.TestClass1;
import org.hornetq.utils.ObjectInputStreamWithClassLoader;
import org.junit.Before;

import org.junit.Test;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Queue;
import javax.jms.QueueBrowser;
import javax.jms.Session;

import org.junit.Assert;

import org.hornetq.api.core.BroadcastGroupConfiguration;
import org.hornetq.api.core.DiscoveryGroupConfiguration;
import org.hornetq.api.core.TransportConfiguration;
import org.hornetq.api.core.UDPBroadcastGroupConfiguration;
import org.hornetq.api.core.client.HornetQClient;
import org.hornetq.api.jms.HornetQJMSClient;
import org.hornetq.api.jms.JMSFactoryType;
import org.hornetq.core.config.Configuration;
import org.hornetq.core.server.HornetQServer;
import org.hornetq.core.server.HornetQServers;
import org.hornetq.jms.client.HornetQConnectionFactory;
import org.hornetq.tests.integration.IntegrationTestLogger;
import org.hornetq.tests.util.RandomUtil;
import org.hornetq.tests.util.UnitTestCase;

/**
 *
 * A HornetQConnectionFactoryTest
 *
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 *
 *
 */
public class HornetQConnectionFactoryTest extends UnitTestCase
{
   private static final IntegrationTestLogger log = IntegrationTestLogger.LOGGER;

   private final String groupAddress = getUDPDiscoveryAddress();

   private final int groupPort = getUDPDiscoveryPort();

   private HornetQServer liveService;

   private TransportConfiguration liveTC;

   @Test
   public void testDefaultConstructor() throws Exception
   {
      HornetQConnectionFactory cf = HornetQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF);
      assertFactoryParams(cf,
                          null,
                          null,
                          null,
                          HornetQClient.DEFAULT_CLIENT_FAILURE_CHECK_PERIOD,
                          HornetQClient.DEFAULT_CONNECTION_TTL,
                          HornetQClient.DEFAULT_CALL_TIMEOUT,
                          HornetQClient.DEFAULT_CALL_FAILOVER_TIMEOUT,
                          HornetQClient.DEFAULT_MIN_LARGE_MESSAGE_SIZE,
                          HornetQClient.DEFAULT_CONSUMER_WINDOW_SIZE,
                          HornetQClient.DEFAULT_CONSUMER_MAX_RATE,
                          HornetQClient.DEFAULT_CONFIRMATION_WINDOW_SIZE,
                          HornetQClient.DEFAULT_PRODUCER_MAX_RATE,
                          HornetQClient.DEFAULT_BLOCK_ON_ACKNOWLEDGE,
                          HornetQClient.DEFAULT_BLOCK_ON_DURABLE_SEND,
                          HornetQClient.DEFAULT_BLOCK_ON_NON_DURABLE_SEND,
                          HornetQClient.DEFAULT_AUTO_GROUP,
                          HornetQClient.DEFAULT_PRE_ACKNOWLEDGE,
                          HornetQClient.DEFAULT_CONNECTION_LOAD_BALANCING_POLICY_CLASS_NAME,
                          HornetQClient.DEFAULT_ACK_BATCH_SIZE,
                          HornetQClient.DEFAULT_ACK_BATCH_SIZE,
                          HornetQClient.DEFAULT_DISCOVERY_INITIAL_WAIT_TIMEOUT,
                          HornetQClient.DEFAULT_USE_GLOBAL_POOLS,
                          HornetQClient.DEFAULT_SCHEDULED_THREAD_POOL_MAX_SIZE,
                          HornetQClient.DEFAULT_THREAD_POOL_MAX_SIZE,
                          HornetQClient.DEFAULT_RETRY_INTERVAL,
                          HornetQClient.DEFAULT_RETRY_INTERVAL_MULTIPLIER,
                          HornetQClient.DEFAULT_RECONNECT_ATTEMPTS);
      Connection conn = null;

      try
      {
         conn = cf.createConnection();

         conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         Assert.fail("Should throw exception");
      }
      catch (JMSException e)
      {
         // Ok
      }
      if (conn != null)
      {
         conn.close();
      }

      HornetQConnectionFactoryTest.log.info("Got here");

      testSettersThrowException(cf);
   }

   @Test
   public void testDefaultConstructorAndSetConnectorPairs() throws Exception
   {
      HornetQConnectionFactory cf = HornetQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF, liveTC);

      assertFactoryParams(cf,
                          new TransportConfiguration[]{liveTC},
                          null,
                          null,
                          HornetQClient.DEFAULT_CLIENT_FAILURE_CHECK_PERIOD,
                          HornetQClient.DEFAULT_CONNECTION_TTL,
                          HornetQClient.DEFAULT_CALL_TIMEOUT,
                          HornetQClient.DEFAULT_CALL_FAILOVER_TIMEOUT,
                          HornetQClient.DEFAULT_MIN_LARGE_MESSAGE_SIZE,
                          HornetQClient.DEFAULT_CONSUMER_WINDOW_SIZE,
                          HornetQClient.DEFAULT_CONSUMER_MAX_RATE,
                          HornetQClient.DEFAULT_CONFIRMATION_WINDOW_SIZE,
                          HornetQClient.DEFAULT_PRODUCER_MAX_RATE,
                          HornetQClient.DEFAULT_BLOCK_ON_ACKNOWLEDGE,
                          HornetQClient.DEFAULT_BLOCK_ON_DURABLE_SEND,
                          HornetQClient.DEFAULT_BLOCK_ON_NON_DURABLE_SEND,
                          HornetQClient.DEFAULT_AUTO_GROUP,
                          HornetQClient.DEFAULT_PRE_ACKNOWLEDGE,
                          HornetQClient.DEFAULT_CONNECTION_LOAD_BALANCING_POLICY_CLASS_NAME,
                          HornetQClient.DEFAULT_ACK_BATCH_SIZE,
                          HornetQClient.DEFAULT_ACK_BATCH_SIZE,
                          HornetQClient.DEFAULT_DISCOVERY_INITIAL_WAIT_TIMEOUT,
                          HornetQClient.DEFAULT_USE_GLOBAL_POOLS,
                          HornetQClient.DEFAULT_SCHEDULED_THREAD_POOL_MAX_SIZE,
                          HornetQClient.DEFAULT_THREAD_POOL_MAX_SIZE,
                          HornetQClient.DEFAULT_RETRY_INTERVAL,
                          HornetQClient.DEFAULT_RETRY_INTERVAL_MULTIPLIER,
                          HornetQClient.DEFAULT_RECONNECT_ATTEMPTS);

      Connection conn = cf.createConnection();

      conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      testSettersThrowException(cf);

      conn.close();
   }

   @Test
   public void testDiscoveryConstructor() throws Exception
   {
      DiscoveryGroupConfiguration groupConfiguration = new DiscoveryGroupConfiguration(HornetQClient.DEFAULT_DISCOVERY_INITIAL_WAIT_TIMEOUT, HornetQClient.DEFAULT_DISCOVERY_INITIAL_WAIT_TIMEOUT,
            new UDPBroadcastGroupConfiguration(groupAddress, groupPort, null, -1));
      HornetQConnectionFactory cf = HornetQJMSClient.createConnectionFactoryWithoutHA(groupConfiguration, JMSFactoryType.CF);
      assertFactoryParams(cf,
                          null,
                          groupConfiguration,
                          null,
                          HornetQClient.DEFAULT_CLIENT_FAILURE_CHECK_PERIOD,
                          HornetQClient.DEFAULT_CONNECTION_TTL,
                          HornetQClient.DEFAULT_CALL_TIMEOUT,
                          HornetQClient.DEFAULT_CALL_FAILOVER_TIMEOUT,
                          HornetQClient.DEFAULT_MIN_LARGE_MESSAGE_SIZE,
                          HornetQClient.DEFAULT_CONSUMER_WINDOW_SIZE,
                          HornetQClient.DEFAULT_CONSUMER_MAX_RATE,
                          HornetQClient.DEFAULT_CONFIRMATION_WINDOW_SIZE,
                          HornetQClient.DEFAULT_PRODUCER_MAX_RATE,
                          HornetQClient.DEFAULT_BLOCK_ON_ACKNOWLEDGE,
                          HornetQClient.DEFAULT_BLOCK_ON_DURABLE_SEND,
                          HornetQClient.DEFAULT_BLOCK_ON_NON_DURABLE_SEND,
                          HornetQClient.DEFAULT_AUTO_GROUP,
                          HornetQClient.DEFAULT_PRE_ACKNOWLEDGE,
                          HornetQClient.DEFAULT_CONNECTION_LOAD_BALANCING_POLICY_CLASS_NAME,
                          HornetQClient.DEFAULT_ACK_BATCH_SIZE,
                          HornetQClient.DEFAULT_ACK_BATCH_SIZE,
                          HornetQClient.DEFAULT_DISCOVERY_INITIAL_WAIT_TIMEOUT,
                          HornetQClient.DEFAULT_USE_GLOBAL_POOLS,
                          HornetQClient.DEFAULT_SCHEDULED_THREAD_POOL_MAX_SIZE,
                          HornetQClient.DEFAULT_THREAD_POOL_MAX_SIZE,
                          HornetQClient.DEFAULT_RETRY_INTERVAL,
                          HornetQClient.DEFAULT_RETRY_INTERVAL_MULTIPLIER,
                          HornetQClient.DEFAULT_RECONNECT_ATTEMPTS);
      Connection conn = cf.createConnection();

      conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      testSettersThrowException(cf);

      conn.close();
   }

   @Test
   public void testStaticConnectorListConstructor() throws Exception
   {
      HornetQConnectionFactory cf = HornetQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF, liveTC);
      assertFactoryParams(cf,
                          new TransportConfiguration[]{liveTC},
                          null,
                          null,
                          HornetQClient.DEFAULT_CLIENT_FAILURE_CHECK_PERIOD,
                          HornetQClient.DEFAULT_CONNECTION_TTL,
                          HornetQClient.DEFAULT_CALL_TIMEOUT,
                          HornetQClient.DEFAULT_CALL_FAILOVER_TIMEOUT,
                          HornetQClient.DEFAULT_MIN_LARGE_MESSAGE_SIZE,
                          HornetQClient.DEFAULT_CONSUMER_WINDOW_SIZE,
                          HornetQClient.DEFAULT_CONSUMER_MAX_RATE,
                          HornetQClient.DEFAULT_CONFIRMATION_WINDOW_SIZE,
                          HornetQClient.DEFAULT_PRODUCER_MAX_RATE,
                          HornetQClient.DEFAULT_BLOCK_ON_ACKNOWLEDGE,
                          HornetQClient.DEFAULT_BLOCK_ON_DURABLE_SEND,
                          HornetQClient.DEFAULT_BLOCK_ON_NON_DURABLE_SEND,
                          HornetQClient.DEFAULT_AUTO_GROUP,
                          HornetQClient.DEFAULT_PRE_ACKNOWLEDGE,
                          HornetQClient.DEFAULT_CONNECTION_LOAD_BALANCING_POLICY_CLASS_NAME,
                          HornetQClient.DEFAULT_ACK_BATCH_SIZE,
                          HornetQClient.DEFAULT_ACK_BATCH_SIZE,
                          HornetQClient.DEFAULT_DISCOVERY_INITIAL_WAIT_TIMEOUT,
                          HornetQClient.DEFAULT_USE_GLOBAL_POOLS,
                          HornetQClient.DEFAULT_SCHEDULED_THREAD_POOL_MAX_SIZE,
                          HornetQClient.DEFAULT_THREAD_POOL_MAX_SIZE,
                          HornetQClient.DEFAULT_RETRY_INTERVAL,
                          HornetQClient.DEFAULT_RETRY_INTERVAL_MULTIPLIER,
                          HornetQClient.DEFAULT_RECONNECT_ATTEMPTS);
      Connection conn = cf.createConnection();

      conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      testSettersThrowException(cf);

      conn.close();

   }

   @Test
   public void testStaticConnectorLiveConstructor() throws Exception
   {
      HornetQConnectionFactory cf = HornetQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF, liveTC);
      assertFactoryParams(cf,
                          new TransportConfiguration[]{liveTC},
                          null,
                          null,
                          HornetQClient.DEFAULT_CLIENT_FAILURE_CHECK_PERIOD,
                          HornetQClient.DEFAULT_CONNECTION_TTL,
                          HornetQClient.DEFAULT_CALL_TIMEOUT,
                          HornetQClient.DEFAULT_CALL_FAILOVER_TIMEOUT,
                          HornetQClient.DEFAULT_MIN_LARGE_MESSAGE_SIZE,
                          HornetQClient.DEFAULT_CONSUMER_WINDOW_SIZE,
                          HornetQClient.DEFAULT_CONSUMER_MAX_RATE,
                          HornetQClient.DEFAULT_CONFIRMATION_WINDOW_SIZE,
                          HornetQClient.DEFAULT_PRODUCER_MAX_RATE,
                          HornetQClient.DEFAULT_BLOCK_ON_ACKNOWLEDGE,
                          HornetQClient.DEFAULT_BLOCK_ON_DURABLE_SEND,
                          HornetQClient.DEFAULT_BLOCK_ON_NON_DURABLE_SEND,
                          HornetQClient.DEFAULT_AUTO_GROUP,
                          HornetQClient.DEFAULT_PRE_ACKNOWLEDGE,
                          HornetQClient.DEFAULT_CONNECTION_LOAD_BALANCING_POLICY_CLASS_NAME,
                          HornetQClient.DEFAULT_ACK_BATCH_SIZE,
                          HornetQClient.DEFAULT_ACK_BATCH_SIZE,
                          HornetQClient.DEFAULT_DISCOVERY_INITIAL_WAIT_TIMEOUT,
                          HornetQClient.DEFAULT_USE_GLOBAL_POOLS,
                          HornetQClient.DEFAULT_SCHEDULED_THREAD_POOL_MAX_SIZE,
                          HornetQClient.DEFAULT_THREAD_POOL_MAX_SIZE,
                          HornetQClient.DEFAULT_RETRY_INTERVAL,
                          HornetQClient.DEFAULT_RETRY_INTERVAL_MULTIPLIER,
                          HornetQClient.DEFAULT_RECONNECT_ATTEMPTS);
      Connection conn = cf.createConnection();

      conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      testSettersThrowException(cf);

      cf.close();

      conn.close();
   }


   @Test
   public void testGettersAndSetters()
   {
      HornetQConnectionFactory cf = HornetQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF, liveTC);

      long clientFailureCheckPeriod = RandomUtil.randomPositiveLong();
      long connectionTTL = RandomUtil.randomPositiveLong();
      long callTimeout = RandomUtil.randomPositiveLong();
      int minLargeMessageSize = RandomUtil.randomPositiveInt();
      int consumerWindowSize = RandomUtil.randomPositiveInt();
      int consumerMaxRate = RandomUtil.randomPositiveInt();
      int confirmationWindowSize = RandomUtil.randomPositiveInt();
      int producerMaxRate = RandomUtil.randomPositiveInt();
      boolean blockOnAcknowledge = RandomUtil.randomBoolean();
      boolean blockOnDurableSend = RandomUtil.randomBoolean();
      boolean blockOnNonDurableSend = RandomUtil.randomBoolean();
      boolean autoGroup = RandomUtil.randomBoolean();
      boolean preAcknowledge = RandomUtil.randomBoolean();
      String loadBalancingPolicyClassName = RandomUtil.randomString();
      boolean useGlobalPools = RandomUtil.randomBoolean();
      int scheduledThreadPoolMaxSize = RandomUtil.randomPositiveInt();
      int threadPoolMaxSize = RandomUtil.randomPositiveInt();
      long retryInterval = RandomUtil.randomPositiveLong();
      double retryIntervalMultiplier = RandomUtil.randomDouble();
      int reconnectAttempts = RandomUtil.randomPositiveInt();
      cf.setClientFailureCheckPeriod(clientFailureCheckPeriod);
      cf.setConnectionTTL(connectionTTL);
      cf.setCallTimeout(callTimeout);
      cf.setMinLargeMessageSize(minLargeMessageSize);
      cf.setConsumerWindowSize(consumerWindowSize);
      cf.setConsumerMaxRate(consumerMaxRate);
      cf.setConfirmationWindowSize(confirmationWindowSize);
      cf.setProducerMaxRate(producerMaxRate);
      cf.setBlockOnAcknowledge(blockOnAcknowledge);
      cf.setBlockOnDurableSend(blockOnDurableSend);
      cf.setBlockOnNonDurableSend(blockOnNonDurableSend);
      cf.setAutoGroup(autoGroup);
      cf.setPreAcknowledge(preAcknowledge);
      cf.setConnectionLoadBalancingPolicyClassName(loadBalancingPolicyClassName);
      cf.setUseGlobalPools(useGlobalPools);
      cf.setScheduledThreadPoolMaxSize(scheduledThreadPoolMaxSize);
      cf.setThreadPoolMaxSize(threadPoolMaxSize);
      cf.setRetryInterval(retryInterval);
      cf.setRetryIntervalMultiplier(retryIntervalMultiplier);
      cf.setReconnectAttempts(reconnectAttempts);
      Assert.assertEquals(clientFailureCheckPeriod, cf.getClientFailureCheckPeriod());
      Assert.assertEquals(connectionTTL, cf.getConnectionTTL());
      Assert.assertEquals(callTimeout, cf.getCallTimeout());
      Assert.assertEquals(minLargeMessageSize, cf.getMinLargeMessageSize());
      Assert.assertEquals(consumerWindowSize, cf.getConsumerWindowSize());
      Assert.assertEquals(consumerMaxRate, cf.getConsumerMaxRate());
      Assert.assertEquals(confirmationWindowSize, cf.getConfirmationWindowSize());
      Assert.assertEquals(producerMaxRate, cf.getProducerMaxRate());
      Assert.assertEquals(blockOnAcknowledge, cf.isBlockOnAcknowledge());
      Assert.assertEquals(blockOnDurableSend, cf.isBlockOnDurableSend());
      Assert.assertEquals(blockOnNonDurableSend, cf.isBlockOnNonDurableSend());
      Assert.assertEquals(autoGroup, cf.isAutoGroup());
      Assert.assertEquals(preAcknowledge, cf.isPreAcknowledge());
      Assert.assertEquals(loadBalancingPolicyClassName, cf.getConnectionLoadBalancingPolicyClassName());
      Assert.assertEquals(useGlobalPools, cf.isUseGlobalPools());
      Assert.assertEquals(scheduledThreadPoolMaxSize, cf.getScheduledThreadPoolMaxSize());
      Assert.assertEquals(threadPoolMaxSize, cf.getThreadPoolMaxSize());
      Assert.assertEquals(retryInterval, cf.getRetryInterval());
      Assert.assertEquals(retryIntervalMultiplier, cf.getRetryIntervalMultiplier(), 0.0001);
      Assert.assertEquals(reconnectAttempts, cf.getReconnectAttempts());

      cf.close();
   }

   @Test
   public void testDeserializationOptions() throws Exception
   {
      testDeserializationOptions(false);
   }

   @Test
   public void testDeserializationOptionsBrowser() throws Exception
   {
      testDeserializationOptions(true);
   }

   private void testDeserializationOptions(boolean useBrowser) throws Exception
   {
      String qname = "SerialTestQueue";
      SimpleString qaddr = new SimpleString("jms.queue." + qname);
      liveService.createQueue(qaddr, qaddr, null, true, false);

      //default ok
      String blackList = null;
      String whiteList = null;
      Object obj = receiveObjectMessage(blackList, whiteList, qname, new TestClass1(), useBrowser);
      assertTrue("Object is " + obj, obj instanceof TestClass1);

      //not in the white list
      blackList = "java.lang";
      whiteList = "some.other.package1";
      obj = receiveObjectMessage(blackList, whiteList, qname, new TestClass1(), useBrowser);
      assertTrue("Object is " + obj, obj instanceof JMSException);
      //but String always trusted
      obj = receiveObjectMessage(blackList, whiteList, qname, new String("hello"), useBrowser);
      assertTrue("java.lang.String always trusted ", "hello".equals(obj));

      //in the blacklist
      blackList = "org.hornetq.tests.integration.jms.serializables";
      whiteList = "org.hornetq.tests.integration.jms.serializables";
      obj = receiveObjectMessage(blackList, whiteList, qname, new TestClass1(), useBrowser);
      assertTrue("Object is " + obj, obj instanceof JMSException);

      //black list parent package
      blackList = "org.hornetq";
      whiteList = "org.hornetq.tests.integration.jms.serializables";
      obj = receiveObjectMessage(blackList, whiteList, qname, new TestClass1(), useBrowser);
      assertTrue("Object is " + obj, obj instanceof JMSException);

      //in white list
      blackList = "some.other.package";
      whiteList = "org.hornetq.tests.integration.jms.serializables";
      obj = receiveObjectMessage(blackList, whiteList, qname, new TestClass1(), useBrowser);
      assertTrue("Object is " + obj, obj instanceof TestClass1);

      //parent in white list
      blackList = "some.other.package";
      whiteList = "org.hornetq.tests.integration.jms";
      obj = receiveObjectMessage(blackList, whiteList, qname, new TestClass1(), useBrowser);
      assertTrue("Object is " + obj, obj instanceof TestClass1);

      //sub package in white list
      blackList = "some.other.package";
      whiteList = "org.hornetq.tests.integration.jms.serializables.pkg1";
      obj = receiveObjectMessage(blackList, whiteList, qname, new TestClass1(), useBrowser);
      assertTrue("Object is " + obj, obj instanceof JMSException);

      //wild card white list but black listed
      blackList = "org.hornetq.tests.integration.jms.serializables";
      whiteList = "*";
      obj = receiveObjectMessage(blackList, whiteList, qname, new TestClass1(), useBrowser);
      assertTrue("Object is " + obj, obj instanceof JMSException);

      //wild card white list and not black listed
      blackList = "some.other.package";
      whiteList = "*";
      obj = receiveObjectMessage(blackList, whiteList, qname, new TestClass1(), useBrowser);
      assertTrue("Object is " + obj, obj instanceof TestClass1);

      //wild card black list
      blackList = "*";
      whiteList = "*";
      obj = receiveObjectMessage(blackList, whiteList, qname, new TestClass1(), useBrowser);
      assertTrue("Object is " + obj, obj instanceof JMSException);
   }

   @Test
   public void testSystemPropertyBlackWhiteListDefault() throws Exception
   {
      System.setProperty(ObjectInputStreamWithClassLoader.BLACKLIST_PROPERTY, "*");
      System.setProperty(ObjectInputStreamWithClassLoader.WHITELIST_PROPERTY, "some.other.package");

      String qname = "SerialTestQueue";
      SimpleString qaddr = new SimpleString("jms.queue." + qname);
      liveService.createQueue(qaddr, qaddr, null, true, false);

      try
      {
         String blackList = null;
         String whiteList = null;
         Object obj = receiveObjectMessage(blackList, whiteList, qname, new TestClass1(), false);
         assertTrue("Object is " + obj, obj instanceof JMSException);
         //but String always trusted
         obj = receiveObjectMessage(blackList, whiteList, qname, new String("hello"), false);
         assertTrue("java.lang.String always trusted " + obj, "hello".equals(obj));

         //override
         blackList = "some.other.package";
         whiteList = "org.hornetq.tests.integration";
         obj = receiveObjectMessage(blackList, whiteList, qname, new TestClass1(), false);
         assertTrue("Object is " + obj, obj instanceof TestClass1);
         //but String always trusted
         obj = receiveObjectMessage(blackList, whiteList, qname, new String("hello"), false);
         assertTrue("java.lang.String always trusted " + obj, "hello".equals(obj));
      }
      finally
      {
         System.clearProperty(ObjectInputStreamWithClassLoader.BLACKLIST_PROPERTY);
         System.clearProperty(ObjectInputStreamWithClassLoader.WHITELIST_PROPERTY);
      }
   }

   private Object receiveObjectMessage(String blackList, String whiteList, String qname,
                                       Serializable obj, boolean useBrowser) throws Exception
   {
      sendObjectMessage(qname, obj);

      HashMap<String, Object> transportConfig = new HashMap<String, Object>();

      HornetQConnectionFactory  factory = new HornetQConnectionFactory(new ServerLocatorImpl(false, new TransportConfiguration(InVMConnectorFactory.class.getName(), transportConfig)));
      factory.setDeserializationBlackList(blackList);
      factory.setDeserializationWhiteList(whiteList);
      Connection connection = null;

      try
      {
         connection = factory.createConnection();
         connection.start();
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Queue queue = session.createQueue(qname);
         Object result = null;
         if (useBrowser)
         {
            QueueBrowser browser = session.createBrowser(queue);
            ObjectMessage objMessage = (ObjectMessage) browser.getEnumeration().nextElement();
            //drain message before triggering deserialization
            MessageConsumer consumer = session.createConsumer(queue);
            consumer.receive(5000);
            result = objMessage.getObject();
         }
         else
         {
            MessageConsumer consumer = session.createConsumer(queue);
            ObjectMessage objMessage = (ObjectMessage) consumer.receive(5000);
            assertNotNull(objMessage);
            result = objMessage.getObject();
         }
         return result;
      }
      catch (Exception e)
      {
         return e;
      }
      finally
      {
         if (connection != null)
         {
            try
            {
               connection.close();
            }
            catch (JMSException e)
            {
               return e;
            }
         }
      }
   }

   private void sendObjectMessage(String qname, Serializable obj) throws Exception
   {
      HashMap<String, Object> transportConfig = new HashMap<String, Object>();

      HornetQConnectionFactory factory = new HornetQConnectionFactory(new ServerLocatorImpl(false, new TransportConfiguration(InVMConnectorFactory.class.getName(), transportConfig)));
      Connection connection = factory.createConnection();
      try
      {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Queue q = session.createQueue(qname);
         MessageProducer producer = session.createProducer(q);
         ObjectMessage objMessage = session.createObjectMessage();
         objMessage.setObject(obj);
         producer.send(objMessage);
      }
      finally
      {
         connection.close();
      }
   }

   private void testSettersThrowException(final HornetQConnectionFactory cf)
   {

      String discoveryAddress = RandomUtil.randomString();
      int discoveryPort = RandomUtil.randomPositiveInt();
      long discoveryRefreshTimeout = RandomUtil.randomPositiveLong();
      String clientID = RandomUtil.randomString();
      long clientFailureCheckPeriod = RandomUtil.randomPositiveLong();
      long connectionTTL = RandomUtil.randomPositiveLong();
      long callTimeout = RandomUtil.randomPositiveLong();
      int minLargeMessageSize = RandomUtil.randomPositiveInt();
      int consumerWindowSize = RandomUtil.randomPositiveInt();
      int consumerMaxRate = RandomUtil.randomPositiveInt();
      int confirmationWindowSize = RandomUtil.randomPositiveInt();
      int producerMaxRate = RandomUtil.randomPositiveInt();
      boolean blockOnAcknowledge = RandomUtil.randomBoolean();
      boolean blockOnDurableSend = RandomUtil.randomBoolean();
      boolean blockOnNonDurableSend = RandomUtil.randomBoolean();
      boolean autoGroup = RandomUtil.randomBoolean();
      boolean preAcknowledge = RandomUtil.randomBoolean();
      String loadBalancingPolicyClassName = RandomUtil.randomString();
      int dupsOKBatchSize = RandomUtil.randomPositiveInt();
      int transactionBatchSize = RandomUtil.randomPositiveInt();
      long initialWaitTimeout = RandomUtil.randomPositiveLong();
      boolean useGlobalPools = RandomUtil.randomBoolean();
      int scheduledThreadPoolMaxSize = RandomUtil.randomPositiveInt();
      int threadPoolMaxSize = RandomUtil.randomPositiveInt();
      long retryInterval = RandomUtil.randomPositiveLong();
      double retryIntervalMultiplier = RandomUtil.randomDouble();
      int reconnectAttempts = RandomUtil.randomPositiveInt();
      boolean failoverOnServerShutdown = RandomUtil.randomBoolean();

      try
      {
         cf.setClientID(clientID);
         Assert.fail("Should throw exception");
      }
      catch (IllegalStateException e)
      {
         // OK
      }
      try
      {
         cf.setClientFailureCheckPeriod(clientFailureCheckPeriod);
         Assert.fail("Should throw exception");
      }
      catch (IllegalStateException e)
      {
         // OK
      }
      try
      {
         cf.setConnectionTTL(connectionTTL);
         Assert.fail("Should throw exception");
      }
      catch (IllegalStateException e)
      {
         // OK
      }
      try
      {
         cf.setCallTimeout(callTimeout);
         Assert.fail("Should throw exception");
      }
      catch (IllegalStateException e)
      {
         // OK
      }
      try
      {
         cf.setMinLargeMessageSize(minLargeMessageSize);
         Assert.fail("Should throw exception");
      }
      catch (IllegalStateException e)
      {
         // OK
      }
      try
      {
         cf.setConsumerWindowSize(consumerWindowSize);
         Assert.fail("Should throw exception");
      }
      catch (IllegalStateException e)
      {
         // OK
      }
      try
      {
         cf.setConsumerMaxRate(consumerMaxRate);
         Assert.fail("Should throw exception");
      }
      catch (IllegalStateException e)
      {
         // OK
      }
      try
      {
         cf.setConfirmationWindowSize(confirmationWindowSize);
         Assert.fail("Should throw exception");
      }
      catch (IllegalStateException e)
      {
         // OK
      }
      try
      {
         cf.setProducerMaxRate(producerMaxRate);
         Assert.fail("Should throw exception");
      }
      catch (IllegalStateException e)
      {
         // OK
      }
      try
      {
         cf.setBlockOnAcknowledge(blockOnAcknowledge);
         Assert.fail("Should throw exception");
      }
      catch (IllegalStateException e)
      {
         // OK
      }
      try
      {
         cf.setBlockOnDurableSend(blockOnDurableSend);
         Assert.fail("Should throw exception");
      }
      catch (IllegalStateException e)
      {
         // OK
      }
      try
      {
         cf.setBlockOnNonDurableSend(blockOnNonDurableSend);
         Assert.fail("Should throw exception");
      }
      catch (IllegalStateException e)
      {
         // OK
      }
      try
      {
         cf.setAutoGroup(autoGroup);
         Assert.fail("Should throw exception");
      }
      catch (IllegalStateException e)
      {
         // OK
      }
      try
      {
         cf.setPreAcknowledge(preAcknowledge);
         Assert.fail("Should throw exception");
      }
      catch (IllegalStateException e)
      {
         // OK
      }
      try
      {
         cf.setConnectionLoadBalancingPolicyClassName(loadBalancingPolicyClassName);
         Assert.fail("Should throw exception");
      }
      catch (IllegalStateException e)
      {
         // OK
      }
      try
      {
         cf.setDupsOKBatchSize(dupsOKBatchSize);
         Assert.fail("Should throw exception");
      }
      catch (IllegalStateException e)
      {
         // OK
      }
      try
      {
         cf.setTransactionBatchSize(transactionBatchSize);
         Assert.fail("Should throw exception");
      }
      catch (IllegalStateException e)
      {
         // OK
      }
      try
      {
         cf.setUseGlobalPools(useGlobalPools);
         Assert.fail("Should throw exception");
      }
      catch (IllegalStateException e)
      {
         // OK
      }
      try
      {
         cf.setScheduledThreadPoolMaxSize(scheduledThreadPoolMaxSize);
         Assert.fail("Should throw exception");
      }
      catch (IllegalStateException e)
      {
         // OK
      }
      try
      {
         cf.setThreadPoolMaxSize(threadPoolMaxSize);
         Assert.fail("Should throw exception");
      }
      catch (IllegalStateException e)
      {
         // OK
      }
      try
      {
         cf.setRetryInterval(retryInterval);
         Assert.fail("Should throw exception");
      }
      catch (IllegalStateException e)
      {
         // OK
      }
      try
      {
         cf.setRetryIntervalMultiplier(retryIntervalMultiplier);
         Assert.fail("Should throw exception");
      }
      catch (IllegalStateException e)
      {
         // OK
      }
      try
      {
         cf.setReconnectAttempts(reconnectAttempts);
         Assert.fail("Should throw exception");
      }
      catch (IllegalStateException e)
      {
         // OK
      }

      cf.getStaticConnectors();
      cf.getClientID();
      cf.getClientFailureCheckPeriod();
      cf.getConnectionTTL();
      cf.getCallTimeout();
      cf.getMinLargeMessageSize();
      cf.getConsumerWindowSize();
      cf.getConsumerMaxRate();
      cf.getConfirmationWindowSize();
      cf.getProducerMaxRate();
      cf.isBlockOnAcknowledge();
      cf.isBlockOnDurableSend();
      cf.isBlockOnNonDurableSend();
      cf.isAutoGroup();
      cf.isPreAcknowledge();
      cf.getConnectionLoadBalancingPolicyClassName();
      cf.getDupsOKBatchSize();
      cf.getTransactionBatchSize();
      cf.isUseGlobalPools();
      cf.getScheduledThreadPoolMaxSize();
      cf.getThreadPoolMaxSize();
      cf.getRetryInterval();
      cf.getRetryIntervalMultiplier();
      cf.getReconnectAttempts();

      cf.close();
   }

   private void assertFactoryParams(final HornetQConnectionFactory cf,
                                    final TransportConfiguration[] staticConnectors,
                                    final DiscoveryGroupConfiguration discoveryGroupConfiguration,
                                    final String clientID,
                                    final long clientFailureCheckPeriod,
                                    final long connectionTTL,
                                    final long callTimeout,
                                    final long callFailoverTimeout,
                                    final int minLargeMessageSize,
                                    final int consumerWindowSize,
                                    final int consumerMaxRate,
                                    final int confirmationWindowSize,
                                    final int producerMaxRate,
                                    final boolean blockOnAcknowledge,
                                    final boolean blockOnDurableSend,
                                    final boolean blockOnNonDurableSend,
                                    final boolean autoGroup,
                                    final boolean preAcknowledge,
                                    final String loadBalancingPolicyClassName,
                                    final int dupsOKBatchSize,
                                    final int transactionBatchSize,
                                    final long initialWaitTimeout,
                                    final boolean useGlobalPools,
                                    final int scheduledThreadPoolMaxSize,
                                    final int threadPoolMaxSize,
                                    final long retryInterval,
                                    final double retryIntervalMultiplier,
                                    final int reconnectAttempts)
   {
      TransportConfiguration[] cfStaticConnectors = cf.getStaticConnectors();
      if (staticConnectors == null)
      {
         Assert.assertNull(staticConnectors);
      }
      else
      {
         Assert.assertEquals(staticConnectors.length, cfStaticConnectors.length);

         for (int i = 0; i < staticConnectors.length; i++)
         {
            Assert.assertEquals(staticConnectors[i], cfStaticConnectors[i]);
         }
      }
      Assert.assertEquals(cf.getClientID(), clientID);
      Assert.assertEquals(cf.getClientFailureCheckPeriod(), clientFailureCheckPeriod);
      Assert.assertEquals(cf.getConnectionTTL(), connectionTTL);
      Assert.assertEquals(cf.getCallTimeout(), callTimeout);
      Assert.assertEquals(cf.getCallFailoverTimeout(), callFailoverTimeout);
      Assert.assertEquals(cf.getMinLargeMessageSize(), minLargeMessageSize);
      Assert.assertEquals(cf.getConsumerWindowSize(), consumerWindowSize);
      Assert.assertEquals(cf.getConsumerMaxRate(), consumerMaxRate);
      Assert.assertEquals(cf.getConfirmationWindowSize(), confirmationWindowSize);
      Assert.assertEquals(cf.getProducerMaxRate(), producerMaxRate);
      Assert.assertEquals(cf.isBlockOnAcknowledge(), blockOnAcknowledge);
      Assert.assertEquals(cf.isBlockOnDurableSend(), blockOnDurableSend);
      Assert.assertEquals(cf.isBlockOnNonDurableSend(), blockOnNonDurableSend);
      Assert.assertEquals(cf.isAutoGroup(), autoGroup);
      Assert.assertEquals(cf.isPreAcknowledge(), preAcknowledge);
      Assert.assertEquals(cf.getConnectionLoadBalancingPolicyClassName(), loadBalancingPolicyClassName);
      Assert.assertEquals(cf.getDupsOKBatchSize(), dupsOKBatchSize);
      Assert.assertEquals(cf.getTransactionBatchSize(), transactionBatchSize);
      Assert.assertEquals(cf.isUseGlobalPools(), useGlobalPools);
      Assert.assertEquals(cf.getScheduledThreadPoolMaxSize(), scheduledThreadPoolMaxSize);
      Assert.assertEquals(cf.getThreadPoolMaxSize(), threadPoolMaxSize);
      Assert.assertEquals(cf.getRetryInterval(), retryInterval);
      Assert.assertEquals(cf.getRetryIntervalMultiplier(), retryIntervalMultiplier, 0.00001);
      Assert.assertEquals(cf.getReconnectAttempts(), reconnectAttempts);
   }

   @Override
   @Before
   public void setUp() throws Exception
   {
      super.setUp();

      startServer();
   }

   private void startServer() throws Exception
   {
      Configuration liveConf = createBasicConfig();
      liveConf.setSecurityEnabled(false);
      liveTC = new TransportConfiguration(INVM_CONNECTOR_FACTORY);
      liveConf.getAcceptorConfigurations().add(new TransportConfiguration(INVM_ACCEPTOR_FACTORY));
      Map<String, TransportConfiguration> connectors = new HashMap<String, TransportConfiguration>();
      connectors.put(liveTC.getName(), liveTC);
      liveConf.setConnectorConfigurations(connectors);
      liveConf.setSharedStore(true);
      List<String> connectorNames = new ArrayList<String>();
      connectorNames.add(liveTC.getName());

      final long broadcastPeriod = 250;

      final String bcGroupName = "bc1";

      final int localBindPort = 5432;

      BroadcastGroupConfiguration bcConfig1 = new BroadcastGroupConfiguration(bcGroupName,
                                                                              broadcastPeriod,
                                                                              connectorNames,
                                              new UDPBroadcastGroupConfiguration(groupAddress, groupPort, null, localBindPort));

      List<BroadcastGroupConfiguration> bcConfigs1 = new ArrayList<BroadcastGroupConfiguration>();
      bcConfigs1.add(bcConfig1);
      liveConf.setBroadcastGroupConfigurations(bcConfigs1);

      liveService = addServer(HornetQServers.newHornetQServer(liveConf, false));
      liveService.start();
   }

}
