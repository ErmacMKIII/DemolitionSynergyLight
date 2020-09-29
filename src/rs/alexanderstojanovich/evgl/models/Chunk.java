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
import rs.alexanderstojanovich.evgl.level.LevelContainer;
import rs.alexanderstojanovich.evgl.main.Game;
import rs.alexanderstojanovich.evgl.shaders.ShaderProgram;
import rs.alexanderstojanovich.evgl.texture.Texture;
import rs.alexanderstojanovich.evgl.util.DSLogger;
import rs.alexanderstojanovich.evgl.util.Pair;
import rs.alexanderstojanovich.evgl.util.Vector3fUtils;

/**
 *
 * @author Alexander Stojanovich <coas91@rocketmail.com>
 */
public class Chunk implements Comparable<Chunk> { // some operations are mutually exclusive    

    // MODULATOR, DIVIDER, VISION are used in chunkCheck and for determining visible chunks
    public static final int ABS_BOUND = Math.round(LevelContainer.SKYBOX_WIDTH / 2.0f); // modulator
    public static final int DIVIDER = 16; // divider -> number of chunks
    public static final int VAL = DIVIDER / 2 - 1; // for iterations of determine visible

    public static final float VISION = 100.0f; // determines visibility

    // id of the chunk (signed)
    private final int id;
    private final boolean solid;

    // is a group of blocks which are prepared for instanced rendering
    // where each tuple is considered as:                
    private final Blocks blocks = new Blocks();

    private Texture waterTexture;

    private boolean buffered = false;

    private static final byte[] MEMORY = new byte[0x100000];
    private static int pos = 0;
    private boolean cached = false;

    private int timeToLive = 0;

    private int cachedSize = 0;

    public Chunk(int id, boolean solid) {
        this.id = id;
        this.solid = solid;
    }

    @Override
    public int compareTo(Chunk o) {
        return Chunks.COMPARATOR.compare(this, o);
    }

    public void updateFluids() {
        for (Block fluidBlock : getBlockList()) {
            Pair<String, Byte> pair = LevelContainer.ALL_FLUID_MAP.get(Vector3fUtils.hashCode(fluidBlock.pos));
            if (pair != null) {
                byte neighborBits = pair.getValue();
                fluidBlock.setFaceBits(~neighborBits & 63, false);
            }
        }
    }

    public void addBlock(Block block, boolean useLevelContainer) {
        List<Block> blockList = blocks.getBlockList();
        blockList.add(block);
        blockList.sort(Block.Y_AXIS_COMP);

        if (useLevelContainer) {
            LevelContainer.putBlock(block, blockList.indexOf(block));
        }

        buffered = false;
    }

    public void removeBlock(Block block, boolean useLevelContainer) {
        blocks.getBlockList().remove(block);

        if (useLevelContainer) {
            LevelContainer.removeBlock(block);
        }

        buffered = false;
    }

    // hint that stuff should be buffered again
    public void unbuffer() {
        if (!cached) {
            buffered = false;
        }
    }

    // renderer does this stuff prior to any rendering
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

    // determine chunk (where am I)
    public static int chunkFunc(Vector3f pos) {
        return Math.round(((pos.x + pos.y + pos.z) / (3.0f * DIVIDER)));
    }

    // determine if chunk is visible
    public static int chunkFunc(Vector3f actorPos, Vector3f actorFront) {
        float x = VISION * actorFront.x + actorPos.x;
        float y = VISION * actorFront.y + actorPos.y;
        float z = VISION * actorFront.z + actorPos.z;

        return Math.round(((x + y + z) / (3.0f * DIVIDER)));
    }

    // determine where chunk position might be based on the chunkId
    public static Vector3f chunkInverFunc(int chunkId) {
        float component = chunkId * DIVIDER;
        return new Vector3f(component, component, component);
    }

    // determine which chunks are visible by this chunk
    public static void determineVisible(Queue<Pair<Integer, Float>> visibleQueue,
            Queue<Pair<Integer, Float>> invisibleQueue, Vector3f actorPos, Vector3f actorFront) {
        // current chunk where player is
        int cid = chunkFunc(actorPos);
        Vector3f temp = new Vector3f();
        // this is for other chunks
        for (int id = -VAL; id <= VAL; id++) {
            Vector3f chunkPos = chunkInverFunc(id);
            float product = chunkPos.sub(actorPos, temp).normalize(temp).dot(actorFront);
            float distance = chunkPos.distance(actorPos);
            Pair<Integer, Float> pair = new Pair<>(id, distance);
            if ((id == cid && distance <= VISION
                    || id != cid && distance <= VISION && product >= 0.25f) && !visibleQueue.contains(pair)) {
                visibleQueue.offer(new Pair<>(id, distance));
            } else if (!invisibleQueue.contains(pair)) {
                invisibleQueue.offer(new Pair<>(id, distance));
            }
        }
    }

    public int loadedSize() { // for debugging purposes
        int size = 0;
        if (!cached) {
            size += blocks.getBlockList().size();
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
        return blocks.getBlockList();
    }

    private synchronized void saveMemToDisk(String filename) {
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

    private synchronized void loadDiskToMem(String filename) {
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
        if (!cached) {
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
            blocks.clear();

            File cacheDir = new File(Game.CACHE);
            if (!cacheDir.exists()) {
                cacheDir.mkdir();
            }

            saveMemToDisk(getFileName());

            cachedSize = blocks.size();

            cached = true;
        }
    }

    public void loadFromDisk() {
        if (cached) {
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
            cached = false;

            cachedSize = 0;
        }
    }

    public static Chunk loadFromDisk(int chunkId, boolean solid) {
        Chunk chunk = new Chunk(chunkId, solid);
        chunk.cached = true;
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

    public void tstCameraInFluid(Vector3f camPos) {
        boolean yea = false;
        for (Block fluidBLock : getBlockList()) {
            if (fluidBLock.containsInsideEqually(camPos)) {
                yea = true;
                break;
            }
        }
        setCameraInFluid(yea);
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

    public static byte[] getMEMORY() {
        return MEMORY;
    }

    public int getPos() {
        return pos;
    }

    public boolean isCached() {
        return cached;
    }

    public int getTimeToLive() {
        return timeToLive;
    }

    public void setTimeToLive(int timeToLive) {
        this.timeToLive = timeToLive;
    }

    public int getCachedSize() {
        return cachedSize;
    }

    public void setCachedSize(int cachedSize) {
        this.cachedSize = cachedSize;
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
