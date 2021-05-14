/*
 * Copyright (C) 2021 Alexander Stojanovich <coas91@rocketmail.com>
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
package rs.alexanderstojanovich.evgl.intrface;

import org.joml.Vector2f;

/**
 *
 * @author Alexander Stojanovich <coas91@rocketmail.com>
 */
public class TextCharacter {

    protected final char value;
    protected final Vector2f[] uvs = new Vector2f[4];
    protected final float xadv;
    protected final float ydrop;

    public TextCharacter(float xadv, float ydrop, char value) {
        this.xadv = xadv;
        this.ydrop = ydrop;
        this.value = value;
        init();
    }

    private void init() {
        for (int i = 0; i < uvs.length; i++) {
            uvs[i] = new Vector2f();
        }

        int asciiCode = value;

        float cellU = (asciiCode % Text.GRID_SIZE) * Text.CELL_SIZE;
        float cellV = (asciiCode / Text.GRID_SIZE) * Text.CELL_SIZE;

        uvs[0].x = cellU;
        uvs[0].y = cellV + Text.CELL_SIZE;

        uvs[1].x = cellU + Text.CELL_SIZE;
        uvs[1].y = cellV + Text.CELL_SIZE;

        uvs[2].x = cellU + Text.CELL_SIZE;
        uvs[2].y = cellV;

        uvs[3].x = cellU;
        uvs[3].y = cellV;
    }

    public char getValue() {
        return value;
    }

    public float getXadv() {
        return xadv;
    }

    public float getYdrop() {
        return ydrop;
    }

    public Vector2f[] getUvs() {
        return uvs;
    }

}
