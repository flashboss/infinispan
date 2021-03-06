/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2009, Red Hat Middleware LLC, and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.infinispan.lucene.locking;

import java.io.IOException;

import javax.transaction.Transaction;
import javax.transaction.TransactionManager;

import org.apache.lucene.store.Lock;
import org.infinispan.AdvancedCache;
import org.infinispan.Cache;
import org.infinispan.CacheException;
import org.infinispan.context.Flag;
import org.infinispan.lucene.FileCacheKey;
import org.infinispan.util.logging.Log;
import org.infinispan.util.logging.LogFactory;

/**
 * Inter-IndexWriter Lucene index lock based on Infinispan.
 * There are pros and cons about using this implementation, please see javadoc on
 * the factory class <code>org.infinispan.lucene.locking.TransactionalLockFactory</code>
 * 
 * @since 4.0
 * @author Sanne Grinovero
 * @see org.infinispan.lucene.locking.TransactionalLockFactory
 * @see org.apache.lucene.store.Lock
 */
@SuppressWarnings("unchecked")
class TransactionalSharedLuceneLock extends Lock {

   private static final Log log = LogFactory.getLog(TransactionalSharedLuceneLock.class);
   private static final Flag[] lockFlags = new Flag[]{Flag.SKIP_CACHE_STORE};

   private final AdvancedCache cache;
   private final String lockName;
   private final String indexName;
   private final TransactionManager tm;
   private final FileCacheKey keyOfLock;

   TransactionalSharedLuceneLock(Cache cache, String indexName, String lockName, TransactionManager tm) {
      this.cache = cache.getAdvancedCache();
      this.lockName = lockName;
      this.indexName = indexName;
      this.tm = tm;
      this.keyOfLock = new FileCacheKey(indexName, lockName);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public boolean obtain() throws IOException {
      Object previousValue = cache.withFlags(lockFlags).putIfAbsent(keyOfLock, keyOfLock);
      if (previousValue == null) {
         if (log.isTraceEnabled()) {
            log.trace("Lock: {0} acquired for index: {1}", lockName, indexName);
         }
         // we own the lock:
         startTransaction();
         return true;
      } else {
         if (log.isTraceEnabled()) {
            log.trace("Lock: {0} not aquired for index: {1}, was taken already.", lockName, indexName);
         }
         return false;
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void release() throws IOException {
      try {
         commitTransactions();
      }
      finally {
         clearLock();
      }
   }

   /**
    * Removes the lock, without committing pending changes or involving transactions. Used by Lucene
    * at Directory creation: we expect the lock to not exist in this case.
    */
   private void clearLock() {
      Object previousValue = cache.withFlags(lockFlags).remove(keyOfLock);
      if (previousValue!=null && log.isTraceEnabled()) {
         log.trace("Lock removed for index: {0}", indexName);
      }
   }
   
   @Override
   public boolean isLocked() {
      boolean locked = false;
      Transaction tx = null;
      try {
         // if there is an ongoing transaction we need to suspend it
         if ((tx = tm.getTransaction()) != null) {
            tm.suspend();
         }
         locked = cache.withFlags(lockFlags).containsKey(keyOfLock);
      } catch (Exception e) {
         log.error("Error in suspending transaction", e);
      } finally {
         if (tx != null) {
            try {
               tm.resume(tx);
            } catch (Exception e) {
               throw new CacheException("Unable to resume suspended transaction " + tx, e);
            }
         }
      }
      return locked;
   }
   
   /**
    * Starts a new transaction. Used to batch changes in LuceneDirectory:
    * a transaction is created at lock acquire, and closed on release.
    * It's also committed and started again at each IndexWriter.commit();
    * 
    * @throws IOException wraps Infinispan exceptions to adapt to Lucene's API
    */
   private void startTransaction() throws IOException {
      try {
         tm.begin();
      } catch (Exception e) {
         log.error("Unable to start transaction", e);
         throw new IOException("SharedLuceneLock could not start a transaction after having acquired the lock", e);
      }
      if (log.isTraceEnabled()) {
         log.trace("Batch transaction started for index: {0}", indexName);
      }
   }
   
   /**
    * Commits the existing transaction.
    * It's illegal to call this if a transaction was not started.
    * 
    * @throws IOException wraps Infinispan exceptions to adapt to Lucene's API
    */
   private void commitTransactions() throws IOException {
      try {
         tm.commit();
      } catch (Exception e) {
         log.error("Unable to commit work done!", e);
         throw new IOException("SharedLuceneLock could not commit a transaction", e);
      }
      if (log.isTraceEnabled()) {
         log.trace("Batch transaction commited for index: {0}", indexName);
      }
   }

   /**
    * Will clear the lock, eventually suspending a running transaction to make sure the
    * release is immediately taking effect.
    */
   public void clearLockSuspending() {
      Transaction tx = null;
      try {
         // if there is an ongoing transaction we need to suspend it
         if ((tx = tm.getTransaction()) != null) {
            tm.suspend();
         }
         clearLock();
      } catch (Exception e) {
         log.error("Error in suspending transaction", e);
      } finally {
         if (tx != null) {
            try {
               tm.resume(tx);
            } catch (Exception e) {
               throw new CacheException("Unable to resume suspended transaction " + tx, e);
            }
         }
      }
   }

}