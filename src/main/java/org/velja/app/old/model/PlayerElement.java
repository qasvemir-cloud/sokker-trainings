package org.velja.app.old.model;

import lombok.Data;

@Data
public class PlayerElement {
    private Transfer transfer;
    private int id;
    private Info info;

    public PlayerElement( int id, Info info)
    {
        this.id=id;this.info=info;
    }
}
