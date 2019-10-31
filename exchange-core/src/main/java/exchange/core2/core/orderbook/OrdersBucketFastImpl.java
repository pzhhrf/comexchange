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
package exchange.core2.core.orderbook;

import exchange.core2.core.common.IOrder;
import exchange.core2.core.common.Order;
import exchange.core2.core.common.cmd.OrderCommand;
import exchange.core2.core.utils.SerializationUtils;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.bytes.BytesIn;
import net.openhft.chronicle.bytes.BytesOut;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.eclipse.collections.api.map.primitive.MutableLongIntMap;
import org.eclipse.collections.impl.map.mutable.primitive.LongIntHashMap;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Fast version of Order Bucket.<br/>
 * Implementation is optimized for fastest place/remove operations O(1).<br/>
 * Matching operation can be slower though.<br/>
 * <p>
 * Orders are stored in resizable queue array.<br/>
 * Queue is indexed by hashmap for fast cancel/update operations.<br/>
 */
@Slf4j
@ToString
public final class OrdersBucketFastImpl implements IOrdersBucket {

    private static final int INITIAL_QUEUE_SIZE = 4;

    @Getter
    @Setter
    private long price;

    private final MutableLongIntMap positions;

    private Order[] queue;

    public int tail = 0;
    public int head = 0;
    public int queueSize = 0;
    public int realSize = 0;

    @Getter
    private long totalVolume = 0;

    public OrdersBucketFastImpl() {
        this.positions = new LongIntHashMap();
        this.queue = new Order[INITIAL_QUEUE_SIZE];
    }

    public OrdersBucketFastImpl(BytesIn bytes) {

        this.price = bytes.readLong();
        this.positions = SerializationUtils.readLongIntHashMap(bytes);

        int length = bytes.readInt();
        int count = bytes.readInt();

        this.queue = new Order[length];
        for (int i = 0; i < count; i++) {
            int pos = bytes.readInt();
            this.queue[pos] = new Order(bytes);
        }

        this.tail = bytes.readInt();
        this.head = bytes.readInt();
        this.queueSize = bytes.readInt();
        this.realSize = bytes.readInt();
        this.totalVolume = bytes.readLong();
    }

    @Override

    public void put(Order order) {

        //validate();

        totalVolume += (order.size - order.filled);

        queue[tail] = order;
        queueSize++;
        realSize++;
        tail++;
        positions.put(order.orderId, tail); // always put actual value+1 (null=0 specifics)
        if (tail == queue.length) {
            tail = 0;
        }

        // check if no space left
        if (queueSize == queue.length) {
            upsizeBuffer();
        }

        //validate();

    }

    private void upsizeBuffer() {
        int oldLength = queue.length;
        Order[] array2 = new Order[oldLength * 2];
        int tail2 = 0;
        int left = realSize;

        while (left > 0) {
            while (queue[head] == null) {
                head++;
                queueSize--;
                if (head == queue.length) {
                    head = 0;
                }
            }
            Order order = queue[head++];
            if (head == queue.length) {
                head = 0;
            }
            array2[tail2++] = order;
            positions.put(order.orderId, tail2);
            left--;
        }

        queue = array2;
        head = 0;
        tail = tail2;

    }


    /**
     * Remove order
     *
     * @param orderId
     * @return
     */
    @Override
    public Order remove(long orderId, long uid) {

        //validate();

        int pos = positions.get(orderId);
        if (pos == 0) {
            // TODO why this msg never logged ?
            //log.debug("order not found {}", orderId);
            return null;
        }

        Order order = queue[pos - 1];
        if (order.uid != uid) {
            // can not remove other user's order
            return null;
        }

        positions.remove(orderId);

        queue[pos - 1] = null;
        totalVolume -= (order.size - order.filled);
//        assert totalVolume >= 0;
        realSize--;

        //validate();
        return order;
    }

    /**
     * Collect a list of matching orders starting from eldest records
     * Completely matching orders will be removed, partially matched order kept in the bucked.
     */
    @Override
    public long match(long volumeToCollect, IOrder activeOrder, OrderCommand triggerCmd, Consumer<Order> removeOrderCallback) {

        //validate();

        long totalMatchingVolume = 0;

        int ptr = head;
        int numOrdersToScan = realSize;
        int ownOrderBarrier = -1;
        final long ignoreUid = activeOrder.getUid();

        while (numOrdersToScan > 0 && volumeToCollect > 0) {
            // fast-forward head pointer until non-empty element found
            // (assume if realSize>0 then at least one order will be found)
            while (queue[ptr] == null) {
                ptr++;
                ptr = (ptr == queue.length) ? 0 : ptr;
            }

            Order order = queue[ptr];
            long orderId = order.orderId;

            // ignoring own orders
            if (order.uid == ignoreUid) {
                numOrdersToScan--;

                // set own order barrier - after matching procedure set head there
                // so next matching will start with this order
                if (ownOrderBarrier == -1) {
                    ownOrderBarrier = ptr;
                }

                ptr++;
                ptr = (ptr == queue.length) ? 0 : ptr;

                continue;
            }

            // calculate exact volume can fill for this order
//            log.debug("volumeToCollect={} order: s{} f{}", volumeToCollect, order.size, order.filled);
            long v = Math.min(volumeToCollect, order.size - order.filled);
            totalMatchingVolume += v;
//            log.debug("totalMatchingVolume={} v={}", totalMatchingVolume, v);

            order.filled += v;
            volumeToCollect -= v;
            totalVolume -= v;
            if (totalVolume < 0) {
                log.warn("{}", totalVolume);
            }

            // remove from order book filled orders
            boolean fullMatch = order.size == order.filled;

            OrderBookEventsHelper.sendTradeEvent(triggerCmd, activeOrder, order, fullMatch, volumeToCollect == 0, price, v);

            if (fullMatch) {

                removeOrderCallback.accept(order);

                // remove head
                queue[ptr] = null;
                ptr++;
                ptr = (ptr == queue.length) ? 0 : ptr;
                realSize--;
                numOrdersToScan--;
                positions.remove(orderId);
            }
        }

        head = (ownOrderBarrier == -1) ? ptr : ownOrderBarrier;
        queueSize = tail - head;
        if (queueSize < 0) {
            queueSize += queue.length;
        }

        //validate();

        return totalMatchingVolume;
    }

    @Override
    public void validate() {
        //log.debug("validate: {}", this.price);

        int c = 0;
        int positionInQueue = 0;
        for (Order order : queue) {
            positionInQueue++;
            if (order != null) {
                c++;
                int positionFromHashtable = positions.get(order.orderId);
                if (positionFromHashtable != positionInQueue) {
                    throw new IllegalStateException("positionFromHashtable=" + positionFromHashtable
                            + " positionInQueue=" + positionInQueue + " order:" + order);
                }
            }
        }

        if (positions.size() != c) {
            String msg = String.format("%d: Found %d orders in queue, but there are %d in hash table", price, c, positions.size());
            throw new IllegalStateException(msg);
        }

        if (positions.size() != realSize) {
            throw new IllegalStateException();
        }

        int expectedSize = tail - head;
        if (expectedSize >= 0) {
            if (expectedSize != queueSize) {
                String msg = String.format("tail=%d head=%d queueSize=%d expectedSize=%d", tail, head, queueSize, expectedSize);
                throw new IllegalStateException(msg);
            }
        } else {
            if (expectedSize + queue.length != queueSize) {
                throw new IllegalStateException();
            }
        }

    }

    @Override
    public Order findOrder(long orderId) {
        int pos = positions.get(orderId);
        if (pos == 0) {
            return null;
        }
        return queue[pos - 1];
    }

    @Override
    public List<Order> getAllOrders() {
        return Arrays.asList(asOrdersArray());
    }

    @Override
    public void forEachOrder(Consumer<Order> consumer) {
        int ptr = head;
        for (int i = 0; i < realSize; i++) {
            // fast-forward head pointer until non-empty element found
            // (assume if realSize>0 then at least one order can be found)
            while (queue[ptr] == null) {
                ptr = inc(ptr);
            }
            consumer.accept(queue[ptr]);
            ptr = inc(ptr);
        }
    }

    @Override
    public OrderBucketImplType getImplementationType() {
        return OrderBucketImplType.FAST;
    }

    private void printSchema() {
        StringBuilder s = new StringBuilder();
        for (Order order : queue) {
            s.append(order == null ? '.' : 'O');
        }

        log.debug("       {}", StringUtils.repeat(' ', tail) + "T");
        log.debug("queue:[{}]", s.toString());
        log.debug("       {}", StringUtils.repeat(' ', head) + "H");
    }

    @Override
    public int getNumOrders() {
        return realSize;
    }

    @Override
    public void writeMarshallable(BytesOut bytes) {
        bytes.writeByte(getImplementationType().getCode());
        bytes.writeLong(price);
        SerializationUtils.marshallLongIntHashMap(positions, bytes);

        // NOTE: orders saved not in execution order, just dumping buffer as-is
        bytes.writeInt(queue.length);
        bytes.writeInt((int) Arrays.stream(queue).filter(Objects::nonNull).count());
        for (int i = 0; i < queue.length; i++) {
            Order order = queue[i];
            if (order != null) {
                bytes.writeInt(i);
                order.writeMarshallable(bytes);
            }
        }

        bytes.writeInt(tail);
        bytes.writeInt(head);
        bytes.writeInt(queueSize);
        bytes.writeInt(realSize);

        bytes.writeLong(totalVolume);
    }

    @Override
    public int hashCode() {
        return IOrdersBucket.hash(price, asOrdersArray());
    }

    private Order[] asOrdersArray() {
        int ptr = head;
        Order[] result = new Order[realSize];
        for (int i = 0; i < realSize; i++) {
            // fast-forward head pointer until non-empty element found
            // (assume if realSize>0 then at least one order can be found)
            while (queue[ptr] == null) {
                ptr = inc(ptr);
            }
            result[i] = queue[ptr];
            ptr = inc(ptr);
        }
        return result;
    }

    // TODO try in main algos
    private int inc(int p) {
        p++;
        return (p == queue.length) ? 0 : p;
    }


    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (o == null) return false;
        if (!(o instanceof IOrdersBucket)) return false;
        IOrdersBucket other = (IOrdersBucket) o;
        return new EqualsBuilder()
                .append(price, other.getPrice())
                .append(getAllOrders(), other.getAllOrders())
                .isEquals();
    }

}
