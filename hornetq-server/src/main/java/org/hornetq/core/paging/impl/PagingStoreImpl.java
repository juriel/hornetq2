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
package org.hornetq.core.paging.impl;

import org.hornetq.api.core.SimpleString;
import org.hornetq.core.journal.SequentialFile;
import org.hornetq.core.journal.SequentialFileFactory;
import org.hornetq.core.paging.PageTransactionInfo;
import org.hornetq.core.paging.PagedMessage;
import org.hornetq.core.paging.PagingManager;
import org.hornetq.core.paging.PagingStore;
import org.hornetq.core.paging.PagingStoreFactory;
import org.hornetq.core.paging.cursor.LivePageCache;
import org.hornetq.core.paging.cursor.PageCursorProvider;
import org.hornetq.core.paging.cursor.impl.LivePageCacheImpl;
import org.hornetq.core.paging.cursor.impl.PageCursorProviderImpl;
import org.hornetq.core.persistence.StorageManager;
import org.hornetq.core.replication.ReplicationManager;
import org.hornetq.core.server.HornetQMessageBundle;
import org.hornetq.core.server.HornetQServerLogger;
import org.hornetq.core.server.LargeServerMessage;
import org.hornetq.core.server.MessageReference;
import org.hornetq.core.server.RouteContextList;
import org.hornetq.core.server.ServerMessage;
import org.hornetq.core.settings.impl.AddressFullMessagePolicy;
import org.hornetq.core.settings.impl.AddressSettings;
import org.hornetq.core.transaction.Transaction;
import org.hornetq.core.transaction.TransactionOperation;
import org.hornetq.core.transaction.TransactionPropertyIndexes;
import org.hornetq.utils.FutureLatch;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;

/**
 * @author <a href="mailto:clebert.suconic@jboss.com">Clebert Suconic</a>
 * @author <a href="mailto:tim.fox@jboss.com">Tim Fox</a>
 * @see PagingStore
 */
public class PagingStoreImpl implements PagingStore
{
   private final SimpleString address;

   private final StorageManager storageManager;

   private final DecimalFormat format = new DecimalFormat("000000000");

   private final AtomicInteger currentPageSize = new AtomicInteger(0);

   private final SimpleString storeName;

   // The FileFactory is created lazily as soon as the first write is attempted
   private volatile SequentialFileFactory fileFactory;

   private final PagingStoreFactory storeFactory;

   // Used to schedule sync threads
   private final PageSyncTimer syncTimer;

   private long maxSize;

   private long pageSize;

   private volatile AddressFullMessagePolicy addressFullMessagePolicy;

   private boolean printedDropMessagesWarning;

   private final PagingManager pagingManager;

   private final Executor executor;

   // Bytes consumed by the queue on the memory
   private final AtomicLong sizeInBytes = new AtomicLong();

   private volatile int numberOfPages;

   private volatile int firstPageId;

   private volatile int currentPageId;

   private volatile Page currentPage;

   private volatile boolean paging = false;

   private final PageCursorProvider cursorProvider;

   private final ReadWriteLock lock = new ReentrantReadWriteLock();

   private volatile boolean running = false;

   private final boolean syncNonTransactional;

   private static final boolean isTrace = HornetQServerLogger.LOGGER.isTraceEnabled();

   public PagingStoreImpl(final SimpleString address,
                          final ScheduledExecutorService scheduledExecutor,
                          final long syncTimeout,
                          final PagingManager pagingManager,
                          final StorageManager storageManager,
                          final SequentialFileFactory fileFactory,
                          final PagingStoreFactory storeFactory,
                          final SimpleString storeName,
                          final AddressSettings addressSettings,
                          final Executor executor,
                          final boolean syncNonTransactional)
   {
      if (pagingManager == null)
      {
         throw new IllegalStateException("Paging Manager can't be null");
      }

      this.address = address;

      this.storageManager = storageManager;

      this.storeName = storeName;

      applySetting(addressSettings);

      if (addressFullMessagePolicy == AddressFullMessagePolicy.PAGE && maxSize != -1 && pageSize >= maxSize)
      {
         throw new IllegalStateException("pageSize for address " + address +
            " >= maxSize. Normally pageSize should" +
            " be significantly smaller than maxSize, ms: " +
            maxSize +
            " ps " +
            pageSize);
      }

      this.executor = executor;

      this.pagingManager = pagingManager;

      this.fileFactory = fileFactory;

      this.storeFactory = storeFactory;

      this.syncNonTransactional = syncNonTransactional;

      if (scheduledExecutor != null)
      {
         this.syncTimer = new PageSyncTimer(this, scheduledExecutor, syncTimeout);
      }
      else
      {
         this.syncTimer = null;
      }

      this.cursorProvider = new PageCursorProviderImpl(this,
         this.storageManager,
         executor,
         addressSettings.getPageCacheMaxSize());

   }

   /**
    * @param addressSettings
    */
   public void applySetting(final AddressSettings addressSettings)
   {
      maxSize = addressSettings.getMaxSizeBytes();

      pageSize = addressSettings.getPageSizeBytes();

      addressFullMessagePolicy = addressSettings.getAddressFullMessagePolicy();

      if (cursorProvider != null)
      {
         cursorProvider.setCacheMaxSize(addressSettings.getPageCacheMaxSize());
      }
   }

   @Override
   public String toString()
   {
      return "PagingStoreImpl(" + this.address + ")";
   }

   @Override
   public boolean lock(long timeout)
   {
      if (timeout == -1)
      {
         lock.writeLock().lock();
         return true;
      }
      try
      {
         return lock.writeLock().tryLock(timeout, TimeUnit.MILLISECONDS);
      }
      catch (InterruptedException e)
      {
         return false;
      }
   }

   public void unlock()
   {
      lock.writeLock().unlock();
   }

   public PageCursorProvider getCursorProvider()
   {
      return cursorProvider;
   }

   public long getFirstPage()
   {
      return firstPageId;
   }

   public SimpleString getAddress()
   {
      return address;
   }

   public long getAddressSize()
   {
      return sizeInBytes.get();
   }

   public long getMaxSize()
   {
      return maxSize;
   }

   public AddressFullMessagePolicy getAddressFullMessagePolicy()
   {
      return addressFullMessagePolicy;
   }

   public long getPageSizeBytes()
   {
      return pageSize;
   }

   public String getFolder()
   {
      SequentialFileFactory factoryUsed = this.fileFactory;
      if (factoryUsed != null)
      {
         return factoryUsed.getDirectory();
      }
      else
      {
         return null;
      }
   }

   public boolean isPaging()
   {
      lock.readLock().lock();

      try
      {
         if (addressFullMessagePolicy == AddressFullMessagePolicy.BLOCK)
         {
            return false;
         }
         if (addressFullMessagePolicy == AddressFullMessagePolicy.FAIL)
         {
            return isFull();
         }
         if (addressFullMessagePolicy == AddressFullMessagePolicy.DROP)
         {
            return isFull();
         }
         return paging;
      }
      finally
      {
         lock.readLock().unlock();
      }
   }

   public int getNumberOfPages()
   {
      return numberOfPages;
   }

   public int getCurrentWritingPage()
   {
      return currentPageId;
   }

   public SimpleString getStoreName()
   {
      return storeName;
   }

   public void sync() throws Exception
   {
      if (syncTimer != null)
      {
         syncTimer.addSync(storageManager.getContext());
      }
      else
      {
         ioSync();
      }

   }

   public void ioSync() throws Exception
   {
      lock.readLock().lock();

      try
      {
         if (currentPage != null)
         {
            currentPage.sync();
         }
      }
      finally
      {
         lock.readLock().unlock();
      }
   }

   public void processReload() throws Exception
   {
      cursorProvider.processReload();
   }

   public PagingManager getPagingManager()
   {
      return pagingManager;
   }

   @Override
   public boolean isStarted()
   {
      return running;
   }

   @Override
   public synchronized void stop() throws Exception
   {
      if (running)
      {
         cursorProvider.stop();

         running = false;

         flushExecutors();

         if (currentPage != null)
         {
            currentPage.close();
            currentPage = null;
         }
      }
   }

   public void flushExecutors()
   {
      cursorProvider.flushExecutors();

      FutureLatch future = new FutureLatch();

      executor.execute(future);

      if (!future.await(60000))
      {
         HornetQServerLogger.LOGGER.pageStoreTimeout(address);
      }
   }

   @Override
   public void start() throws Exception
   {
      lock.writeLock().lock();

      try
      {

         if (running)
         {
            // don't throw an exception.
            // You could have two threads adding PagingStore to a
            // ConcurrentHashMap,
            // and having both threads calling init. One of the calls should just
            // need to be ignored
            return;
         }
         else
         {
            running = true;
            firstPageId = Integer.MAX_VALUE;

            // There are no files yet on this Storage. We will just return it empty
            if (fileFactory != null)
            {

               currentPageId = 0;
               if (currentPage != null)
               {
                  currentPage.close();
               }
               currentPage = null;

               List<String> files = fileFactory.listFiles("page");

               numberOfPages = files.size();

               for (String fileName : files)
               {
                  final int fileId = PagingStoreImpl.getPageIdFromFileName(fileName);

                  if (fileId > currentPageId)
                  {
                     currentPageId = fileId;
                  }

                  if (fileId < firstPageId)
                  {
                     firstPageId = fileId;
                  }
               }

               if (currentPageId != 0)
               {
                  currentPage = createPage(currentPageId);
                  currentPage.open();

                  List<PagedMessage> messages = currentPage.read(storageManager);

                  LivePageCache pageCache = new LivePageCacheImpl(currentPage);

                  for (PagedMessage msg : messages)
                  {
                     pageCache.addLiveMessage(msg);
                     if (msg.getMessage().isLargeMessage())
                     {
                        // We have to do this since addLIveMessage will increment an extra one
                        ((LargeServerMessage) msg.getMessage()).decrementDelayDeletionCount();
                     }
                  }

                  currentPage.setLiveCache(pageCache);

                  currentPageSize.set(currentPage.getSize());

                  cursorProvider.addPageCache(pageCache);
               }

               // We will not mark it for paging if there's only a single empty file
               if (currentPage != null && !(numberOfPages == 1 && currentPage.getSize() == 0))
               {
                  startPaging();
               }
            }
         }

      }
      finally
      {
         lock.writeLock().unlock();
      }
   }

   public void stopPaging()
   {
      lock.writeLock().lock();
      try
      {
         paging = false;
         this.cursorProvider.onPageModeCleared();
      }
      finally
      {
         lock.writeLock().unlock();
      }
   }

   public boolean startPaging()
   {
      if (!running)
      {
         return false;
      }

      lock.readLock().lock();
      try
      {
         // I'm not calling isPaging() here because
         // isPaging will perform extra steps.
         // at this context it doesn't really matter what policy we are using
         // since this method is only called when paging.
         // Besides that isPaging() will perform lock.readLock() again which is not needed here
         // for that reason the attribute is used directly here.
         if (paging)
         {
            return false;
         }
      }
      finally
      {
         lock.readLock().unlock();
      }

      // if the first check failed, we do it again under a global currentPageLock
      // (writeLock) this time
      lock.writeLock().lock();

      try
      {
         // Same notes from previous if (paging) on this method will apply here
         if (paging)
         {
            return false;
         }

         if (currentPage == null)
         {
            try
            {
               openNewPage();
            }
            catch (Exception e)
            {
               // If not possible to starting page due to an IO error, we will just consider it non paging.
               // This shouldn't happen anyway
               HornetQServerLogger.LOGGER.pageStoreStartIOError(e);
               return false;
            }
         }

         paging = true;

         return true;
      }
      finally
      {
         lock.writeLock().unlock();
      }
   }

   public Page getCurrentPage()
   {
      return currentPage;
   }

   public boolean checkPageFileExists(final int pageNumber)
   {
      String fileName = createFileName(pageNumber);
      SequentialFile file = fileFactory.createSequentialFile(fileName, 1);
      return file.exists();
   }

   public Page createPage(final int pageNumber) throws Exception
   {
      String fileName = createFileName(pageNumber);

      if (fileFactory == null)
      {
         fileFactory = storeFactory.newFileFactory(getStoreName());
      }

      SequentialFile file = fileFactory.createSequentialFile(fileName, 1000);

      Page page = new Page(storeName, storageManager, fileFactory, file, pageNumber);

      // To create the file
      file.open();

      file.position(0);

      file.close();

      return page;
   }

   public void forceAnotherPage() throws Exception
   {
      openNewPage();
   }

   /**
    * Returns a Page out of the Page System without reading it.
    * <p>
    * The method calling this method will remove the page and will start reading it outside of any
    * locks. This method could also replace the current file by a new file, and that process is done
    * through acquiring a writeLock on currentPageLock.
    * </p>
    * <p>
    * Observation: This method is used internally as part of the regular depage process, but
    * externally is used only on tests, and that's why this method is part of the Testable Interface
    * </p>
    */
   public Page depage() throws Exception
   {
      lock.writeLock().lock(); // Make sure no checks are done on currentPage while we are depaging
      try
      {
         if (!running)
         {
            return null;
         }

         if (numberOfPages == 0)
         {
            return null;
         }
         else
         {
            numberOfPages--;

            final Page returnPage;

            // We are out of old pages, all that is left now is the current page.
            // On that case we need to replace it by a new empty page, and return the current page immediately
            if (currentPageId == firstPageId)
            {
               firstPageId = Integer.MAX_VALUE;

               if (currentPage == null)
               {
                  // sanity check... it shouldn't happen!
                  throw new IllegalStateException("CurrentPage is null");
               }

               returnPage = currentPage;
               returnPage.close();
               currentPage = null;

               // The current page is empty... which means we reached the end of the pages
               if (returnPage.getNumberOfMessages() == 0)
               {
                  stopPaging();
                  returnPage.open();
                  returnPage.delete(null);

                  // This will trigger this address to exit the page mode,
                  // and this will make HornetQ start using the journal again
                  return null;
               }
               else
               {
                  // We need to create a new page, as we can't lock the address until we finish depaging.
                  openNewPage();
               }

               return returnPage;
            }
            else
            {
               returnPage = createPage(firstPageId++);
            }

            return returnPage;
         }
      }
      finally
      {
         lock.writeLock().unlock();
      }

   }

   private final Queue<OurRunnable> onMemoryFreedRunnables = new ConcurrentLinkedQueue<OurRunnable>();

   private class MemoryFreedRunnablesExecutor implements Runnable
   {
      public void run()
      {
         Runnable runnable;

         while ((runnable = onMemoryFreedRunnables.poll()) != null)
         {
            runnable.run();
         }
      }
   }

   private final Runnable memoryFreedRunnablesExecutor = new MemoryFreedRunnablesExecutor();

   private static final class OurRunnable implements Runnable
   {
      private boolean ran;

      private final Runnable runnable;

      private OurRunnable(final Runnable runnable)
      {
         this.runnable = runnable;
      }

      public synchronized void run()
      {
         if (!ran)
         {
            runnable.run();

            ran = true;
         }
      }
   }

   public boolean checkMemory(final Runnable runWhenAvailable)
   {
      if (addressFullMessagePolicy == AddressFullMessagePolicy.BLOCK && maxSize != -1)
      {
         if (sizeInBytes.get() > maxSize)
         {
            OurRunnable ourRunnable = new OurRunnable(runWhenAvailable);

            onMemoryFreedRunnables.add(ourRunnable);

            // We check again to avoid a race condition where the size can come down just after the element
            // has been added, but the check to execute was done before the element was added
            // NOTE! We do not fix this race by locking the whole thing, doing this check provides
            // MUCH better performance in a highly concurrent environment
            if (sizeInBytes.get() <= maxSize)
            {
               // run it now
               ourRunnable.run();
            }

            return true;
         }
      }
      else if (addressFullMessagePolicy == AddressFullMessagePolicy.FAIL && maxSize != -1)
      {
         if (sizeInBytes.get() > maxSize)
         {
            return false;
         }
      }

      runWhenAvailable.run();

      return true;
   }

   public void addSize(final int size)
   {
      if (addressFullMessagePolicy == AddressFullMessagePolicy.BLOCK)
      {
         if (maxSize != -1)
         {
            long newSize = sizeInBytes.addAndGet(size);

            if (newSize <= maxSize)
            {
               if (!onMemoryFreedRunnables.isEmpty())
               {
                  executor.execute(memoryFreedRunnablesExecutor);
               }
            }
         }

         return;
      }
      else if (addressFullMessagePolicy == AddressFullMessagePolicy.PAGE)
      {
         final long addressSize = sizeInBytes.addAndGet(size);

         if (size > 0)
         {
            if (maxSize > 0 && addressSize > maxSize)
            {
               if (startPaging())
               {
                  if (PagingStoreImpl.isTrace)
                  {
                     HornetQServerLogger.LOGGER.pageStoreStart(storeName, addressSize, maxSize);
                  }
               }
            }
         }

         return;
      }
      else if (addressFullMessagePolicy == AddressFullMessagePolicy.DROP || addressFullMessagePolicy == AddressFullMessagePolicy.FAIL)
      {
         sizeInBytes.addAndGet(size);
      }

   }

   @Override
   public boolean
   page(ServerMessage message, final Transaction tx, RouteContextList listCtx, final ReadLock managerLock) throws Exception
   {

      if (!running)
      {
         throw new IllegalStateException("PagingStore(" + getStoreName() + ") not initialized");
      }

      boolean full = isFull();

      if (addressFullMessagePolicy == AddressFullMessagePolicy.DROP || addressFullMessagePolicy == AddressFullMessagePolicy.FAIL)
      {
         if (full)
         {
            if (!printedDropMessagesWarning)
            {
               printedDropMessagesWarning = true;

               HornetQServerLogger.LOGGER.pageStoreDropMessages(storeName);
            }

            if (message.isLargeMessage())
            {
               ((LargeServerMessage)message).deleteFile();
            }

            if (addressFullMessagePolicy == AddressFullMessagePolicy.FAIL)
            {
               throw HornetQMessageBundle.BUNDLE.addressIsFull(address.toString());
            }

            // Address is full, we just pretend we are paging, and drop the data
            return true;
         }
         else
         {
            return false;
         }
      }
      else if (addressFullMessagePolicy == AddressFullMessagePolicy.BLOCK)
      {
         return false;
      }

      // We need to ensure a read lock, as depage could change the paging state
      lock.readLock().lock();

      try
      {
         // First check done concurrently, to avoid synchronization and increase throughput
         if (!paging)
         {
            return false;
         }
      }
      finally
      {
         lock.readLock().unlock();
      }


      managerLock.lock();
      try
      {
         lock.writeLock().lock();

         try
         {
            if (!paging)
            {
               return false;
            }

            if (!message.isDurable())
            {
               // The address should never be transient when paging (even for non-persistent messages when paging)
               // This will force everything to be persisted
               message.forceAddress(address);
            }

            final long transactionID = tx == null ? -1 : tx.getID();
            PagedMessage pagedMessage = new PagedMessageImpl(message, routeQueues(tx, listCtx), transactionID);

            if (message.isLargeMessage())
            {
               ((LargeServerMessage) message).setPaged();
            }

            int bytesToWrite = pagedMessage.getEncodeSize() + Page.SIZE_RECORD;

            if (currentPageSize.addAndGet(bytesToWrite) > pageSize && currentPage.getNumberOfMessages() > 0)
            {
               // Make sure nothing is currently validating or using currentPage
               openNewPage();
               currentPageSize.addAndGet(bytesToWrite);
            }

            if (tx != null)
            {
               installPageTransaction(tx, listCtx);
            }

            // the apply counter will make sure we write a record on journal
            // especially on the case for non transactional sends and paging
            // doing this will give us a possibility of recovering the page counters
            applyPageCounters(tx, getCurrentPage(), listCtx);

            currentPage.write(pagedMessage);

            if (tx == null && syncNonTransactional)
            {
               sync();
            }

            if (isTrace)
            {
               HornetQServerLogger.LOGGER.trace("Paging message " + pagedMessage + " on pageStore " + this.getStoreName() +
                  " pageId=" + currentPage.getPageId());
            }

            return true;
         }
         finally
         {
            lock.writeLock().unlock();
         }
      }
      finally
      {
         managerLock.unlock();
      }
   }

   /**
    * This method will disable cleanup of pages. No page will be deleted after this call.
    */
   public void disableCleanup()
   {
      getCursorProvider().disableCleanup();
   }


   /**
    * This method will re-enable cleanup of pages. Notice that it will also start cleanup threads.
    */
   public void enableCleanup()
   {
      getCursorProvider().resumeCleanup();
   }

   private long[] routeQueues(Transaction tx, RouteContextList ctx) throws Exception
   {
      List<org.hornetq.core.server.Queue> durableQueues = ctx.getDurableQueues();
      List<org.hornetq.core.server.Queue> nonDurableQueues = ctx.getNonDurableQueues();
      long[] ids = new long[durableQueues.size() + nonDurableQueues.size()];
      int i = 0;

      for (org.hornetq.core.server.Queue q : durableQueues)
      {
         q.getPageSubscription().notEmpty();
         ids[i++] = q.getID();
      }

      for (org.hornetq.core.server.Queue q : nonDurableQueues)
      {
         q.getPageSubscription().getCounter().increment(tx, 1);
         q.getPageSubscription().notEmpty();
         ids[i++] = q.getID();
      }
      return ids;
   }

   /**
    * This is done to prevent non tx to get out of sync in case of failures
    * @param tx
    * @param page
    * @param ctx
    * @throws Exception
    */
   private void applyPageCounters(Transaction tx, Page page, RouteContextList ctx) throws Exception
   {
      List<org.hornetq.core.server.Queue> durableQueues = ctx.getDurableQueues();
      List<org.hornetq.core.server.Queue> nonDurableQueues = ctx.getNonDurableQueues();
      for (org.hornetq.core.server.Queue q : durableQueues)
      {
         if (tx == null)
         {
            // non transactional writes need an intermediate place
            // to avoid the counter getting out of sync
            q.getPageSubscription().getCounter().pendingCounter(page, 1);
         }
         else
         {
            // null tx is treated through pending counters
            q.getPageSubscription().getCounter().increment(tx, 1);
         }
      }

      for (org.hornetq.core.server.Queue q : nonDurableQueues)
      {
         q.getPageSubscription().getCounter().increment(tx, 1);
      }

   }

   private void installPageTransaction(final Transaction tx, final RouteContextList listCtx) throws Exception
   {
      FinishPageMessageOperation pgOper = (FinishPageMessageOperation)tx.getProperty(TransactionPropertyIndexes.PAGE_TRANSACTION);
      if (pgOper == null)
      {
         PageTransactionInfo pgTX = new PageTransactionInfoImpl(tx.getID());
         pagingManager.addTransaction(pgTX);
         pgOper = new FinishPageMessageOperation(pgTX, storageManager, pagingManager);
         tx.putProperty(TransactionPropertyIndexes.PAGE_TRANSACTION, pgOper);
         tx.addOperation(pgOper);
      }

      pgOper.addStore(this);
      pgOper.pageTransaction.increment(listCtx.getNumberOfDurableQueues(), listCtx.getNumberOfNonDurableQueues());

      return;
   }

   private static class FinishPageMessageOperation implements TransactionOperation
   {
      private final PageTransactionInfo pageTransaction;
      private final StorageManager storageManager;
      private final PagingManager pagingManager;
      private final Set<PagingStore> usedStores = new HashSet<PagingStore>();

      private boolean stored = false;

      public void addStore(PagingStore store)
      {
         this.usedStores.add(store);
      }

      public FinishPageMessageOperation(final PageTransactionInfo pageTransaction,
                                        final StorageManager storageManager,
                                        final PagingManager pagingManager)
      {
         this.pageTransaction = pageTransaction;
         this.storageManager = storageManager;
         this.pagingManager = pagingManager;
      }

      public void afterCommit(final Transaction tx)
      {
         // If part of the transaction goes to the queue, and part goes to paging, we can't let depage start for the
         // transaction until all the messages were added to the queue
         // or else we could deliver the messages out of order

         if (pageTransaction != null)
         {
            pageTransaction.commit();
         }
      }

      public void afterPrepare(final Transaction tx)
      {
      }

      public void afterRollback(final Transaction tx)
      {
         if (pageTransaction != null)
         {
            pageTransaction.rollback();
         }
      }

      public void beforeCommit(final Transaction tx) throws Exception
      {
         syncStore();
         storePageTX(tx);
      }

      /**
       * @throws Exception
       */
      private void syncStore() throws Exception
      {
         for (PagingStore store : usedStores)
         {
            store.sync();
         }
      }

      public void beforePrepare(final Transaction tx) throws Exception
      {
         syncStore();
         storePageTX(tx);
      }

      private void storePageTX(final Transaction tx) throws Exception
      {
         if (!stored)
         {
            tx.setContainsPersistent();
            pageTransaction.store(storageManager, pagingManager, tx);
            stored = true;
         }
      }

      public void beforeRollback(final Transaction tx) throws Exception
      {
      }

      @Override
      public List<MessageReference> getRelatedMessageReferences()
      {
         return Collections.emptyList();
      }

      @Override
      public List<MessageReference> getListOnConsumer(long consumerID)
      {
         return Collections.emptyList();
      }

   }

   private void openNewPage() throws Exception
   {
      lock.writeLock().lock();

      try
      {
         numberOfPages++;

         int tmpCurrentPageId = currentPageId + 1;

         if (currentPage != null)
         {
            currentPage.close();
         }

         currentPage = createPage(tmpCurrentPageId);

         LivePageCache pageCache = new LivePageCacheImpl(currentPage);

         currentPage.setLiveCache(pageCache);

         cursorProvider.addPageCache(pageCache);

         currentPageSize.set(0);

         currentPage.open();

         currentPageId = tmpCurrentPageId;

         if (currentPageId < firstPageId)
         {
            firstPageId = currentPageId;
         }
      }
      finally
      {
         lock.writeLock().unlock();
      }
   }

   /**
    * @param pageID
    * @return
    */
   private String createFileName(final int pageID)
   {
      /** {@link DecimalFormat} is not thread safe. */
      synchronized (format)
      {
         return format.format(pageID) + ".page";
      }
   }

   private static int getPageIdFromFileName(final String fileName)
   {
      return Integer.parseInt(fileName.substring(0, fileName.indexOf('.')));
   }

   // To be used on isDropMessagesWhenFull
   private boolean isFull()
   {
      return maxSize > 0 && getAddressSize() > maxSize;
   }

   @Override
   public Collection<Integer> getCurrentIds() throws Exception
   {
      List<Integer> ids = new ArrayList<Integer>();
      if (fileFactory != null)
      {
         for (String fileName : fileFactory.listFiles("page"))
         {
            ids.add(getPageIdFromFileName(fileName));
         }
      }
      return ids;
   }

   @Override
   public void sendPages(ReplicationManager replicator, Collection<Integer> pageIds) throws Exception
   {
      lock.writeLock().lock();
      try
      {
         for (Integer id : pageIds)
         {
            SequentialFile sFile = fileFactory.createSequentialFile(createFileName(id), 1);
            if (!sFile.exists())
            {
               continue;
            }
            replicator.syncPages(sFile, id, getAddress());
         }
      }
      finally
      {
         lock.writeLock().unlock();
      }
   }


   // Inner classes -------------------------------------------------
}
