/*
 * Copyright (C) 2020 Coa
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package rs.alexanderstojanovich.evgl.level;

import java.util.List;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.magicwerk.brownies.collections.GapList;
import rs.alexanderstojanovich.evgl.critter.NPC;
import rs.alexanderstojanovich.evgl.critter.Player;
import rs.alexanderstojanovich.evgl.texture.Texture;

/**
 *
 * @author Coa
 */
public class LevelActors {

    private final Player player = new Player("icosphere.obj", Texture.MARBLE, new Vector3f(10.5f, 0.0f, -3.0f), new Vector4f(1.0f, 1.0f, 1.0f, 1.0f), 0.05f);
    private final List<NPC> npcList = new GapList<>();

    public void freeze() {
        player.setGivenControl(false);
        for (NPC npc : npcList) {
            npc.setGivenControl(false);
        }
    }

    public void unfreeze() {
        player.setGivenControl(true);
        for (NPC npc : npcList) {
            npc.setGivenControl(true);
        }
    }

    public void render() {
        player.render();
        for (NPC npc : npcList) {
            npc.render();
        }
    }

    public Player getPlayer() {
        return player;
    }

    public List<NPC> getNpcList() {
        return npcList;
    }

}
