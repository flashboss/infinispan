/*
 * JBoss, Home of Professional Open Source
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
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
package org.infinispan.atomic;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Map;

import org.infinispan.marshall.Ids;
import org.infinispan.marshall.Marshallable;

/**
 * An atomic put operation.
 * <p/>
 *
 * @author (various)
 * @param <K>
 * @param <V>
 * @since 4.0
 */
@Marshallable(externalizer = PutOperation.Externalizer.class, id = Ids.ATOMIC_PUT_OPERATION)
public class PutOperation<K, V> extends Operation<K, V> {
   private K key;
   private V oldValue;
   private V newValue;

   public PutOperation() {
   }

   PutOperation(K key, V oldValue, V newValue) {
      this.key = key;
      this.oldValue = oldValue;
      this.newValue = newValue;
   }

   public void rollback(Map<K, V> delegate) {
      if (oldValue == null)
         delegate.remove(key);
      else
         delegate.put(key, oldValue);
   }

   public void replay(Map<K, V> delegate) {
      delegate.put(key, newValue);
   }

   public static class Externalizer implements org.infinispan.marshall.Externalizer {
      public void writeObject(ObjectOutput output, Object object) throws IOException {
         PutOperation put = (PutOperation) object;
         output.writeObject(put.key);
         output.writeObject(put.newValue);
      }

      public Object readObject(ObjectInput input) throws IOException, ClassNotFoundException {
         PutOperation put = new PutOperation();
         put.key = input.readObject();
         put.newValue = input.readObject();         
         return put;
      }
   }
}