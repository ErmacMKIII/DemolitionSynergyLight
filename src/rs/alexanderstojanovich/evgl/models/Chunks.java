/*
 * Copyright (C) 2019 Coa
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import org.joml.Vector3f;
import org.magicwerk.brownies.collections.GapList;
import rs.alexanderstojanovich.evgl.level.LevelContainer;
import rs.alexanderstojanovich.evgl.shaders.ShaderProgram;

/**
 *
 * @author Coa
 */
public class Chunks {

    private boolean buffered = false;
    // single mat4Vbo is for model matrix shared amongst the vertices of the same instance    
    // single vec4Vbo is color shared amongst the vertices of the same instance    
    //--------------------------A--------B--------C-------D--------E-----------------------------
    //------------------------blocks-vec4Vbos-mat4Vbos-texture-faceEnBits------------------------
    private final List<Chunk> chunkList = new GapList<>();

    //----------------Vector3f hash, Block hash---------------------------------
    private final Map<Vector3f, Integer> posMap = new HashMap<>();

    // for both internal (Init) and external use (Editor)
    public void addBlock(Block block) {
        posMap.put(block.getPos(), block.hashCode());

        //----------------------------------------------------------------------
        int chunkId = Chunk.chunkFunc(block.pos);
        Chunk chunk = getChunk(chunkId);

        if (chunk == null) {
            chunk = new Chunk(chunkId);
            chunkList.add(chunk);
        }

        chunk.getBlocks().getBlockList().add(block);
        chunk.getBlocks().getBlockList().sort(Block.Y_AXIS_COMP);
    }

    // for removing blocks (Editor)
    public void removeBlock(Block block) {
        posMap.remove(block.getPos());

        int chunkId = Chunk.chunkFunc(block.pos);
        Chunk chunk = getChunk(chunkId);

        if (chunk != null) { // if chunk exists already                            
            List<Block> blockList = chunk.getBlocks().getBlockList();
            blockList.remove(block);
            // if chunk is empty (with no tuples) -> remove it
            if (blockList.isEmpty()) {
                chunkList.remove(chunk);
            }
        }
    }

    public void updateFluids() { // call only for fluid blocks after adding
        for (Block fluidBlock : getTotalList()) {
            fluidBlock.enableAllFaces(false);
            for (int j = 0; j <= 5; j++) { // j - face number
                Integer hash = posMap.get(Block.getAdjacentPos(fluidBlock.getPos(), j));
                if (hash != null) {
                    fluidBlock.disableFace(j, false);
                }
            }
        }
    }

    public void bufferAll() {
        for (Chunk chunk : chunkList) {
            chunk.bufferAll();
        }
        buffered = true;
    }

    public Chunk getChunk(int chunkId) { // linear search through chunkList to get the chunk
        Chunk result = null;
        for (Chunk chunk : chunkList) {
            if (chunk.getId() == chunkId) {
                result = chunk;
                break;
            }
        }
        return result;
    }

    public void animate() { // call only for fluid blocks
        for (Chunk chunk : getVisibleChunks()) {
            chunk.animate();
        }
    }

    public void prepare() { // call only for fluid blocks before rendering        
        for (Chunk chunk : chunkList) {
            chunk.prepare();
        }
    }

    // for each rendering
    public void render(ShaderProgram shaderProgram, Vector3f lightSrc) {
        if (buffered) {
            for (Chunk chunk : getVisibleChunks()) {
                chunk.render(shaderProgram, lightSrc);
            }
        }
    }

    // for each rendering with predicate
    public void renderIf(ShaderProgram shaderProgram, Vector3f lightSrc, Predicate<Block> predicate) {
        if (buffered) {
            for (Chunk chunk : getVisibleChunks()) {
                chunk.renderIf(shaderProgram, lightSrc, predicate);
            }
        }
    }

    // total size
    public int totalSize() {
        int result = 0;
        for (Chunk chunk : chunkList) {
            result += chunk.size();
        }
        return result;
    }

    // all blocks from all the chunks in one big list
    public List<Block> getTotalList() {
        List<Block> result = new GapList<>();
        for (Chunk chunk : chunkList) {
            result.addAll(chunk.getBlocks().getBlockList());
        }
        return result;
    }

    public List<Chunk> getVisibleChunks() {
        List<Chunk> result = new GapList<>();
        for (Chunk chunk : chunkList) {
            if (chunk.isVisible()) {
                result.add(chunk);
            }
        }
        return result;
    }

    // all blocks from all the chunks in one big list
    public List<Block> getTotalVisibleList() {
        List<Block> result = new GapList<>();
        for (Chunk chunk : getVisibleChunks()) {
            result.addAll(chunk.getBlocks().getBlockList());
        }
        return result;
    }

    public List<Chunk> getChunkList() {
        return chunkList;
    }

    public boolean isBuffered() {
        return buffered;
    }

    public void setBuffered(boolean buffered) {
        this.buffered = buffered;
        for (Chunk chunk : getVisibleChunks()) {
            chunk.setBuffered(buffered);
        }
    }

    public void setCameraInFluid(boolean cameraInFluid) {
        for (Chunk chunk : getVisibleChunks()) {
            chunk.getBlocks().setCameraInFluid(cameraInFluid);
        }
    }

    public Map<Vector3f, Integer> getPosMap() {
        return posMap;
    }

}
