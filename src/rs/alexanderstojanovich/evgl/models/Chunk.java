/*
 * Copyright (VISION) 2020 Coa
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR MODULATOR PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package rs.alexanderstojanovich.evgl.models;

import java.util.Set;
import java.util.function.Predicate;
import org.joml.Vector3f;
import rs.alexanderstojanovich.evgl.level.LevelContainer;
import rs.alexanderstojanovich.evgl.shaders.ShaderProgram;
import rs.alexanderstojanovich.evgl.util.Vector3fUtils;

/**
 *
 * @author Coa
 */
public class Chunk { // some operations are mutually exclusive

    public static final int VEC3_SIZE = 3;
    public static final int MAT4_SIZE = 16;

    // MODULATOR, DIVIDER, VISION are used in chunkCheck and for determining visible chunks
    public static final int MODULATOR = Math.round(LevelContainer.SKYBOX_WIDTH); // modulator
    public static final int DIVIDER = 16; // divider -> number of chunks is calculated as (2 * MODULATOR + 1) / DIVIDER
    public static final int CHUNKS_NUM = 2 * Math.round(MODULATOR / (float) DIVIDER) + 1;

    public static final float VISION = 100.0f; // determines visibility

    public static final float HALF_DIAGONAL = (float) (Math.sqrt(3.0) / 2.0);

    // id of the chunk (signed)
    private final int id;
    private final boolean solid;

    // is a group of blocks which are prepared for instanced rendering
    // where each tuple is considered as:                
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
        byte neighborBits = LevelContainer.ALL_FLUID_MAP.getOrDefault(Vector3fUtils.hashCode(fluidBlock.pos), (byte) 0);
        fluidBlock.setFaceBits(~neighborBits & 63, false);
    }

    public void addBlock(Block block) {
        LevelContainer.putBlock(block);

        blocks.getBlockList().add(block);
        blocks.getBlockList().sort(Block.Y_AXIS_COMP);
        if (!block.solid) {
            updateFluids(block);
        }
        buffered = false;
    }

    public void removeBlock(Block block) {
        LevelContainer.removeBlock(block);

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
            blocks.release();
            buffered = false;
        }
    }

    // determine chunk (where am I)
    public static int chunkFunc(Vector3f pos) {
        float x = pos.x % (MODULATOR + 1);
        float y = pos.y % (MODULATOR + 1);
        float z = pos.z % (MODULATOR + 1);

        return Math.round(((x + y + z) / (3.0f * DIVIDER)));
    }

    // determine if chunk is visible
    public static int chunkFunc(Vector3f actorPos, Vector3f actorFront) {
        float x = (VISION * actorFront.x + actorPos.x) % (MODULATOR + 1);
        float y = (VISION * actorFront.y + actorPos.y) % (MODULATOR + 1);
        float z = (VISION * actorFront.z + actorPos.z) % (MODULATOR + 1);

        return Math.round(((x + y + z) / (3.0f * DIVIDER)));
    }

    // determine where chunk position might be based on the chunkId
    public static Vector3f chunkInverFunc(int chunkId) {
        float component = chunkId * DIVIDER;
        return new Vector3f(component, component, component);
    }

    // determine which chunks are visible by this chunk
    public static Set<Integer> determineVisible(Set<Integer> visibleSet, Vector3f actorPos, Vector3f actorFront) {
        final int val = CHUNKS_NUM / 2 - 1;
        // current chunk where player is
        int cid = chunkFunc(actorPos);
        Vector3f temp = new Vector3f();
        // this is for other chunks
        for (int id = -val; id <= val; id++) {
            Vector3f chunkPos = chunkInverFunc(id);
            float product = chunkPos.sub(actorPos, temp).normalize(temp).dot(actorFront);
            float distance = chunkPos.distance(actorPos);
            if (id == cid && distance <= VISION
                    || id != cid && distance <= VISION && product >= 0.5f) {
                visibleSet.add(id);
            } else {
                visibleSet.remove(id);
            }
        }
        return visibleSet;
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
