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
package org.hornetq.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class StringUtil
{

   /**
    * Convert a list of Strings into a single String
    * @param strList the string list
    * @param delimit the delimiter used to separate each string entry in the list
    * @return the converted string
    */
   public static String joinStringList(List<String> strList, String delimit)
   {
      Iterator<String> entries = strList.iterator();
      StringBuilder builder = new StringBuilder();

      while (entries.hasNext())
      {
         builder.append(entries.next());
         if (entries.hasNext())
         {
            builder.append(delimit);
         }
      }
      return builder.toString();
   }

   /**
    * Convert a String into a list of String
    * @param strList the String
    * @param delimit used to separate items within the string.
    * @return the string list
    */
   public static List<String> splitStringList(String strList, String delimit)
   {
      ArrayList<String> list = new ArrayList<String>();
      if (strList != null && !strList.isEmpty())
      {
         list.addAll(Arrays.asList(strList.split(delimit)));
      }
      return list;
   }
}
