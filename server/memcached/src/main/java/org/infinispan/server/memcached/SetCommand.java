/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2009, Red Hat, Inc. and/or its affiliates, and
 * individual contributors as indicated by the @author tags. See the
 * copyright.txt file in the distribution for a full listing of
 * individual contributors.
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
package org.infinispan.server.memcached;

import java.util.Date;
import java.util.concurrent.TimeUnit;

import org.infinispan.Cache;
import org.infinispan.CacheException;
import org.infinispan.util.logging.Log;
import org.infinispan.util.logging.LogFactory;

import static org.infinispan.server.memcached.TextProtocolUtil.CRLF;
import static org.jboss.netty.buffer.ChannelBuffers.*;
import org.jboss.netty.channel.Channel;

/**
 * SetCommand.
 * 
 * @author Galder Zamarreño
 * @since 4.0
 */
public class SetCommand extends StorageCommand {

   private static final Log log = LogFactory.getLog(SetCommand.class);

   SetCommand(Cache cache, CommandType type, StorageParameters params, byte[] data) {
      super(cache, type, params, data);
   }

   @Override
   public Object perform(Channel ch) throws Exception {
      StorageReply reply;
      try {
         if (params.expiry == 0) {
            reply = put(params.key, params.flags, data);
         } else {
            if (params.expiry > TextProtocolUtil.SECONDS_IN_A_MONTH) {
               // If expiry bigger number of seconds in 30 days, then it's considered unix time
               long future = TimeUnit.SECONDS.toMillis(params.expiry);
               long expiry = future - System.currentTimeMillis();
               if (expiry > 0) {
                  reply = put(params.key, params.flags, data, expiry);
               } else {
                  StringBuilder sb = new StringBuilder();
                  sb.append("Given expiry is bigger than 30 days, hence is treated as Unix time, ")
                    .append("but this time is in the past: ").append(future)
                    .append(", date: ").append(new Date(future));
                  throw new CacheException(sb.toString());
               }
            } else {
               // Convert seconds to milliseconds to simplify code
               long expiry = TimeUnit.SECONDS.toMillis(params.expiry);
               reply = put(params.key, params.flags, data, expiry);
            }
         }
         
      } catch (Exception e) {
         log.error("Unexpected exception performing command", e);
         reply = StorageReply.NOT_STORED;
      }
      ch.write(wrappedBuffer(wrappedBuffer(reply.toString().getBytes()), wrappedBuffer(CRLF)));
      return null;
   }

   protected StorageReply put(String key, int flags, byte[] data) {
      return put(key, flags, data, -1);
   }

   protected StorageReply put(String key, int flags, byte[] data, long expiry) {
      Value value = new Value(flags, data);
      cache.put(key, value, expiry, TimeUnit.MILLISECONDS);
      return reply();
   }

   private StorageReply reply() {
      return StorageReply.STORED;
   }
}