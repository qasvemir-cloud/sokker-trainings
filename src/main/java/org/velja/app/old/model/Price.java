package org.velja.app.old.model;

import lombok.Data;

@Data
public class Price {
    private PreviousValue minBid;
    private PreviousValue suggestedBid;
    private PreviousValue listed;
    private PreviousValue bid;
}
