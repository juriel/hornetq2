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
package org.hornetq.rest.queue;

import org.hornetq.api.core.TransportConfiguration;
import org.hornetq.api.core.client.ClientSessionFactory;
import org.hornetq.api.core.client.HornetQClient;
import org.hornetq.api.core.client.ServerLocator;
import org.hornetq.core.remoting.impl.invm.InVMConnectorFactory;
import org.hornetq.jms.client.ConnectionFactoryOptions;
import org.hornetq.rest.util.LinkStrategy;
import org.hornetq.rest.util.TimeoutTask;
import org.hornetq.spi.core.naming.BindingRegistry;

/**
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @version $Revision: 1 $
 */
public abstract class DestinationServiceManager
{
   protected ServerLocator locator;
   protected ClientSessionFactory sessionFactory;
   protected ServerLocator consumerServerLocator;
   protected ClientSessionFactory consumerSessionFactory;
   protected boolean started;
   protected String pushStoreFile;
   protected DestinationSettings defaultSettings = DestinationSettings.defaultSettings;
   protected TimeoutTask timeoutTask;
   protected int producerPoolSize;
   protected long producerTimeToLive;
   protected LinkStrategy linkStrategy;
   protected BindingRegistry registry;

   protected ConnectionFactoryOptions jmsOptions;

   public DestinationServiceManager(ConnectionFactoryOptions jmsOptions)
   {
      this.jmsOptions = jmsOptions;
   }

   public BindingRegistry getRegistry()
   {
      return registry;
   }

   public void setRegistry(BindingRegistry registry)
   {
      this.registry = registry;
   }

   public LinkStrategy getLinkStrategy()
   {
      return linkStrategy;
   }

   public void setLinkStrategy(LinkStrategy linkStrategy)
   {
      this.linkStrategy = linkStrategy;
   }

   public long getProducerTimeToLive()
   {
      return producerTimeToLive;
   }

   public void setProducerTimeToLive(long producerTimeToLive)
   {
      this.producerTimeToLive = producerTimeToLive;
   }

   public int getProducerPoolSize()
   {
      return producerPoolSize;
   }

   public void setProducerPoolSize(int producerPoolSize)
   {
      this.producerPoolSize = producerPoolSize;
   }

   public ClientSessionFactory getConsumerSessionFactory()
   {
      return consumerSessionFactory;
   }

   public void setConsumerSessionFactory(ClientSessionFactory consumerSessionFactory)
   {
      this.consumerSessionFactory = consumerSessionFactory;
   }

   /**
    * @return the consumerServerLocator
    */
   public ServerLocator getConsumerServerLocator()
   {
      return consumerServerLocator;
   }

   /**
    * @param consumerServerLocator the consumerServerLocator to set
    */
   public void setConsumerServerLocator(ServerLocator consumerServerLocator)
   {
      this.consumerServerLocator = consumerServerLocator;
   }

   public TimeoutTask getTimeoutTask()
   {
      return timeoutTask;
   }

   public void setTimeoutTask(TimeoutTask timeoutTask)
   {
      this.timeoutTask = timeoutTask;
   }

   public DestinationSettings getDefaultSettings()
   {
      return defaultSettings;
   }

   public void setDefaultSettings(DestinationSettings defaultSettings)
   {
      this.defaultSettings = defaultSettings;
   }

   public ServerLocator getServerLocator()
   {
      return this.locator;
   }

   public void setServerLocator(ServerLocator locator)
   {
      this.locator = locator;
   }

   public ClientSessionFactory getSessionFactory()
   {
      return sessionFactory;
   }

   public void setSessionFactory(ClientSessionFactory sessionFactory)
   {
      this.sessionFactory = sessionFactory;
   }

   public String getPushStoreFile()
   {
      return pushStoreFile;
   }

   public void setPushStoreFile(String pushStoreFile)
   {
      this.pushStoreFile = pushStoreFile;
   }

   protected void initDefaults()
   {
      if (locator == null)
      {
         locator = HornetQClient.createServerLocatorWithoutHA(new TransportConfiguration(InVMConnectorFactory.class.getName()));
      }
      if (sessionFactory == null)
      {
         try
         {
            sessionFactory = locator.createSessionFactory();
         }
         catch (Exception e)
         {
            throw new RuntimeException(e.getMessage(), e);
         }
      }

      if (consumerSessionFactory == null) consumerSessionFactory = sessionFactory;

      if (timeoutTask == null) throw new RuntimeException("TimeoutTask is not set");
   }

   public abstract void start() throws Exception;

   public abstract void stop();

   public ConnectionFactoryOptions getJmsOptions()
   {
      return jmsOptions;
   }
}
