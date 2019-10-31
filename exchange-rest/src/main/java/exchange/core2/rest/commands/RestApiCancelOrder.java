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
package exchange.core2.rest.commands;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
public final class RestApiCancelOrder {

    private final long orderId;

    private final String symbol;

    // TODO remove
    private final long uid;

    @JsonCreator
    public RestApiCancelOrder(
            @JsonProperty("orderId") long orderId,
            @JsonProperty("symbol") String symbol,
            @JsonProperty("uid") long uid) {

        this.orderId = orderId;
        this.symbol = symbol;
        this.uid = uid;
    }

    @Override
    public String toString() {
        return "[CANCEL " + orderId + "]";
    }
}
