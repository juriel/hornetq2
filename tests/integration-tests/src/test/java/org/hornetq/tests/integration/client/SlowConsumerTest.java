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
package org.hornetq.tests.integration.client;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.hornetq.api.config.HornetQDefaultConfiguration;
import org.hornetq.api.core.HornetQException;
import org.hornetq.api.core.HornetQExceptionType;
import org.hornetq.api.core.HornetQObjectClosedException;
import org.hornetq.api.core.SimpleString;
import org.hornetq.api.core.client.ClientConsumer;
import org.hornetq.api.core.client.ClientMessage;
import org.hornetq.api.core.client.ClientProducer;
import org.hornetq.api.core.client.ClientSession;
import org.hornetq.api.core.client.ClientSessionFactory;
import org.hornetq.api.core.client.MessageHandler;
import org.hornetq.api.core.client.ServerLocator;
import org.hornetq.api.core.management.CoreNotificationType;
import org.hornetq.api.core.management.ManagementHelper;
import org.hornetq.core.server.HornetQServer;
import org.hornetq.core.settings.impl.AddressSettings;
import org.hornetq.core.settings.impl.SlowConsumerPolicy;
import org.hornetq.tests.util.RandomUtil;
import org.hornetq.tests.util.ServiceTestBase;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * @author Justin Bertram
 */
@RunWith(value = Parameterized.class)
public class SlowConsumerTest extends ServiceTestBase
{
   private boolean isNetty = false;

   // this will ensure that all tests in this class are run twice,
   // once with "true" passed to the class' constructor and once with "false"
   @Parameterized.Parameters
   public static Collection getParameters()
   {
      return Arrays.asList(new Object[][]{
         {true},
         {false}
      });
   }

   public SlowConsumerTest(boolean isNetty)
   {
      this.isNetty = isNetty;
   }

   private HornetQServer server;

   private final SimpleString QUEUE = new SimpleString("ConsumerTestQueue");

   private ServerLocator locator;

   @Before
   @Override
   public void setUp() throws Exception
   {
      super.setUp();

      server = createServer(false, isNetty);

      AddressSettings addressSettings = new AddressSettings();
      addressSettings.setSlowConsumerCheckPeriod(2);
      addressSettings.setSlowConsumerThreshold(10);
      addressSettings.setSlowConsumerPolicy(SlowConsumerPolicy.KILL);

      server.start();

      server.getAddressSettingsRepository().addMatch(QUEUE.toString(), addressSettings);

      locator = createFactory(isNetty);
   }

   @Test
   public void testSlowConsumerKilled() throws Exception
   {
      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = addClientSession(sf.createSession(false, true, true, false));

      session.createQueue(QUEUE, QUEUE, null, false);

      ClientProducer producer = addClientProducer(session.createProducer(QUEUE));

      final int numMessages = 25;

      for (int i = 0; i < numMessages; i++)
      {
         producer.send(createTextMessage(session, "m" + i));
      }

      ClientConsumer consumer = addClientConsumer(session.createConsumer(QUEUE));
      session.start();

      Thread.sleep(3000);

      try
      {
         consumer.receiveImmediate();
         fail();
      }
      catch (HornetQObjectClosedException e)
      {
         assertEquals(e.getType(), HornetQExceptionType.OBJECT_CLOSED);
      }
   }

   @Test
   public void testSlowConsumerNotification() throws Exception
   {

      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = addClientSession(sf.createSession(false, true, true, false));

      session.createQueue(QUEUE, QUEUE, null, false);

      AddressSettings addressSettings = new AddressSettings();
      addressSettings.setSlowConsumerCheckPeriod(2);
      addressSettings.setSlowConsumerThreshold(10);
      addressSettings.setSlowConsumerPolicy(SlowConsumerPolicy.NOTIFY);

      server.getAddressSettingsRepository().removeMatch(QUEUE.toString());
      server.getAddressSettingsRepository().addMatch(QUEUE.toString(), addressSettings);

      ClientProducer producer = addClientProducer(session.createProducer(QUEUE));

      final int numMessages = 25;

      for (int i = 0; i < numMessages; i++)
      {
         producer.send(createTextMessage(session, "m" + i));
      }

      SimpleString notifQueue = RandomUtil.randomSimpleString();

      session.createQueue(HornetQDefaultConfiguration.getDefaultManagementNotificationAddress(), notifQueue, null, false);

      ClientConsumer notifConsumer = session.createConsumer(notifQueue.toString(), ManagementHelper.HDR_NOTIFICATION_TYPE + "='" + CoreNotificationType.CONSUMER_SLOW + "'");

      final CountDownLatch notifLatch = new CountDownLatch(1);

      notifConsumer.setMessageHandler(new MessageHandler()
      {
         @Override
         public void onMessage(ClientMessage message)
         {
            assertEquals(CoreNotificationType.CONSUMER_SLOW.toString(), message.getObjectProperty(ManagementHelper.HDR_NOTIFICATION_TYPE).toString());
            assertEquals(QUEUE.toString(), message.getObjectProperty(ManagementHelper.HDR_ADDRESS).toString());
            assertEquals(Integer.valueOf(1), message.getIntProperty(ManagementHelper.HDR_CONSUMER_COUNT));
            if (isNetty)
            {
               assertTrue(message.getSimpleStringProperty(ManagementHelper.HDR_REMOTE_ADDRESS).toString().startsWith("/127.0.0.1"));
            }
            else
            {
               assertEquals(SimpleString.toSimpleString("invm:0"), message.getSimpleStringProperty(ManagementHelper.HDR_REMOTE_ADDRESS));
            }
            assertNotNull(message.getSimpleStringProperty(ManagementHelper.HDR_CONNECTION_NAME));
            assertNotNull(message.getSimpleStringProperty(ManagementHelper.HDR_CONSUMER_NAME));
            assertNotNull(message.getSimpleStringProperty(ManagementHelper.HDR_SESSION_NAME));
            try
            {
               message.acknowledge();
            }
            catch (HornetQException e)
            {
               e.printStackTrace();
            }
            notifLatch.countDown();
         }
      });

      ClientConsumer consumer = addClientConsumer(session.createConsumer(QUEUE));
      session.start();

      assertTrue(notifLatch.await(3, TimeUnit.SECONDS));
   }

   @Test
   public void testSlowConsumerSpared() throws Exception
   {
      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = addClientSession(sf.createSession(true, true));

      session.createQueue(QUEUE, QUEUE, null, false);

      ClientProducer producer = addClientProducer(session.createProducer(QUEUE));

      final int numMessages = 5;

      for (int i = 0; i < numMessages; i++)
      {
         producer.send(createTextMessage(session, "m" + i));
      }

      ClientConsumer consumer = addClientConsumer(session.createConsumer(QUEUE));
      session.start();

      Thread.sleep(3000);

      for (int i = 0; i < numMessages; i++)
      {
         assertNotNull(consumer.receive(500));
      }
   }

   @Test
   public void testFastThenSlowConsumerSpared() throws Exception
   {
      locator.setAckBatchSize(0);

      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = addClientSession(sf.createSession(true, true));

      final ClientSession producerSession = addClientSession(sf.createSession(true, true));

      session.createQueue(QUEUE, QUEUE, null, false);

      final ClientProducer producer = addClientProducer(producerSession.createProducer(QUEUE));

      final AtomicLong messagesProduced = new AtomicLong(0);

      Thread t = new Thread(new Runnable()
      {
         @Override
         public void run()
         {
            long start = System.currentTimeMillis();
            ClientMessage m = createTextMessage(producerSession, "m", true);

            // send messages as fast as possible for 3 seconds
            while (System.currentTimeMillis() < (start + 3000))
            {
               try
               {
                  producer.send(m);
                  messagesProduced.incrementAndGet();
               }
               catch (HornetQException e)
               {
                  e.printStackTrace();
                  return;
               }
            }

            start = System.currentTimeMillis();

            // send 1 msg/second for 10 seconds
            while (System.currentTimeMillis() < (start + 10000))
            {
               try
               {
                  producer.send(m);
                  messagesProduced.incrementAndGet();
                  Thread.sleep(1000);
               }
               catch (Exception e)
               {
                  e.printStackTrace();
                  return;
               }
            }
         }
      });

      t.start();

      ClientConsumer consumer = addClientConsumer(session.createConsumer(QUEUE));
      session.start();

      ClientMessage m = null;
      long messagesConsumed = 0;

      do
      {
         m = consumer.receive(1500);
         if (m != null)
         {
            m.acknowledge();
            messagesConsumed++;
         }
      }
      while (m != null);

      assertEquals(messagesProduced.longValue(), messagesConsumed);
   }

   @Test
   public void testSlowWildcardConsumer() throws Exception
   {
      SimpleString addressAB = new SimpleString("a.b");
      SimpleString addressAC = new SimpleString("a.c");
      SimpleString address = new SimpleString("a.*");
      SimpleString queueName1 = new SimpleString("Q1");
      SimpleString queueName2 = new SimpleString("Q2");
      SimpleString queueName = new SimpleString("Q");

      AddressSettings addressSettings = new AddressSettings();
      addressSettings.setSlowConsumerCheckPeriod(2);
      addressSettings.setSlowConsumerThreshold(10);
      addressSettings.setSlowConsumerPolicy(SlowConsumerPolicy.KILL);

      server.getAddressSettingsRepository().addMatch(address.toString(), addressSettings);

      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = addClientSession(sf.createSession(false, true, true, false));
      session.createQueue(addressAB, queueName1, null, false);
      session.createQueue(addressAC, queueName2, null, false);
      session.createQueue(address, queueName, null, false);
      ClientProducer producer = session.createProducer(addressAB);
      ClientProducer producer2 = session.createProducer(addressAC);

      final int numMessages = 20;

      for (int i = 0; i < numMessages; i++)
      {
         producer.send(createTextMessage(session, "m1" + i));
         producer2.send(createTextMessage(session, "m2" + i));
      }

      ClientConsumer consumer = addClientConsumer(session.createConsumer(queueName));
      session.start();

      Thread.sleep(3000);

      try
      {
         consumer.receiveImmediate();
         fail();
      }
      catch (HornetQObjectClosedException e)
      {
         assertEquals(e.getType(), HornetQExceptionType.OBJECT_CLOSED);
      }
   }
}
