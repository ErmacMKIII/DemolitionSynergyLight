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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Queue;
import java.util.function.Predicate;
import org.joml.Vector3f;
import org.magicwerk.brownies.collections.GapList;
import rs.alexanderstojanovich.evgl.level.LevelContainer;
import rs.alexanderstojanovich.evgl.main.Game;
import rs.alexanderstojanovich.evgl.main.GameObject;
import rs.alexanderstojanovich.evgl.shaders.ShaderProgram;
import rs.alexanderstojanovich.evgl.util.DSLogger;
import rs.alexanderstojanovich.evgl.util.Pair;
import rs.alexanderstojanovich.evgl.util.Vector3fUtils;

/**
 *
 * @author Alexander Stojanovich <coas91@rocketmail.com>
 */
public class Chunk implements Comparable<Chunk> { // some operations are mutually exclusive    

    // MODULATOR, DIVIDER, VISION are used in chunkCheck and for determining visible chunks
    public static final int BOUND = Math.round(LevelContainer.SKYBOX_WIDTH);
    public static final float VISION = 200.0f; // determines visibility
    public static final int MULTIPLIER = 8;

    public static final int CHUNK_NUM = 3 * MULTIPLIER / 2 + 1;

    // id of the chunk (signed)
    private final int id;
    private final boolean solid;

    // is a group of blocks which are prepared for instanced rendering
    // where each tuple is considered as:                
    //--------------------------MODULATOR--------DIVIDER--------VISION-------D--------E-----------------------------
    //------------------------blocks-vec4Vbos-mat4Vbos-texture-faceEnBits------------------------
    private final List<Tuple> tupleList = new GapList<>();

    private boolean buffered = false;

    private static final byte[] MEMORY = new byte[0x100000];
    private static int pos = 0;

    private int timeToLive = 0;

    public Chunk(int id, boolean solid) {
        this.id = id;
        this.solid = solid;
    }

    @Override
    public int compareTo(Chunk o) {
        return Chunks.COMPARATOR.compare(this, o);
    }

    private Tuple getTuple(String keyTexture, Integer keyFaceBits) {
        Tuple result = null;
        for (Tuple tuple : tupleList) {
            if (tuple.texName.equals(keyTexture)
                    && tuple.faceEnBits == keyFaceBits) {
                result = tuple;
                break;
            }
        }
        return result;
    }

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
        }
        List<Block> blockList = dstTuple.getBlockList();
        blockList.add(block);
        blockList.sort(Block.Y_AXIS_COMP);

        buffered = false;
    }

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

    public void addBlock(Block block, boolean useLevelContainer) {
        String blockTexture = block.texName;
        int blockFaceBits = block.getFaceBits();
        Tuple tuple = getTuple(blockTexture, blockFaceBits);

        if (tuple == null) {
            tuple = new Tuple(blockTexture, blockFaceBits);
            tupleList.add(tuple);
        }

        List<Block> blockList = tuple.getBlockList();
        blockList.add(block);
        blockList.sort(Block.Y_AXIS_COMP);

        if (useLevelContainer) {
            LevelContainer.putBlock(block);
        }

        buffered = false;
    }

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
                LevelContainer.removeBlock(block);
            }
        }
    }

    // hint that stuff should be buffered again
    public void unbuffer() {
        if (!Chunk.isCached(id, solid)) {
            buffered = false;
        }
    }

    // renderer does this stuff prior to any rendering
    public void bufferAll() {
        if (!Chunk.isCached(id, solid)) {
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
    public void render(ShaderProgram shaderProgram, List<Vector3f> lightSrc) {
        if (buffered && shaderProgram != null && !tupleList.isEmpty() && timeToLive > 0) {
            for (Tuple tuple : tupleList) {
                tuple.render(shaderProgram, lightSrc);
            }
        }
    }

    public void renderIf(ShaderProgram shaderProgram, List<Vector3f> lightSrc, Predicate<Block> predicate) {
        if (buffered && shaderProgram != null && !tupleList.isEmpty() && timeToLive > 0) {
            for (Tuple tuple : tupleList) {
                tuple.renderIf(shaderProgram, lightSrc, predicate);
            }
        }
    }

    // deallocates Chunk from graphic card
    @Deprecated
    public void release() {
        if (!Chunk.isCached(id, solid)) {
            //--------------------------MODULATOR--------DIVIDER--------VISION-------D--------E-----------------------------
            //------------------------blocks-vec4Vbos-mat4Vbos-texture-faceEnBits------------------------
            for (Tuple tuple : tupleList) {
                tuple.release();
            }
            buffered = false;
        }
    }

    // determine chunk (where am I)
    public static int chunkFunc(Vector3f pos) {
        float nx = (pos.x + BOUND) / (float) (BOUND << 1);
        float ny = (pos.y + BOUND) / (float) (BOUND << 1);
        float nz = (pos.z + BOUND) / (float) (BOUND << 1);

        float halfSum = (nx + ny + nz) / 2.0f;

        int cid = Math.round(MULTIPLIER * halfSum);
        return cid;
    }

    // determine chunk (where am I)
    public static Vector3f invChunkFunc(int chunkId) {
        float k = chunkId / (float) MULTIPLIER;
        float d = 2.0f * k / 3.0f;
        float t = d * (BOUND << 1) - BOUND;
        return new Vector3f(t);
    }

    // determine which chunks are visible by this chunk
    public static void determineVisible(Queue<Pair<Integer, Float>> visibleQueue,
            Queue<Pair<Integer, Float>> invisibleQueue, Vector3f actorPos, Vector3f actorFront) {
        // current chunk where player is
        int currChunkId = chunkFunc(actorPos);
        visibleQueue.offer(new Pair<>(currChunkId, 0.0f));
        // this is for other chunks
        Vector3f temp = new Vector3f();
        for (int id = 0; id < Chunk.CHUNK_NUM; id++) {
            if (id != currChunkId) {
                Vector3f chunkPos = invChunkFunc(id);
                float product = chunkPos.sub(actorPos, temp).normalize(temp).dot(actorFront);
                float distance = chunkPos.distance(actorPos);
                if (distance <= Chunk.VISION && product >= 0.25f) {
                    visibleQueue.offer(new Pair<>(id, distance));
                } else {
                    invisibleQueue.offer(new Pair<>(id, distance));
                }
            }
        }
    }

    public int loadedSize() { // for debugging purposes
        int size = 0;
        if (!Chunk.isCached(id, solid)) {
            for (Tuple tuple : tupleList) {
                size += tuple.getBlockList().size();
            }
        }
        return size;
    }

    public static int cachedSize(int id, boolean solid) { // for debugging purposes
        int size = 0;
        if (Chunk.isCached(id, solid)) {
            try {
                FileInputStream fos = new FileInputStream(getFileName(id, solid));
                byte[] bytes = new byte[3];
                fos.read(bytes, 0, 3);
                size = ((bytes[2] & 0xFF) << 8) | (bytes[1] & 0xFF);
            } catch (FileNotFoundException ex) {
                DSLogger.reportError(ex.getMessage(), ex);
            } catch (IOException ex) {
                DSLogger.reportError(ex.getMessage(), ex);
            }
        }
        return size;
    }

    public List<Block> getBlockList() {
        List<Block> result = new GapList<>();
        for (Tuple tuple : tupleList) {
            result.addAll(tuple.getBlockList());
        }
        return result;
    }

    private void saveMemToDisk(String filename) {
        BufferedOutputStream bos = null;
        File file = new File(filename);
        if (file.exists()) {
            file.delete();
        }
        try {
            bos = new BufferedOutputStream(new FileOutputStream(file));
            bos.write(MEMORY, 0, pos);
        } catch (FileNotFoundException ex) {
            DSLogger.reportFatalError(ex.getMessage(), ex);
        } catch (IOException ex) {
            DSLogger.reportFatalError(ex.getMessage(), ex);
        }
        if (bos != null) {
            try {
                bos.close();
            } catch (IOException ex) {
                DSLogger.reportFatalError(ex.getMessage(), ex);
            }
        }
    }

    private void loadDiskToMem(String filename) {
        File file = new File(filename);
        if (file.exists()) {
            BufferedInputStream bis = null;
            try {
                bis = new BufferedInputStream(new FileInputStream(file));
                bis.read(MEMORY);
            } catch (FileNotFoundException ex) {
                DSLogger.reportFatalError(ex.getMessage(), ex);
            } catch (IOException ex) {
                DSLogger.reportFatalError(ex.getMessage(), ex);
            }
            if (bis != null) {
                try {
                    bis.close();
                } catch (IOException ex) {
                    DSLogger.reportFatalError(ex.getMessage(), ex);
                }
            }
            file.delete();
        }
    }

    private String getFileName() {
        return Game.CACHE + File.separator + (solid ? "s" : "f") + "chnk" + (id < 0 ? "m" + (-id) : id) + ".cache";
    }

    private static String getFileName(int id, boolean solid) {
        return Game.CACHE + File.separator + (solid ? "s" : "f") + "chnk" + (id < 0 ? "m" + (-id) : id) + ".cache";
    }

    public void saveToDisk() {
        if (!Chunk.isCached(id, solid)) {
            List<Block> blocks = getBlockList();
            pos = 0;
            MEMORY[pos++] = (byte) id;
            MEMORY[pos++] = (byte) blocks.size();
            MEMORY[pos++] = (byte) (blocks.size() >> 8);
            for (Block block : blocks) {
                byte[] texName = block.texName.getBytes();
                System.arraycopy(texName, 0, MEMORY, pos, 5);
                pos += 5;
                byte[] solidPos = Vector3fUtils.vec3fToByteArray(block.getPos());
                System.arraycopy(solidPos, 0, MEMORY, pos, solidPos.length);
                pos += solidPos.length;
                Vector3f primCol = block.getPrimaryColor();
                byte[] solidCol = Vector3fUtils.vec3fToByteArray(primCol);
                System.arraycopy(solidCol, 0, MEMORY, pos, solidCol.length);
                pos += solidCol.length;
            }

            // better than tuples clear (otherwise much slower to load)
            // this indicates that add with no transfer on fluid blocks will be used!
            for (Tuple tuple : tupleList) {
                tuple.getBlockList().clear();
            }

            File cacheDir = new File(Game.CACHE);
            if (!cacheDir.exists()) {
                cacheDir.mkdir();
            }

            saveMemToDisk(getFileName());

            tupleList.clear();
        }
    }

    public void loadFromDisk() {
        if (Chunk.isCached(id, solid)) {
            loadDiskToMem(getFileName());
            pos = 1;
            int len = ((MEMORY[pos + 1] & 0xFF) << 8) | (MEMORY[pos] & 0xFF);
            pos += 2;
            for (int i = 0; i < len; i++) {
                char[] texNameArr = new char[5];
                for (int k = 0; k < texNameArr.length; k++) {
                    texNameArr[k] = (char) MEMORY[pos++];
                }
                String texName = String.valueOf(texNameArr);

                byte[] blockPosArr = new byte[12];
                System.arraycopy(MEMORY, pos, blockPosArr, 0, blockPosArr.length);
                Vector3f blockPos = Vector3fUtils.vec3fFromByteArray(blockPosArr);
                pos += blockPosArr.length;

                byte[] blockPosCol = new byte[12];
                System.arraycopy(MEMORY, pos, blockPosCol, 0, blockPosCol.length);
                Vector3f blockCol = Vector3fUtils.vec3fFromByteArray(blockPosCol);
                pos += blockPosCol.length;

                Block block = new Block(texName, blockPos, blockCol, solid);
                addBlock(block, false);
            }
        }
    }

    public static Chunk loadFromDisk(int chunkId, boolean solid) {
        Chunk chunk = new Chunk(chunkId, solid);
        chunk.loadFromDisk();
        return chunk;
    }

    public static void deleteCache() {
        // deleting cache
        File cache = new File(Game.CACHE);
        if (cache.exists()) {
            for (File file : cache.listFiles()) {
                file.delete(); // deleting all chunk files
            }
            cache.delete();
        }
    }

    public static boolean isCached(int chunkId, boolean solid) {
        File file = new File(getFileName(chunkId, solid));
        return file.exists();
    }

    public boolean isCameraInFluid(Vector3f camPos) {
        boolean yea = false;
        for (Block fluidBLock : getBlockList()) {
            if (fluidBLock.containsInsideEqually(camPos)) {
                yea = true;
                break;
            }
        }
        return yea;
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

    public static byte[] getMEMORY() {
        return MEMORY;
    }

    public int getPos() {
        return pos;
    }

    public int getTimeToLive() {
        return timeToLive;
    }

    public void setTimeToLive(int timeToLive) {
        this.timeToLive = timeToLive;
    }

    public void decTimeToLive() {
        if (timeToLive > 0) {
            timeToLive--;
        } else {
            timeToLive = 0;
        }
    }

    public boolean isAlive() {
        return timeToLive > 0;
    }

}
