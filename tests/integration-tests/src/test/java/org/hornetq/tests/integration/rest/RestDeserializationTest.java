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

package org.hornetq.tests.integration.rest;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Session;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import java.io.File;
import java.io.Serializable;
import java.io.StringReader;
import java.util.HashMap;

import org.hornetq.api.core.TransportConfiguration;
import org.hornetq.core.client.impl.ServerLocatorImpl;
import org.hornetq.core.remoting.impl.invm.InVMConnectorFactory;
import org.hornetq.jms.client.HornetQConnectionFactory;
import org.hornetq.jms.client.HornetQDestination;
import org.hornetq.rest.HttpHeaderProperty;
import org.hornetq.tests.integration.rest.util.RestHornetQConnection;
import org.hornetq.tests.integration.rest.util.RestMessageContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class RestDeserializationTest extends RestTestBase
{

   private RestHornetQConnection restConnection;

   @Before
   public void setUp() throws Exception
   {
      super.setUp();
      createJettyServer("localhost", 12345);
      jmsServer.createQueue(true, "orders", null, true, "/jms/orders");
   }

   @After
   public void tearDown() throws Exception
   {
      if (restConnection != null)
      {
         restConnection.close();
      }
      super.tearDown();
   }

   @Test
   public void testWithoutBlackWhiteListQueue() throws Exception
   {
      deployAndconfigureRESTService("rest-test.war");

      Order order = new Order();
      order.setName("Bill");
      order.setItem("iPhone4");
      order.setAmount("$199.99");

      jmsSendMessage(order, "orders", true);

      String received = restReceiveQueueMessage("orders");

      Object object = xmlToObject(received);

      assertEquals(order, object);
   }

   @Test
   public void testWithoutBlackWhiteListTopic() throws Exception
   {
      deployAndconfigureRESTService("rest-test.war");

      RestMessageContext topicContext = restConnection.createTopicContext("ordersTopic");
      topicContext.initPullConsumers();

      Order order = new Order();
      order.setName("Bill");
      order.setItem("iPhone4");
      order.setAmount("$199.99");

      jmsSendMessage(order, "ordersTopic", false);

      String received = topicContext.pullMessage();

      Object object = xmlToObject(received);

      assertEquals(order, object);
   }

   @Test
   public void testBlackWhiteListQueuePull() throws Exception
   {
      deployAndconfigureRESTService("rest-test-bwlist.war");

      Order order = new Order();
      order.setName("Bill");
      order.setItem("iPhone4");
      order.setAmount("$199.99");

      jmsSendMessage(order, "orders", true);

      try
      {
         String received = restReceiveQueueMessage("orders");
         fail("Object should be rejected by blacklist, but " + received);
      }
      catch (IllegalStateException e)
      {
         String error = e.getMessage();
         assertTrue(error, error.contains("ClassNotFoundException"));
      }
   }

   @Test
   public void testBlackWhiteListTopicPull() throws Exception
   {
      deployAndconfigureRESTService("rest-test-bwlist.war");

      RestMessageContext topicContext = restConnection.createTopicContext("ordersTopic");
      topicContext.initPullConsumers();

      Order order = new Order();
      order.setName("Bill");
      order.setItem("iPhone4");
      order.setAmount("$199.99");

      jmsSendMessage(order, "ordersTopic", false);

      try
      {
         String received = topicContext.pullMessage();
         fail("object should have been rejected but: " + received);
      }
      catch (IllegalStateException e)
      {
         String error = e.getMessage();
         assertTrue(error, error.contains("ClassNotFoundException"));
      }
   }

   private void deployAndconfigureRESTService(String warFileName) throws Exception
   {
      jmsServer.createTopic(false, "ordersTopic", (String[]) null);
      File warFile = getResourceFile("/rest/" + warFileName, warFileName);
      deployWebApp("/restapp", warFile);
      server.start();
      String uri = server.getURI().toASCIIString();
      System.out.println("Sever started with uri: " + uri);

      restConnection = new RestHornetQConnection(uri);
   }

   private Object xmlToObject(String xmlString) throws JAXBException
   {
      JAXBContext jc = JAXBContext.newInstance(Order.class);
      Unmarshaller unmarshaller = jc.createUnmarshaller();
      StringReader reader = new StringReader(xmlString);
      return unmarshaller.unmarshal(reader);
   }

   private String restReceiveQueueMessage(String destName) throws Exception
   {
      RestMessageContext restContext = restConnection.createQueueContext(destName);
      String val = restContext.pullMessage();
      return val;
   }


   private void jmsSendMessage(Serializable value, String destName, boolean isQueue) throws JMSException
   {
      HashMap<String, Object> transportConfig = new HashMap<String, Object>();

      HornetQConnectionFactory factory = new HornetQConnectionFactory(new ServerLocatorImpl(false, new TransportConfiguration(InVMConnectorFactory.class.getName(), transportConfig)));
      String jmsDest;
      if (isQueue)
      {
         jmsDest = "jms.queue." + destName;
      }
      else
      {
         jmsDest = "jms.topic." + destName;
      }
      Destination destination = HornetQDestination.fromAddress(jmsDest);

      Connection conn = factory.createConnection();
      try
      {
         Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageProducer producer = session.createProducer(destination);
         ObjectMessage message = session.createObjectMessage();
         message.setStringProperty(HttpHeaderProperty.CONTENT_TYPE, "application/xml");
         message.setObject(value);
         producer.send(message);
      }
      finally
      {
         conn.close();
      }
   }
}
