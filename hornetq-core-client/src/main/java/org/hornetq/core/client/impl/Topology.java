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
package org.hornetq.core.client.impl;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

import org.hornetq.api.core.TransportConfiguration;
import org.hornetq.api.core.client.ClusterTopologyListener;
import org.hornetq.core.client.HornetQClientLogger;
import org.hornetq.spi.core.remoting.Connector;

/**
 * @author <a href="mailto:andy.taylor@jboss.org">Andy Taylor</a>
 * @author Clebert Suconic
 *         Created Aug 16, 2010
 */
public final class Topology implements Serializable
{

   private static final long serialVersionUID = -9037171688692471371L;

   private final Set<ClusterTopologyListener> topologyListeners = new HashSet<ClusterTopologyListener>();

   private transient Executor executor = null;

   /**
    * Used to debug operations.
    * <p>
    * Someone may argue this is not needed. But it's impossible to debug anything related to
    * topology without knowing what node or what object missed a Topology update. Hence I added some
    * information to locate debugging here.
    */
   private volatile Object owner;

   /**
    * topology describes the other cluster nodes that this server knows about:
    *
    * keys are node IDs
    * values are a pair of live/backup transport configurations
    */
   private final Map<String, TopologyMemberImpl> topology = new ConcurrentHashMap<String, TopologyMemberImpl>();

   private transient Map<String, Long> mapDelete;

   public Topology(final Object owner)
   {
      this.owner = owner;
      if (HornetQClientLogger.LOGGER.isTraceEnabled())
      {
         HornetQClientLogger.LOGGER.trace("Topology@" + Integer.toHexString(System.identityHashCode(this)) + " CREATE",
                            new Exception("trace"));
      }
   }

   public void setExecutor(final Executor executor)
   {
      this.executor = executor;
   }

   /**
    * It will remove all elements as if it haven't received anyone from the server.
    */
   public void clear()
   {
      topology.clear();
   }

   public void addClusterTopologyListener(final ClusterTopologyListener listener)
   {
      if (HornetQClientLogger.LOGGER.isTraceEnabled())
      {
         HornetQClientLogger.LOGGER.trace(this + "::Adding topology listener " + listener, new Exception("Trace"));
      }
      synchronized (topologyListeners)
      {
         topologyListeners.add(listener);
      }
      this.sendTopology(listener);
   }

   public void removeClusterTopologyListener(final ClusterTopologyListener listener)
   {
      if (HornetQClientLogger.LOGGER.isTraceEnabled())
      {
         HornetQClientLogger.LOGGER.trace(this + "::Removing topology listener " + listener, new Exception("Trace"));
      }
      synchronized (topologyListeners)
      {
         topologyListeners.remove(listener);
      }
   }

   /** This is called by the server when the node is activated from backup state. It will always succeed */
   public void updateAsLive(final String nodeId, final TopologyMemberImpl memberInput)
   {
      synchronized (this)
      {
         if (HornetQClientLogger.LOGGER.isDebugEnabled())
         {
            HornetQClientLogger.LOGGER.debug(this + "::node " + nodeId + "=" + memberInput);
         }
         memberInput.setUniqueEventID(System.currentTimeMillis());
         topology.remove(nodeId);
         topology.put(nodeId, memberInput);
         sendMemberUp(nodeId, memberInput);
      }
   }

   /**
    * After the node is started, it will resend the notifyLive a couple of times to avoid gossip between two servers
    * @param nodeId
    */
   public void resendNode(final String nodeId)
   {
      synchronized (this)
      {
         TopologyMemberImpl memberInput = topology.get(nodeId);
         if (memberInput != null)
         {
            memberInput.setUniqueEventID(System.currentTimeMillis());
            sendMemberUp(nodeId, memberInput);
         }
      }
   }

   /** This is called by the server when the node is activated from backup state. It will always succeed */
   public TopologyMemberImpl updateBackup(final TopologyMemberImpl memberInput)
   {
      final String nodeId = memberInput.getNodeId();
      if (HornetQClientLogger.LOGGER.isTraceEnabled())
      {
         HornetQClientLogger.LOGGER.trace(this + "::updateBackup::" + nodeId + ", memberInput=" + memberInput);
      }

      synchronized (this)
      {
         TopologyMemberImpl currentMember = getMember(nodeId);
         if (currentMember == null)
         {
            if (HornetQClientLogger.LOGGER.isTraceEnabled())
            {
               HornetQClientLogger.LOGGER.trace("There's no live to be updated on backup update, node=" + nodeId + " memberInput=" + memberInput,
                                                new Exception("trace"));
            }

            currentMember = memberInput;
            topology.put(nodeId, currentMember);
         }

         TopologyMemberImpl newMember =
                  new TopologyMemberImpl(nodeId, currentMember.getBackupGroupName(), currentMember.getLive(),
                                         memberInput.getBackup());
         newMember.setUniqueEventID(System.currentTimeMillis());
         topology.remove(nodeId);
         topology.put(nodeId, newMember);
         sendMemberUp(nodeId, newMember);

         return newMember;
      }
   }

   /**
    * @param uniqueEventID an unique identifier for when the change was made. We will use current
    *           time millis for starts, and a ++ of that number for shutdown.
    * @param nodeId
    * @param memberInput
    * @return {@code true} if an update did take place. Note that backups are *always* updated.
    */
   public boolean updateMember(final long uniqueEventID, final String nodeId, final TopologyMemberImpl memberInput)
   {

      Long deleteTme = getMapDelete().get(nodeId);
      if (deleteTme != null && uniqueEventID != 0 && uniqueEventID < deleteTme)
      {
         HornetQClientLogger.LOGGER.debug("Update uniqueEvent=" + uniqueEventID +
                   ", nodeId=" +
                   nodeId +
                   ", memberInput=" +
                   memberInput +
                   " being rejected as there was a delete done after that");
         return false;
      }

      synchronized (this)
      {
         TopologyMemberImpl currentMember = topology.get(nodeId);

         if (currentMember == null)
         {
            if (HornetQClientLogger.LOGGER.isTraceEnabled())
            {
               HornetQClientLogger.LOGGER.trace(this + "::NewMemberAdd nodeId=" + nodeId + " member = " + memberInput,
                                          new Exception("trace"));
            }
            memberInput.setUniqueEventID(uniqueEventID);
            topology.put(nodeId, memberInput);
            sendMemberUp(nodeId, memberInput);
            return true;
         }
         if (uniqueEventID > currentMember.getUniqueEventID())
         {
            TopologyMemberImpl newMember =
                     new TopologyMemberImpl(nodeId, memberInput.getBackupGroupName(), memberInput.getLive(),
                                            memberInput.getBackup());

            if (newMember.getLive() == null && currentMember.getLive() != null)
            {
               newMember.setLive(currentMember.getLive());
            }

            if (newMember.getBackup() == null && currentMember.getBackup() != null)
            {
               newMember.setBackup(currentMember.getBackup());
            }

            if (HornetQClientLogger.LOGGER.isTraceEnabled())
            {
               HornetQClientLogger.LOGGER.trace(this + "::updated currentMember=nodeID=" + nodeId + ", currentMember=" +
                                                   currentMember + ", memberInput=" + memberInput + "newMember=" +
                                                   newMember,
                                          new Exception("trace"));
            }

            newMember.setUniqueEventID(uniqueEventID);
            topology.remove(nodeId);
            topology.put(nodeId, newMember);
            sendMemberUp(nodeId, newMember);

            return true;
         }
         /*
          * always add the backup, better to try to reconnect to something thats not there then to
          * not know about it at all
          */
         if (currentMember.getBackup() == null && memberInput.getBackup() != null)
         {
            currentMember.setBackup(memberInput.getBackup());
         }
         return false;
      }
   }

   /**
    * @param nodeId
    * @param memberToSend
    */
   private void sendMemberUp(final String nodeId, final TopologyMemberImpl memberToSend)
   {
      final ArrayList<ClusterTopologyListener> copy = copyListeners();

      if (HornetQClientLogger.LOGGER.isTraceEnabled())
      {
         HornetQClientLogger.LOGGER.trace(this + "::prepare to send " + nodeId + " to " + copy.size() + " elements");
      }

      if (copy.size() > 0)
      {
         execute(new Runnable()
         {
            public void run()
            {
               for (ClusterTopologyListener listener : copy)
               {
                  if (HornetQClientLogger.LOGGER.isTraceEnabled())
                  {
                     HornetQClientLogger.LOGGER.trace(Topology.this + " informing " +
                                        listener +
                                        " about node up = " +
                                        nodeId +
                                        " connector = " +
                                        memberToSend.getConnector());
                  }

                  try
                  {
                     listener.nodeUP(memberToSend, false);
                  }
                  catch (Throwable e)
                  {
                     HornetQClientLogger.LOGGER.errorSendingTopology(e);
                  }
               }
            }
         });
      }
   }

   /**
    * @return
    */
   private ArrayList<ClusterTopologyListener> copyListeners()
   {
      ArrayList<ClusterTopologyListener> listenersCopy;
      synchronized (topologyListeners)
      {
         listenersCopy = new ArrayList<ClusterTopologyListener>(topologyListeners);
      }
      return listenersCopy;
   }

   boolean removeMember(final long uniqueEventID, final String nodeId)
   {
      TopologyMemberImpl member;

      synchronized (this)
      {
         member = topology.get(nodeId);
         if (member != null)
         {
            if (member.getUniqueEventID() > uniqueEventID)
            {
               HornetQClientLogger.LOGGER.debug("The removeMember was issued before the node " + nodeId + " was started, ignoring call");
               member = null;
            }
            else
            {
               getMapDelete().put(nodeId, uniqueEventID);
               member = topology.remove(nodeId);
            }
         }
      }

      if (HornetQClientLogger.LOGGER.isTraceEnabled())
      {
         HornetQClientLogger.LOGGER.trace("removeMember " + this +
                            " removing nodeID=" +
                            nodeId +
                            ", result=" +
                            member +
                            ", size = " +
                            topology.size(), new Exception("trace"));
      }

      if (member != null)
      {
         final ArrayList<ClusterTopologyListener> copy = copyListeners();

         execute(new Runnable()
         {
            public void run()
            {
               for (ClusterTopologyListener listener : copy)
               {
                  if (HornetQClientLogger.LOGGER.isTraceEnabled())
                  {
                     HornetQClientLogger.LOGGER.trace(this + " informing " + listener + " about node down = " + nodeId);
                  }
                  try
                  {
                     listener.nodeDown(uniqueEventID, nodeId);
                  }
                  catch (Exception e)
                  {
                     HornetQClientLogger.LOGGER.errorSendingTopologyNodedown(e);
                  }
               }
            }
         });

      }
      return member != null;
   }

   private void execute(final Runnable runnable)
   {
      if (executor != null)
      {
         executor.execute(runnable);
      }
      else
      {
         runnable.run();
      }
   }

   public synchronized void sendTopology(final ClusterTopologyListener listener)
   {
      if (HornetQClientLogger.LOGGER.isDebugEnabled())
      {
         HornetQClientLogger.LOGGER.debug(this + " is sending topology to " + listener);
      }

      execute(new Runnable()
      {
         public void run()
         {
            int count = 0;

            final Map<String, TopologyMemberImpl> copy;

            synchronized (Topology.this)
            {
               copy = new HashMap<String, TopologyMemberImpl>(topology);
            }

            for (Map.Entry<String, TopologyMemberImpl> entry : copy.entrySet())
            {
               if (HornetQClientLogger.LOGGER.isDebugEnabled())
               {
                  HornetQClientLogger.LOGGER.debug(Topology.this + " sending " +
                            entry.getKey() +
                            " / " +
                            entry.getValue().getConnector() +
                            " to " +
                            listener);
               }
               listener.nodeUP(entry.getValue(), ++count == copy.size());
            }
         }
      });
   }

   public synchronized TopologyMemberImpl getMember(final String nodeID)
   {
      return topology.get(nodeID);
   }

   public synchronized TopologyMemberImpl getMember(final TransportConfiguration configuration)
   {
      for (TopologyMemberImpl member : topology.values())
      {
         if (member.isMember(configuration))
         {
            return member;
         }
      }

      return null;
   }

   public synchronized boolean isEmpty()
   {
      return topology.isEmpty();
   }

   public Collection<TopologyMemberImpl> getMembers()
   {
      ArrayList<TopologyMemberImpl> members;
      synchronized (this)
      {
         members = new ArrayList<TopologyMemberImpl>(topology.values());
      }
      return members;
   }

   synchronized int nodes()
   {
      int count = 0;
      for (TopologyMemberImpl member : topology.values())
      {
         if (member.getLive() != null)
         {
            count++;
         }
         if (member.getBackup() != null)
         {
            count++;
         }
      }
      return count;
   }

   public synchronized String describe()
   {
      return describe("");
   }

   private synchronized String describe(final String text)
   {
      StringBuilder desc = new StringBuilder(text + "topology on " + this + ":\n");
      for (Entry<String, TopologyMemberImpl> entry : new HashMap<String, TopologyMemberImpl>(topology).entrySet())
      {
         desc.append("\t" + entry.getKey() + " => " + entry.getValue() + "\n");
      }
      desc.append("\t" + "nodes=" + nodes() + "\t" + "members=" + members());
      if (topology.isEmpty())
      {
         desc.append("\tEmpty");
      }
      return desc.toString();
   }

   private int members()
   {
      return topology.size();
   }

   /** The owner exists mainly for debug purposes.
    *  When enabling logging and tracing, the Topology updates will include the owner, what will enable to identify
    *  what instances are receiving the updates, what will enable better debugging.*/
   public void setOwner(final Object owner)
   {
      this.owner = owner;
   }

   public TransportConfiguration getBackupForConnector(final Connector connector)
   {
      for (TopologyMemberImpl member : topology.values())
      {
         if (member.getLive() != null && connector.isEquivalent(member.getLive().getParams()))
         {
            return member.getBackup();
         }
      }
      return null;
   }

   @Override
   public String toString()
   {
      if (owner == null)
      {
         return "Topology@" + Integer.toHexString(System.identityHashCode(this));
      }
      return "Topology@" + Integer.toHexString(System.identityHashCode(this)) + "[owner=" + owner + "]";
   }

   private synchronized Map<String, Long> getMapDelete()
   {
      if (mapDelete == null)
      {
         mapDelete = new ConcurrentHashMap<String, Long>();
      }
      return mapDelete;
   }

}
