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
 */
package org.apache.geode.redis.internal.executor.string;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.geode.redis.internal.data.ByteArrayWrapper;
import org.apache.geode.redis.internal.executor.RedisCompatibilityResponse;
import org.apache.geode.redis.internal.netty.Command;
import org.apache.geode.redis.internal.netty.ExecutionHandlerContext;

public class MGetExecutor extends StringExecutor {

  @Override
  public RedisCompatibilityResponse executeCommand(Command command,
      ExecutionHandlerContext context) {
    List<byte[]> commandElems = command.getProcessedCommand();
    RedisCompatibilityStringCommands stringCommands = getRedisStringCommands(context);

    Collection<ByteArrayWrapper> values = new ArrayList<>();
    for (int i = 1; i < commandElems.size(); i++) {
      byte[] keyArray = commandElems.get(i);
      ByteArrayWrapper key = new ByteArrayWrapper(keyArray);
      values.add(stringCommands.mget(key));
    }

    return respondBulkStrings(values);
  }

}
