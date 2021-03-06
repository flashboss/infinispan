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

import org.apache.lucene.store.Lock;
import org.infinispan.AdvancedCache;
import org.infinispan.Cache;
import org.infinispan.context.Flag;
import org.infinispan.lucene.FileCacheKey;
import org.infinispan.util.logging.Log;
import org.infinispan.util.logging.LogFactory;

/**
 * Inter-IndexWriter Lucene index lock based on Infinispan.
 * This implementation is not bound to and does not need a TransactionManager,
 * is more suited for large batch work and index optimization.
 * 
 * @since 4.0
 * @author Sanne Grinovero
 * @see org.apache.lucene.store.Lock
 */
@SuppressWarnings("unchecked")
class BaseLuceneLock extends Lock {

   private static final Log log = LogFactory.getLog(BaseLuceneLock.class);
   private static final Flag[] lockFlags = new Flag[]{Flag.SKIP_CACHE_STORE};

   private final AdvancedCache cache;
   private final String lockName;
   private final String indexName;
   private final FileCacheKey keyOfLock;

   BaseLuceneLock(Cache cache, String indexName, String lockName) {
      this.cache = cache.getAdvancedCache();
      this.lockName = lockName;
      this.indexName = indexName;
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
      clearLock();
   }

   /**
    * Used by Lucene at Directory creation: we expect the lock to not exist in this case.
    */
   public void clearLock() {
      Object previousValue = cache.withFlags(lockFlags).remove(keyOfLock);
      if (previousValue!=null && log.isTraceEnabled()) {
         log.trace("Lock removed for index: {0}", indexName);
      }
   }
   
   @Override
   public boolean isLocked() {
      boolean locked = cache.withFlags(lockFlags).containsKey(keyOfLock);
      return locked;
   }
   
}