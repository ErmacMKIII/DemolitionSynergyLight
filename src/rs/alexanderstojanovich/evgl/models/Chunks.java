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

import java.util.Comparator;
import java.util.List;
import java.util.Queue;
import java.util.function.Predicate;
import org.joml.Vector3f;
import org.magicwerk.brownies.collections.BigList;
import org.magicwerk.brownies.collections.GapList;
import rs.alexanderstojanovich.evgl.level.LevelContainer;
import rs.alexanderstojanovich.evgl.main.GameObject;
import rs.alexanderstojanovich.evgl.shaders.ShaderProgram;
import rs.alexanderstojanovich.evgl.util.DSLogger;
import rs.alexanderstojanovich.evgl.util.Pair;

/**
 *
 * @author Alexander Stojanovich <coas91@rocketmail.com>
 */
public class Chunks {

    private final boolean solid;
    // single mat4Vbo is for model matrix shared amongst the vertices of the same instance    
    // single vec4Vbo is color shared amongst the vertices of the same instance    
    //--------------------------A--------B--------C-------D--------E-----------------------------
    //------------------------blocks-vec4Vbos-mat4Vbos-texture-faceEnBits------------------------
    private final List<Chunk> chunkList = new GapList<>();

    public Chunks(boolean solid) {
        this.solid = solid;
    }

    public static final Comparator<Chunk> COMPARATOR = new Comparator<Chunk>() {
        @Override
        public int compare(Chunk o1, Chunk o2) {
            if (o1.getId() > o2.getId()) {
                return 1;
            } else if (o1.getId() == o2.getId()) {
                return 0;
            } else {
                return -1;
            }
        }
    };

    @Deprecated
    public void updateSolids() {
        for (Block solidBlock : getTotalList()) {
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
                    int chunkId = Chunk.chunkFunc(solidBlock.getPos());
                    Chunk solidChunk = getChunk(chunkId);
                    if (solidChunk != null) {
                        solidChunk.transfer(solidBlock, faceBitsBefore, faceBitsAfter);
                    }
                }
            }
        }
    }

    @Deprecated
    public void updateFluids() {
        for (Block fluidBlock : getTotalList()) {
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
                    int chunkId = Chunk.chunkFunc(fluidBlock.getPos());
                    Chunk fluidChunk = getChunk(chunkId);
                    if (fluidChunk != null) {
                        fluidChunk.transfer(fluidBlock, faceBitsBefore, faceBitsAfter);
                    }
                }
            }
        }
    }

    /**
     * Adds block to the chunks. Block will be added to the corresponding solid
     * chunk based on Chunk.chunkFunc
     *
     * @param block block to add
     * @param useLevelContainer update level container environment map (for
     * adjacency)
     */
    public void addBlock(Block block, boolean useLevelContainer) {
        //----------------------------------------------------------------------
        int chunkId = Chunk.chunkFunc(block.pos);
        Chunk chunk = getChunk(chunkId);

        if (chunk == null) {
            chunk = new Chunk(chunkId, block.solid);
            chunkList.add(chunk);
            chunkList.sort(COMPARATOR);
        }

        chunk.addBlock(block, useLevelContainer);
    }

    /**
     * Removes block from the chunks. Block will be located based on
     * Chunk.chunkFunc and then removed if exits.
     *
     * @param block block to remove
     * @param useLevelContainer update level container environment map (for
     * adjacency)
     */
    public void removeBlock(Block block, boolean useLevelContainer) {
        int chunkId = Chunk.chunkFunc(block.pos);
        Chunk chunk = getChunk(chunkId);

        if (chunk != null) { // if chunk exists already                            
            chunk.removeBlock(block, useLevelContainer);
            // if chunk is empty (with no tuples) -> remove it
            if (chunk.getTupleList().isEmpty()) {
                chunkList.remove(chunk);
            }
        }
    }

// linear search through chunkList to get the chunk
//    public Chunk getChunk(int chunkId) { 
//        Chunk result = null;
//        for (Chunk chunk : chunkList) {
//            if (chunk.getId() == chunkId) {
//                result = chunk;
//                break;
//            }
//        }
//        return result;
//    }
    // (logaritmic) binary search through sorted chunkList to get the chunk
    public Chunk getChunk(int chunkId) {
        int left = 0;
        int right = chunkList.size() - 1;
        while (left <= right) {
            int mid = left + (right - left) / 2;
            Chunk candidate = chunkList.get(mid);
            if (candidate.getId() == chunkId) {
                return candidate;
            } else if (candidate.getId() < chunkId) {
                left = mid + 1;
            } else {
                right = mid - 1;
            }
        }
        return null;
    }

    public void animate() { // call only for fluid blocks
        for (Chunk chunk : getChunkList()) {
            if (!Chunk.isCached(chunk.getId(), solid) && chunk.isBuffered()) {
                chunk.animate();
            }
        }
    }

    public void prepare(boolean cameraInFluid) { // call only for fluid blocks before rendering        
        for (Chunk chunk : chunkList) {
            chunk.prepare(cameraInFluid);
        }
    }

    // for each instanced rendering
    public void render(ShaderProgram shaderProgram, List<Vector3f> lightSrc) {
        for (Chunk chunk : chunkList) {
            if (!chunk.isBuffered()) {
                chunk.bufferAll();
            }
            chunk.render(shaderProgram, lightSrc);
        }
    }

    // for each instanced rendering
    public void renderIf(ShaderProgram shaderProgram, List<Vector3f> lightSrc, Predicate<Block> predicate) {
        for (Chunk chunk : chunkList) {
            if (!chunk.isBuffered()) {
                chunk.bufferAll();
            }
            chunk.renderIf(shaderProgram, lightSrc, predicate);
        }
    }

    // very useful -> it should be like this initially
    @Deprecated
    public void saveAllToDisk() {
        for (Chunk chunk : chunkList) {
            chunk.saveToDisk();
        }
    }

    // variation on the topic
    public void saveToDisk(Queue<Pair<Integer, Float>> chunkQueue) {
        for (Pair<Integer, Float> pair : chunkQueue) {
            int chunkId = pair.getKey();
            Chunk chunk = getChunk(chunkId);
            if (chunk != null) {
                chunk.saveToDisk();
            }
        }
    }

    // useful when saving and wanna load everything into memory
    @Deprecated
    public void loadAllFromDisk() {
        for (Chunk chunk : chunkList) {
            chunk.loadFromDisk();
        }
    }

    // variation on the topic
    public void loadFromDisk(Queue<Pair<Integer, Float>> chunkQueue) {
        for (Pair<Integer, Float> pair : chunkQueue) {
            int chunkId = pair.getKey();
            Chunk chunk = getChunk(chunkId);
            if (chunk != null) {
                chunk.loadFromDisk();
            }
        }
    }

    // total loaded + cached size
    public int totalSize() {
        int result = 0;
        for (int id = 0; id < Chunk.CHUNK_NUM; id++) {
            Chunk chunk;
            if (Chunk.isCached(id, solid)) {
                result += Chunk.cachedSize(id, solid);
            } else {
                chunk = getChunk(id);
                if (chunk != null) {
                    result += chunk.loadedSize();
                }
            }
        }
        return result;
    }

    // all blocks from all the chunks in one big list
    public List<Block> getTotalList() {
        List<Block> result = new BigList<>();
        for (int id = 0; id < Chunk.CHUNK_NUM; id++) {
            Chunk chunk;
            if (Chunk.isCached(id, solid)) {
                chunk = Chunk.loadFromDisk(id, solid);
                result.addAll(chunk.getBlockList());
                chunk.saveToDisk();
            } else {
                chunk = getChunk(id);
                if (chunk != null) {
                    result.addAll(chunk.getBlockList());
                }
            }
        }
        return result;
    }

    public void printInfo() { // for debugging purposes
        StringBuilder sb = new StringBuilder();
        sb.append("CHUNKS\n");
        sb.append("CHUNKS TOTAL SIZE = ").append(totalSize()).append("\n");
        sb.append("DETAILED INFO\n");
        for (int id = 0; id < Chunk.CHUNK_NUM; id++) {
            boolean cached = Chunk.isCached(id, solid);
            Chunk chunk = null;
            if (!cached) {
                chunk = getChunk(id);
            }

            sb.append("id = ").append(id)
                    .append(" | solid = ").append(solid)
                    .append(" | size = ").append((!cached && chunk != null) ? chunk.loadedSize() : Chunk.cachedSize(id, solid))
                    .append(" | timeToLive = ").append((chunk != null) ? chunk.getTimeToLive() : 0)
                    .append(" | buffered = ").append((chunk != null) ? chunk.isBuffered() : false)
                    .append(" | cached = ").append(cached)
                    .append("\n");
        }
        sb.append("------------------------------------------------------------");
        DSLogger.reportInfo(sb.toString(), null);
    }

    public List<Chunk> getChunkList() {
        return chunkList;
    }

}
