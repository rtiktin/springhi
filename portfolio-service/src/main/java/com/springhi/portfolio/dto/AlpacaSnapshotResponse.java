package com.springhi.portfolio.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public class AlpacaSnapshotResponse {

    public record Bar(
            @JsonProperty("o") BigDecimal open,
            @JsonProperty("h") BigDecimal high,
            @JsonProperty("l") BigDecimal low,
            @JsonProperty("c") BigDecimal close,
            @JsonProperty("v") Long volume,
            @JsonProperty("t") String timestamp
    ) {}

    public record Trade(
            @JsonProperty("p") BigDecimal price,
            @JsonProperty("s") Long size,
            @JsonProperty("t") String timestamp
    ) {}

    public record Snapshot(
            @JsonProperty("dailyBar") Bar dailyBar,
            @JsonProperty("prevDailyBar") Bar prevDailyBar,
            @JsonProperty("latestTrade") Trade latestTrade
    ) {}
}
