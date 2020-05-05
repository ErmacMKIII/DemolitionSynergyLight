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
package rs.alexanderstojanovich.evgl.models;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import org.joml.Vector3f;
import rs.alexanderstojanovich.evgl.level.LevelContainer;
import rs.alexanderstojanovich.evgl.shaders.ShaderProgram;
import rs.alexanderstojanovich.evgl.texture.Texture;

/**
 *
 * @author Coa
 */
public class Chunk {

    public static final int VEC4_SIZE = 4;
    public static final int MAT4_SIZE = 16;

    // A, B, C are used in chunkFunc and for determining visible chunks
    public static final int A = Math.round(LevelContainer.SKYBOX_WIDTH); // modulator
    public static final int B = A >> 4; // divider    
    public static final float C = 1.5f * B; // determines visibility

    // id of the chunk (signed)
    private final int id;
    // is a group of blocks which are prepared for instanced rendering
    // where each tuple is considered as:                
    //--------------------------A--------B--------C-------D--------E-----------------------------
    //------------------------blocks-vec4Vbos-mat4Vbos-texture-faceEnBits------------------------
    private final Blocks blocks = new Blocks();

    private Texture waterTexture;

    private boolean buffered = false;

    private boolean visible = false;

    public Chunk(int id) {
        this.id = id;
    }

    public void bufferAll() {
        blocks.bufferAll();
        buffered = true;
    }

    public void animate() { // call only for fluid blocks
        blocks.animate();
    }

    public void prepare() { // call only for fluid blocks before rendering        
        blocks.prepare();
    }

    // it renders all of them instanced if they're visible
    public void render(ShaderProgram shaderProgram, Vector3f lightSrc) {
        if (shaderProgram != null) {
            blocks.render(shaderProgram, lightSrc);
        }
    }

    // it renders all of them instanced if they're visible
    public void renderIf(ShaderProgram shaderProgram, Vector3f lightSrc, Predicate<Block> predicate) {
        if (shaderProgram != null) {
            blocks.renderIf(shaderProgram, lightSrc, predicate);
        }
    }

    // determine chunk
    public static int chunkFunc(Vector3f pos) {
        float x = Math.round((pos.x % A) / B);
        float y = Math.round((pos.y % A) / B);
        float z = Math.round((pos.z % A) / B);

        return Math.round(((x + y + z) / 3.0f));
    }

    // determine chunk
    public static int chunkFunc(Vector3f pos, Vector3f front) {
        float x = Math.round(((pos.x + pos.length() * front.x) % A) / B);
        float y = Math.round(((pos.y + pos.length() * front.y) % A) / B);
        float z = Math.round(((pos.z + pos.length() * front.z) % A) / B);

        return Math.round(((x + y + z) / 3.0f));
    }

    // determine which chunks are visible by this chunk
    public static List<Integer> determineVisible(Vector3f pos, Vector3f front) {
        List<Integer> result = new ArrayList<>();
        Vector3f va = new Vector3f();
        pos.add(C, 0.0f, 0.0f, va);
        int a = chunkFunc(va, front);
        if (!result.contains(a)) {
            result.add(a);
        }

        Vector3f vb = new Vector3f();
        pos.add(0.0f, C, 0.0f, vb);
        int b = chunkFunc(vb, front);
        if (!result.contains(b)) {
            result.add(b);
        }

        Vector3f vc = new Vector3f();
        pos.add(0.0f, C, 0.0f, vc);
        int c = chunkFunc(vc, front);
        if (!result.contains(c)) {
            result.add(c);
        }

        Vector3f vd = new Vector3f();
        pos.add(-C, 0.0f, 0.0f, vd);
        int d = chunkFunc(vd, front);
        if (!result.contains(d)) {
            result.add(d);
        }

        Vector3f ve = new Vector3f();
        pos.add(0.0f, -C, 0.0f, ve);
        int e = chunkFunc(ve, front);
        if (!result.contains(e)) {
            result.add(e);
        }

        Vector3f vf = new Vector3f();
        pos.add(0.0f, 0.0f, -C, vf);
        int f = chunkFunc(vf, front);
        if (!result.contains(f)) {
            result.add(f);
        }

        return result;
    }

    public void release() {
        blocks.release();
    }

    public int size() { // for debugging purposes       
        return blocks.getBlockList().size();
    }

    public int getId() {
        return id;
    }

    public Blocks getBlocks() {
        return blocks;
    }

    public Texture getWaterTexture() {
        return waterTexture;
    }

    public void setWaterTexture(Texture waterTexture) {
        this.waterTexture = waterTexture;
    }

    public boolean isBuffered() {
        return buffered;
    }

    public void setBuffered(boolean buffered) {
        this.buffered = buffered;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

}
