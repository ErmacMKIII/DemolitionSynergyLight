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
import org.joml.Intersectionf;
import org.joml.Vector2f;
import org.joml.Vector3f;
import rs.alexanderstojanovich.evgl.level.LevelContainer;
import rs.alexanderstojanovich.evgl.shaders.ShaderProgram;
import rs.alexanderstojanovich.evgl.util.Vector3fUtils;

/**
 *
 * @author Coa
 */
public class Chunk {

    public static final int VEC3_SIZE = 3;
    public static final int MAT4_SIZE = 16;

    // A, B, C are used in chunkCheck and for determining visible chunks
    public static final int A = Math.round(LevelContainer.SKYBOX_WIDTH); // modulator
    public static final int B = 16; // divider (number of chunks is calculated as 2 * B + 1)   
    public static final float C = 100.0f; // determines visibility

    // id of the chunk (signed)
    private final int id;
    private final boolean solid;

    // is a group of blocks which are prepared for instanced rendering
    // where each tuple is considered as:                
    //--------------------------A--------B--------C-------D--------E-----------------------------
    //------------------------blocks-vec4Vbos-mat4Vbos-texture-faceEnBits------------------------
    private final Blocks blocks = new Blocks();

    private boolean buffered = false;

    private boolean visible = false;

    private final byte[] memory = new byte[0x100000];
    private int pos = 0;
    private boolean cached = false;

    public Chunk(int id, boolean solid) {
        this.id = id;
        this.solid = solid;
    }

    private void updateFluids(Block fluidBlock) { // call only for fluid blocks after adding
        for (int j = 0; j <= 5; j++) { // j - face number
            if (LevelContainer.ALL_FLUID_POS.contains(Block.getAdjacentPos(fluidBlock.getPos(), j))) {
                fluidBlock.disableFace(j, false);
            }
        }
        buffered = false;
    }

    public void addBlock(Block block) {
        if (block.solid) {
            LevelContainer.ALL_SOLID_POS.add(new Vector3f(block.pos));
        } else {
            LevelContainer.ALL_FLUID_POS.add(new Vector3f(block.pos));
        }

        blocks.getBlockList().add(block);
        blocks.getBlockList().sort(Block.Y_AXIS_COMP);
        if (!block.solid) {
            updateFluids(block);
        }
        buffered = false;
    }

    public void removeBlock(Block block) {
        if (block.solid) {
            LevelContainer.ALL_SOLID_POS.remove(block.pos);
        } else {
            LevelContainer.ALL_FLUID_POS.remove(block.pos);
        }
        blocks.getBlockList().remove(block);
        if (!block.solid) {
            updateFluids(block);
        }
        buffered = false;
    }

    public void bufferAll() {
        if (!cached) {
            blocks.bufferAll();
            buffered = true;
        }
    }

    public void animate() { // call only for fluid blocks
        blocks.animate();
    }

    public void prepare() { // call only for fluid blocks before rendering        
        blocks.prepare();
    }

    // set camera in fluid for underwater effects (call only for fluid)
    public void setCameraInFluid(boolean cameraInFluid) {
        blocks.setCameraInFluid(cameraInFluid);
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

    // deallocates Chunk from graphic card
    public void release() {
        if (buffered && !cached) {
            //--------------------------A--------B--------C-------D--------E-----------------------------
            //------------------------blocks-vec4Vbos-mat4Vbos-texture-faceEnBits------------------------
            blocks.release();
            buffered = false;
        }
    }

    // determine chunk (where am I)
    public static int chunkFunc(Vector3f pos) {
        float x = Math.round((pos.x % (A + 1)));
        float y = Math.round((pos.y % (A + 1)));
        float z = Math.round((pos.z % (A + 1)));

        return Math.round(((x + y + z) / (3.0f * B)));
    }

    // determine if chunk is visible
    public static boolean chunkCheck(Vector3f pos, Vector3f front) {
        boolean yea = false;

        float d = C / 2.0f;
        Vector3f temp1 = new Vector3f();
        Vector3f min = pos.sub(d, d, d, temp1);
        Vector3f temp2 = new Vector3f();
        Vector3f max = pos.add(d, d, d, temp2);
        Vector2f result = new Vector2f();
        boolean ints = Intersectionf.intersectRayAab(pos, front, min, max, result);
        if (ints && result.x <= C) {
            yea = true;
        }
        return yea;
    }

    // determine which chunks are visible by this chunk
    public static List<Integer> determineVisible(Vector3f pos, Vector3f front) {
        List<Integer> result = new ArrayList<>();

        int x = Chunk.chunkFunc(pos);
        if (!result.contains(x) && chunkCheck(pos, front)) {
            result.add(x);
        }

        Vector3f va = new Vector3f();
        pos.add(C, 0.0f, 0.0f, va);
        int a = Chunk.chunkFunc(va);
        if (!result.contains(a) && Chunk.chunkCheck(va, front)) {
            result.add(a);
        }

        Vector3f vb = new Vector3f();
        pos.add(0.0f, C, 0.0f, vb);
        int b = Chunk.chunkFunc(vb);
        if (!result.contains(b) && Chunk.chunkCheck(vb, front)) {
            result.add(b);
        }

        Vector3f vc = new Vector3f();
        pos.add(0.0f, C, 0.0f, vc);
        int c = Chunk.chunkFunc(vc);
        if (!result.contains(c) && Chunk.chunkCheck(vc, front)) {
            result.add(c);
        }

        Vector3f vd = new Vector3f();
        pos.add(-C, 0.0f, 0.0f, vd);
        int d = Chunk.chunkFunc(vd);
        if (!result.contains(d) && Chunk.chunkCheck(vd, front)) {
            result.add(d);
        }

        Vector3f ve = new Vector3f();
        pos.add(0.0f, -C, 0.0f, ve);
        int e = Chunk.chunkFunc(ve);
        if (!result.contains(e) && Chunk.chunkCheck(ve, front)) {
            result.add(e);
        }

        Vector3f vf = new Vector3f();
        pos.add(0.0f, 0.0f, -C, vf);
        int f = Chunk.chunkFunc(vf);
        if (!result.contains(f) && Chunk.chunkCheck(vf, front)) {
            result.add(f);
        }

        return result;
    }

    public int size() { // for debugging purposes
        int size = 0;
        if (cached) {
            size = (pos + 1 - 3) / 29;
        } else {
            size += blocks.getBlockList().size();
        }
        return size;
    }

    public void saveToMemory() {
        if (!buffered && !cached) {
            pos = 0;
            memory[pos++] = (byte) id;
            memory[pos++] = (byte) blocks.getBlockList().size();
            memory[pos++] = (byte) (blocks.getBlockList().size() >> 8);
            for (Block block : blocks.getBlockList()) {
                byte[] texName = block.texName.getBytes();
                System.arraycopy(texName, 0, memory, pos, 5);
                pos += 5;
                byte[] solidPos = Vector3fUtils.vec3fToByteArray(block.getPos());
                System.arraycopy(solidPos, 0, memory, pos, solidPos.length);
                pos += solidPos.length;
                Vector3f primCol = block.getPrimaryColor();
                byte[] solidCol = Vector3fUtils.vec3fToByteArray(primCol);
                System.arraycopy(solidCol, 0, memory, pos, solidCol.length);
                pos += solidCol.length;
            }
            blocks.getBlockList().clear();
            cached = true;
        }
    }

    public void loadFromMemory() {
        if (!buffered && cached) {
            pos = 1;
            int len = ((memory[pos + 1] & 0xFF) << 8) | (memory[pos] & 0xFF);
            pos += 2;
            for (int i = 0; i < len; i++) {
                char[] texNameArr = new char[5];
                for (int k = 0; k < texNameArr.length; k++) {
                    texNameArr[k] = (char) memory[pos++];
                }
                String texName = String.valueOf(texNameArr);

                byte[] blockPosArr = new byte[12];
                System.arraycopy(memory, pos, blockPosArr, 0, blockPosArr.length);
                Vector3f blockPos = Vector3fUtils.vec3fFromByteArray(blockPosArr);
                pos += blockPosArr.length;

                byte[] blockPosCol = new byte[12];
                System.arraycopy(memory, pos, blockPosCol, 0, blockPosCol.length);
                Vector3f blockCol = Vector3fUtils.vec3fFromByteArray(blockPosCol);
                pos += blockPosCol.length;

                Block block = new Block(false, texName, blockPos, blockCol, solid);
                addBlock(block);
            }
            cached = false;
        }
    }

    public int getId() {
        return id;
    }

    public boolean isSolid() {
        return solid;
    }

    public Blocks getBlocks() {
        return blocks;
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

    public byte[] getMemory() {
        return memory;
    }

    public int getPos() {
        return pos;
    }

    public boolean isCached() {
        return cached;
    }

}
