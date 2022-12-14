/*
 * Copyright 2005-2014 Red Hat, Inc.
 * Red Hat licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */
package org.hornetq.rest.util;

import org.hornetq.api.core.SimpleString;
import org.hornetq.api.core.client.ClientMessage;
import org.hornetq.jms.client.ConnectionFactoryOptions;
import org.hornetq.rest.HornetQRestLogger;
import org.hornetq.rest.HttpHeaderProperty;
import org.hornetq.utils.ObjectInputStreamWithClassLoader;
import org.jboss.resteasy.client.ClientRequest;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MultivaluedMap;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map.Entry;

/**
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @version $Revision: 1 $
 */
public class HttpMessageHelper
{
   public static final String POSTED_AS_HTTP_MESSAGE = "postedAsHttpMessage";

   public static boolean isTransferableHttpHeader(String key)
   {
      String lowerKey = key.toLowerCase();
      return lowerKey.toLowerCase().startsWith("content") || lowerKey.toLowerCase().equals("link");
   }

   public static void buildMessage(ClientMessage message, ClientRequest request, String contentType, ConnectionFactoryOptions jmsOptions)
   {
      for (SimpleString key : message.getPropertyNames())
      {
         String k = key.toString();
         String headerName = HttpHeaderProperty.fromPropertyName(k);
         if (headerName == null || headerName.contains("content-length"))
         {
            continue;
         }
         String value = message.getStringProperty(k);

         request.header(headerName, value);
         HornetQRestLogger.LOGGER.debug("Examining " + headerName + ": " + value);
         // override default content type if it is set as a message property
         if (headerName.equalsIgnoreCase("content-type"))
         {
            contentType = value;
            HornetQRestLogger.LOGGER.debug("Using contentType: " + contentType);
         }
      }
      int size = message.getBodySize();
      if (size > 0)
      {
         Boolean aBoolean = message.getBooleanProperty(POSTED_AS_HTTP_MESSAGE);
         if (aBoolean != null && aBoolean.booleanValue())
         {
            byte[] body = new byte[size];
            message.getBodyBuffer().readBytes(body);
            HornetQRestLogger.LOGGER.debug("Building Message from HTTP message");
            request.body(contentType, body);
         }
         else
         {
            // assume posted as a JMS or HornetQ object message
            size = message.getBodyBuffer().readInt();
            byte[] body = new byte[size];
            message.getBodyBuffer().readBytes(body);
            ByteArrayInputStream bais = new ByteArrayInputStream(body);
            Object obj = null;
            try
            {
               ObjectInputStreamWithClassLoader ois = new ObjectInputStreamWithClassLoader(bais);
               if (jmsOptions != null)
               {
                  ois.setBlackList(jmsOptions.getDeserializationBlackList());
                  ois.setWhiteList(jmsOptions.getDeserializationWhiteList());
               }
               obj = ois.readObject();
               HornetQRestLogger.LOGGER.debug("**** Building Message from object: " + obj.toString());
               request.body(contentType, obj);
            }
            catch (Exception e)
            {
               e.printStackTrace();
               throw new RuntimeException(e);
            }
         }
      }
   }

   public static void writeHttpMessage(HttpHeaders headers, byte[] body, ClientMessage message) throws Exception
   {

      MultivaluedMap<String, String> hdrs = headers.getRequestHeaders();
      for (Entry<String, List<String>> entry : hdrs.entrySet())
      {
         String key = entry.getKey();
         if (isTransferableHttpHeader(key))
         {
            List<String> vals = entry.getValue();
            String value = concatenateHeaderValue(vals);
            message.putStringProperty(HttpHeaderProperty.toPropertyName(key), value);
         }
      }
      message.putBooleanProperty(POSTED_AS_HTTP_MESSAGE, true);
      message.getBodyBuffer().writeBytes(body);
   }

   public static String concatenateHeaderValue(List<String> vals)
   {
      if (vals == null)
      {
         return "";
      }
      StringBuilder val = new StringBuilder();
      for (String v : vals)
      {
         if (val.length() > 0)
         {
            val.append(",");
         }
         val.append(v);
      }
      return val.toString();
   }
}
