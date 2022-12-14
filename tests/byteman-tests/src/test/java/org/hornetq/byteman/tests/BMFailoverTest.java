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

package org.hornetq.byteman.tests;

import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import org.hornetq.api.core.HornetQTransactionOutcomeUnknownException;
import org.hornetq.api.core.HornetQTransactionRolledBackException;
import org.hornetq.api.core.HornetQUnBlockedException;
import org.hornetq.api.core.SimpleString;
import org.hornetq.api.core.TransportConfiguration;
import org.hornetq.api.core.client.ClientConsumer;
import org.hornetq.api.core.client.ClientMessage;
import org.hornetq.api.core.client.ClientProducer;
import org.hornetq.api.core.client.ClientSession;
import org.hornetq.api.core.client.ClientSessionFactory;
import org.hornetq.api.core.client.ServerLocator;
import org.hornetq.core.client.HornetQClientMessageBundle;
import org.hornetq.core.client.impl.ClientMessageImpl;
import org.hornetq.core.client.impl.ClientSessionFactoryInternal;
import org.hornetq.core.client.impl.ClientSessionInternal;
import org.hornetq.core.postoffice.Binding;
import org.hornetq.core.protocol.core.Packet;
import org.hornetq.core.protocol.core.impl.wireformat.SessionXAEndMessage;
import org.hornetq.core.server.Queue;
import org.hornetq.core.transaction.impl.XidImpl;
import org.hornetq.tests.integration.cluster.failover.FailoverTestBase;
import org.hornetq.tests.integration.cluster.util.TestableServer;
import org.hornetq.tests.util.RandomUtil;
import org.hornetq.utils.UUIDGenerator;
import org.jboss.byteman.contrib.bmunit.BMRule;
import org.jboss.byteman.contrib.bmunit.BMRules;
import org.jboss.byteman.contrib.bmunit.BMUnitRunner;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author <a href="mailto:andy.taylor@jboss.org">Andy Taylor</a>
 *         4/18/13
 */
@RunWith(BMUnitRunner.class)
public class BMFailoverTest extends FailoverTestBase
{
   private ServerLocator locator;
   private ClientSessionFactoryInternal sf;
   private ClientSessionFactoryInternal sf2;
   public static TestableServer serverToStop;

   @Before
   @Override
   public void setUp() throws Exception
   {
      super.setUp();
      locator = getServerLocator();
   }

   @After
   @Override
   public void tearDown() throws Exception
   {
      super.tearDown();
   }


   private static boolean stopped = false;
   public static void stopAndThrow(Packet packet) throws HornetQUnBlockedException
   {
      if (!stopped && packet instanceof SessionXAEndMessage)
      {
         try
         {
            serverToStop.getServer().stop(true);
         }
         catch (Exception e)
         {
            e.printStackTrace();
         }
         try
         {
            Thread.sleep(2000);
         }
         catch (InterruptedException e)
         {
            e.printStackTrace();
         }
         stopped = true;
         throw HornetQClientMessageBundle.BUNDLE.unblockingACall(null);
      }
   }
   @Test
   @BMRules
         (
               rules =
                     {
                           @BMRule
                                 (
                                       name = "trace HornetQSessionContext xaEnd",
                                       targetClass = "org.hornetq.core.protocol.core.impl.ChannelImpl",
                                       targetMethod = "sendBlocking",
                                       targetLocation = "AT EXIT",
                                       action = "org.hornetq.byteman.tests.BMFailoverTest.stopAndThrow($1)"
                                 )
                     }
         )
   //https://bugzilla.redhat.com/show_bug.cgi?id=1152410
   public void testFailOnEndAndRetry() throws Exception
   {
      serverToStop = liveServer;

      createSessionFactory();

      ClientSession session = createSession(sf, true, false, false);

      session.createQueue(FailoverTestBase.ADDRESS, FailoverTestBase.ADDRESS, null, true);

      ClientProducer producer = session.createProducer(FailoverTestBase.ADDRESS);

      for (int i = 0; i < 100; i++)
      {
         producer.send(createMessage(session, i, true));
      }

      ClientConsumer consumer = session.createConsumer(FailoverTestBase.ADDRESS);

      Xid xid = RandomUtil.randomXid();

      session.start(xid, XAResource.TMNOFLAGS);
      session.start();
      // Receive MSGs but don't ack!
      for (int i = 0; i < 100; i++)
      {
         ClientMessage message = consumer.receive(1000);

         Assert.assertNotNull(message);

         assertMessageBody(i, message);

         Assert.assertEquals(i, message.getIntProperty("counter").intValue());
      }
      try
      {
         //top level prepare
         session.end(xid, XAResource.TMSUCCESS);
      }
      catch (XAException e)
      {
         try
         {
            //top level abort
            session.end(xid, XAResource.TMFAIL);
         }
         catch (XAException e1)
         {
            try
            {
               //rollback
               session.rollback(xid);
            }
            catch (XAException e2)
            {
            }
         }
      }
      xid = RandomUtil.randomXid();
      session.start(xid, XAResource.TMNOFLAGS);

      for (int i = 0; i < 50; i++)
      {
         ClientMessage message = consumer.receive(1000);

         Assert.assertNotNull(message);

         assertMessageBody(i, message);

         Assert.assertEquals(i, message.getIntProperty("counter").intValue());
      }
      session.end(xid, XAResource.TMSUCCESS);
      session.commit(xid, true);
   }

   @Test
   @BMRules
      (
         rules =
            {
               @BMRule
                  (
                     name = "trace clientsessionimpl commit",
                     targetClass = "org.hornetq.core.client.impl.ClientSessionImpl",
                     targetMethod = "start(javax.transaction.xa.Xid, int)",
                     targetLocation = "AT EXIT",
                     action = "org.hornetq.byteman.tests.BMFailoverTest.serverToStop.getServer().stop(true)"
                  )
            }
      )
   public void testFailoverOnCommit2() throws Exception
   {
      serverToStop = liveServer;
      locator = getServerLocator();
      locator.setFailoverOnInitialConnection(true);
      SimpleString inQueue = new SimpleString("inQueue");
      SimpleString outQueue = new SimpleString("outQueue");
      createSessionFactory();
      createSessionFactory2();

      // closeable will take care of closing it
      try (ClientSession session = sf.createSession(false, true, true);
           ClientProducer sendInitialProducer = session.createProducer();)
      {
         session.createQueue(inQueue, inQueue, null, true);
         session.createQueue(outQueue, outQueue, null, true);
         sendInitialProducer.send(inQueue, createMessage(session, 0, true));
      }

      ClientSession xaSessionRec = addClientSession(sf.createSession(true, false, false));

      ClientConsumer consumer = addClientConsumer(xaSessionRec.createConsumer(inQueue));

      byte[] globalTransactionId = UUIDGenerator.getInstance().generateStringUUID().getBytes();
      Xid xidRec = new XidImpl("xa2".getBytes(), 1, globalTransactionId);

      xaSessionRec.start();

      xaSessionRec.getXAResource().start(xidRec, XAResource.TMNOFLAGS);

      //failover is now occurring, receive, ack and end will be called whilst this is happening.

      ClientMessageImpl m = (ClientMessageImpl) consumer.receive(5000);

      assertNotNull(m);

      System.out.println("********************" + m.getIntProperty("counter"));
      //the mdb would ack the message before calling onMessage()
      m.acknowledge();

      try
      {
         //this may fail but thats ok, it depends on the race and when failover actually happens
         xaSessionRec.end(xidRec, XAResource.TMSUCCESS);
      }
      catch (XAException ignore)
      {
      }

      //we always reset the client on the RA
      ((ClientSessionInternal) xaSessionRec).resetIfNeeded();

      // closeable will take care of closing it
      try (ClientSession session = sf.createSession(false, true, true);
           ClientProducer sendInitialProducer = session.createProducer();)
      {
         sendInitialProducer.send(inQueue, createMessage(session, 0, true));
      }

      //now receive and send a message successfully

      globalTransactionId = UUIDGenerator.getInstance().generateStringUUID().getBytes();
      xidRec = new XidImpl("xa4".getBytes(), 1, globalTransactionId);
      xaSessionRec.getXAResource().start(xidRec, XAResource.TMNOFLAGS);

      Binding binding = backupServer.getServer().getPostOffice().getBinding(inQueue);
      Queue inQ = (Queue) binding.getBindable();

      m = (ClientMessageImpl) consumer.receive(5000);

      assertNotNull(m);
      //the mdb would ack the message before calling onMessage()
      m.acknowledge();

      System.out.println("********************" + m.getIntProperty("counter"));

      xaSessionRec.getXAResource().end(xidRec, XAResource.TMSUCCESS);
      xaSessionRec.getXAResource().prepare(xidRec);
      xaSessionRec.getXAResource().commit(xidRec, false);


      //let's close the consumer so anything pending is handled
      consumer.close();

      assertTrue("actual message count=" + inQ.getMessageCount(), inQ.getMessageCount() == 1);
   }


   @Test
   @BMRules
      (
         rules =
            {
               @BMRule
                  (
                     name = "trace clientsessionimpl commit",
                     targetClass = "org.hornetq.core.client.impl.ClientSessionImpl",
                     targetMethod = "commit",
                     targetLocation = "ENTRY",
                     action = "org.hornetq.byteman.tests.BMFailoverTest.serverToStop.getServer().stop(true)"
                  )
            }
      )
   public void testFailoverOnCommit() throws Exception
   {
      serverToStop = liveServer;
      locator = getServerLocator();
      locator.setFailoverOnInitialConnection(true);
      createSessionFactory();
      ClientSession session = createSessionAndQueue();

      ClientProducer producer = addClientProducer(session.createProducer(FailoverTestBase.ADDRESS));

      sendMessages(session, producer, 10);
      try
      {
         session.commit();
         fail("should have thrown an exception");
      }
      catch (HornetQTransactionOutcomeUnknownException e)
      {
         //pass
      }
      sendMessages(session, producer, 10);
      session.commit();
      Queue bindable = (Queue) backupServer.getServer().getPostOffice().getBinding(FailoverTestBase.ADDRESS).getBindable();
      assertTrue(bindable.getMessageCount() == 10);
   }

   @Test
   @BMRules
      (
         rules =
            {
               @BMRule
                  (
                     name = "trace clientsessionimpl commit",
                     targetClass = "org.hornetq.core.client.impl.ClientSessionImpl",
                     targetMethod = "commit",
                     targetLocation = "ENTRY",
                     action = "org.hornetq.byteman.tests.BMFailoverTest.serverToStop.getServer().stop(true)"
                  )
            }
      )
   public void testFailoverOnReceiveCommit() throws Exception
   {
      serverToStop = liveServer;
      locator = getServerLocator();
      locator.setFailoverOnInitialConnection(true);
      createSessionFactory();
      ClientSession session = createSessionAndQueue();

      ClientSession sendSession = createSession(sf, true, true);

      ClientProducer producer = addClientProducer(sendSession.createProducer(FailoverTestBase.ADDRESS));

      sendMessages(sendSession, producer, 10);

      ClientConsumer consumer = session.createConsumer(FailoverTestBase.ADDRESS);
      session.start();
      for (int i = 0; i < 10; i++)
      {
         ClientMessage m = consumer.receive(500);
         assertNotNull(m);
         m.acknowledge();
      }
      try
      {
         session.commit();
         fail("should have thrown an exception");
      }
      catch (HornetQTransactionOutcomeUnknownException e)
      {
         //pass
      }
      catch (HornetQTransactionRolledBackException e1)
      {
         //pass
      }
      Queue bindable = (Queue) backupServer.getServer().getPostOffice().getBinding(FailoverTestBase.ADDRESS).getBindable();
      assertTrue("messager count = " + bindable.getMessageCount(), bindable.getMessageCount() == 10);
   }

   @Override
   protected TransportConfiguration getAcceptorTransportConfiguration(final boolean live)
   {
      return getNettyAcceptorTransportConfiguration(live);
   }

   @Override
   protected TransportConfiguration getConnectorTransportConfiguration(final boolean live)
   {
      return getNettyConnectorTransportConfiguration(live);
   }

   private ClientSession createSessionAndQueue() throws Exception
   {
      ClientSession session = createSession(sf, false, false);

      session.createQueue(FailoverTestBase.ADDRESS, FailoverTestBase.ADDRESS, null, true);
      return session;
   }

   private ClientSession createXASessionAndQueue() throws Exception
   {
      ClientSession session = addClientSession(sf.createSession(true, true, true));

      session.createQueue(FailoverTestBase.ADDRESS, FailoverTestBase.ADDRESS, null, true);
      return session;
   }

   protected ClientSession
   createSession(ClientSessionFactory sf1, boolean autoCommitSends, boolean autoCommitAcks) throws Exception
   {
      return addClientSession(sf1.createSession(autoCommitSends, autoCommitAcks));
   }
   protected ClientSession
   createSession(ClientSessionFactory sf1, boolean xa,boolean autoCommitSends, boolean autoCommitAcks) throws Exception
   {
      return addClientSession(sf1.createSession(xa, autoCommitSends, autoCommitAcks));
   }

   private void createSessionFactory() throws Exception
   {
      locator.setBlockOnNonDurableSend(true);
      locator.setBlockOnDurableSend(true);
      locator.setReconnectAttempts(-1);

      sf = createSessionFactoryAndWaitForTopology(locator, 2);
   }

   private void createSessionFactory2() throws Exception
   {
      sf2 = createSessionFactoryAndWaitForTopology(locator, 2);
   }
}
