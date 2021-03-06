package org.infinispan.container.entries;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import org.infinispan.io.UnsignedNumeric;
import org.infinispan.marshall.Ids;
import org.infinispan.marshall.Marshallable;

/**
 * A transient, mortal cache value to correspond with {@link org.infinispan.container.entries.TransientMortalCacheEntry}
 *
 * @author Manik Surtani
 * @since 4.0
 */
@Marshallable(externalizer = TransientMortalCacheValue.Externalizer.class, id = Ids.TRANSIENT_MORTAL_VALUE)
public class TransientMortalCacheValue extends MortalCacheValue {
   long maxIdle = -1;
   long lastUsed;

   TransientMortalCacheValue(Object value, long created, long lifespan, long maxIdle, long lastUsed) {
      this(value, created, lifespan, maxIdle);
      this.lastUsed = lastUsed;
   }

   public TransientMortalCacheValue(Object value, long created, long lifespan, long maxIdle) {
      super(value, created, lifespan);
      this.maxIdle = maxIdle;
   }

   public TransientMortalCacheValue(Object value, long created) {
      super(value, created, -1);
   }

   @Override
   public long getMaxIdle() {
      return maxIdle;
   }

   public void setMaxIdle(long maxIdle) {
      this.maxIdle = maxIdle;
   }

   @Override
   public long getLastUsed() {
      return lastUsed;
   }

   public void setLastUsed(long lastUsed) {
      this.lastUsed = lastUsed;
   }

   public boolean isExpired() {
      return ExpiryHelper.isExpiredTransientMortal(maxIdle, lastUsed, lifespan, created);
   }

   @Override
   public InternalCacheEntry toInternalCacheEntry(Object key) {
      return new TransientMortalCacheEntry(key, value, maxIdle, lifespan, lastUsed, created);
   }

   @Override
   public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof TransientMortalCacheValue)) return false;
      if (!super.equals(o)) return false;

      TransientMortalCacheValue that = (TransientMortalCacheValue) o;

      if (lastUsed != that.lastUsed) return false;
      if (maxIdle != that.maxIdle) return false;

      return true;
   }

   @Override
   public int hashCode() {
      int result = super.hashCode();
      result = 31 * result + (int) (maxIdle ^ (maxIdle >>> 32));
      result = 31 * result + (int) (lastUsed ^ (lastUsed >>> 32));
      return result;
   }

   @Override
   public String toString() {
      return "TransientMortalCacheValue{" +
            "maxIdle=" + maxIdle +
            ", lastUsed=" + lastUsed +
            "} " + super.toString();
   }

   @Override
   public TransientMortalCacheValue clone() {
      return (TransientMortalCacheValue) super.clone();
   }
   
   public static class Externalizer implements org.infinispan.marshall.Externalizer {
      public void writeObject(ObjectOutput output, Object subject) throws IOException {
         TransientMortalCacheValue icv = (TransientMortalCacheValue) subject;
         output.writeObject(icv.value);
         UnsignedNumeric.writeUnsignedLong(output, icv.created);
         output.writeLong(icv.lifespan); // could be negative so should not use unsigned longs
         UnsignedNumeric.writeUnsignedLong(output, icv.lastUsed);
         output.writeLong(icv.maxIdle); // could be negative so should not use unsigned longs
      }

      public Object readObject(ObjectInput input) throws IOException, ClassNotFoundException {
         Object v = input.readObject();
         long created = UnsignedNumeric.readUnsignedLong(input);
         Long lifespan = input.readLong();
         long lastUsed = UnsignedNumeric.readUnsignedLong(input);
         Long maxIdle = input.readLong();
         return new TransientMortalCacheValue(v, created, lifespan, maxIdle, lastUsed);
      }      
   }
}
