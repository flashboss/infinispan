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
package org.infinispan.config.parsing;

import java.beans.PropertyEditor;
import java.beans.PropertyEditorManager;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.infinispan.config.AbstractConfigurationBean;
import org.infinispan.config.Configuration;
import org.infinispan.config.ConfigurationAttribute;
import org.infinispan.config.ConfigurationElement;
import org.infinispan.config.ConfigurationElements;
import org.infinispan.config.ConfigurationException;
import org.infinispan.config.ConfigurationProperties;
import org.infinispan.config.ConfigurationProperty;
import org.infinispan.config.DuplicateCacheNameException;
import org.infinispan.config.GlobalConfiguration;
import org.infinispan.util.ClassFinder;
import org.infinispan.util.FileLookup;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * XML configuration parser that uses reflection API and annotations to read Infinispan configuration files.
 *
 * @author Vladimir Blagojevic
 * @since 4.0
 */
public class AutomatedXmlConfigurationParserImpl extends XmlParserBase implements XmlConfigurationParser {   
   private static  List<Class<?>> CONFIG_BEANS =null;
   
   static {
      String path = System.getProperty("java.class.path") + File.pathSeparator
               + System.getProperty("surefire.test.class.path");
      try {         
         CONFIG_BEANS = ClassFinder.isAssignableFrom(ClassFinder.infinispanClasses(),AbstractConfigurationBean.class);
      } catch (Exception e) {
         throw new ConfigurationException(
                  "Exception while searching for Infinispan configuration beans, path is " + path,
                  e);
      }
      if (CONFIG_BEANS == null || CONFIG_BEANS.isEmpty())
         throw new ConfigurationException("Could not find Infinispan configuration beans, path is "
                  + path);
   }

   // this parser will need to be initialized.
   boolean initialized = false;

   // the root element, representing the <infinispan /> tag
   Element rootElement;

   GlobalConfiguration gc;
   Map<String, Configuration> namedCaches;

   /**
    * Constructs a new parser
    */
   public AutomatedXmlConfigurationParserImpl() {}

   /**
    * Constructs a parser and initializes it with the file name passed in, by calling {@link #initialize(String)}.
    *
    * @param fileName file name to initialize the parser with
    * @throws IOException if there is a problem reading or locating the file.
    */
   public AutomatedXmlConfigurationParserImpl(String fileName) throws IOException {
      initialize(fileName);
   }

   /**
    * Constructs a parser and initializes it with the input stream passed in, by calling {@link
    * #initialize(InputStream)}.
    *
    * @param inputStream input stream to initialize the parser with
    * @throws IOException if there is a problem reading the stream
    */
   public AutomatedXmlConfigurationParserImpl(InputStream inputStream) throws IOException {
      initialize(inputStream);
   }

   public void initialize(String fileName) throws IOException {
      if (fileName == null) throw new NullPointerException("File name cannot be null!");
      FileLookup fileLookup = new FileLookup();
      InputStream is = fileLookup.lookupFile(fileName);
      if (is == null)
         throw new FileNotFoundException("File " + fileName + " could not be found, either on the classpath or on the file system!");
      initialize(is);
   }

   public void initialize(InputStream inputStream) throws IOException {
      if (inputStream == null) throw new NullPointerException("Input stream cannot be null!");
      initialized = true;
      rootElement = new RootElementBuilder().readRoot(inputStream);
   }

   public Configuration parseDefaultConfiguration() throws ConfigurationException {
      assertInitialized();
      if (gc == null) {
         Element defaultElement = getSingleElementInCoreNS("default", rootElement);
         // there may not be a <default /> element!
         if (defaultElement == null) {
            return new Configuration();
         } else {
            defaultElement.normalize();            
            AbstractConfigurationBean bean = findAndInstantiateBean(defaultElement);
            visitElement(defaultElement, bean);
            return (Configuration) bean;
         }
      } else {
         return gc.getDefaultConfiguration();
      }
   }

   public Map<String, Configuration> parseNamedConfigurations() throws ConfigurationException {
      assertInitialized();
      // there may not be any namedCache elements!
      if (namedCaches == null) {
         Set<Element> elements = getAllElementsInCoreNS("namedCache", rootElement);
         if (elements.isEmpty()) return Collections.emptyMap();
         namedCaches = new HashMap<String, Configuration>(elements.size(), 1.0f);
         for (Element e : elements) {
            String configurationName = getAttributeValue(e, "name");
            if (namedCaches.containsKey(configurationName)) {
               namedCaches = null;
               throw new DuplicateCacheNameException("Named cache " + configurationName + " is declared more than once!");
            }
            try {
               AbstractConfigurationBean bean = findAndInstantiateBean(e);
               visitElement(e, bean);               
               namedCaches.put(configurationName,(Configuration) bean);
            } catch (ConfigurationException ce) {
               throw new ConfigurationException("Problems configuring named cache '" + configurationName + "'", ce);
            }
         }
      }

      return namedCaches;
   }

   public GlobalConfiguration parseGlobalConfiguration() {
      assertInitialized();
      if (gc == null) {
         Configuration defaultConfiguration = parseDefaultConfiguration();
         Element globalElement = getSingleElementInCoreNS("global", rootElement);         
         AbstractConfigurationBean bean = findAndInstantiateBean(globalElement);
         visitElement(globalElement, bean);
         gc = (GlobalConfiguration) bean;
         gc.setDefaultConfiguration(defaultConfiguration);
      }
      return gc;
   }
   
   public AbstractConfigurationBean findAndInstantiateBean(List<Class<?>> b, Element e) throws ConfigurationException {
      String name = e.getTagName();
      for (Class<?> clazz : b) {
         ConfigurationElements elements = clazz.getAnnotation(ConfigurationElements.class);
         if (elements != null) {
            for (ConfigurationElement ce : elements.elements()) {
               if (ce.name().equals(name)) {
                  try {
                     return (AbstractConfigurationBean) clazz.newInstance();
                  } catch (Exception e1) {
                     throw new ConfigurationException("Could not instantiate class " + clazz, e1);
                  }
               }
            }
         } else {
            ConfigurationElement ce = clazz.getAnnotation(ConfigurationElement.class);
            if (ce != null && ce.name().equals(name)) {
               try {
                  return (AbstractConfigurationBean) clazz.newInstance();
               } catch (Exception e1) {
                  throw new ConfigurationException("Could not instantiate class " + clazz, e1);
               }
            }
         }
      }
      return null;
   }
   
   public AbstractConfigurationBean findAndInstantiateBean(Element e) throws ConfigurationException {
      return findAndInstantiateBean(CONFIG_BEANS,e);
   }
   
   private ConfigurationElement findConfigurationElement(Element e, Class<?> bean) {
      ConfigurationElement ces[] = null;
      ConfigurationElements configurationElements = bean.getAnnotation(ConfigurationElements.class);
      ConfigurationElement configurationElement = bean.getAnnotation(ConfigurationElement.class);

      if (configurationElement != null) {
         ces = new ConfigurationElement[] { configurationElement };
      }
      if (configurationElements != null) {
         ces = configurationElements.elements();
      }
      if (ces != null) {
         for (ConfigurationElement el : ces) {
            if (el.name().equals(e.getNodeName())) {
               return el;
            }
         }
      }
      return null;
   }
   
   private ConfigurationElement customReader(Element e, Class<?> bean) {     
      ConfigurationElement result = findConfigurationElement(e, bean);
      if (result == null) {
         for (Class<?> beanClass : CONFIG_BEANS) {
            result = findConfigurationElement(e, beanClass);
            if (result != null)
               break;
         }
      }
      if(result != null){
         if(!result.customReader().equals(ConfigurationElementReader.class)){
            return result;
         }
      }
      return null;
   }
   
   public void visitElement(Element e, AbstractConfigurationBean bean) throws ConfigurationException {     
      ConfigurationElement ce = customReader(e, bean.getClass());      
      //has custom reader? if so, use it
      if (ce != null) {
         Class<? extends ConfigurationElementReader> readerClass = ce.customReader();
         ConfigurationElementReader reader = null;
         try {            
            reader = readerClass.newInstance();
            reader.setParser(this);
            reader.process(e, bean);
         } catch (Exception e1) {
            throw new ConfigurationException("Exception while using custom reader " + readerClass
                     + " for element " + e.getNodeName(), e1);
         }
      } else {
         //normal processing
         visitElementWithNoCustomReader(e, bean);
      }
   }

   public void visitElementWithNoCustomReader(Element e, AbstractConfigurationBean bean) {
      for (Method m : bean.getClass().getMethods()) {
         boolean setter = m.getName().startsWith("set") && m.getParameterTypes().length == 1;
         if (setter) {
            reflectAndInvokeAttribute(bean, m, e);
            reflectAndInvokeProperties(bean, m, e);              
         }
      }
      NodeList nodeList = e.getChildNodes();
      for (int numChildren = nodeList.getLength(), i = 0; i < numChildren; i++) {
         Node child = nodeList.item(i);
         if (child instanceof Element) {               
            // recursive step
            visitElement((Element) child, bean);
         }
      }
   }
   
   private void reflectAndInvokeAttribute(AbstractConfigurationBean bean, Method m, Element node) {     
      Class<?> parameterType = m.getParameterTypes()[0];
      // is there a ConfigurationAttribute matching the current node iterated?
      ConfigurationAttribute a = m.getAnnotation(ConfigurationAttribute.class);
      boolean matchedAttributeToSetter = a != null && a.containingElement().equals(node.getNodeName());
      boolean isConfigBean = AbstractConfigurationBean.class.isAssignableFrom(parameterType);
      if (matchedAttributeToSetter) {
         String attValue = getAttributeValue(node, a.name());
         Object methodAttributeValue = null;
         if (attValue != null && attValue.length() > 0) {
            PropertyEditor editor = PropertyEditorManager.findEditor(parameterType);
            if (editor == null) {
               throw new ConfigurationException("Could not find property editor, type="
                        + parameterType + ",method=" + m + ",attribute=" + a.name());
            }
            editor.setAsText(attValue);
            methodAttributeValue = editor.getValue();
         } else if (a.defaultValue().length() > 0) {
            methodAttributeValue = a.defaultValue();
         }
         if (methodAttributeValue != null) {
            try {
               m.invoke(bean, methodAttributeValue);
            } catch (Exception ae) {
               throw new ConfigurationException("Illegal attribute value " + attValue + ",type="
                        + parameterType + ",method=" + m + ",attribute=" + a.name(), ae);
            }
         }
      } else if (isConfigBean) {
         AbstractConfigurationBean childBean = findAndInstantiateBean(node);
         boolean foundMatchingChild = childBean != null
                  && !bean.getClass().equals(childBean.getClass())
                  && parameterType.isInstance(childBean);
         if (foundMatchingChild) {  
            //recurse into child
            visitElement(node,childBean);
            try {
               //and finally invoke setter on father bean
               m.invoke(bean, childBean);
            } catch (Exception ae) {
               throw new ConfigurationException("Illegal bean value " + childBean + ",type="
                        + parameterType + ", method=" + m, ae);
            }
         }
      }
   }
   
   private boolean reflectAndInvokeProperties(AbstractConfigurationBean bean, Method m, Element node){
      Class<?> parameterType = m.getParameterTypes()[0];
      
      //how about ConfigurationProperties or ConfigurationProperty matching the current node iterated?
      ConfigurationProperty[] cprops = null;
      ConfigurationProperties cp = m.getAnnotation(ConfigurationProperties.class);               
      if (cp != null) {
         cprops = cp.elements();
      } else {
         ConfigurationProperty p = null;
         p = m.getAnnotation(ConfigurationProperty.class);
         if (p != null) {
            cprops = new ConfigurationProperty[] { p };
         }
      }
      boolean matchedPropertyToSetter  = cprops != null && cprops.length >0;
      if(matchedPropertyToSetter){
         String parentElement = cprops[0].parentElement();         
         if(parentElement.equals(node.getParentNode().getNodeName())){           
            if(node.getNodeName().equals("property")){
               Properties props = XmlConfigHelper.extractProperties((Element) node.getParentNode());
               //special case where setter argument is Properties object
               if (Properties.class.isAssignableFrom(parameterType)) {
                  try {
                     m.invoke(bean, props);
                  } catch (Exception ae) {
                     throw new ConfigurationException("Illegal props " + props + ",type="
                              + parameterType + ", method=" + m, ae);
                  }
               } else {
                  //regular case where setter argument are primitives and String
                  //follow bean conventions
                  XmlConfigHelper.setValues(bean, props, false, true);
               }
               //we assume that all other siblings of <property> element are also <property> elements 
               // there no need to iterate them as we have extracted them all using the  method above
               // therefore skip them by returning
               return true;
            }
         }         
      }
      return false;
   }
   
   private void assertInitialized() {
      if (!initialized)
         throw new ConfigurationException("Parser not initialized.  Please invoke initialize() first, or use a constructor that initializes the parser.");
   }
}