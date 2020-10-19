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

package org.apache.geode.redis.internal.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;

import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.stubbing.OngoingStubbing;

import org.apache.geode.DataSerializer;
import org.apache.geode.cache.Region;
import org.apache.geode.internal.HeapDataOutputStream;
import org.apache.geode.internal.InternalDataSerializer;
import org.apache.geode.internal.serialization.ByteArrayDataInput;
import org.apache.geode.internal.serialization.DataSerializableFixedID;

public class RedisStringTest {

  @BeforeClass
  public static void beforeClass() {
    InternalDataSerializer
        .getDSFIDSerializer()
        .registerDSFID(DataSerializableFixedID.REDIS_BYTE_ARRAY_WRAPPER, ByteArrayWrapper.class);
  }

  @Test
  public void constructorSetsValue() {
    ByteArrayWrapper byteArrayWrapper = new ByteArrayWrapper(new byte[] {0, 1, 2});
    RedisString string = new RedisString(byteArrayWrapper);
    ByteArrayWrapper returnedByteArrayWrapper = string.get();
    assertThat(returnedByteArrayWrapper).isNotNull();
    assertThat(returnedByteArrayWrapper.value).isEqualTo(byteArrayWrapper.value);
  }

  @Test
  public void setSetsValue() {
    RedisString string = new RedisString();
    string.set(new ByteArrayWrapper(new byte[] {0, 1, 2}));
    ByteArrayWrapper returnedByteArrayWrapper = string.get();
    assertThat(returnedByteArrayWrapper).isNotNull();
    assertThat(returnedByteArrayWrapper.value).isEqualTo(new ByteArrayWrapper(new byte[] {0, 1, 2}).value);
  }

  @Test
  public void getReturnsSetValue() {
    RedisString string = new RedisString(new ByteArrayWrapper(new byte[] {0, 1}));
    ByteArrayWrapper returnedByteArrayWrapper = string.get();
    assertThat(returnedByteArrayWrapper).isNotNull();
    assertThat(returnedByteArrayWrapper.value).isEqualTo(new ByteArrayWrapper(new byte[] {0, 1}).value);
  }

  @Test
  public void appendResizesByteArray() {
    // allows unchecked cast of mock to Region<ByteArrayWrapper, RedisData>
    @SuppressWarnings("unchecked")
    Region<ByteArrayWrapper, RedisData> region = mock(Region.class);
    RedisString redisString = new RedisString(new ByteArrayWrapper(new byte[] {0, 1}));
    ByteArrayWrapper part2 = new ByteArrayWrapper(new byte[] {2, 3, 4, 5});
    int redisStringSize = redisString.strlen();
    int part2Size = part2.length();
    int appendedStringSize = redisString.append(part2, region, null);
    assertThat(appendedStringSize).isEqualTo(redisStringSize + part2Size);
  }

  @Test
  public void appendStoresStableDelta() throws IOException {
    // allows unchecked cast of mock to Region<ByteArrayWrapper, RedisData>
    @SuppressWarnings("unchecked")
    Region<ByteArrayWrapper, RedisData> region = mock(Region.class);
    RedisString o1 = new RedisString(new ByteArrayWrapper(new byte[] {0, 1}));
    ByteArrayWrapper part2 = new ByteArrayWrapper(new byte[] {2, 3});
    o1.append(part2, region, null);
    assertThat(o1.hasDelta()).isTrue();
    assertThat(o1.get()).isEqualTo(new ByteArrayWrapper(new byte[] {0, 1, 2, 3}));
    HeapDataOutputStream out = new HeapDataOutputStream(100);
    o1.toDelta(out);
    assertThat(o1.hasDelta()).isFalse();
    ByteArrayDataInput in = new ByteArrayDataInput(out.toByteArray());
    RedisString o2 = new RedisString(new ByteArrayWrapper(new byte[] {0, 1}));
    assertThat(o2).isNotEqualTo(o1);
    o2.fromDelta(in);
    assertThat(o2.get()).isEqualTo(new ByteArrayWrapper(new byte[] {0, 1, 2, 3}));
    assertThat(o2).isEqualTo(o1);
  }

  @Test
  public void serializationIsStable() throws IOException, ClassNotFoundException {
    RedisString o1 = new RedisString(new ByteArrayWrapper(new byte[] {0, 1, 2, 3}));
    o1.setExpirationTimestampNoDelta(1000);
    HeapDataOutputStream outputStream = new HeapDataOutputStream(100);
    DataSerializer.writeObject(o1, outputStream);
    ByteArrayDataInput dataInput = new ByteArrayDataInput(outputStream.toByteArray());
    RedisString o2 = DataSerializer.readObject(dataInput);
    assertThat(o2).isEqualTo(o1);
  }

  @Test
  public void incrThrowsArithmeticErrorWhenNotALong() {
    // allows unchecked cast of mock to Region<ByteArrayWrapper, RedisData>
    @SuppressWarnings("unchecked")
    Region<ByteArrayWrapper, RedisData> region = mock(Region.class);
    ByteArrayWrapper byteArrayWrapper = new ByteArrayWrapper(new byte[] {'1', '0', ' ', '1'});
    RedisString string = new RedisString(byteArrayWrapper);
    assertThatThrownBy(() -> string.incr(region, byteArrayWrapper)).isInstanceOf(NumberFormatException.class);
  }

  @Test
  public void incrErrorsWhenValueOverflows() {
    // allows unchecked cast of mock to Region<ByteArrayWrapper, RedisData>
    @SuppressWarnings("unchecked")
    Region<ByteArrayWrapper, RedisData> region = mock(Region.class);
    ByteArrayWrapper byteArrayWrapper = new ByteArrayWrapper(
        // max value for signed long
        new byte[] {'9','2','2','3','3','7','2','0','3','6','8','5','4','7','7','5','8','0','7'}
        );
    RedisString string = new RedisString(byteArrayWrapper);
    assertThatThrownBy(() -> string.incr(region, byteArrayWrapper)).isInstanceOf(ArithmeticException.class);
  }

  @Test
  public void incrIncrementsValueAtGivenKey() {
    // allows unchecked cast of mock to Region<ByteArrayWrapper, RedisData>
    @SuppressWarnings("unchecked")
    Region<ByteArrayWrapper, RedisData> region = mock(Region.class);
    ByteArrayWrapper byteArrayWrapper = new ByteArrayWrapper(new byte[]{'1', '0'});
    RedisString string = new RedisString(byteArrayWrapper);
    string.incr(region, byteArrayWrapper);
    assertThat(string.get().toString()).isEqualTo("11");
  }

  @Test
  public void incrbyThrowsNumberFormatExceptionWhenNotALong() {
    // allows unchecked cast of mock to Region<ByteArrayWrapper, RedisData>
    @SuppressWarnings("unchecked")
    Region<ByteArrayWrapper, RedisData> region = mock(Region.class);
    ByteArrayWrapper byteArrayWrapper = new ByteArrayWrapper(new byte[] {'1', '0', ' ', '1'});
    RedisString string = new RedisString(byteArrayWrapper);
    assertThatThrownBy(() -> string.incrby(region, byteArrayWrapper, 2L)).isInstanceOf(NumberFormatException.class);
  }

  @Test
  public void incrbyErrorsWhenValueOverflows() {
    // allows unchecked cast of mock to Region<ByteArrayWrapper, RedisData>
    @SuppressWarnings("unchecked")
    Region<ByteArrayWrapper, RedisData> region = mock(Region.class);
    ByteArrayWrapper byteArrayWrapper = new ByteArrayWrapper(
        // max value for signed long
        new byte[] {'9','2','2','3','3','7','2','0','3','6','8','5','4','7','7','5','8','0','7'}
    );
    RedisString string = new RedisString(byteArrayWrapper);
    assertThatThrownBy(() -> string.incrby(region, byteArrayWrapper, 2L)).isInstanceOf(ArithmeticException.class);
  }

  @Test
  public void incrbyIncrementsValueByGivenLong() {
    // allows unchecked cast of mock to Region<ByteArrayWrapper, RedisData>
    @SuppressWarnings("unchecked")
    Region<ByteArrayWrapper, RedisData> region = mock(Region.class);
    ByteArrayWrapper byteArrayWrapper = new ByteArrayWrapper(new byte[]{'1', '0'});
    RedisString string = new RedisString(byteArrayWrapper);
    string.incrby(region, byteArrayWrapper, 2L);
    assertThat(string.get().toString()).isEqualTo("12");
  }

  @Test
  public void incrbyfloatThrowsArithmeticErrorWhenNotADouble() {
    // allows unchecked cast of mock to Region<ByteArrayWrapper, RedisData>
    @SuppressWarnings("unchecked")
    Region<ByteArrayWrapper, RedisData> region = mock(Region.class);
    ByteArrayWrapper byteArrayWrapper = new ByteArrayWrapper(new byte[] {'1', '0', ' ', '1'});
    RedisString string = new RedisString(byteArrayWrapper);
    assertThatThrownBy(() -> string.incrbyfloat(region, byteArrayWrapper, 1.1)).isInstanceOf(NumberFormatException.class);
  }

  @Test
  public void incrbyfloatErrorsWhenValueOverflows() {
    // allows unchecked cast of mock to Region<ByteArrayWrapper, RedisData>
    @SuppressWarnings("unchecked")
    Region<ByteArrayWrapper, RedisData> region = mock(Region.class);
    ByteArrayWrapper byteArrayWrapper = new ByteArrayWrapper(
        // max value for signed double
        new byte[] {'1','.','7','9','7','6','9','3','1','3','4','8','6','2','3','1','5','7','e','+','3','0','8'}
    );
    RedisString string = new RedisString(byteArrayWrapper);
    assertThatThrownBy(() -> string.incrbyfloat(region, byteArrayWrapper, 1.2)).isInstanceOf(ArithmeticException.class);
  }

  @Test
  public void incrbyfloatIncrementsValueByGivenFloat() {
    // allows unchecked cast of mock to Region<ByteArrayWrapper, RedisData>
    @SuppressWarnings("unchecked")
    Region<ByteArrayWrapper, RedisData> region = mock(Region.class);
    ByteArrayWrapper byteArrayWrapper = new ByteArrayWrapper(new byte[]{'1', '0'});
    RedisString string = new RedisString(byteArrayWrapper);
    string.incrbyfloat(region, byteArrayWrapper, 2.20);
    assertThat(string.get().toString()).isEqualTo("12.2");
  }

  @Test
  public void decrErrorsWhenOverflows() {
    // allows unchecked cast of mock to Region<ByteArrayWrapper, RedisData>
    @SuppressWarnings("unchecked")
    Region<ByteArrayWrapper, RedisData> region = mock(Region.class);
    ByteArrayWrapper byteArrayWrapper = new ByteArrayWrapper(new byte[]{0});
    RedisString string = new RedisString(byteArrayWrapper);
    assertThatThrownBy(() -> string.decr(region, byteArrayWrapper)).isInstanceOf(NumberFormatException.class);
  }

  @Test
  public void decrDecrementsValue() {
    // allows unchecked cast of mock to Region<ByteArrayWrapper, RedisData>
    @SuppressWarnings("unchecked")
    Region<ByteArrayWrapper, RedisData> region = mock(Region.class);
    ByteArrayWrapper byteArrayWrapper = new ByteArrayWrapper(new byte[]{'1', '0'});
    RedisString string = new RedisString(byteArrayWrapper);
    string.decr(region, byteArrayWrapper);
    assertThat(string.get().toString()).isEqualTo("9");
  }

  @Test
  public void decrbyErrorsWhenOverflows() {
    // allows unchecked cast of mock to Region<ByteArrayWrapper, RedisData>
    @SuppressWarnings("unchecked")
    Region<ByteArrayWrapper, RedisData> region = mock(Region.class);
    ByteArrayWrapper byteArrayWrapper = new ByteArrayWrapper(new byte[]{1});
    RedisString string = new RedisString(byteArrayWrapper);
    assertThatThrownBy(() -> string.decrby(region, byteArrayWrapper, 2)).isInstanceOf(NumberFormatException.class);
  }

  @Test
  public void decrbyDecrementsValue() {
    // allows unchecked cast of mock to Region<ByteArrayWrapper, RedisData>
    @SuppressWarnings("unchecked")
    Region<ByteArrayWrapper, RedisData> region = mock(Region.class);
    ByteArrayWrapper byteArrayWrapper = new ByteArrayWrapper(new byte[]{'1', '0'});
    RedisString string = new RedisString(byteArrayWrapper);
    string.decrby(region, byteArrayWrapper, 2);
    assertThat(string.get().toString()).isEqualTo("8");
  }

  @Test
  public void equals_returnsFalse_givenDifferentExpirationTimes() {
    RedisString o1 = new RedisString(new ByteArrayWrapper(new byte[] {0, 1, 2, 3}));
    o1.setExpirationTimestampNoDelta(1000);
    RedisString o2 = new RedisString(new ByteArrayWrapper(new byte[] {0, 1, 2, 3}));
    o2.setExpirationTimestampNoDelta(999);
    assertThat(o1).isNotEqualTo(o2);
  }

  @Test
  public void equals_returnsFalse_givenDifferentValueBytes() {
    RedisString o1 = new RedisString(new ByteArrayWrapper(new byte[] {0, 1, 2, 3}));
    o1.setExpirationTimestampNoDelta(1000);
    RedisString o2 = new RedisString(new ByteArrayWrapper(new byte[] {0, 1, 2, 2}));
    o2.setExpirationTimestampNoDelta(1000);
    assertThat(o1).isNotEqualTo(o2);
  }

  @Test
  public void equals_returnsTrue_givenEqualValueBytesAndExpiration() {
    RedisString o1 = new RedisString(new ByteArrayWrapper(new byte[] {0, 1, 2, 3}));
    o1.setExpirationTimestampNoDelta(1000);
    RedisString o2 = new RedisString(new ByteArrayWrapper(new byte[] {0, 1, 2, 3}));
    o2.setExpirationTimestampNoDelta(1000);
    assertThat(o1).isEqualTo(o2);
  }


  @SuppressWarnings("unchecked")
  @Test
  public void setExpirationTimestamp_stores_delta_that_is_stable() throws IOException {
    Region<ByteArrayWrapper, RedisData> region = mock(Region.class);
    RedisString o1 = new RedisString(new ByteArrayWrapper(new byte[] {0, 1}));
    o1.setExpirationTimestamp(region, null, 999);
    assertThat(o1.hasDelta()).isTrue();
    HeapDataOutputStream out = new HeapDataOutputStream(100);
    o1.toDelta(out);
    assertThat(o1.hasDelta()).isFalse();
    ByteArrayDataInput in = new ByteArrayDataInput(out.toByteArray());
    RedisString o2 = new RedisString(new ByteArrayWrapper(new byte[] {0, 1}));
    assertThat(o2).isNotEqualTo(o1);
    o2.fromDelta(in);
    assertThat(o2).isEqualTo(o1);
  }
}
