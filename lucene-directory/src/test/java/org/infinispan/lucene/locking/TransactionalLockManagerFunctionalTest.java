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

import org.apache.lucene.store.LockFactory;
import org.infinispan.Cache;
import org.testng.annotations.Test;

/**
 * TransactionalLockManagerFunctionalTest.
 * 
 * @author Sanne Grinovero
 * @since 4.0
 */
@SuppressWarnings("unchecked")
@Test(groups = "functional", testName = "lucene.locking.TransactionalLockManagerFunctionalTest", enabled = true)
public class TransactionalLockManagerFunctionalTest extends LockManagerFunctionalTest {
   
   protected LockFactory makeLockFactory(Cache cache, String commonIndexName) {
      return new TransactionalLockFactory(cache, commonIndexName);
   }

}
