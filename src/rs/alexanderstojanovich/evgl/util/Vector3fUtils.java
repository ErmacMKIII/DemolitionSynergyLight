/* 
 * Copyright (C) 2020 Alexander Stojanovich <coas91@rocketmail.com>
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
package rs.alexanderstojanovich.evgl.util;

import org.joml.Vector3f;

/**
 *
 * @author Alexander Stojanovich <coas91@rocketmail.com>
 */
public class Vector3fUtils {

    public static int hashCode(Vector3f vector) {
        final int h1 = Float.floatToIntBits(vector.x);
        final int h2 = Float.floatToIntBits(vector.y);
        final int h3 = Float.floatToIntBits(vector.z);

        int result = (h1 ^ (h1 >>> 16));
        result = 31 * result + (h2 ^ (h2 >>> 16));
        result = 31 * result + (h3 ^ (h3 >>> 16));
        return result;
    }

    public static byte[] vec3fToByteArray(Vector3f vector) {
        byte[] buffer = new byte[12];
        int x = Float.floatToIntBits(vector.x);
        buffer[0] = (byte) (x);
        buffer[1] = (byte) (x >> 8);
        buffer[2] = (byte) (x >> 16);
        buffer[3] = (byte) (x >> 24);

        int y = Float.floatToIntBits(vector.y);
        buffer[4] = (byte) (y);
        buffer[5] = (byte) (y >> 8);
        buffer[6] = (byte) (y >> 16);
        buffer[7] = (byte) (y >> 24);

        int z = Float.floatToIntBits(vector.z);
        buffer[8] = (byte) (z);
        buffer[9] = (byte) (z >> 8);
        buffer[10] = (byte) (z >> 16);
        buffer[11] = (byte) (z >> 24);

        return buffer;
    }

    public static Vector3f vec3fFromByteArray(byte[] array) {
        int valx = (array[3] & 0xFF) << 24 | (array[2] & 0xFF) << 16 | (array[1] & 0xFF) << 8 | (array[0] & 0xFF);
        float x = Float.intBitsToFloat(valx);

        int valy = (array[7] & 0xFF) << 24 | (array[6] & 0xFF) << 16 | (array[5] & 0xFF) << 8 | (array[4] & 0xFF);
        float y = Float.intBitsToFloat(valy);

        int valz = (array[11] & 0xFF) << 24 | (array[10] & 0xFF) << 16 | (array[9] & 0xFF) << 8 | (array[8] & 0xFF);
        float z = Float.intBitsToFloat(valz);

        return new Vector3f(x, y, z);
    }
}
