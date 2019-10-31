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
package exchange.core2.tests.perf.modules;


import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import exchange.core2.core.common.Order;
import exchange.core2.core.common.cmd.OrderCommand;
import exchange.core2.core.orderbook.IOrdersBucket;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static exchange.core2.core.orderbook.OrdersBucketBaseTest.IGNORE_CMD_CONSUMER;

@Slf4j
public abstract class ITOrdersBucketBase {


    private static final int PRICE = 1000;
    private static final int UID_1 = 412;
    private static final int UID_2 = 413;
    private static final int UID_9 = 419;

    private IOrdersBucket bucket;

    protected abstract IOrdersBucket createNewOrdersBucket();

    @Before
    public void before() {
        bucket = createNewOrdersBucket();
        bucket.setPrice(PRICE);

        bucket.put(Order.builder().orderId(1).uid(UID_1).size(100).build());
        assertThat(bucket.getNumOrders(), is(1));
        assertThat(bucket.getTotalVolume(), is(100L));

        bucket.validate();

        bucket.put(Order.builder().orderId(2).uid(UID_2).size(40).build());
        assertThat(bucket.getNumOrders(), is(2));
        assertThat(bucket.getTotalVolume(), is(140L));

        bucket.validate();

        bucket.put(Order.builder().orderId(3).uid(UID_1).size(1).build());
        assertThat(bucket.getNumOrders(), is(3));
        assertThat(bucket.getTotalVolume(), is(141L));

        bucket.validate();

        bucket.remove(2, UID_2);
        assertThat(bucket.getNumOrders(), is(2));
        assertThat(bucket.getTotalVolume(), is(101L));

        bucket.validate();

        bucket.put(Order.builder().orderId(4).uid(UID_1).size(200).build());
        assertThat(bucket.getNumOrders(), is(3));
        assertThat(bucket.getTotalVolume(), is(301L));
    }


    @Test
    public void shouldMatchAllOrdersAndValidateEachStep() {
        int numOrdersToAdd = 1000;
        long expectedVolume = bucket.getTotalVolume();
        int expectedNumOrders = bucket.getNumOrders();

        bucket.validate();
        int orderId = 5;

        for (int j = 0; j < 300; j++) {
            List<Order> orders = new ArrayList<>(numOrdersToAdd);
            for (int i = 0; i < numOrdersToAdd; i++) {
                Order order = Order.builder().orderId(orderId++).uid(UID_2).size(i).build();
                orders.add(order);

                bucket.put(order);
                expectedNumOrders++;
                expectedVolume += i;

                //log.debug("{}-{}: orderId:{}", j, i, orderId);

                bucket.validate();
            }

            assertThat(bucket.getNumOrders(), is(expectedNumOrders));
            assertThat(bucket.getTotalVolume(), is(expectedVolume));

            Collections.shuffle(orders, new Random(1));

            List<Order> orders1 = orders.subList(0, 900);

            for (Order order : orders1) {
                bucket.remove(order.orderId, UID_2);
                expectedNumOrders--;
                expectedVolume -= order.size;
                assertThat(bucket.getNumOrders(), is(expectedNumOrders));
                assertThat(bucket.getTotalVolume(), is(expectedVolume));

                bucket.validate();
            }

            long toMatch = expectedVolume / 2;
            OrderCommand triggerOrder = OrderCommand.update(1238729387, UID_9, 1000);
            long totalVolume = bucket.match(toMatch, triggerOrder, triggerOrder, IGNORE_CMD_CONSUMER);
            assertThat(totalVolume, is(toMatch));
            expectedVolume -= totalVolume;
            assertThat(bucket.getTotalVolume(), is(expectedVolume));
            expectedNumOrders = bucket.getNumOrders();

            bucket.validate();
        }

        OrderCommand triggerOrder = OrderCommand.update(1238729387, UID_9, 1000);
        bucket.match(expectedVolume, triggerOrder, triggerOrder, IGNORE_CMD_CONSUMER);
        assertThat(triggerOrder.extractEvents().size(), is(expectedNumOrders));

        assertThat(bucket.getNumOrders(), is(0));
        assertThat(bucket.getTotalVolume(), is(0L));

        bucket.getNumOrders();

    }


    // ---------------------- PERFORMANCE ----------------

    @Test
    public void perfAddManyOrders() {
        int numOrdersToAdd = 10_000_000;
        long expectedVolume = bucket.getTotalVolume();
        int expectedNumOrders = bucket.getNumOrders() + numOrdersToAdd;
        Order[] orders = new Order[numOrdersToAdd];
        for (int i = 0; i < numOrdersToAdd; i++) {
            orders[i] = Order.builder().orderId(i + 5).uid(UID_2).size(i).build();
            expectedVolume += i;
        }

        long t = System.currentTimeMillis();
        for (Order order : orders) {
            bucket.put(order);
        }
        log.debug("{}ms", System.currentTimeMillis() - t);

        assertThat(bucket.getNumOrders(), is(expectedNumOrders));
        assertThat(bucket.getTotalVolume(), is(expectedVolume));
    }


    @Test
    public void perfFullCycle() {
        int numOrdersToAdd = 1000;
        long expectedVolume = bucket.getTotalVolume();
        int expectedNumOrders = bucket.getNumOrders();

        long timeAccum = 0;

        bucket.validate();
        int orderId = 5;

        for (int j = 0; j < 50_000; j++) {
            List<Order> orders = new ArrayList<>(numOrdersToAdd);

            long s = System.nanoTime();
            for (int i = 0; i < numOrdersToAdd; i++) {
                Order order = Order.builder().orderId(orderId++).uid(UID_2).size(i).build();
                orders.add(order);

                bucket.put(order);
                expectedNumOrders++;
                expectedVolume += i;

                //log.debug("{}-{}: orderId:{}", j, i, orderId);
            }
            timeAccum += System.nanoTime() - s;

            assertThat(bucket.getNumOrders(), is(expectedNumOrders));
            assertThat(bucket.getTotalVolume(), is(expectedVolume));

            Collections.shuffle(orders, new Random(1));

            List<Order> orders1 = orders.subList(0, 900);

            s = System.nanoTime();
            for (Order order : orders1) {
                bucket.remove(order.orderId, UID_2);
                expectedNumOrders--;
                expectedVolume -= order.size;
                assertThat(bucket.getNumOrders(), is(expectedNumOrders));
                assertThat(bucket.getTotalVolume(), is(expectedVolume));
            }
            timeAccum += System.nanoTime() - s;

            long toMatch = expectedVolume / 2;
            s = System.nanoTime();

            OrderCommand triggerOrd = OrderCommand.update(1238729387, UID_9, 1000);
            long matchingTotalVol = bucket.match(toMatch, triggerOrd, triggerOrd, IGNORE_CMD_CONSUMER);

            timeAccum += System.nanoTime() - s;
            assertThat(matchingTotalVol, is(toMatch));
            expectedVolume -= matchingTotalVol;
            assertThat(bucket.getTotalVolume(), is(expectedVolume));
            expectedNumOrders = bucket.getNumOrders();

        }

        log.debug("Time: {}ms", timeAccum / 1000000);

    }

}
