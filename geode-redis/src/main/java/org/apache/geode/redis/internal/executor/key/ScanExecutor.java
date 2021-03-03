/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 *
 */
package org.apache.geode.redis.internal.executor.key;

import static org.apache.geode.redis.internal.RedisCompatibilityConstants.ERROR_CURSOR;
import static org.apache.geode.redis.internal.RedisCompatibilityConstants.ERROR_NOT_INTEGER;
import static org.apache.geode.redis.internal.RedisCompatibilityConstants.ERROR_SYNTAX;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import org.apache.geode.logging.internal.log4j.api.LogService;
import org.apache.geode.redis.internal.data.ByteArrayWrapper;
import org.apache.geode.redis.internal.executor.RedisCompatibilityResponse;
import org.apache.geode.redis.internal.netty.Coder;
import org.apache.geode.redis.internal.netty.Command;
import org.apache.geode.redis.internal.netty.ExecutionHandlerContext;

public class ScanExecutor extends AbstractScanExecutor {

  @Override
  public RedisCompatibilityResponse executeCommand(Command command,
      ExecutionHandlerContext context) {
    List<byte[]> commandElems = command.getProcessedCommand();

    String cursorString = command.getStringKey();
    BigInteger cursor;
    Pattern matchPattern;
    String globPattern = null;
    int count = DEFAULT_COUNT;

    try {
      cursor = new BigInteger(cursorString).abs();
    } catch (NumberFormatException e) {
      return RedisCompatibilityResponse.error(ERROR_CURSOR);
    }

    if (cursor.compareTo(UNSIGNED_LONG_CAPACITY) > 0) {
      return RedisCompatibilityResponse.error(ERROR_CURSOR);
    }

    if (!cursor.equals(context.getScanCursor())) {
      cursor = new BigInteger("0");
    }

    for (int i = 2; i < commandElems.size(); i = i + 2) {
      byte[] commandElemBytes = commandElems.get(i);
      String keyword = Coder.bytesToString(commandElemBytes);
      if (keyword.equalsIgnoreCase("MATCH")) {
        commandElemBytes = commandElems.get(i + 1);
        globPattern = Coder.bytesToString(commandElemBytes);

      } else if (keyword.equalsIgnoreCase("COUNT")) {
        commandElemBytes = commandElems.get(i + 1);
        try {
          count = Coder.bytesToInt(commandElemBytes);
        } catch (NumberFormatException e) {
          return RedisCompatibilityResponse.error(ERROR_NOT_INTEGER);
        }

        if (count < 1) {
          return RedisCompatibilityResponse.error(ERROR_SYNTAX);
        }

      } else {
        return RedisCompatibilityResponse.error(ERROR_SYNTAX);
      }
    }

    try {
      matchPattern = convertGlobToRegex(globPattern);
    } catch (PatternSyntaxException e) {
      LogService.getLogger().warn(
          "Could not compile the pattern: '{}' due to the following exception: '{}'. SCAN will return an empty list.",
          globPattern, e.getMessage());
      return RedisCompatibilityResponse.emptyScan();
    }

    Pair<BigInteger, List<Object>> scanResult =
        scan(getDataRegion(context).keySet(), matchPattern, count, cursor);
    context.setScanCursor(scanResult.getLeft());

    return RedisCompatibilityResponse.scan(scanResult.getLeft(), scanResult.getRight());
  }

  private Pair<BigInteger, List<Object>> scan(Collection<ByteArrayWrapper> list,
      Pattern matchPattern,
      int count, BigInteger cursor) {
    List<Object> returnList = new ArrayList<>();
    int size = list.size();
    BigInteger beforeCursor = new BigInteger("0");
    int numElements = 0;
    int i = -1;
    for (ByteArrayWrapper key : list) {
      i++;
      if (beforeCursor.compareTo(cursor) < 0) {
        beforeCursor = beforeCursor.add(new BigInteger("1"));
        continue;
      }

      if (matchPattern != null) {
        if (matchPattern.matcher(key.toString()).matches()) {
          returnList.add(key);
          numElements++;
        }
      } else {
        returnList.add(key);
        numElements++;
      }
      if (numElements == count) {
        break;
      }
    }

    Pair<BigInteger, List<Object>> scanResult;
    if (i >= size - 1) {
      scanResult = new ImmutablePair<>(new BigInteger("0"), returnList);
    } else {
      scanResult = new ImmutablePair<>(new BigInteger(String.valueOf(i + 1)), returnList);
    }
    return scanResult;
  }
}
