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
package rs.alexanderstojanovich.evgl.models;

import java.util.List;
import java.util.Queue;
import java.util.function.Predicate;
import org.joml.Vector3f;
import org.magicwerk.brownies.collections.BigList;
import org.magicwerk.brownies.collections.GapList;
import rs.alexanderstojanovich.evgl.level.CacheModule;
import rs.alexanderstojanovich.evgl.level.LevelContainer;
import rs.alexanderstojanovich.evgl.level.LightSource;
import rs.alexanderstojanovich.evgl.main.GameObject;
import rs.alexanderstojanovich.evgl.shaders.ShaderProgram;
import rs.alexanderstojanovich.evgl.util.Pair;
import rs.alexanderstojanovich.evgl.util.Vector3fUtils;

/**
 *
 * @author Alexander Stojanovich <coas91@rocketmail.com>
 */
public class Chunk implements Comparable<Chunk> { // some operations are mutually exclusive    

    // MODULATOR, DIVIDER, VISION are used in chunkCheck and for determining visible chunks
    public static final int BOUND = 512;
    public static final float VISION = 256.0f; // determines visibility
    private static final int GRID_SIZE = 4;

    public static final float STEP = 1.0f / (float) (GRID_SIZE);
    public static final int CHUNK_NUM = GRID_SIZE * GRID_SIZE;
    public static final float LENGTH = BOUND * STEP;

    // id of the chunk (signed)
    private final int id;
    private final boolean solid;

    // is a group of blocks which are prepared for instanced rendering
    // where each tuple is considered as:                
    //--------------------------MODULATOR--------DIVIDER--------VISION-------D--------E-----------------------------
    //------------------------blocks-vec4Vbos-mat4Vbos-texture-faceEnBits------------------------
    private final List<Tuple> tupleList = new GapList<>();

    private boolean buffered = false;

    private float timeToLive = LevelContainer.STD_TTL;

    public Chunk(int id, boolean solid) {
        this.id = id;
        this.solid = solid;
    }

    @Override
    public int compareTo(Chunk o) {
        return Chunks.COMPARATOR.compare(this, o);
    }

    /**
     * Binary search of the tuple. Tuples are sorted by name ascending.
     * Complexity is logarithmic.
     *
     * @param keyTexture texture name part
     * @param keyFaceBits face bits part
     * @return Tuple if found (null if not found)
     */
    public Tuple getTuple(String keyTexture, Integer keyFaceBits) {
        String keyName = String.format("%s%02d", keyTexture, keyFaceBits);
        int left = 0;
        int right = tupleList.size() - 1;
        while (left <= right) {
            int mid = left + (right - left) / 2;
            Tuple candidate = tupleList.get(mid);
            int res = candidate.getName().compareTo(keyName);
            if (res < 0) {
                left = mid + 1;
            } else if (res == 0) {
                return candidate;
            } else {
                right = mid - 1;
            }
        }
        return null;
    }

    /**
     * Gets Block from the tuple block list (duplicates may exist but in very
     * low quantity). Complexity is O(log(n)+k).
     *
     * @param tuple (chunk) tuple where block might be located
     * @param pos Vector3f position of the block
     * @return block if found (null if not found)
     */
    public static Block getBlock(Tuple tuple, Vector3f pos) {
        String keyStr = Vector3fUtils.float3ToUniqueString(pos);

        int left = 0;
        int right = tuple.blockList.size() - 1;
        int startIndex = -1;

        while (left <= right) {
            int mid = left + (right - left) / 2;
            Block candidate = tuple.blockList.get(mid);
            String candStr = Vector3fUtils.float3ToUniqueString(candidate.pos);
            int res = candStr.compareTo(keyStr);
            if (res < 0) {
                left = mid + 1;
            } else if (res == 0) {
                startIndex = mid;
                right = mid - 1;
            } else {
                right = mid - 1;
            }
        }

        left = 0;
        right = tuple.blockList.size() - 1;
        int endIndex = -1;
        while (left <= right) {
            int mid = left + (right - left) / 2;
            Block candidate = tuple.blockList.get(mid);
            String candStr = Vector3fUtils.float3ToUniqueString(candidate.pos);
            int res = candStr.compareTo(keyStr);
            if (res < 0) {
                left = mid + 1;
            } else if (res == 0) {
                endIndex = mid;
                left = mid + 1;
            } else {
                right = mid - 1;
            }
        }

        if (startIndex != -1 && endIndex != -1) {
            for (int i = startIndex; i <= endIndex; i++) {
                Block blk = tuple.blockList.get(i);
                if (blk.pos.equals(pos)) {
                    return blk;
                }
            }
        }

        return null;
    }

    /**
     * Transfer block between two tuples. Block will be transfered from tuple
     * with formFaceBits to tuple with current facebits.
     *
     * @param block block to transfer
     * @param formFaceBits face bits before
     * @param currFaceBits face bits current (after the change)
     */
    public void transfer(Block block, int formFaceBits, int currFaceBits) { // update fluids use this to transfer fluid blocks between tuples
        String texture = block.texName;

        Tuple srcTuple = getTuple(texture, formFaceBits);
        if (srcTuple != null) { // lazy aaah!
            srcTuple.getBlockList().remove(block);
            if (srcTuple.getBlockList().isEmpty()) {
                tupleList.remove(srcTuple);
            }
        }

        Tuple dstTuple = getTuple(texture, currFaceBits);
        if (dstTuple == null) {
            dstTuple = new Tuple(texture, currFaceBits);
            tupleList.add(dstTuple);
            tupleList.sort(Tuple.TUPLE_COMP);
        }
        List<Block> blockList = dstTuple.getBlockList();
        blockList.add(block);
        blockList.sort(Block.FLOAT3_BITS_COMP);

        buffered = false;
    }

    /**
     * Updates blocks faces of both original block and adjacent blocks. Block
     * must be solid.
     *
     * Used after add operation.
     *
     * @param block block to update
     */
    protected void updateSolidForAdd(Block block) {
        int faceBitsBefore = block.getFaceBits();
        Pair<String, Byte> pair = LevelContainer.ALL_SOLID_MAP.get(block.pos);
        if (pair != null) {
            byte neighborBits = pair.getValue();
            block.setFaceBits(~neighborBits & 63);
            int faceBitsAfter = block.getFaceBits();
            if (faceBitsBefore != faceBitsAfter) {
                // if bits changed, i.e. some face(s) got disabled
                // tranfer to correct tuple
                transfer(block, faceBitsBefore, faceBitsAfter);
                // check adjacent blocks
                for (int j = Block.LEFT; j <= Block.FRONT; j++) {
                    Vector3f adjPos = Block.getAdjacentPos(block.pos, j);
                    Pair<String, Byte> adjPair = LevelContainer.ALL_SOLID_MAP.get(adjPos);
                    if (adjPair != null) {
                        String tupleTexName = adjPair.getKey();
                        byte adjNBits = adjPair.getValue();
                        int k = ((j & 1) == 0 ? j + 1 : j - 1);
                        int mask = 1 << k;
                        // revert the bit that was set in LevelContainer
                        //(looking for old bits i.e. current tuple)
                        int tupleBits = adjNBits ^ (~mask & 63);

                        Tuple tuple = getTuple(tupleTexName, tupleBits);
                        Block adjBlock = null;
                        if (tuple != null) {
                            adjBlock = Chunk.getBlock(tuple, adjPos);
                        }
                        if (adjBlock != null) {
                            int adjFaceBitsBefore = adjBlock.getFaceBits();
                            adjBlock.setFaceBits(~adjNBits & 63);
                            int adjFaceBitsAfter = adjBlock.getFaceBits();
                            if (adjFaceBitsBefore != adjFaceBitsAfter) {
                                // if bits changed, i.e. some face(s) got disabled
                                // tranfer to correct tuple
                                transfer(adjBlock, adjFaceBitsBefore, adjFaceBitsAfter);
                            }
                        }
                    }
                }
            }
        }

    }

    /**
     * Updates blocks faces of both original block and adjacent blocks. Block
     * must be solid.
     *
     * Used after removal operation.
     *
     * @param block block to update
     */
    protected void updateSolidForRem(Block block) {
        // check adjacent blocks
        for (int j = Block.LEFT; j <= Block.FRONT; j++) {
            Vector3f adjPos = Block.getAdjacentPos(block.pos, j);
            Pair<String, Byte> adjPair = LevelContainer.ALL_SOLID_MAP.get(adjPos);
            if (adjPair != null) {
                String tupleTexName = adjPair.getKey();
                byte adjNBits = adjPair.getValue();
                int k = ((j & 1) == 0 ? j + 1 : j - 1);
                int mask = 1 << k;
                // revert the bit that was set in LevelContainer
                //(looking for old bits i.e. current tuple)
                int tupleBits = adjNBits ^ (~mask & 63);

                Tuple tuple = getTuple(tupleTexName, tupleBits);
                Block adjBlock = null;
                if (tuple != null) {
                    adjBlock = Chunk.getBlock(tuple, adjPos);
                }
                if (adjBlock != null) {
                    int adjFaceBitsBefore = adjBlock.getFaceBits();
                    adjBlock.setFaceBits(~adjNBits & 63);
                    int adjFaceBitsAfter = adjBlock.getFaceBits();
                    if (adjFaceBitsBefore != adjFaceBitsAfter) {
                        // if bits changed, i.e. some face(s) got disabled
                        // tranfer to correct tuple
                        transfer(adjBlock, adjFaceBitsBefore, adjFaceBitsAfter);
                    }
                }
            }
        }
    }

    /**
     * Updates blocks faces of both original block and adjacent blocks. Block
     * must be solid.
     *
     * Used after removal operation.
     *
     * @param block block to update
     */
    protected void updateFluidForRem(Block block) {
        // check adjacent blocks
        for (int j = Block.LEFT; j <= Block.FRONT; j++) {
            Vector3f adjPos = Block.getAdjacentPos(block.pos, j);
            Pair<String, Byte> adjPair = LevelContainer.ALL_FLUID_MAP.get(adjPos);
            if (adjPair != null) {
                String tupleTexName = adjPair.getKey();
                byte adjNBits = adjPair.getValue();
                int k = ((j & 1) == 0 ? j + 1 : j - 1);
                int mask = 1 << k;
                // revert the bit that was set in LevelContainer
                //(looking for old bits i.e. current tuple)
                int tupleBits = adjNBits ^ (~mask & 63);

                Tuple tuple = getTuple(tupleTexName, tupleBits);
                Block adjBlock = null;
                if (tuple != null) {
                    adjBlock = Chunk.getBlock(tuple, adjPos);
                }
                if (adjBlock != null) {
                    int adjFaceBitsBefore = adjBlock.getFaceBits();
                    adjBlock.setFaceBits(~adjNBits & 63);
                    int adjFaceBitsAfter = adjBlock.getFaceBits();
                    if (adjFaceBitsBefore != adjFaceBitsAfter) {
                        // if bits changed, i.e. some face(s) got disabled
                        // tranfer to correct tuple
                        transfer(adjBlock, adjFaceBitsBefore, adjFaceBitsAfter);
                    }
                }
            }
        }
    }

    @Deprecated
    public void updateSolids() {
        for (Block solidBlock : getBlockList()) {
            if (GameObject.MY_WINDOW.shouldClose()) {
                break;
            }

            int faceBitsBefore = solidBlock.getFaceBits();
            Pair<String, Byte> pair = LevelContainer.ALL_SOLID_MAP.get(solidBlock.pos);
            if (pair != null) {
                byte neighborBits = pair.getValue();
                solidBlock.setFaceBits(~neighborBits & 63);
                int faceBitsAfter = solidBlock.getFaceBits();
                if (faceBitsBefore != faceBitsAfter) { // if bits changed, i.e. some face(s) got disabled
                    transfer(solidBlock, faceBitsBefore, faceBitsAfter);
                }
            }
        }
    }

    /**
     * Updates blocks faces of both original block and adjacent blocks. Block
     * must be fluid. Removal is used only if block is being removed.
     *
     * @param block block to update
     */
    protected void updateFluidForAdd(Block block) {
        int faceBitsBefore = block.getFaceBits();
        Pair<String, Byte> pair = LevelContainer.ALL_FLUID_MAP.get(block.pos);
        if (pair != null) {
            byte neighborBits = pair.getValue();
            block.setFaceBits(~neighborBits & 63);
            int faceBitsAfter = block.getFaceBits();
            if (faceBitsBefore != faceBitsAfter) {
                // if bits changed, i.e. some face(s) got disabled
                // tranfer to correct tuple
                transfer(block, faceBitsBefore, faceBitsAfter);
                // check adjacent blocks
                for (int j = Block.LEFT; j <= Block.FRONT; j++) {
                    Vector3f adjPos = Block.getAdjacentPos(block.pos, j);
                    Pair<String, Byte> adjPair = LevelContainer.ALL_FLUID_MAP.get(adjPos);
                    if (adjPair != null) {
                        String tupleTexName = adjPair.getKey();
                        byte adjNBits = adjPair.getValue();
                        int k = ((j & 1) == 0 ? j + 1 : j - 1);
                        int mask = 1 << k;
                        // revert the bit that was set in LevelContainer
                        //(looking for old bits i.e. current tuple)
                        int tupleBits = adjNBits ^ (~mask & 63);

                        Tuple tuple = getTuple(tupleTexName, tupleBits);
                        Block adjBlock = null;
                        if (tuple != null) {
                            adjBlock = Chunk.getBlock(tuple, adjPos);
                        }
                        if (adjBlock != null) {
                            int adjFaceBitsBefore = adjBlock.getFaceBits();
                            adjBlock.setFaceBits(~adjNBits & 63);
                            int adjFaceBitsAfter = adjBlock.getFaceBits();
                            if (adjFaceBitsBefore != adjFaceBitsAfter) {
                                // if bits changed, i.e. some face(s) got disabled
                                // tranfer to correct tuple
                                transfer(adjBlock, adjFaceBitsBefore, adjFaceBitsAfter);
                            }
                        }
                    }
                }
            }
        }

    }

    @Deprecated
    public void updateFluids() {
        for (Block fluidBlock : getBlockList()) {
            if (GameObject.MY_WINDOW.shouldClose()) {
                break;
            }

            int faceBitsBefore = fluidBlock.getFaceBits();
            Pair<String, Byte> pair = LevelContainer.ALL_FLUID_MAP.get(fluidBlock.pos);
            if (pair != null) {
                byte neighborBits = pair.getValue();
                fluidBlock.setFaceBits(~neighborBits & 63);
                int faceBitsAfter = fluidBlock.getFaceBits();
                if (faceBitsBefore != faceBitsAfter) { // if bits changed, i.e. some face(s) got disabled
                    transfer(fluidBlock, faceBitsBefore, faceBitsAfter);
                }
            }
        }
    }

    /**
     * Add block to the chunk.
     *
     * @param block block to add
     * @param useLevelContainer update level container environment map (for
     * adjacency)
     */
    public void addBlock(Block block, boolean useLevelContainer) {
        String blockTexture = block.texName;
        int blockFaceBits = block.getFaceBits();
        Tuple tuple = getTuple(blockTexture, blockFaceBits);

        if (tuple == null) {
            tuple = new Tuple(blockTexture, blockFaceBits);
            tupleList.add(tuple);
            tupleList.sort(Tuple.TUPLE_COMP);
        }

        List<Block> blockList = tuple.getBlockList();
        blockList.add(block);
        blockList.sort(Block.FLOAT3_BITS_COMP);

        if (useLevelContainer) {
            // level container also set neighbor bits
            LevelContainer.putBlock(block);
            // update original block with neighbor blocks
            if (solid) {
                LightSource lightSource = new LightSource(block.pos, block.primaryColor, 1.0f);
                if (block.getTexName().equals("reflc")
                        && !LevelContainer.LIGHT_SRC.contains(lightSource)) {
                    LevelContainer.LIGHT_SRC.add(lightSource);
                }
                updateSolidForAdd(block);
            } else {
                updateFluidForAdd(block);
            }
        }

        buffered = false;
    }

    /**
     * Remove block from the chunk.
     *
     * @param block block to remove
     * @param useLevelContainer update level container environment map (for
     * adjacency)
     */
    public void removeBlock(Block block, boolean useLevelContainer) {
        String blockTexture = block.texName;
        int blockFaceBits = block.getFaceBits();
        Tuple target = getTuple(blockTexture, blockFaceBits);
        if (target != null) {
            target.getBlockList().remove(block);
            buffered = false;
            // if tuple has no blocks -> remove it
            if (target.getBlockList().isEmpty()) {
                tupleList.remove(target);
            }

            if (useLevelContainer) {
                // level container also set neighbor bits
                LevelContainer.removeBlock(block);
                // update original block with neighbor blocks
                if (solid) {
                    // check if it's light block
                    LightSource lightSource = new LightSource(block.pos, block.primaryColor, 1.0f);
                    if (block.getTexName().equals("reflc")
                            && LevelContainer.LIGHT_SRC.contains(lightSource)) {
                        LevelContainer.LIGHT_SRC.remove(lightSource);
                    }
                    updateSolidForRem(block);
                } else {
                    updateFluidForRem(block);
                }
            }
        }
    }

    // hint that stuff should be buffered again
    public void unbuffer() {
        if (!CacheModule.isCached(id, solid)) {
            buffered = false;
        }
    }

    // renderer does this stuff prior to any rendering
    public void bufferAll() {
        if (!CacheModule.isCached(id, solid)) {
            for (Tuple tuple : tupleList) {
                tuple.bufferAll();
            }
            buffered = true;
        }
    }

    public void animate() { // call only for fluid blocks
        for (Tuple tuple : tupleList) {
            tuple.animate();
        }
    }

    public void prepare(boolean cameraInFluid) { // call only for fluid blocks before rendering        
        for (Tuple tuple : tupleList) {
            tuple.prepare(cameraInFluid);
        }
    }

    // it renders all of them instanced if they're visible
    public void render(ShaderProgram shaderProgram, List<LightSource> lightSrc) {
        if (buffered && shaderProgram != null && !tupleList.isEmpty() && timeToLive > 0) {
            for (Tuple tuple : tupleList) {
                tuple.render(shaderProgram, lightSrc);
            }
        }
    }

    public void renderIf(ShaderProgram shaderProgram, List<LightSource> lightSrc, Predicate<Block> predicate) {
        if (buffered && shaderProgram != null && !tupleList.isEmpty() && timeToLive > 0) {
            for (Tuple tuple : tupleList) {
                tuple.renderIf(shaderProgram, lightSrc, predicate);
            }
        }
    }

    // deallocates Chunk from graphic card
    @Deprecated
    public void release() {
        if (!CacheModule.isCached(id, solid)) {
            //--------------------------MODULATOR--------DIVIDER--------VISION-------D--------E-----------------------------
            //------------------------blocks-vec4Vbos-mat4Vbos-texture-faceEnBits------------------------
            for (Tuple tuple : tupleList) {
                tuple.release();
            }
            buffered = false;
        }
    }

    /**
     * Calculate chunk based on position.
     *
     * @param pos position of the thing (critter or object)
     * @return chunk number (grid size based)
     */
    public static int chunkFunc(Vector3f pos) {
        // normalized x & z
        float nx = (pos.x + BOUND) / (float) (BOUND << 1);
        float nz = (pos.z + BOUND) / (float) (BOUND << 1);

        // check which column of the interval
        int col = Math.round(nx * (1.0f / STEP - 1.0f));

        // check which rows of the interval
        int row = Math.round(nz * (1.0f / STEP - 1.0f));

        // determining chunk id -> row(z) & col(x)
        int cid = row * GRID_SIZE + col;

        return cid;
    }

    /**
     * Calculate position centroid based on the chunk Id
     *
     * @param chunkId chunk number
     *
     * @return chunk middle position
     */
    public static Vector3f invChunkFunc(int chunkId) {
        // determining row(z) & col(x)
        int col = chunkId % GRID_SIZE;
        int row = chunkId / GRID_SIZE;

        // calculating middle normalized
        // col * STEP + STEP / 2.0f;
        // row * STEP + STEP / 2.0f;
        float nx = STEP * (col + 0.5f);
        float nz = STEP * (row + 0.5f);

        float x = nx * (BOUND << 1) - BOUND;
        float z = nz * (BOUND << 1) - BOUND;

        return new Vector3f(x, 0.0f, z);
    }

    // determine which chunks are visible by this chunk
    public static void determineVisible(Queue<Integer> vChnkIdQueue, Queue<Integer> iChnkIdQueue, Vector3f actorPos) {
        vChnkIdQueue.clear();
        iChnkIdQueue.clear();
        // current chunk where player is        
        int currChunkId = chunkFunc(actorPos);
        Vector3f currChunkPos = invChunkFunc(currChunkId);
        float distance0 = actorPos.distance(currChunkPos);
        if (!vChnkIdQueue.contains(currChunkId)) {
            vChnkIdQueue.offer(currChunkId);
        }
        // rest of the chunks
        for (int chunkId = 0; chunkId < Chunk.CHUNK_NUM; chunkId++) {
            if (chunkId != currChunkId) {
                Vector3f chunkPos = invChunkFunc(chunkId);
                float distance1 = actorPos.distance(chunkPos);
                if (distance1 - distance0 <= LENGTH) {
                    vChnkIdQueue.offer(chunkId);
                } else if (!iChnkIdQueue.contains(chunkId)) {
                    iChnkIdQueue.offer(chunkId);
                }
            }
        }
    }

    public List<Block> getBlockList() {
        List<Block> result = new BigList<>();
        for (Tuple tuple : tupleList) {
            result.addAll(tuple.getBlockList());
        }
        return result;
    }

    public int getId() {
        return id;
    }

    public boolean isSolid() {
        return solid;
    }

    public List<Tuple> getTupleList() {
        return tupleList;
    }

    public boolean isBuffered() {
        return buffered;
    }

    public void setBuffered(boolean buffered) {
        this.buffered = buffered;
    }

    public float getTimeToLive() {
        return timeToLive;
    }

    public void setTimeToLive(float timeToLive) {
        this.timeToLive = timeToLive;
    }

    public void decTimeToLive(float timeDec) {
        this.timeToLive -= timeDec;
        if (this.timeToLive < 0.0f) {
            this.timeToLive = 0.0f;
        }
    }

    public boolean isAlive() {
        return timeToLive > 0.0f;
    }

}
