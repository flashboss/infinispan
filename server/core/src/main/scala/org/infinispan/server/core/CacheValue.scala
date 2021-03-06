package org.infinispan.server.core

import org.infinispan.util.Util
import java.io.{ObjectOutput, ObjectInput}
import org.infinispan.marshall.Marshallable
import java.util.Arrays

/**
 * Represents the value part of a key/value pair stored in a protocol cache. With each value, a version is stored
 * which allows for conditional operations to be executed remotely in a efficient way. For more detailed info on
 * conditional operations, check <a href="http://community.jboss.org/docs/DOC-15604">this document</a>.
 *
 * @author Galder Zamarreño
 * @since 4.1
 */
// TODO: putting Ids.SERVER_CACHE_VALUE fails compilation in 2.8 - https://lampsvn.epfl.ch/trac/scala/ticket/2764
@Marshallable(externalizer = classOf[CacheValue.Externalizer], id = 55)
class CacheValue(val data: Array[Byte], val version: Long) {

   override def toString = {
      new StringBuilder().append("CacheValue").append("{")
         .append("data=").append(Util.printArray(data, false))
         .append(", version=").append(version)
         .append("}").toString
   }

   override def equals(obj: Any) = {
      obj match {
         // Apparenlty this is the way arrays should be compared for equality of contents, see:
         // http://old.nabble.com/-scala--Array-equality-td23149094.html
         case k: CacheValue => Arrays.equals(k.data, this.data) && k.version == this.version
         case _ => false
      }
   }

   override def hashCode: Int = {
      41 + Arrays.hashCode(data)
   }
   
}

object CacheValue {
   class Externalizer extends org.infinispan.marshall.Externalizer {
      override def writeObject(output: ObjectOutput, obj: AnyRef) {
         val cacheValue = obj.asInstanceOf[CacheValue]
         output.writeInt(cacheValue.data.length)
         output.write(cacheValue.data)
         output.writeLong(cacheValue.version)
      }

      override def readObject(input: ObjectInput): AnyRef = {
         val data = new Array[Byte](input.readInt())
         input.readFully(data) // Must be readFully, otherwise partial arrays can be read under load!
         val version = input.readLong
         new CacheValue(data, version)
      }
   }
}
