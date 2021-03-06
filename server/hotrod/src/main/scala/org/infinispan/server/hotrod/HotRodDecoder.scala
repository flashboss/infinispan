package org.infinispan.server.hotrod

import org.infinispan.stats.Stats
import org.infinispan.server.core._
import transport._
import OperationStatus._
import org.infinispan.manager.EmbeddedCacheManager
import java.io.StreamCorruptedException
import org.infinispan.server.hotrod.ProtocolFlag._
import org.infinispan.server.hotrod.OperationResponse._
import java.nio.channels.ClosedChannelException
import org.infinispan.{CacheException, Cache}
import org.infinispan.util.ByteArrayKey

/**
 * Top level Hot Rod decoder that after figuring out the version, delegates the rest of the reading to the
 * corresponding versioned decoder.
 *
 * @author Galder Zamarreño
 * @since 4.1
 */
class HotRodDecoder(cacheManager: EmbeddedCacheManager) extends AbstractProtocolDecoder[ByteArrayKey, CacheValue] {
   import HotRodDecoder._
   import HotRodServer._
   
   type SuitableHeader = HotRodHeader
   type SuitableParameters = RequestParameters

   private var isError = false
   private var joined = false
   private val isTrace = isTraceEnabled

   override def readHeader(buffer: ChannelBuffer): HotRodHeader = {
      try {
         val magic = buffer.readUnsignedByte
         if (magic != Magic) {
            if (!isError) {               
               throw new InvalidMagicIdException("Error reading magic byte or message id: " + magic)
            } else {
               if (isTrace) trace("Error happened previously, ignoring {0} byte until we find the magic number again", magic)
               return null // Keep trying to read until we find magic
            }
         }
      } catch {
         case e: Exception => {
            isError = true
            throw new ServerException(new ErrorHeader(0), e)
         }
      }

      val messageId = buffer.readUnsignedLong
      
      try {
         val version = buffer.readUnsignedByte
         val decoder = version match {
            case Version10 => Decoder10
            case _ => throw new UnknownVersionException("Unknown version:" + version)
         }
         val header = decoder.readHeader(buffer, messageId)
         if (isTrace) trace("Decoded header {0}", header)
         isError = false
         header
      } catch {
         case e: Exception => {
            isError = true
            throw new ServerException(new ErrorHeader(messageId), e)
         }
      }
   }

   override def getCache(header: HotRodHeader): Cache[ByteArrayKey, CacheValue] = {
      val cacheName = header.cacheName
      if (cacheName == TopologyCacheName)
         throw new CacheException("Remote requests are not allowed to topology cache. Do no send remote requests to cache "
               + TopologyCacheName)

      if (!cacheName.isEmpty && !cacheManager.getCacheNames.contains(cacheName))
         throw new CacheNotFoundException("Cache with name '" + cacheName + "' not found amongst the configured caches")

      getCacheInstance(cacheName, cacheManager)
   }

   override def readKey(h: HotRodHeader, b: ChannelBuffer): ByteArrayKey =
      h.decoder.readKey(b)

   override def readParameters(h: HotRodHeader, b: ChannelBuffer): Option[RequestParameters] =
      h.decoder.readParameters(h, b)

   override def createValue(h: HotRodHeader, p: RequestParameters, nextVersion: Long): CacheValue =
      h.decoder.createValue(p, nextVersion)

   override def createSuccessResponse(h: HotRodHeader, p: Option[RequestParameters], prev: CacheValue): AnyRef =
      h.decoder.createSuccessResponse(h, prev)

   override def createNotExecutedResponse(h: HotRodHeader, p: Option[RequestParameters], prev: CacheValue): AnyRef =
      h.decoder.createNotExecutedResponse(h, prev)

   override def createNotExistResponse(h: HotRodHeader, p: Option[RequestParameters]): AnyRef =
      h.decoder.createNotExistResponse(h)

   override def createGetResponse(h: HotRodHeader, k: ByteArrayKey, v: CacheValue): AnyRef =
      h.decoder.createGetResponse(h, v, h.op)

   override def createMultiGetResponse(h: HotRodHeader, pairs: Map[ByteArrayKey, CacheValue]): AnyRef =
      null // Unsupported

   override def handleCustomRequest(h: HotRodHeader, b: ChannelBuffer, cache: Cache[ByteArrayKey, CacheValue]): AnyRef = {
      val result = h.decoder.handleCustomRequest(h, b, cache)
      if (isTrace) trace("About to return: " + result)
      result
   }

   override def createStatsResponse(h: HotRodHeader, stats: Stats): AnyRef =
      h.decoder.createStatsResponse(h, stats)

   override def createErrorResponse(t: Throwable): AnyRef = {
      t match {
         case se: ServerException => {
            val h = se.header.asInstanceOf[HotRodHeader]
            se.getCause match {
               case i: InvalidMagicIdException => new ErrorResponse(0, "", 1, InvalidMagicOrMsgId, 0, i.toString)
               case u: UnknownOperationException => new ErrorResponse(h.messageId, "", 1, UnknownOperation, 0, u.toString)
               case u: UnknownVersionException => new ErrorResponse(h.messageId, "", 1, UnknownVersion, 0, u.toString)
               case t: Throwable => h.decoder.createErrorResponse(h, t)
            }
         }
         case c: ClosedChannelException => null
         case t: Throwable => new ErrorResponse(0, "", 1, ServerError, 0, t.toString)
      }
   }

   override protected def getOptimizedCache(h: HotRodHeader, c: Cache[ByteArrayKey, CacheValue]): Cache[ByteArrayKey, CacheValue] = {
      h.decoder.getOptimizedCache(h, c)
   }
}

object HotRodDecoder extends Logging {
   private val Magic = 0xA0
   private val Version10 = 10
}

class UnknownVersionException(reason: String) extends StreamCorruptedException(reason)

class InvalidMagicIdException(reason: String) extends StreamCorruptedException(reason)

class HotRodHeader(override val op: Enumeration#Value, val messageId: Long, val cacheName: String,
                   val flag: ProtocolFlag, val clientIntel: Short, val topologyId: Int,
                   val decoder: AbstractVersionedDecoder) extends RequestHeader(op) {
   override def toString = {
      new StringBuilder().append("HotRodHeader").append("{")
         .append("op=").append(op)
         .append(", messageId=").append(messageId)
         .append(", cacheName=").append(cacheName)
         .append(", flag=").append(flag)
         .append(", clientIntelligence=").append(clientIntel)
         .append(", topologyId=").append(topologyId)
         .append("}").toString
   }
}

class ErrorHeader(override val messageId: Long) extends HotRodHeader(ErrorResponse, messageId, "", NoFlag, 0, 0, null) {
   override def toString = {
      new StringBuilder().append("ErrorHeader").append("{")
         .append("messageId=").append(messageId)
         .append("}").toString
   }
}

class CacheNotFoundException(msg: String) extends CacheException(msg)