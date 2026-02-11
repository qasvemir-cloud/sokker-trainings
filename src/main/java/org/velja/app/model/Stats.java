package org.velja.app.model;

import lombok.Data;

@Data
public class Stats {
    private Cards cards;
    private long assists;
    private long matches;
    private long goals;
}
