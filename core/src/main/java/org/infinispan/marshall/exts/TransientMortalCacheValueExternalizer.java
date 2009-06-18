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
package org.infinispan.marshall.exts;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import net.jcip.annotations.Immutable;

import org.infinispan.container.entries.InternalEntryFactory;
import org.infinispan.container.entries.TransientMortalCacheValue;
import org.infinispan.io.UnsignedNumeric;
import org.infinispan.marshall.Externalizer;

/**
 * TransientMortalCacheValueExternalizer.
 * 
 * @author Galder Zamarreño
 * @since 4.0
 */
@Immutable
public class TransientMortalCacheValueExternalizer implements Externalizer {

   public void writeObject(ObjectOutput output, Object subject) throws IOException {
      TransientMortalCacheValue icv = (TransientMortalCacheValue) subject;
      output.writeObject(icv.getValue());
      UnsignedNumeric.writeUnsignedLong(output, icv.getCreated());
      output.writeLong(icv.getLifespan()); // could be negative so should not use unsigned longs
      UnsignedNumeric.writeUnsignedLong(output, icv.getLastUsed());
      output.writeLong(icv.getMaxIdle()); // could be negative so should not use unsigned longs
   }

   public Object readObject(ObjectInput input) throws IOException, ClassNotFoundException {
      Object v = input.readObject();
      long created = UnsignedNumeric.readUnsignedLong(input);
      Long lifespan = input.readLong();
      long lastUsed = UnsignedNumeric.readUnsignedLong(input);
      Long maxIdle = input.readLong();
      return InternalEntryFactory.createValue(v, created, lifespan, lastUsed, maxIdle);
   }
}