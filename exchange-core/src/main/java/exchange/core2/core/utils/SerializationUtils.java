/*
 * Copyright 2019 Maksim Zheravin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package exchange.core2.core.utils;

import net.openhft.chronicle.bytes.*;
import net.openhft.chronicle.wire.Wire;
import net.openhft.chronicle.wire.WireType;
import org.eclipse.collections.api.map.primitive.MutableIntLongMap;
import org.eclipse.collections.api.map.primitive.MutableLongIntMap;
import org.eclipse.collections.impl.map.mutable.primitive.IntLongHashMap;
import org.eclipse.collections.impl.map.mutable.primitive.IntObjectHashMap;
import org.eclipse.collections.impl.map.mutable.primitive.LongIntHashMap;
import org.eclipse.collections.impl.map.mutable.primitive.LongObjectHashMap;
import org.eclipse.collections.impl.set.mutable.primitive.LongHashSet;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class SerializationUtils {


    public static long[] bytesToLongArray(final NativeBytes<Void> bytes, final int padding) {
        final ByteBuffer byteBuffer = ByteBuffer.allocate((int) bytes.readRemaining());
        bytes.read(byteBuffer);
        final byte[] array = byteBuffer.array();
//        log.debug("array:{}", array);
        final long[] longs = toLongsArray(array, padding);
//        log.debug("longs:{}", longs);
        return longs;
    }

    public static long[] toLongsArray(final byte[] bytes, final int padding) {

        final int longLength = requiredLongArraySize(bytes.length, padding);
        long[] longArray = new long[longLength];
        //log.debug("byte[{}]={}", bytes.length, bytes);
        final ByteBuffer allocate = ByteBuffer.allocate(longLength * 8 * 2);
        final LongBuffer longBuffer = allocate.asLongBuffer();
        allocate.put(bytes);
        longBuffer.get(longArray);
        return longArray;
    }

    public static int requiredLongArraySize(final int bytesLength, final int padding) {
        int len = requiredLongArraySize(bytesLength);
        if (padding == 1) {
            return len;
        } else {
            int rem = len % padding;
            return rem == 0 ? len : (len + padding - rem);
        }
    }


    public static Wire longsToWire(long[] dataArray) {

        final int sizeInBytes = dataArray.length * 8;
        final ByteBuffer byteBuffer = ByteBuffer.allocate(sizeInBytes);
        byteBuffer.asLongBuffer().put(dataArray);

        final byte[] bytesArray = new byte[sizeInBytes];
        byteBuffer.get(bytesArray);

        //log.debug(" section {} -> {}", section, bytes);

        final Bytes<ByteBuffer> bytes = Bytes.elasticHeapByteBuffer(sizeInBytes);
        bytes.ensureCapacity(sizeInBytes);

        bytes.write(bytesArray);

        //byte[] array = bytes1.underlyingObject().array();
        //byteBuffer.get(array);

        //log.debug("{}", bytesArray);

        return WireType.RAW.apply(bytes);
    }


    public static int requiredLongArraySize(final int bytesLength) {
        return ((bytesLength - 1) >> 3) + 1;
    }

    public static void marshallBitSet(final BitSet bitSet, final BytesOut bytes) {
        marshallLongArray(bitSet.toLongArray(), bytes);
    }

    public static BitSet readBitSet(final BytesIn bytes) {
        // TODO use LongBuffer
        return BitSet.valueOf(readLongArray(bytes));
    }


    public static void marshallLongArray(final long[] longs, final BytesOut bytes) {
        bytes.writeInt(longs.length);
        for (long word : longs) {
            bytes.writeLong(word);
        }
    }


    public static long[] readLongArray(final BytesIn bytes) {
        final int length = bytes.readInt();
        final long[] array = new long[length];
        // TODO read byte[], then convert into long[]
        for (int i = 0; i < length; i++) {
            array[i] = bytes.readLong();
        }
        return array;
    }

    public static void marshallLongIntHashMap(final MutableLongIntMap hashMap, final BytesOut bytes) {

        bytes.writeInt(hashMap.size());
        hashMap.forEachKeyValue((k, v) -> {
            bytes.writeLong(k);
            bytes.writeInt(v);
        });
    }

    public static LongIntHashMap readLongIntHashMap(final BytesIn bytes) {
        int length = bytes.readInt();
        final LongIntHashMap hashMap = new LongIntHashMap(length);
        // TODO shuffle (? performance can be reduced if populating linearly)
        for (int i = 0; i < length; i++) {
            long k = bytes.readLong();
            int v = bytes.readInt();
            hashMap.put(k, v);
        }
        return hashMap;
    }

    public static void marshallIntLongHashMap(final MutableIntLongMap hashMap, final BytesOut bytes) {

        bytes.writeInt(hashMap.size());

        hashMap.forEachKeyValue((k, v) -> {
            bytes.writeInt(k);
            bytes.writeLong(v);
        });
    }

    public static IntLongHashMap readIntLongHashMap(final BytesIn bytes) {
        int length = bytes.readInt();
        final IntLongHashMap hashMap = new IntLongHashMap(length);
        // TODO shuffle (? performance can be reduced if populating linearly)
        for (int i = 0; i < length; i++) {
            int k = bytes.readInt();
            long v = bytes.readLong();
            hashMap.put(k, v);
        }
        return hashMap;
    }


    public static void marshallLongHashSet(final LongHashSet set, final BytesOut bytes) {
        bytes.writeInt(set.size());
        set.forEach(bytes::writeLong);
    }

    public static LongHashSet readLongHashSet(final BytesIn bytes) {
        int length = bytes.readInt();
        final LongHashSet set = new LongHashSet(length);
        // TODO shuffle (? performance can be reduced if populating linearly)
        for (int i = 0; i < length; i++) {
            set.add(bytes.readLong());
        }
        return set;
    }


    public static <T extends WriteBytesMarshallable> void marshallLongHashMap(final LongObjectHashMap<T> hashMap, final BytesOut bytes) {

        bytes.writeInt(hashMap.size());

        hashMap.forEachKeyValue((k, v) -> {
            bytes.writeLong(k);
            v.writeMarshallable(bytes);
        });

    }

    public static <T> void marshallLongHashMap(final LongObjectHashMap<T> hashMap, final BiConsumer<T, BytesOut> valuesMarshaller, final BytesOut bytes) {

        bytes.writeInt(hashMap.size());

        hashMap.forEachKeyValue((k, v) -> {
            bytes.writeLong(k);
            valuesMarshaller.accept(v, bytes);
        });

    }

    public static <T> LongObjectHashMap<T> readLongHashMap(final BytesIn bytes, final Function<BytesIn, T> creator) {
        int length = bytes.readInt();
        final LongObjectHashMap<T> hashMap = new LongObjectHashMap<>(length);
        for (int i = 0; i < length; i++) {
            hashMap.put(bytes.readLong(), creator.apply(bytes));
        }
        return hashMap;
    }

    public static <T extends WriteBytesMarshallable> void marshallIntHashMap(final IntObjectHashMap<T> hashMap, final BytesOut bytes) {
        bytes.writeInt(hashMap.size());
        hashMap.forEachKeyValue((k, v) -> {
            bytes.writeInt(k);
            v.writeMarshallable(bytes);
        });
    }

    public static <T> void marshallIntHashMap(final IntObjectHashMap<T> hashMap, final BytesOut bytes, final Consumer<T> elementMarshaller) {
        bytes.writeInt(hashMap.size());
        hashMap.forEachKeyValue((k, v) -> {
            bytes.writeInt(k);
            elementMarshaller.accept(v);
        });
    }


    public static <T> IntObjectHashMap<T> readIntHashMap(final BytesIn bytes, final Function<BytesIn, T> creator) {
        int length = bytes.readInt();
        final IntObjectHashMap<T> hashMap = new IntObjectHashMap<>(length);
        for (int i = 0; i < length; i++) {
            hashMap.put(bytes.readInt(), creator.apply(bytes));
        }
        return hashMap;
    }


    public static <T extends WriteBytesMarshallable> void marshallLongMap(final Map<Long, T> map, final BytesOut bytes) {
        bytes.writeInt(map.size());

        map.forEach((k, v) -> {
            bytes.writeLong(k);
            v.writeMarshallable(bytes);
        });
    }

    public static <T, M extends Map<Long, T>> M readLongMap(final BytesIn bytes, final Supplier<M> mapSupplier, final Function<BytesIn, T> creator) {
        int length = bytes.readInt();
        final M map = mapSupplier.get();
        for (int i = 0; i < length; i++) {
            map.put(bytes.readLong(), creator.apply(bytes));
        }
        return map;
    }

    public static <T extends WriteBytesMarshallable> void marshallList(final List<T> list, final BytesOut bytes) {
        bytes.writeInt(list.size());
        list.forEach(v -> v.writeMarshallable(bytes));
    }

    public static <T> List<T> readList(final BytesIn bytes, final Function<BytesIn, T> creator) {
        final int length = bytes.readInt();
        final List<T> list = new ArrayList<>(length);
        for (int i = 0; i < length; i++) {
            list.add(creator.apply(bytes));
        }
        return list;
    }

    public static <V> LongObjectHashMap<V> mergeOverride(final LongObjectHashMap<V> a, final LongObjectHashMap<V> b) {
        final LongObjectHashMap<V> res = a == null ? new LongObjectHashMap<>() : new LongObjectHashMap<>(a);
        if (b != null) {
            res.putAll(b);
        }
        return res;
    }

    public static <V> IntObjectHashMap<V> mergeOverride(final IntObjectHashMap<V> a, final IntObjectHashMap<V> b) {
        final IntObjectHashMap<V> res = a == null ? new IntObjectHashMap<>() : new IntObjectHashMap<>(a);
        if (b != null) {
            res.putAll(b);
        }
        return res;
    }

    public static IntLongHashMap mergeSum(final IntLongHashMap a, final IntLongHashMap b) {
        final IntLongHashMap res = a == null ? new IntLongHashMap() : new IntLongHashMap(a);
        if (b != null) {
            b.forEachKeyValue(res::addToValue);
        }
        return res;
    }


}
