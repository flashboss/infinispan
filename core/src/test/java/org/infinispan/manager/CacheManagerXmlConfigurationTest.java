package org.infinispan.manager;

import org.infinispan.Cache;
import org.infinispan.test.AbstractInfinispanTest;
import org.infinispan.test.TestingUtil;
import org.infinispan.config.Configuration;
import org.infinispan.remoting.rpc.RpcManager;
import org.infinispan.test.fwk.TestCacheManagerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import javax.transaction.TransactionManager;
import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.infinispan.test.TestingUtil.INFINISPAN_END_TAG;
import static org.infinispan.test.TestingUtil.INFINISPAN_START_TAG;
import static org.testng.Assert.assertEquals;

/**
 * @author Manik Surtani
 * @since 4.0
 */
@Test(groups = "functional", testName = "manager.CacheManagerXmlConfigurationTest")
public class CacheManagerXmlConfigurationTest extends AbstractInfinispanTest {
   EmbeddedCacheManager cm;

   @AfterMethod
   public void tearDown() {
      if (cm != null) cm.stop();
      cm =null;
   }

   public void testNamedCacheXML() throws IOException {
      cm = TestCacheManagerFactory.fromXml("configs/named-cache-test.xml");

      assertEquals("s1", cm.getGlobalConfiguration().getSiteId());
      assertEquals("r1", cm.getGlobalConfiguration().getRackId());
      assertEquals("m1", cm.getGlobalConfiguration().getMachineId());

      // test default cache
      Cache c = cm.getCache();
      assert c.getConfiguration().getConcurrencyLevel() == 100;
      assert c.getConfiguration().getLockAcquisitionTimeout() == 1000;
      assert TestingUtil.extractComponent(c, TransactionManager.class) == null;
      assert TestingUtil.extractComponent(c, RpcManager.class) != null : "This should not be null, since a shared RPC manager should be present";

      // test the "transactional" cache
      c = cm.getCache("transactional");
      assert c.getConfiguration().getConcurrencyLevel() == 100;
      assert c.getConfiguration().getLockAcquisitionTimeout() == 1000;
      assert TestingUtil.extractComponent(c, TransactionManager.class) != null;
      assert TestingUtil.extractComponent(c, RpcManager.class) != null : "This should not be null, since a shared RPC manager should be present";

      // test the "replicated" cache
      c = cm.getCache("syncRepl");
      assert c.getConfiguration().getConcurrencyLevel() == 100;
      assert c.getConfiguration().getLockAcquisitionTimeout() == 1000;
      assert TestingUtil.extractComponent(c, TransactionManager.class) == null;
      assert TestingUtil.extractComponent(c, RpcManager.class) != null : "This should not be null, since a shared RPC manager should be present";

      // test the "txSyncRepl" cache
      c = cm.getCache("txSyncRepl");
      assert c.getConfiguration().getConcurrencyLevel() == 100;
      assert c.getConfiguration().getLockAcquisitionTimeout() == 1000;
      assert TestingUtil.extractComponent(c, TransactionManager.class) != null;
      assert TestingUtil.extractComponent(c, RpcManager.class) != null : "This should not be null, since a shared RPC manager should be present";
   }

   public void testNamedCacheXMLClashingNames() throws IOException {
      String xml = INFINISPAN_START_TAG +
            "\n" +
            "    <default>\n" +
            "        <locking concurrencyLevel=\"100\" lockAcquisitionTimeout=\"1000\" />\n" +
            "    </default>\n" +
            "\n" +
            "    <namedCache name=\"c1\">\n" +
            "        <transaction transactionManagerLookupClass=\"org.infinispan.transaction.GenericTransactionManagerLookup\"/>\n" +
            "    </namedCache>\n" +
            "\n" +
            "    <namedCache name=\"c1\">\n" +
            "        <clustering>\n" +
            "            <sync replTimeout=\"15000\"/>\n" +
            "        </clustering>\n" +
            "    </namedCache>\n" +
            INFINISPAN_END_TAG;

      ByteArrayInputStream bais = new ByteArrayInputStream(xml.getBytes());
      try {
         cm = TestCacheManagerFactory.fromStream(bais);
         assert false : "Should fail";
      } catch (Throwable expected) {

         System.out.println("Blah");

      }
   }

   public void testNamedCacheXMLClashingNamesProgrammatic() throws IOException {
      String xml = INFINISPAN_START_TAG +
            "\n" +
            "<global/>\n" +
            "    <default>\n" +
            "        <locking concurrencyLevel=\"100\" lockAcquisitionTimeout=\"1000\" />\n" +
            "    </default>\n" +
            "\n" +
            "    <namedCache name=\"c1\">\n" +
            "        <transaction transactionManagerLookupClass=\"org.infinispan.transaction.lookup.GenericTransactionManagerLookup\"/>\n" +
            "    </namedCache>\n" + INFINISPAN_END_TAG;

      ByteArrayInputStream bais = new ByteArrayInputStream(xml.getBytes());
      cm = TestCacheManagerFactory.fromStream(bais);

      assert cm.getCache() != null;
      assert cm.getCache("c1") != null;
      Configuration c1Config = cm.getCache("c1").getConfiguration();
      assert c1Config != null;
      Configuration redefinedConfig = cm.defineConfiguration("c1", new Configuration());
      assert c1Config.equals(redefinedConfig);
   }
}


