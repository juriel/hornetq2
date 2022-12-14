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

package org.hornetq.core.server;

import org.hornetq.api.core.DiscoveryGroupConfiguration;
import org.hornetq.api.core.HornetQAddressFullException;
import org.hornetq.api.core.HornetQClusterSecurityException;
import org.hornetq.api.core.HornetQConnectionTimedOutException;
import org.hornetq.api.core.HornetQDisconnectedException;
import org.hornetq.api.core.HornetQDuplicateMetaDataException;
import org.hornetq.api.core.HornetQException;
import org.hornetq.api.core.HornetQIOErrorException;
import org.hornetq.api.core.HornetQIllegalStateException;
import org.hornetq.api.core.HornetQIncompatibleClientServerException;
import org.hornetq.api.core.HornetQInternalErrorException;
import org.hornetq.api.core.HornetQInvalidFilterExpressionException;
import org.hornetq.api.core.HornetQInvalidTransientQueueUseException;
import org.hornetq.api.core.HornetQNonExistentQueueException;
import org.hornetq.api.core.HornetQQueueExistsException;
import org.hornetq.api.core.HornetQSecurityException;
import org.hornetq.api.core.HornetQSessionCreationException;
import org.hornetq.api.core.SimpleString;
import org.hornetq.core.postoffice.Binding;
import org.hornetq.core.protocol.core.impl.wireformat.ReplicationSyncFileMessage;
import org.hornetq.core.security.CheckType;
import org.jboss.logging.Cause;
import org.jboss.logging.Message;
import org.jboss.logging.MessageBundle;
import org.jboss.logging.Messages;

import java.io.File;

/**
 * Logger Code 11
 * <p>
 * Each message id must be 6 digits long starting with 10, the 3rd digit should be 9. So the range
 * is from 119000 to 119999.
 * <p>
 * Once released, methods should not be deleted as they may be referenced by knowledge base
 * articles. Unused methods should be marked as deprecated.
 * @author <a href="mailto:andy.taylor@jboss.org">Andy Taylor</a>
 */
@MessageBundle(projectCode = "HQ")
public interface HornetQMessageBundle
{
   HornetQMessageBundle BUNDLE = Messages.getBundle(HornetQMessageBundle.class);

   @Message(id = 119000, value = "Activation for server {0}", format = Message.Format.MESSAGE_FORMAT)
   String activationForServer(HornetQServer server);

   @Message(id = 119001, value = "Generating thread dump because - {0}", format = Message.Format.MESSAGE_FORMAT)
   String generatingThreadDump(String reason);

   @Message(id = 119002, value = "Thread {0} name = {1} id = {2} group = {3}", format = Message.Format.MESSAGE_FORMAT)
   String threadDump(Thread key, String name, Long id, ThreadGroup threadGroup);

   @Message(id = 119003, value = "End Thread dump", format = Message.Format.MESSAGE_FORMAT)
   String endThreadDump();

   @Message(id = 119004, value = "Information about server {0}\nCluster Connection:{1}", format = Message.Format.MESSAGE_FORMAT)
   String serverDescribe(String identity, String describe);

   @Message(id = 119005, value = "connections for {0} closed by management" , format = Message.Format.MESSAGE_FORMAT)
   HornetQInternalErrorException connectionsClosedByManagement(String ipAddress);

   @Message(id = 119006, value = "journals are not JournalImpl. You can't set a replicator!" , format = Message.Format.MESSAGE_FORMAT)
   HornetQInternalErrorException notJournalImpl();

   @Message(id = 119007, value = "unhandled error during replication" , format = Message.Format.MESSAGE_FORMAT)
   HornetQInternalErrorException replicationUnhandledError(@Cause Exception e);

   @Message(id = 119008, value = "Live Node contains more journals than the backup node. Probably a version match error", format = Message.Format.MESSAGE_FORMAT)
   HornetQInternalErrorException replicationTooManyJournals();

   @Message(id = 119009, value = "Unhandled file type {0}", format = Message.Format.MESSAGE_FORMAT)
   HornetQInternalErrorException replicationUnhandledFileType(ReplicationSyncFileMessage.FileType fileType);

   @Message(id = 119010, value = "Remote Backup can not be up-to-date!", format = Message.Format.MESSAGE_FORMAT)
   HornetQInternalErrorException replicationBackupUpToDate();

   @Message(id = 119011, value = "unhandled data type!", format = Message.Format.MESSAGE_FORMAT)
   HornetQInternalErrorException replicationUnhandledDataType();

   @Message(id = 119012, value = "No binding for divert {0}", format = Message.Format.MESSAGE_FORMAT)
   HornetQInternalErrorException noBindingForDivert(SimpleString name);

   @Message(id = 119013, value = "Binding {0} is not a divert", format = Message.Format.MESSAGE_FORMAT)
   HornetQInternalErrorException bindingNotDivert(SimpleString name);

   @Message(id = 119014,
            value = "Did not receive data from {0}. It is likely the client has exited or crashed without "
                     +
                                  "closing its connection, or the network between the server and client has failed. " +
                                  "You also might have configured connection-ttl and client-failure-check-period incorrectly. " +
                                  "Please check user manual for more information." +
                                  " The connection will now be closed.", format = Message.Format.MESSAGE_FORMAT)
   HornetQConnectionTimedOutException clientExited(String remoteAddress);

   @Message(id = 119015, value = "Timeout on waiting I/O completion", format = Message.Format.MESSAGE_FORMAT)
   HornetQIOErrorException ioTimeout();

   @Message(id = 119016, value =  "queue {0} has been removed cannot deliver message, queues should not be removed when grouping is used", format = Message.Format.MESSAGE_FORMAT)
   HornetQNonExistentQueueException groupingQueueRemoved(SimpleString chosenClusterName);

   @Message(id = 119017, value =  "Queue {0} does not exist", format = Message.Format.MESSAGE_FORMAT)
   HornetQNonExistentQueueException noSuchQueue(SimpleString queueName);

   @Message(id = 119018, value =  "Binding already exists {0}", format = Message.Format.MESSAGE_FORMAT)
   HornetQQueueExistsException bindingAlreadyExists(Binding binding);

   @Message(id = 119019, value =  "Queue already exists {0}", format = Message.Format.MESSAGE_FORMAT)
   HornetQQueueExistsException queueAlreadyExists(SimpleString queueName);

   @Message(id = 119020, value =  "Invalid filter: {0}", format = Message.Format.MESSAGE_FORMAT)
   HornetQInvalidFilterExpressionException invalidFilter(@Cause Throwable e, SimpleString filter);

   @Message(id = 119021, value =  "MessageId was not assigned to Message", format = Message.Format.MESSAGE_FORMAT)
   HornetQIllegalStateException messageIdNotAssigned();

   @Message(id = 119022, value =  "Cannot compare journals if not in sync!", format = Message.Format.MESSAGE_FORMAT)
   HornetQIllegalStateException journalsNotInSync();

   @Message(id = 119023, value =  "Connected server is not a backup server", format = Message.Format.MESSAGE_FORMAT)
   HornetQIllegalStateException serverNotBackupServer();

   @Message(id = 119024, value =  "Backup replication server is already connected to another server", format = Message.Format.MESSAGE_FORMAT)
   HornetQIllegalStateException alreadyHaveReplicationServer();

   @Message(id = 119025, value =  "Cannot delete queue {0} on binding {1} - it has consumers = {2}", format = Message.Format.MESSAGE_FORMAT)
   HornetQIllegalStateException cannotDeleteQueue(SimpleString name, SimpleString queueName, String s);

   @Message(id = 119026, value =  "Backup Server was not yet in sync with live", format = Message.Format.MESSAGE_FORMAT)
   HornetQIllegalStateException backupServerNotInSync();

   @Message(id = 119027, value =  "Could not find reference on consumer ID={0}, messageId = {1} queue = {2}", format = Message.Format.MESSAGE_FORMAT)
   HornetQIllegalStateException consumerNoReference(Long id, Long messageID, SimpleString name);

   @Message(id = 119028, value =  "Consumer {0} doesn't exist on the server" , format = Message.Format.MESSAGE_FORMAT)
   HornetQIllegalStateException consumerDoesntExist(long consumerID);

   @Message(id = 119029, value =  "No address configured on the Server's Session" , format = Message.Format.MESSAGE_FORMAT)
   HornetQIllegalStateException noAddress();

   @Message(id = 119030, value =  "large-message not initialized on server", format = Message.Format.MESSAGE_FORMAT)
   HornetQIllegalStateException largeMessageNotInitialised();

   @Message(id = 119031, value =  "Unable to validate user: {0}", format = Message.Format.MESSAGE_FORMAT)
   HornetQSecurityException unableToValidateUser(String user);

   @Message(id = 119032, value =  "User: {0} doesn't have permission='{1}' on address {2}", format = Message.Format.MESSAGE_FORMAT)
   HornetQSecurityException userNoPermissions(String username, CheckType checkType, String saddress);

   @Message(id = 119033, value =  "Server and client versions incompatible", format = Message.Format.MESSAGE_FORMAT)
   HornetQIncompatibleClientServerException incompatibleClientServer();

   @Message(id = 119034, value = "Server not started", format = Message.Format.MESSAGE_FORMAT)
   HornetQSessionCreationException serverNotStarted();

   @Message(id = 119035, value =  "Metadata {0}={1} had been set already", format = Message.Format.MESSAGE_FORMAT)
   HornetQDuplicateMetaDataException duplicateMetadata(String key, String data);

   @Message(id = 119036, value = "Invalid type: {0}", format = Message.Format.MESSAGE_FORMAT)
   IllegalArgumentException invalidType(Object type);

   @Message(id = 119037, value = "retry interval must be positive, was {0}", format = Message.Format.MESSAGE_FORMAT)
   IllegalArgumentException invalidRetryInterval(Long size);

   @Message(id = 119038, value = "{0} must neither be null nor empty", format = Message.Format.MESSAGE_FORMAT)
   IllegalArgumentException emptyOrNull(String name);

   @Message(id = 119039, value = "{0}  must be greater than 0 (actual value: {1})", format = Message.Format.MESSAGE_FORMAT)
   IllegalArgumentException greaterThanZero(String name, Number val);

   @Message(id = 119040, value = "{0} must be a valid percentual value between 0 and 100 (actual value: {1})", format = Message.Format.MESSAGE_FORMAT)
   IllegalArgumentException notPercent(String name, Number val);

   @Message(id = 119041, value = "{0}  must be equals to -1 or greater than 0 (actual value: {1})", format = Message.Format.MESSAGE_FORMAT)
   IllegalArgumentException greaterThanMinusOne(String name, Number val);

   @Message(id = 119042, value = "{0}  must be equals to -1 or greater or equals to 0 (actual value: {1})", format = Message.Format.MESSAGE_FORMAT)
   IllegalArgumentException greaterThanZeroOrMinusOne(String name, Number val);

   @Message(id = 119043, value = "{0} must be between {1} and {2} inclusive (actual value: {3})", format = Message.Format.MESSAGE_FORMAT)
   IllegalArgumentException mustbeBetween(String name, Integer minPriority, Integer maxPriority, Object value);

   @Message(id = 119044, value = "Invalid journal type {0}", format = Message.Format.MESSAGE_FORMAT)
   IllegalArgumentException invalidJournalType(String val);

   @Message(id = 119045, value = "Invalid address full message policy type {0}", format = Message.Format.MESSAGE_FORMAT)
   IllegalArgumentException invalidAddressFullPolicyType(String val);

   @Message(id = 119046, value = "invalid value: {0} count must be greater than 0", format = Message.Format.MESSAGE_FORMAT)
   IllegalArgumentException greaterThanZero(Integer count);

   @Message(id = 119047, value = "Cannot set Message Counter Sample Period < {0}ms", format = Message.Format.MESSAGE_FORMAT)
   IllegalArgumentException invalidMessageCounterPeriod(Long period);

   @Message(id = 119048, value = "invalid new Priority value: {0}. It must be between 0 and 9 (both included)", format = Message.Format.MESSAGE_FORMAT)
   IllegalArgumentException invalidNewPriority(Integer period);

   @Message(id = 119049, value = "No queue found for {0}", format = Message.Format.MESSAGE_FORMAT)
   IllegalArgumentException noQueueFound(String otherQueueName);

   @Message(id = 119050, value = "Only NIO and AsyncIO are supported journals", format = Message.Format.MESSAGE_FORMAT)
   IllegalArgumentException invalidJournal();

   @Message(id = 119051, value = "Invalid journal type {0}", format = Message.Format.MESSAGE_FORMAT)
   IllegalArgumentException invalidJournalType2(JournalType journalType);

   @Message(id = 119052, value = "Directory {0} does not exist and cannot be created", format = Message.Format.MESSAGE_FORMAT)
   IllegalArgumentException cannotCreateDir(String dir);

   @Message(id = 119053, value = "Invalid index {0}", format = Message.Format.MESSAGE_FORMAT)
   IllegalArgumentException invalidIndex(Integer index);

   @Message(id = 119054, value = "Cannot convert to int", format = Message.Format.MESSAGE_FORMAT)
   IllegalArgumentException cannotConvertToInt();

   @Message(id = 119055, value = "Routing name is null", format = Message.Format.MESSAGE_FORMAT)
   IllegalArgumentException routeNameIsNull();

   @Message(id = 119056, value = "Cluster name is null", format = Message.Format.MESSAGE_FORMAT)
   IllegalArgumentException clusterNameIsNull();

   @Message(id = 119057, value = "Address is null", format = Message.Format.MESSAGE_FORMAT)
   IllegalArgumentException addressIsNull();

   @Message(id = 119058, value = "Binding type not specified", format = Message.Format.MESSAGE_FORMAT)
   IllegalArgumentException bindingTypeNotSpecified();

   @Message(id = 119059, value = "Binding ID is null", format = Message.Format.MESSAGE_FORMAT)
   IllegalArgumentException bindingIdNotSpecified();

   @Message(id = 119060, value = "Distance is null", format = Message.Format.MESSAGE_FORMAT)
   IllegalArgumentException distancenotSpecified();

   @Message(id = 119061, value = "Connection already exists with id {0}", format = Message.Format.MESSAGE_FORMAT)
   IllegalArgumentException connectionExists(Object id);

   @Message(id = 119062, value = "Acceptor with id {0} already registered", format = Message.Format.MESSAGE_FORMAT)
   IllegalArgumentException acceptorExists(Integer id);

   @Message(id = 119063, value = "Acceptor with id {0} not registered", format = Message.Format.MESSAGE_FORMAT)
   IllegalArgumentException acceptorNotExists(Integer id);

   @Message(id = 119064, value = "Unknown protocol {0}", format = Message.Format.MESSAGE_FORMAT)
   IllegalArgumentException unknownProtocol(String protocol);

   @Message(id = 119065, value = "node id is null", format = Message.Format.MESSAGE_FORMAT)
   IllegalArgumentException nodeIdNull();

   @Message(id = 119066, value = "Queue name is null", format = Message.Format.MESSAGE_FORMAT)
   IllegalArgumentException queueNameIsNull();

   @Message(id = 119067, value = "Cannot find resource with name {0}", format = Message.Format.MESSAGE_FORMAT)
   IllegalArgumentException cannotFindResource(String resourceName);

   @Message(id = 119068, value = "no getter method for {0}", format = Message.Format.MESSAGE_FORMAT)
   IllegalArgumentException noGetterMethod(String resourceName);

   @Message(id = 119069, value = "no operation {0}/{1}", format = Message.Format.MESSAGE_FORMAT)
   IllegalArgumentException noOperation(String operation, Integer length);

   @Message(id = 119070, value = "match can not be null", format = Message.Format.MESSAGE_FORMAT)
   IllegalArgumentException nullMatch();

   @Message(id = 119071, value = "# can only be at end of match", format = Message.Format.MESSAGE_FORMAT)
   IllegalArgumentException invalidMatch();

   @Message(id = 119072, value = "User cannot be null", format = Message.Format.MESSAGE_FORMAT)
   IllegalArgumentException nullUser();

   @Message(id = 119073, value = "Password cannot be null", format = Message.Format.MESSAGE_FORMAT)
   IllegalArgumentException nullPassword();

   @Message(id = 119074, value = "Error instantiating transformer class {0}" , format = Message.Format.MESSAGE_FORMAT)
   IllegalArgumentException errorCreatingTransformerClass(@Cause Exception e, String transformerClassName);

   @Message(id = 119075, value = "method autoEncode doesn't know how to convert {0} yet", format = Message.Format.MESSAGE_FORMAT)
   IllegalArgumentException autoConvertError(Class<? extends Object> aClass);

   /** Message used on on {@link org.hornetq.core.server.impl.HornetQServerImpl#destroyConnectionWithSessionMetadata(String, String)} */
   @Message(id = 119076, value = "Executing destroyConnection with {0}={1} through management's request", format = Message.Format.MESSAGE_FORMAT)
   String destroyConnectionWithSessionMetadataHeader(String key, String value);

   /** Message used on on {@link org.hornetq.core.server.impl.HornetQServerImpl#destroyConnectionWithSessionMetadata(String, String)} */
   @Message(id = 119077, value = "Closing connection {0}", format = Message.Format.MESSAGE_FORMAT)
   String destroyConnectionWithSessionMetadataClosingConnection(String serverSessionString);

   /** Exception used on on {@link org.hornetq.core.server.impl.HornetQServerImpl#destroyConnectionWithSessionMetadata(String, String)} */
   @Message(id = 119078, value = "Disconnected per admin's request on {0}={1}", format = Message.Format.MESSAGE_FORMAT)
   HornetQDisconnectedException destroyConnectionWithSessionMetadataSendException(String key, String value);

   /** Message used on on {@link org.hornetq.core.server.impl.HornetQServerImpl#destroyConnectionWithSessionMetadata(String, String)} */
   @Message(id = 119079, value = "No session found with {0}={1}", format = Message.Format.MESSAGE_FORMAT)
   String destroyConnectionWithSessionMetadataNoSessionFound(String key, String value);

   @Message(id = 119080, value =  "Invalid Page IO, PagingManager was stopped or closed", format = Message.Format.MESSAGE_FORMAT)
   HornetQIllegalStateException invalidPageIO();

   @Message(id = 119081, value =  "No Discovery Group configuration named {0} found", format = Message.Format.MESSAGE_FORMAT)
   HornetQException noDiscoveryGroupFound(DiscoveryGroupConfiguration dg);

   @Message(id = 119082, value =  "Queue {0} already exists on another subscription", format = Message.Format.MESSAGE_FORMAT)
   HornetQInvalidTransientQueueUseException queueSubscriptionBelongsToDifferentAddress(SimpleString queueName);

   @Message(id = 119083, value =  "Queue {0} has a different filter than requested", format = Message.Format.MESSAGE_FORMAT)
   HornetQInvalidTransientQueueUseException queueSubscriptionBelongsToDifferentFilter(SimpleString queueName);

   @Message(id = 119085, value = "Classpath lacks a protocol-manager for protocol {0}",
            format = Message.Format.MESSAGE_FORMAT)
   HornetQException noProtocolManagerFound(String protocol);

   // this code has to match with version 2.3.x as it's used on integration tests at Wildfly and JBoss EAP
   @Message(id = 119099, value = "Unable to authenticate cluster user: {0}",
            format = Message.Format.MESSAGE_FORMAT)
   HornetQClusterSecurityException unableToValidateClusterUser(String user);


   @Message(id = 119100, value = "Trying to move a journal file that refers to a file instead of a directory: {0}",
         format = Message.Format.MESSAGE_FORMAT)
   IllegalStateException journalDirIsFile(File fDir);

   @Message(id = 119101, value = "error trying to backup journal files at directory: {0}",
            format = Message.Format.MESSAGE_FORMAT)
   IllegalStateException couldNotMoveJournal(File dir);

   @Message(id = 119102, value = "Address \"{0}\" is full.", format = Message.Format.MESSAGE_FORMAT)
   HornetQAddressFullException addressIsFull(String addressName);

   @Message(id = 119103, value =  "Server is stopping. Message grouping not allowed", format = Message.Format.MESSAGE_FORMAT)
   HornetQException groupWhileStopping();

   @Message(id = 119105, value = "Server will not accept create session request since scale down has not occurred", format = Message.Format.MESSAGE_FORMAT)
   HornetQSessionCreationException sessionNotFailedOver();

   @Message(id = 119106, value = "Invalid slow consumer policy type {0}", format = Message.Format.MESSAGE_FORMAT)
   IllegalArgumentException invalidSlowConsumerPolicyType(String val);

   @Message(id = 119107, value = "consumer connections for address {0} closed by management", format = Message.Format.MESSAGE_FORMAT)
   HornetQInternalErrorException consumerConnectionsClosedByManagement(String address);

   @Message(id = 119108, value = "connections for user {0} closed by management", format = Message.Format.MESSAGE_FORMAT)
   HornetQInternalErrorException connectionsForUserClosedByManagement(String userName);
}
