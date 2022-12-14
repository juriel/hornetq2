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
package org.hornetq.rest.topic;

import org.hornetq.api.core.SimpleString;
import org.hornetq.api.core.client.ClientSession;
import org.hornetq.jms.client.ConnectionFactoryOptions;
import org.hornetq.rest.queue.DestinationServiceManager;

import java.util.ArrayList;
import java.util.List;

/**
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @version $Revision: 1 $
 */
public class TopicServiceManager extends DestinationServiceManager
{
   protected TopicPushStore pushStore;
   protected List<TopicDeployment> topics = new ArrayList<TopicDeployment>();
   protected TopicDestinationsResource destination;

   public TopicServiceManager(ConnectionFactoryOptions jmsOptions)
   {
      super(jmsOptions);
   }

   public TopicPushStore getPushStore()
   {
      return pushStore;
   }

   public void setPushStore(TopicPushStore pushStore)
   {
      this.pushStore = pushStore;
   }

   public List<TopicDeployment> getTopics()
   {
      return topics;
   }

   public void setTopics(List<TopicDeployment> topics)
   {
      this.topics = topics;
   }

   public TopicDestinationsResource getDestination()
   {
      return destination;
   }

   public void setDestination(TopicDestinationsResource destination)
   {
      this.destination = destination;
   }

   public void start() throws Exception
   {
      initDefaults();

      started = true;

      if (pushStoreFile != null && pushStore == null)
      {
         pushStore = new FileTopicPushStore(pushStoreFile);
      }

      if (destination == null)
      {
         destination = new TopicDestinationsResource(this);
      }

      for (TopicDeployment topic : topics)
      {
         deploy(topic);
      }
   }

   public void deploy(TopicDeployment topicDeployment) throws Exception
   {
      if (!started)
      {
         throw new Exception("You must start() this class instance before deploying");
      }
      String queueName = topicDeployment.getName();
      ClientSession session = sessionFactory.createSession(false, false, false);
      ClientSession.QueueQuery query = session.queueQuery(new SimpleString(queueName));
      boolean defaultDurable = topicDeployment.isDurableSend();
      if (query.isExists())
      {
         defaultDurable = query.isDurable();
      }
      else
      {
         session.createQueue(queueName, queueName, topicDeployment.isDurableSend());
      }
      session.close();

      destination.createTopicResource(queueName, defaultDurable, topicDeployment.getConsumerSessionTimeoutSeconds(), topicDeployment.isDuplicatesAllowed());
   }

   public void stop()
   {
      if (started == false) return;
      for (TopicResource topic : destination.getTopics().values())
      {
         topic.stop();
      }
      try
      {
         sessionFactory.close();
      }
      catch (Exception e)
      {
      }
   }
}