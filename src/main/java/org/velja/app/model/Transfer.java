package org.velja.app.model;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class Transfer {
    private Price price;
    private OffsetDateTime deadline;
    private long buyerId;
}
