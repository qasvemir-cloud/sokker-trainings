// Player.java

// YApi QuickType插件生成，具体参考文档:https://plugins.jetbrains.com/plugin/18847-yapi-quicktype/documentation

package org.velja.app.model;
import java.util.List;
import lombok.Data;

@Data
public class Player {
    public long total;
    public List<PlayerElement> players;
}