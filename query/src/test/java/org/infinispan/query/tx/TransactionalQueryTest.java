/*
 * JBoss, Home of Professional Open Source
 * Copyright 2009, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
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
package org.infinispan.query.tx;

import org.hibernate.search.annotations.Field;
import org.hibernate.search.annotations.Indexed;
import org.hibernate.search.annotations.ProvidedId;
import org.infinispan.Cache;
import org.infinispan.config.Configuration;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.query.CacheQuery;
import org.infinispan.query.QueryFactory;
import org.infinispan.query.backend.QueryHelper;
import org.infinispan.test.SingleCacheManagerTest;
import org.infinispan.test.fwk.TestCacheManagerFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.transaction.TransactionManager;
import java.util.Properties;

@Test(groups = "functional", testName = "query.tx.TransactionalQueryTest")
public class TransactionalQueryTest extends SingleCacheManagerTest {
   protected EmbeddedCacheManager m_cacheManager;
   private QueryHelper m_queryHelper;
   private Cache<String, Session> m_cache;
   private TransactionManager m_transactionManager;

   @Override
   protected EmbeddedCacheManager createCacheManager() throws Exception {
      Configuration c = getDefaultStandaloneConfig(true);
      c.setIndexingEnabled(true);
      c.setIndexLocalOnly(true);
      m_cacheManager = TestCacheManagerFactory.createCacheManager(c, true);
      m_cache = m_cacheManager.getCache();
      m_transactionManager = m_cache.getAdvancedCache().getTransactionManager();
      return m_cacheManager;
   }

   @BeforeMethod
   public void initialize() {
      try {
         // Make the hibernate cache an in memory cache
         Properties properties = new Properties();
         properties.put("hibernate.search.default.directory_provider", "org.hibernate.search.store.RAMDirectoryProvider");

         // Initialze the query helper.
         m_queryHelper = new QueryHelper(m_cache, properties, Session.class);

         // Initialize the cache
         m_transactionManager.begin();
         for (int i = 0; i < 100; i++) {
            m_cache.put(String.valueOf(i), new Session(String.valueOf(i)));
         }
         m_transactionManager.commit();
      }
      catch (Exception e) {
         e.printStackTrace();
      }
   }

   public void run() {
      try {
         // Verify querying works
         QueryFactory queryFactory = new QueryFactory(m_cache, m_queryHelper);
         CacheQuery cacheQuery = queryFactory.getBasicQuery("", "Id:2?");
         System.out.println("Hits: " + cacheQuery.getResultSize());

         // Remove something that exists
         m_transactionManager.begin();
         m_cache.remove("50");
         m_transactionManager.commit();

         // Remove something that doesn't exist with a transaction
         // This also fails without using a transaction
         m_transactionManager.begin();
         m_cache.remove("200");
         m_transactionManager.commit();
      }
      catch (Exception e) {
         e.printStackTrace();
      }
   }

   @ProvidedId
   @Indexed(index = "SessionIndex")
   public class Session {
      private String m_id;

      public Session(String id) {
         m_id = id;
      }

      @Field(name = "Id")
      public String getId() {
         return m_id;
      }
   }
}
