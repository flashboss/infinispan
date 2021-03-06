package org.infinispan.replication;

import org.infinispan.Cache;
import org.infinispan.commands.ReplicableCommand;
import org.infinispan.config.Configuration;
import org.infinispan.config.GlobalConfiguration;
import org.infinispan.manager.CacheContainer;
import org.infinispan.remoting.ReplicationQueueImpl;
import org.infinispan.test.MultipleCacheManagersTest;
import org.infinispan.test.TestingUtil;
import org.infinispan.test.fwk.TestCacheManagerFactory;
import org.testng.annotations.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Verifies that concurrent flushes are handled properly. These can occur when both flushes due to queue max size
 * being exceeded and interval based queue flushes occur at exactly the same time. The test verifies that order of
 * operations is guaranteed under these circumstances.
 *
 * @author Galder Zamarreño
 * @since 4.2
 */
@Test(groups = "functional", testName = "replication.ConcurrentFlushReplQueueTest")
public class ConcurrentFlushReplQueueTest extends MultipleCacheManagersTest {

   @Override
   protected void createCacheManagers() throws Throwable {
      Configuration cfg = new Configuration();
      cfg.setCacheMode(Configuration.CacheMode.REPL_ASYNC);
      cfg.setUseReplQueue(true);
      cfg.setReplQueueInterval(100);
      cfg.setReplQueueMaxElements(2);
      cfg.setReplQueueClass(MockReplQueue.class.getName());
      CacheContainer first = TestCacheManagerFactory.createCacheManager(GlobalConfiguration.getClusteredDefault(), cfg);
      CacheContainer second = TestCacheManagerFactory.createCacheManager(GlobalConfiguration.getClusteredDefault(), cfg);
      registerCacheManager(first, second);
   }

   public void testConcurrentFlush(Method m) throws Exception {
      Cache cache1 = cache(0);
      Cache cache2 = cache(1);
      CountDownLatch intervalFlushLatch = new CountDownLatch(1);
      CountDownLatch secondPutLatch = new CountDownLatch(1);
      CountDownLatch removeCompletedLatch = new CountDownLatch(1);
      MockReplQueue.intervalFlushLatch = intervalFlushLatch;
      MockReplQueue.secondPutLatch = secondPutLatch;
      MockReplQueue.removeCompletedLatch = removeCompletedLatch; 
      final String k = "k-" + m.getName();
      final String v = "v-" + m.getName();
      cache1.put(k, v);
      // Wait for periodic repl queue task to try repl the single modification
      secondPutLatch.await();
      // Put something random so that after remove call, the element number exceeds
      cache1.put("k-blah","v-blah");
      cache1.remove(k);
      // Wait for remove to go over draining the queue
      removeCompletedLatch.await();
      // Once remove executed, now let the interval flush continue
      intervalFlushLatch.countDown();
      // Wait for periodic flush to send modifications over the wire
      TestingUtil.sleepThread(500);
      assert !cache2.containsKey(k);
   }

   public static class MockReplQueue extends ReplicationQueueImpl {
      static CountDownLatch intervalFlushLatch;
      static CountDownLatch secondPutLatch;
      static CountDownLatch removeCompletedLatch;

      @Override
      protected List<ReplicableCommand> drainReplQueue() {
         List<ReplicableCommand> drained = super.drainReplQueue();
         try {
            if (drained.size() > 0 && Thread.currentThread().getName().startsWith("Scheduled-")) {
               secondPutLatch.countDown();
                // Wait a max of 5 seconds, because if a remove could have gone through,
               // it would have done it in that time. If it hasn't and the test passes,
               // it means that correct synchronization is in place.
               intervalFlushLatch.await(5, TimeUnit.SECONDS);
            } else if (drained.size() > 0) {
               removeCompletedLatch.countDown();
            }
         } catch (InterruptedException e) {
            throw new RuntimeException(e);
         }
         return drained;
      }
   }
}
