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

import exchange.core2.tests.util.L2MarketDataHelper;
import exchange.core2.tests.util.TestOrdersGenerator;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import exchange.core2.core.common.L2MarketData;
import exchange.core2.core.common.MatcherEventType;
import exchange.core2.core.common.MatcherTradeEvent;
import exchange.core2.core.common.OrderAction;
import exchange.core2.core.common.cmd.CommandResultCode;
import exchange.core2.core.common.cmd.OrderCommand;

import java.util.Arrays;
import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;
import static exchange.core2.core.common.OrderAction.ASK;
import static exchange.core2.core.common.OrderAction.BID;
import static exchange.core2.core.common.OrderType.GTC;
import static exchange.core2.core.common.OrderType.IOC;
import static exchange.core2.core.common.cmd.CommandResultCode.MATCHING_UNKNOWN_ORDER_ID;
import static exchange.core2.core.common.cmd.CommandResultCode.SUCCESS;

/**
 * TODO add tests where orders for same UID ignored during matching
 * TODO cancel/update other uid not allowed
 * TODO tests where IOC order is not fully matched because of limit price (similar to GTC tests)
 * TODO tests where GTC order has duplicate id - rejection event should be sent
 * TODO add tests for exchange mode (moves)
 * TODO test reserve price validation for BID orders in exchange mode
 */
@Slf4j
public abstract class OrderBookBaseTest {

    IOrderBook orderBook;

    private L2MarketDataHelper expectedState;

    static final int INITIAL_PRICE = 81600;

    static final int MAX_PRICE = 400000;

    static final int UID_1 = 412;
    static final int UID_2 = 413;


    protected abstract IOrderBook createNewOrderBook();


    @Before
    public void before() {
        orderBook = createNewOrderBook();
        orderBook.validateInternalState();

        IOrderBook.processCommand(orderBook, OrderCommand.newOrder(GTC, 0, UID_2, INITIAL_PRICE, 0, 131, ASK));
        orderBook.cancelOrder(OrderCommand.cancel(0, UID_2));

        IOrderBook.processCommand(orderBook, OrderCommand.newOrder(GTC, 1, UID_1, 81600, 0, 100, ASK));
        IOrderBook.processCommand(orderBook, OrderCommand.newOrder(GTC, 2, UID_1, 81599, 0, 50, ASK));
        IOrderBook.processCommand(orderBook, OrderCommand.newOrder(GTC, 3, UID_1, 81599, 0, 25, ASK));
        orderBook.validateInternalState();

        IOrderBook.processCommand(orderBook, OrderCommand.newOrder(GTC, 4, UID_1, 81593, 82000, 40, BID));
        IOrderBook.processCommand(orderBook, OrderCommand.newOrder(GTC, 5, UID_1, 81590, 82000, 20, BID));
        IOrderBook.processCommand(orderBook, OrderCommand.newOrder(GTC, 6, UID_1, 81590, 82000, 1, BID));
        IOrderBook.processCommand(orderBook, OrderCommand.newOrder(GTC, 7, UID_1, 81200, 82000, 20, BID));
        orderBook.validateInternalState();

        // FAR orders section
        IOrderBook.processCommand(orderBook, OrderCommand.newOrder(GTC, 8, UID_1, 201000, 0, 28, ASK));
        IOrderBook.processCommand(orderBook, OrderCommand.newOrder(GTC, 9, UID_1, 201000, 0, 32, ASK));
        IOrderBook.processCommand(orderBook, OrderCommand.newOrder(GTC, 10, UID_1, 200954, 0, 10, ASK));
        orderBook.validateInternalState();
        IOrderBook.processCommand(orderBook, OrderCommand.newOrder(GTC, 11, UID_1, 10000, 12000, 12, BID));
        IOrderBook.processCommand(orderBook, OrderCommand.newOrder(GTC, 12, UID_1, 10000, 12000, 1, BID));
        IOrderBook.processCommand(orderBook, OrderCommand.newOrder(GTC, 13, UID_1, 9136, 12000, 2, BID));
        orderBook.validateInternalState();

        expectedState = new L2MarketDataHelper(
                new L2MarketData(
                        new long[]{81599, 81600, 200954, 201000},
                        new long[]{75, 100, 10, 60},
                        new long[]{81593, 81590, 81200, 10000, 9136},
                        new long[]{40, 21, 20, 13, 2}
                )
        );

        L2MarketData snapshot = orderBook.getL2MarketDataSnapshot(25);
        assertEquals(expectedState.build(), snapshot);
    }

    /**
     * In the end of each test remove all orders by sending market orders wit proper size.
     * Check order book is empty.
     */
    @After
    public void after() {
        clearOrderBook();
    }

    void clearOrderBook() {
        orderBook.validateInternalState();
        L2MarketData snapshot = orderBook.getL2MarketDataSnapshot(-1);

        // match all asks
        long askSum = Arrays.stream(snapshot.askVolumes).sum();
        IOrderBook.processCommand(orderBook, OrderCommand.newOrder(IOC, 100000000000L, -1, MAX_PRICE, MAX_PRICE, askSum, BID));

//        log.debug("{}", dumpOrderBook(orderBook.getL2MarketDataSnapshot(100000)));

        orderBook.validateInternalState();

        // match all bids
        long bidSum = Arrays.stream(snapshot.bidVolumes).sum();
        IOrderBook.processCommand(orderBook, OrderCommand.newOrder(IOC, 100000000001L, -2, 1, 0, bidSum, ASK));

//        log.debug("{}", dumpOrderBook(orderBook.getL2MarketDataSnapshot(100000)));

        assertThat(orderBook.getL2MarketDataSnapshot(-1).askSize, is(0));
        assertThat(orderBook.getL2MarketDataSnapshot(-1).bidSize, is(0));

        orderBook.validateInternalState();
    }


    // ------------------------ TESTS WITHOUT MATCHING -----------------------

    /**
     * Just place few GTC orders
     */
    @Test
    public void shouldAddGtcOrders() {

        IOrderBook.processCommand(orderBook, OrderCommand.newOrder(GTC, 93, UID_1, 81598, 0, 1, ASK));
        expectedState.insertAsk(0, 81598, 1);

        IOrderBook.processCommand(orderBook, OrderCommand.newOrder(GTC, 94, UID_1, 81594, MAX_PRICE, 9_000_000_000L, BID));
        expectedState.insertBid(0, 81594, 9_000_000_000L);

        L2MarketData snapshot = orderBook.getL2MarketDataSnapshot(25);
        assertEquals(expectedState.build(), snapshot);
        orderBook.validateInternalState();

        IOrderBook.processCommand(orderBook, OrderCommand.newOrder(GTC, 95, UID_1, 130000, 0, 13_000_000_000L, ASK));
        expectedState.insertAsk(3, 130000, 13_000_000_000L);

        IOrderBook.processCommand(orderBook, OrderCommand.newOrder(GTC, 96, UID_1, 1000, MAX_PRICE, 4, BID));
        expectedState.insertBid(6, 1000, 4);

        snapshot = orderBook.getL2MarketDataSnapshot(25);
        assertEquals(expectedState.build(), snapshot);
        orderBook.validateInternalState();

        //        log.debug("{}", dumpOrderBook(snapshot));
    }

    /**
     * Remove existing order
     */
    @Test
    public void shouldRemoveOrder() {

        // remove bid order
        OrderCommand cmd = OrderCommand.cancel(5, UID_1);
        processAndValidate(cmd, SUCCESS);

        expectedState.setBidVolume(1, 1);
        assertEquals(expectedState.build(), orderBook.getL2MarketDataSnapshot(25));

        List<MatcherTradeEvent> events = cmd.extractEvents();
        assertThat(events.size(), is(1));
        checkEventCancel(events.get(0), 5L, BID, 20L, UID_1);

        // remove ask order
        cmd = OrderCommand.cancel(2, UID_1);
        processAndValidate(cmd, SUCCESS);

        expectedState.setAskVolume(0, 25);
        assertEquals(expectedState.build(), orderBook.getL2MarketDataSnapshot(25));

        events = cmd.extractEvents();
        assertThat(events.size(), is(1));
        checkEventCancel(events.get(0), 2L, ASK, 50L, UID_1);
    }

    /**
     * When cancelling an order, order book implementation should also remove a bucket if no orders left for specified price
     */
    @Test
    public void shouldRemoveOrderAndEmptyBucket() {
        OrderCommand cmdCancel2 = OrderCommand.cancel(2, UID_1);
        processAndValidate(cmdCancel2, SUCCESS);

        List<MatcherTradeEvent> events = cmdCancel2.extractEvents();
        assertThat(events.size(), is(1));
        checkEventCancel(events.get(0), 2L, ASK, 50L, UID_1);

        //log.debug("{}", orderBook.getL2MarketDataSnapshot(10).dumpOrderBook());

        OrderCommand cmdCancel3 = OrderCommand.cancel(3, UID_1);
        processAndValidate(cmdCancel3, SUCCESS);

        L2MarketData snapshot = orderBook.getL2MarketDataSnapshot(10);

        assertEquals(expectedState.removeAsk(0).build(), snapshot);

        events = cmdCancel3.extractEvents();
        assertThat(events.size(), is(1));
        checkEventCancel(events.get(0), 3L, ASK, 25L, UID_1);
    }

    @Test
    public void shouldReturnErrorWhenCancelUnknownOrder() {

        OrderCommand cmd = OrderCommand.cancel(5291, UID_1);
        processAndValidate(cmd, MATCHING_UNKNOWN_ORDER_ID);

        L2MarketData snapshot = orderBook.getL2MarketDataSnapshot(10);
//        log.debug("{}", dumpOrderBook(snapshot));

        // nothing has changed
        assertEquals(expectedState.build(), snapshot);
    }

    @Test
    public void shouldReturnErrorWhenUpdatingUnknownOrder() {

        OrderCommand cmd = OrderCommand.update(2433, UID_1, 300);
        processAndValidate(cmd, MATCHING_UNKNOWN_ORDER_ID);

        L2MarketData snapshot = orderBook.getL2MarketDataSnapshot(10);
        //        log.debug("{}", dumpOrderBook(snapshot));

        assertEquals(expectedState.build(), snapshot);

        List<MatcherTradeEvent> events = cmd.extractEvents();
        assertThat(events.size(), is(0));
    }

    @Test
    public void shouldMoveOrderExistingBucket() {
        OrderCommand cmd = OrderCommand.update(7, UID_1, 81590);
        processAndValidate(cmd, SUCCESS);

        L2MarketData snapshot = orderBook.getL2MarketDataSnapshot(10);

        // moved
        L2MarketData expected = expectedState.setBidVolume(1, 41).removeBid(2).build();
        assertEquals(expected, snapshot);

        List<MatcherTradeEvent> events = cmd.extractEvents();
        assertThat(events.size(), is(0));
    }

    @Test
    public void shouldMoveOrderNewBucket() {
        OrderCommand cmd = OrderCommand.update(7, UID_1, 81594);
        processAndValidate(cmd, SUCCESS);

        L2MarketData snapshot = orderBook.getL2MarketDataSnapshot(10);

        // moved
        L2MarketData expected = expectedState.removeBid(2).insertBid(0, 81594, 20).build();
        assertEquals(expected, snapshot);

        List<MatcherTradeEvent> events = cmd.extractEvents();
        assertThat(events.size(), is(0));
    }

    // ------------------------ MATCHING TESTS -----------------------

    @Test
    public void shouldMatchIocOrderPartialBBO() {

        // size=10
        OrderCommand cmd = OrderCommand.newOrder(IOC, 123, UID_2, 1, 0, 10, ASK);
        processAndValidate(cmd, SUCCESS);

        L2MarketData snapshot = orderBook.getL2MarketDataSnapshot(10);
        // best bid matched
        L2MarketData expected = expectedState.setBidVolume(0, 30).build();
        assertEquals(expected, snapshot);

        List<MatcherTradeEvent> events = cmd.extractEvents();
        assertThat(events.size(), is(1));
        checkEventTrade(events.get(0), 123L, 4L, 81593, 10L);
    }


    @Test
    public void shouldMatchIocOrderFullBBO() {

        // size=40
        OrderCommand cmd = OrderCommand.newOrder(IOC, 123, UID_2, 1, 0, 40, ASK);
        processAndValidate(cmd, SUCCESS);

        L2MarketData snapshot = orderBook.getL2MarketDataSnapshot(10);
        // best bid matched
        L2MarketData expected = expectedState.removeBid(0).build();
        assertEquals(expected, snapshot);

        List<MatcherTradeEvent> events = cmd.extractEvents();
        assertThat(events.size(), is(1));
        checkEventTrade(events.get(0), 123L, 4L, 81593, 40L);
    }

    @Test
    public void shouldMatchIocOrderWithTwoLimitOrdersPartial() {

        // size=41
        OrderCommand cmd = OrderCommand.newOrder(IOC, 123, UID_2, 1, 0, 41, ASK);
        processAndValidate(cmd, SUCCESS);

        L2MarketData snapshot = orderBook.getL2MarketDataSnapshot(10);
        // bids matched
        L2MarketData expected = expectedState.removeBid(0).setBidVolume(0, 20).build();
        assertEquals(expected, snapshot);

        List<MatcherTradeEvent> events = cmd.extractEvents();
        assertThat(events.size(), is(2));
        checkEventTrade(events.get(0), 123L, 4L, 81593, 40L);
        checkEventTrade(events.get(1), 123L, 5L, 81590, 1L);

        // check orders are removed from map
        assertNull(orderBook.getOrderById(4L));
        assertNotNull(orderBook.getOrderById(5L));
    }


    @Test
    public void shouldMatchIocOrderFullLiquidity() {

        // size=175
        OrderCommand cmd = OrderCommand.newOrder(IOC, 123, UID_2, MAX_PRICE, MAX_PRICE, 175, BID);
        processAndValidate(cmd, SUCCESS);

        L2MarketData snapshot = orderBook.getL2MarketDataSnapshot(10);
        // all asks matched
        L2MarketData expected = expectedState.removeAsk(0).removeAsk(0).build();
        assertEquals(expected, snapshot);

        List<MatcherTradeEvent> events = cmd.extractEvents();
        assertThat(events.size(), is(3));
        checkEventTrade(events.get(0), 123L, 2L, 81599, 50L);
        checkEventTrade(events.get(1), 123L, 3L, 81599, 25L);
        checkEventTrade(events.get(2), 123L, 1L, 81600, 100L);

        // check orders are removed from map
        assertNull(orderBook.getOrderById(1L));
        assertNull(orderBook.getOrderById(2L));
        assertNull(orderBook.getOrderById(3L));
    }

    @Test
    public void shouldMatchIocOrderWithRejection() {

        // size=270
        OrderCommand cmd = OrderCommand.newOrder(IOC, 123, UID_2, MAX_PRICE, MAX_PRICE, 270, BID);
        processAndValidate(cmd, SUCCESS);

        L2MarketData snapshot = orderBook.getL2MarketDataSnapshot(10);
        // all asks matched
        L2MarketData expected = expectedState.removeAllAsks().build();
        assertEquals(expected, snapshot);

        List<MatcherTradeEvent> events = cmd.extractEvents();
        assertThat(events.size(), is(7));

        // 7 trades generated and then rejection with size=25 left unmatched
        checkEventRejection(events.get(6), 123L, 25L);
    }

    // MARKETABLE GTC ORDERS

    @Test
    public void shouldFullyMatchMarketableGtcOrder() {

        // size=1
        OrderCommand cmd = OrderCommand.newOrder(GTC, 123, UID_2, 81599, MAX_PRICE, 1, BID);
        processAndValidate(cmd, SUCCESS);

        L2MarketData snapshot = orderBook.getL2MarketDataSnapshot(10);
        // best ask partially matched
        L2MarketData expected = expectedState.setAskVolume(0, 74).build();
        assertEquals(expected, snapshot);

        List<MatcherTradeEvent> events = cmd.extractEvents();
        assertThat(events.size(), is(1));
        checkEventTrade(events.get(0), 123L, 2L, 81599, 1L);
    }


    @Test
    public void shouldPartiallyMatchMarketableGtcOrderAndPlace() {

        // size=77
        OrderCommand cmd = OrderCommand.newOrder(GTC, 123, UID_2, 81599, MAX_PRICE, 77, BID);
        processAndValidate(cmd, SUCCESS);

        L2MarketData snapshot = orderBook.getL2MarketDataSnapshot(10);
        // best asks fully matched, limit bid order placed
        L2MarketData expected = expectedState.removeAsk(0).insertBid(0, 81599, 2).build();
        assertEquals(expected, snapshot);

        List<MatcherTradeEvent> events = cmd.extractEvents();
        assertThat(events.size(), is(2));

        checkEventTrade(events.get(0), 123L, 2L, 81599, 50L);
        checkEventTrade(events.get(1), 123L, 3L, 81599, 25L);
    }

    @Test
    public void shouldFullyMatchMarketableGtcOrder2Prices() {

        // size=77
        OrderCommand cmd = OrderCommand.newOrder(GTC, 123, UID_2, 81600, MAX_PRICE, 77, BID);
        processAndValidate(cmd, SUCCESS);

        L2MarketData snapshot = orderBook.getL2MarketDataSnapshot(10);
        // best asks fully matched, limit bid order placed
        L2MarketData expected = expectedState.removeAsk(0).setAskVolume(0, 98).build();
        assertEquals(expected, snapshot);

        List<MatcherTradeEvent> events = cmd.extractEvents();
        assertThat(events.size(), is(3));

        checkEventTrade(events.get(0), 123L, 2L, 81599, 50L);
        checkEventTrade(events.get(1), 123L, 3L, 81599, 25L);
        checkEventTrade(events.get(2), 123L, 1L, 81600, 2L);
    }


    @Test
    public void shouldFullyMatchMarketableGtcOrderWithAllLiquidity() {

        // size=1000
        OrderCommand cmd = OrderCommand.newOrder(GTC, 123, UID_2, 220000, MAX_PRICE, 1000, BID);
        processAndValidate(cmd, SUCCESS);

        L2MarketData snapshot = orderBook.getL2MarketDataSnapshot(10);
        // best asks fully matched, limit bid order placed
        L2MarketData expected = expectedState.removeAllAsks().insertBid(0, 220000, 755).build();
        assertEquals(expected, snapshot);

        // trades only, rejection not generated for limit order
        List<MatcherTradeEvent> events = cmd.extractEvents();
        assertThat(events.size(), is(6));

        checkEventTrade(events.get(0), 123L, 2L, 81599, 50L);
        checkEventTrade(events.get(1), 123L, 3L, 81599, 25L);
        checkEventTrade(events.get(2), 123L, 1L, 81600, 100L);
        checkEventTrade(events.get(3), 123L, 10L, 200954, 10L);
        checkEventTrade(events.get(4), 123L, 8L, 201000, 28L);
        checkEventTrade(events.get(5), 123L, 9L, 201000, 32L);
    }


    // Move GTC order to marketable price
    // TODO add into far area
    @Test
    public void shouldMoveOrderFullyMatchAsMarketable() {

        // add new order and check it is there
        OrderCommand cmd = OrderCommand.newOrder(GTC, 83, UID_2, 81200, MAX_PRICE, 20, BID);
        processAndValidate(cmd, SUCCESS);

        List<MatcherTradeEvent> events = cmd.extractEvents();
        assertThat(events.size(), is(0));

        L2MarketData expected = expectedState.setBidVolume(2, 40).build();
        assertEquals(expected, orderBook.getL2MarketDataSnapshot(10));

        // move to marketable price area
        cmd = OrderCommand.update(83, UID_2, 81602);
        processAndValidate(cmd, SUCCESS);

        // moved
        expected = expectedState.setBidVolume(2, 20).setAskVolume(0, 55).build();
        assertEquals(expected, orderBook.getL2MarketDataSnapshot(10));

        events = cmd.extractEvents();
        assertThat(events.size(), is(1));
        checkEventTrade(events.get(0), 83L, 2L, 81599, 20L);
    }


    @Test
    public void shouldMoveOrderFullyMatchAsMarketable2Prices() {

        OrderCommand cmd = OrderCommand.newOrder(GTC, 83, UID_2, 81594, MAX_PRICE, 100, BID);
        processAndValidate(cmd, SUCCESS);

        List<MatcherTradeEvent> events = cmd.extractEvents();
        assertThat(events.size(), is(0));

        // move to marketable zone
        cmd = OrderCommand.update(83, UID_2, 81600);
        processAndValidate(cmd, SUCCESS);

        L2MarketData snapshot = orderBook.getL2MarketDataSnapshot(10);

        // moved
        L2MarketData expected = expectedState.removeAsk(0).setAskVolume(0, 75).build();
        assertEquals(expected, snapshot);

        events = cmd.extractEvents();
        assertThat(events.size(), is(3));
        checkEventTrade(events.get(0), 83L, 2L, 81599, 50L);
        checkEventTrade(events.get(1), 83L, 3L, 81599, 25L);
        checkEventTrade(events.get(2), 83L, 1L, 81600, 25L);

    }

    @Test
    public void shouldMoveOrderMatchesAllLiquidity() {

        OrderCommand cmd = OrderCommand.newOrder(GTC, 83, UID_2, 81594, MAX_PRICE, 246, BID);
        processAndValidate(cmd, SUCCESS);

        // move to marketable zone
        cmd = OrderCommand.update(83, UID_2, 201000);
        processAndValidate(cmd, SUCCESS);

        L2MarketData snapshot = orderBook.getL2MarketDataSnapshot(10);

        // moved
        L2MarketData expected = expectedState.removeAllAsks().insertBid(0, 201000, 1).build();
        assertEquals(expected, snapshot);

        List<MatcherTradeEvent> events = cmd.extractEvents();
        assertThat(events.size(), is(6));
        checkEventTrade(events.get(0), 83L, 2L, 81599, 50L);
        checkEventTrade(events.get(1), 83L, 3L, 81599, 25L);
        checkEventTrade(events.get(2), 83L, 1L, 81600, 100L);
        checkEventTrade(events.get(3), 83L, 10L, 200954, 10L);
        checkEventTrade(events.get(4), 83L, 8L, 201000, 28L);
        checkEventTrade(events.get(5), 83L, 9L, 201000, 32L);
    }


    @Test
    public void multipleCommandsTest() {

        int tranNum = 25000;

        final IOrderBook localOrderBook = createNewOrderBook();
        localOrderBook.validateInternalState();

        TestOrdersGenerator.GenResult genResult = TestOrdersGenerator.generateCommands(
                tranNum,
                200,
                6,
                TestOrdersGenerator.UID_PLAIN_MAPPER,
                0,
                false,
                TestOrdersGenerator.createAsyncProgressLogger(tranNum));

        genResult.getCommands().forEach(cmd -> {
            cmd.orderId += 100; // TODO set start id
            CommandResultCode commandResultCode = IOrderBook.processCommand(localOrderBook, cmd);
            assertThat(commandResultCode, is(SUCCESS));
            localOrderBook.validateInternalState();
        });

    }

    // ------------------------------- UTILITY METHODS --------------------------

    public void processAndValidate(OrderCommand cmd, CommandResultCode expectedCmdState) {
        CommandResultCode resultCode = IOrderBook.processCommand(orderBook, cmd);
        assertThat(resultCode, is(expectedCmdState));
        orderBook.validateInternalState();
    }

    public void checkEventTrade(MatcherTradeEvent event, long activeId, long matchedId, long price, long size) {

        assertThat(event.eventType, is(MatcherEventType.TRADE));

        assertThat(event.activeOrderId, is(activeId));
        assertThat(event.matchedOrderId, is(matchedId));
        assertThat(event.price, is(price));
        assertThat(event.size, is(size));
        // TODO add more checks for MatcherTradeEvent
    }

    public void checkEventRejection(MatcherTradeEvent event, long activeId, long size) {

        assertThat(event.eventType, is(MatcherEventType.REJECTION));

        assertThat(event.activeOrderId, is(activeId));
        assertThat(event.size, is(size));
        // TODO add more checks for MatcherTradeEvent
    }

    public void checkEventCancel(MatcherTradeEvent event, long orderId, OrderAction action, long remainingSize, long uid) {
        assertThat(event.eventType, is(MatcherEventType.CANCEL));

        assertThat(event.activeOrderId, is(orderId));
        assertThat(event.activeOrderAction, is(action));
        assertThat(event.size, is(remainingSize));

        assertThat(event.activeOrderUid, is(uid));
        // TODO add more checks for MatcherTradeEvent
    }

}