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
package exchange.core2.core.common;

import exchange.core2.core.utils.HashingUtils;
import exchange.core2.core.utils.SerializationUtils;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.bytes.BytesIn;
import net.openhft.chronicle.bytes.BytesOut;
import net.openhft.chronicle.bytes.WriteBytesMarshallable;
import org.eclipse.collections.impl.map.mutable.primitive.IntLongHashMap;
import org.eclipse.collections.impl.map.mutable.primitive.IntObjectHashMap;
import org.eclipse.collections.impl.set.mutable.primitive.LongHashSet;

import java.util.Objects;

@Slf4j
public final class UserProfile implements WriteBytesMarshallable, StateHash {

    public final long uid;

    // symbol -> margin position records
    public final IntObjectHashMap<SymbolPositionRecord> positions;

    // set of applied transactionId
    public final LongHashSet externalTransactions;

    // collected from accounts

    // currency accounts
    // currency -> balance
    public final IntLongHashMap accounts;

    public long commandsCounter = 0L;

    public UserProfile(long uid) {
        //log.debug("New {}", uid);
        this.uid = uid;
        this.positions = new IntObjectHashMap<>();
        this.externalTransactions = new LongHashSet();
        this.accounts = new IntLongHashMap();
    }

    public UserProfile(BytesIn bytesIn) {

        this.uid = bytesIn.readLong();

        // positions
        this.positions = SerializationUtils.readIntHashMap(bytesIn, b -> new SymbolPositionRecord(uid, b));

        // externalTransactions
        this.externalTransactions = SerializationUtils.readLongHashSet(bytesIn);

        // account balances
        this.accounts = SerializationUtils.readIntLongHashMap(bytesIn);
    }

    public SymbolPositionRecord getOrCreatePositionRecord(CoreSymbolSpecification spec) {
        final int symbol = spec.symbolId;
        SymbolPositionRecord record = positions.get(symbol);
        if (record == null) {
            record = new SymbolPositionRecord(uid, symbol, spec.quoteCurrency);
            positions.put(symbol, record);
        }
        return record;
    }

    public SymbolPositionRecord getPositionRecordOrThrowEx(int symbol) {
        final SymbolPositionRecord record = positions.get(symbol);
        if (record == null) {
            throw new IllegalStateException("not found position for symbol " + symbol);
        }
        return record;
    }

    public void removeRecordIfEmpty(SymbolPositionRecord record) {
        if (record.isEmpty()) {
            accounts.addToValue(record.currency, record.profit);
            positions.removeKey(record.symbol);
        }
    }

    @Override
    public void writeMarshallable(BytesOut bytes) {

        bytes.writeLong(uid);

        // positions
        SerializationUtils.marshallIntHashMap(positions, bytes);

        // externalTransactions
        SerializationUtils.marshallLongHashSet(externalTransactions, bytes);

        // account balances
        SerializationUtils.marshallIntLongHashMap(accounts, bytes);
    }


    @Override
    public String toString() {
        return "UserProfile{" +
                "uid=" + uid +
                ", positions=" + positions.size() +
                ", accounts=" + accounts +
                ", commandsCounter=" + commandsCounter +
                '}';
    }

    @Override
    public int stateHash() {
        return Objects.hash(
                uid,
                HashingUtils.stateHash(positions),
                externalTransactions.hashCode(),
                accounts.hashCode());
    }
}
