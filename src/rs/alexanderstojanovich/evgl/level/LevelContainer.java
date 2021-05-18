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
package rs.alexanderstojanovich.evgl.level;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.function.Predicate;
import org.joml.Vector3f;
import rs.alexanderstojanovich.evgl.audio.AudioFile;
import rs.alexanderstojanovich.evgl.audio.AudioPlayer;
import rs.alexanderstojanovich.evgl.core.Camera;
import rs.alexanderstojanovich.evgl.core.Window;
import rs.alexanderstojanovich.evgl.critter.Critter;
import rs.alexanderstojanovich.evgl.main.GameObject;
import rs.alexanderstojanovich.evgl.models.Block;
import rs.alexanderstojanovich.evgl.models.Chunk;
import rs.alexanderstojanovich.evgl.models.Chunks;
import rs.alexanderstojanovich.evgl.shaders.ShaderProgram;
import rs.alexanderstojanovich.evgl.util.DSLogger;
import rs.alexanderstojanovich.evgl.util.Pair;
import rs.alexanderstojanovich.evgl.util.Vector3fUtils;

/**
 *
 * @author Alexander Stojanovich <coas91@rocketmail.com>
 */
public class LevelContainer implements GravityEnviroment {

    private final GameObject gameObject;

    public static final Block SKYBOX = new Block("night");

    private final Chunks solidChunks = new Chunks(true);
    private final Chunks fluidChunks = new Chunks(false);

    public static final int VIPAIR_QUEUE_CAPACITY = 8;
    public static final Comparator<Pair<Integer, Float>> VIPAIR_COMPARATOR = new Comparator<Pair<Integer, Float>>() {
        @Override
        public int compare(Pair<Integer, Float> o1, Pair<Integer, Float> o2) {
            if (o1.getValue() > o2.getValue()) {
                return 1;
            } else if (o1.getValue() == o2.getValue()) {
                return 0;
            } else {
                return -1;
            }
        }
    };

    private final Queue<Pair<Integer, Float>> visibleQueue = new PriorityQueue<>(VIPAIR_QUEUE_CAPACITY, VIPAIR_COMPARATOR);
    private final Queue<Pair<Integer, Float>> invisibleQueue = new PriorityQueue<>(VIPAIR_QUEUE_CAPACITY, VIPAIR_COMPARATOR);

    private int operation = 0;
    private static final int LD = 0;
    private static final int SV = 1;

    private final byte[] buffer = new byte[0x1000000]; // 16 MB Buffer
    private int pos = 0;

    public static final float BASE = 8.0f;
    public static final float SKYBOX_SCALE = BASE * BASE * BASE;
    public static final float SKYBOX_WIDTH = 2.0f * SKYBOX_SCALE;
    public static final Vector3f SKYBOX_COLOR = new Vector3f(0.25f, 0.5f, 0.75f); // cool bluish color for SKYBOX

    public static final int MAX_NUM_OF_SOLID_BLOCKS = 65536;
    public static final int MAX_NUM_OF_FLUID_BLOCKS = 65536;

    private float progress = 0.0f;

    private boolean working = false;

    private final LevelActors levelActors = new LevelActors();

    // position of all the solid blocks to texture name & neighbors
    public static final Map<Integer, Pair<String, Byte>> ALL_SOLID_MAP = new HashMap<>(MAX_NUM_OF_SOLID_BLOCKS);

    // position of all the fluid blocks to texture name & neighbors
    public static final Map<Integer, Pair<String, Byte>> ALL_FLUID_MAP = new HashMap<>(MAX_NUM_OF_FLUID_BLOCKS);

    // std time to live
    public static final int STD_TTL = 30; // 40 seconds

    private static byte updateSolidNeighbors(Vector3f vector) {
        byte bits = 0;
        for (int j = 0; j <= 5; j++) {
            int mask = 1 << j;
            Vector3f adjPos = Block.getAdjacentPos(vector, j);
            int hashCodeY = Vector3fUtils.hashCode(adjPos);
            Pair<String, Byte> adjPair = ALL_SOLID_MAP.get(hashCodeY);
            if (adjPair != null) {
                bits |= mask;
                byte adjBits = adjPair.getValue();
                int k = ((j & 1) == 0 ? j + 1 : j - 1);
                int maskAdj = 1 << k;
                adjBits |= maskAdj;
                adjPair.setValue(adjBits);
            }
        }
        return bits;
    }

    private static byte updateFluidNeighbors(Vector3f vector) {
        byte bits = 0;
        for (int j = 0; j <= 5; j++) {
            int mask = 1 << j;
            Vector3f adjPos = Block.getAdjacentPos(vector, j);
            int hashCodeY = Vector3fUtils.hashCode(adjPos);
            Pair<String, Byte> adjPair = ALL_FLUID_MAP.get(hashCodeY);
            if (adjPair != null) {
                bits |= mask;
                byte adjBits = adjPair.getValue();
                int k = ((j & 1) == 0 ? j + 1 : j - 1);
                int maskAdj = 1 << k;
                adjBits |= maskAdj;
                adjPair.setValue(adjBits);
            }
        }
        return bits;
    }

    public static void putBlock(Block block) {
        Vector3f pos = block.getPos();
        String str = block.getTexName();
        if (block.isSolid()) {
            byte bits = updateSolidNeighbors(pos);
            int hashCodeX = Vector3fUtils.hashCode(pos);
            Pair<String, Byte> pairX = new Pair<>(str, bits);
            ALL_SOLID_MAP.put(hashCodeX, pairX);
        } else {
            byte bits = updateFluidNeighbors(pos);
            int hashCodeX = Vector3fUtils.hashCode(pos);
            Pair<String, Byte> pairX = new Pair<>(str, bits);
            ALL_FLUID_MAP.put(hashCodeX, pairX);
        }
    }

    public static void removeBlock(Block block) {
        Vector3f pos = block.getPos();
        int hashCode = Vector3fUtils.hashCode(pos);
        if (block.isSolid()) {
            Pair<String, Byte> pair = ALL_SOLID_MAP.remove(hashCode);
            if (pair != null && pair.getValue() > 0) {
                updateSolidNeighbors(pos);
            }
        } else {
            Pair<String, Byte> pair = ALL_FLUID_MAP.remove(hashCode);
            if (pair != null && pair.getValue() > 0) {
                updateFluidNeighbors(pos);
            }
        }
    }

    static {
        // setting SKYBOX     
        SKYBOX.setPrimaryColor(SKYBOX_COLOR);
        SKYBOX.setUVsForSkybox();
        SKYBOX.setScale(SKYBOX_SCALE);
    }

    public LevelContainer(GameObject gameObject) {
        this.gameObject = gameObject;
    }

    public static void printPositionMaps() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        sb.append("SOLID POSITION MAP");
        sb.append("(size = ").append(ALL_SOLID_MAP.size()).append(")\n");
        sb.append("---------------------------");
        sb.append("\n");
        sb.append("FLUID POSITION MAP");
        sb.append("(size = ").append(ALL_FLUID_MAP.size()).append(")\n");
        sb.append("---------------------------");
        DSLogger.reportInfo(sb.toString(), null);
    }

    public void printPriorityQueues() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        sb.append("VISIBLE QUEUE\n");
        sb.append(visibleQueue);
        sb.append("\n");
        sb.append("---------------------------");
        sb.append("\n");
        sb.append("INVISIBLE QUEUE\n");
        sb.append(invisibleQueue);
        sb.append("\n");
        sb.append("---------------------------");
        DSLogger.reportInfo(sb.toString(), null);
    }

    // -------------------------------------------------------------------------
    // -------------------------------------------------------------------------
    public boolean startNewLevel() {
        if (working) {
            return false;
        }
        boolean success = false;
        working = true;
        progress = 0.0f;
        levelActors.freeze();
        gameObject.getMusicPlayer().play(AudioFile.INTERMISSION, true);

        solidChunks.getChunkList().clear();
        fluidChunks.getChunkList().clear();

        ALL_SOLID_MAP.clear();
        ALL_FLUID_MAP.clear();
        Chunk.deleteCache();

        for (int i = 0; i <= 2; i++) {
            for (int j = 0; j <= 2; j++) {
                Block entity = new Block("doom0");
                entity.getPos().x = (4 * i) & 0xFFFFFFFE;
                entity.getPos().y = (4 * j) & 0xFFFFFFFE;
                entity.getPos().z = 3 & 0xFFFFFFFE;

                entity.getPrimaryColor().x = 0.5f * i + 0.25f;
                entity.getPrimaryColor().y = 0.5f * j + 0.25f;
                entity.getPrimaryColor().z = 0.0f;

                solidChunks.addBlock(entity, true);

                progress += 100.0f / 9.0f;
            }
        }

        levelActors.getPlayer().getCamera().setPos(new Vector3f(10.5f, 0.0f, -3.0f));
        levelActors.getPlayer().getCamera().setFront(Camera.Z_AXIS);
        levelActors.getPlayer().getCamera().setUp(Camera.Y_AXIS);
        levelActors.getPlayer().getCamera().setRight(Camera.X_AXIS);
        levelActors.getPlayer().getCamera().calcViewMatrixPub();
        levelActors.getPlayer().updateModelPos();

        levelActors.unfreeze();
        progress = 100.0f;
        working = false;
        success = true;
        gameObject.getMusicPlayer().stop();
        return success;
    }

    public boolean generateRandomLevel(RandomLevelGenerator randomLevelGenerator, int numberOfBlocks) {
        if (working) {
            return false;
        }
        working = true;
        levelActors.freeze();
        boolean success = false;
        progress = 0.0f;
        gameObject.getMusicPlayer().play(AudioFile.RANDOM, true);

        solidChunks.getChunkList().clear();
        fluidChunks.getChunkList().clear();

        ALL_SOLID_MAP.clear();
        ALL_FLUID_MAP.clear();
        Chunk.deleteCache();

        if (numberOfBlocks > 0 && numberOfBlocks <= MAX_NUM_OF_SOLID_BLOCKS + MAX_NUM_OF_FLUID_BLOCKS) {
            randomLevelGenerator.setNumberOfBlocks(numberOfBlocks);
            randomLevelGenerator.generate();
            success = true;
        }

        fluidChunks.updateFluids();

        progress = 100.0f;
        working = false;
        levelActors.unfreeze();
        gameObject.getMusicPlayer().stop();
        return success;
    }

    private boolean storeLevelToBuffer() {
        working = true;
        boolean success = false;
        if (progress > 0.0f) {
            return false;
        }
        progress = 0.0f;
        levelActors.freeze();
        gameObject.getMusicPlayer().play(AudioFile.INTERMISSION, true);
        pos = 0;
        buffer[0] = 'D';
        buffer[1] = 'S';
        pos += 2;
        Camera camera = levelActors.getPlayer().getCamera();
        byte[] campos = Vector3fUtils.vec3fToByteArray(camera.getPos());
        System.arraycopy(campos, 0, buffer, pos, campos.length);
        pos += campos.length;

        byte[] camfront = Vector3fUtils.vec3fToByteArray(camera.getFront());
        System.arraycopy(camfront, 0, buffer, pos, camfront.length);
        pos += camfront.length;

        byte[] camup = Vector3fUtils.vec3fToByteArray(camera.getUp());
        System.arraycopy(camup, 0, buffer, pos, camup.length);
        pos += camup.length;

        byte[] camright = Vector3fUtils.vec3fToByteArray(camera.getRight());
        System.arraycopy(camup, 0, buffer, pos, camright.length);
        pos += camright.length;

        buffer[pos++] = 'S';
        buffer[pos++] = 'O';
        buffer[pos++] = 'L';
        buffer[pos++] = 'I';
        buffer[pos++] = 'D';

        List<Block> solidBlocks = solidChunks.getTotalList();
        List<Block> fluidBlocks = fluidChunks.getTotalList();

        int solidNum = solidChunks.totalSize();
        buffer[pos++] = (byte) (solidNum);
        buffer[pos++] = (byte) (solidNum >> 8);

        //----------------------------------------------------------------------
        for (Block solidBlock : solidBlocks) {
            if (GameObject.MY_WINDOW.shouldClose()) {
                break;
            }
            byte[] byteArraySolid = solidBlock.toByteArray();
            System.arraycopy(byteArraySolid, 0, buffer, pos, 29);
            pos += 29;
            progress += 100.0f / (solidBlocks.size() + fluidBlocks.size());
        }

        buffer[pos++] = 'F';
        buffer[pos++] = 'L';
        buffer[pos++] = 'U';
        buffer[pos++] = 'I';
        buffer[pos++] = 'D';

        int fluidNum = fluidChunks.totalSize();
        buffer[pos++] = (byte) (fluidNum);
        buffer[pos++] = (byte) (fluidNum >> 8);

        for (Block fluidBlock : fluidBlocks) {
            if (GameObject.MY_WINDOW.shouldClose()) {
                break;
            }
            byte[] byteArrayFluid = fluidBlock.toByteArray();
            System.arraycopy(byteArrayFluid, 0, buffer, pos, 29);
            pos += 29;
            progress += 100.0f / (solidBlocks.size() + fluidBlocks.size());
        }

        buffer[pos++] = 'E';
        buffer[pos++] = 'N';
        buffer[pos++] = 'D';

        levelActors.unfreeze();
        progress = 100.0f;

        if (progress == 100.0f && !GameObject.MY_WINDOW.shouldClose()) {
            success = true;
        }
        working = false;
        gameObject.getMusicPlayer().stop();
        return success;
    }

    private boolean loadLevelFromBuffer() {
        working = true;
        boolean success = false;
        if (progress > 0.0f) {
            return false;
        }
        progress = 0.0f;
        levelActors.freeze();
        gameObject.getMusicPlayer().play(AudioFile.INTERMISSION, true);
        pos = 0;
        if (buffer[0] == 'D' && buffer[1] == 'S') {
            solidChunks.getChunkList().clear();
            fluidChunks.getChunkList().clear();

            ALL_SOLID_MAP.clear();
            ALL_FLUID_MAP.clear();
            Chunk.deleteCache();

            pos += 2;
            byte[] posArr = new byte[12];
            System.arraycopy(buffer, pos, posArr, 0, posArr.length);
            Vector3f campos = Vector3fUtils.vec3fFromByteArray(posArr);
            pos += posArr.length;

            byte[] frontArr = new byte[12];
            System.arraycopy(buffer, pos, frontArr, 0, frontArr.length);
            Vector3f camfront = Vector3fUtils.vec3fFromByteArray(frontArr);
            pos += frontArr.length;

            byte[] upArr = new byte[12];
            System.arraycopy(buffer, pos, frontArr, 0, upArr.length);
            Vector3f camup = Vector3fUtils.vec3fFromByteArray(upArr);
            pos += upArr.length;

            byte[] rightArr = new byte[12];
            System.arraycopy(buffer, pos, rightArr, 0, rightArr.length);
            Vector3f camright = Vector3fUtils.vec3fFromByteArray(rightArr);
            pos += rightArr.length;

            levelActors.getPlayer().getCamera().setPos(campos);
            levelActors.getPlayer().getCamera().setFront(camfront);
            levelActors.getPlayer().getCamera().setUp(camup);
            levelActors.getPlayer().getCamera().setRight(camright);
            levelActors.getPlayer().getCamera().calcViewMatrixPub();
            levelActors.getPlayer().updateModelPos();

            char[] solid = new char[5];
            for (int i = 0; i < solid.length; i++) {
                solid[i] = (char) buffer[pos++];
            }
            String strSolid = String.valueOf(solid);

            if (strSolid.equals("SOLID")) {
                int solidNum = ((buffer[pos + 1] & 0xFF) << 8) | (buffer[pos] & 0xFF);
                pos += 2;
                for (int i = 0; i < solidNum && !GameObject.MY_WINDOW.shouldClose(); i++) {
                    byte[] byteArraySolid = new byte[29];
                    System.arraycopy(buffer, pos, byteArraySolid, 0, 29);
                    Block solidBlock = Block.fromByteArray(byteArraySolid, true);
                    solidChunks.addBlock(solidBlock, true);
                    pos += 29;
                    progress += 50.0f / solidNum;
                }

                char[] fluid = new char[5];
                for (int i = 0; i < fluid.length; i++) {
                    fluid[i] = (char) buffer[pos++];
                }
                String strFluid = String.valueOf(fluid);

                if (strFluid.equals("FLUID")) {
                    int fluidNum = ((buffer[pos + 1] & 0xFF) << 8) | (buffer[pos] & 0xFF);
                    pos += 2;
                    for (int i = 0; i < fluidNum && !GameObject.MY_WINDOW.shouldClose(); i++) {
                        byte[] byteArrayFluid = new byte[29];
                        System.arraycopy(buffer, pos, byteArrayFluid, 0, 29);
                        Block fluidBlock = Block.fromByteArray(byteArrayFluid, false);
                        fluidChunks.addBlock(fluidBlock, true);
                        pos += 29;
                        progress += 50.0f / fluidNum;
                    }

                    fluidChunks.updateFluids();

                    char[] end = new char[3];
                    for (int i = 0; i < end.length; i++) {
                        end[i] = (char) buffer[pos++];
                    }
                    String strEnd = String.valueOf(end);
                    if (strEnd.equals("END")) {
                        success = true;
                    }
                }

            }

        }
        levelActors.unfreeze();
        progress = 100.0f;
        working = false;
        gameObject.getMusicPlayer().stop();
        return success;
    }

    public boolean saveLevelToFile(String filename) {
        if (working) {
            return false;
        }
        boolean success = false;
        if (!filename.endsWith(".dat")) {
            filename += ".dat";
        }
        BufferedOutputStream bos = null;
        File file = new File(filename);
        if (file.exists()) {
            file.delete();
        }
        success = storeLevelToBuffer(); // saves level to bufferVertices first
        try {
            bos = new BufferedOutputStream(new FileOutputStream(file));
            bos.write(buffer, 0, pos); // save bufferVertices to file at pos mark
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
        return success;
    }

    public boolean loadLevelFromFile(String filename) {
        if (working) {
            return false;
        }
        boolean success = false;
        if (filename.isEmpty()) {
            return false;
        }
        if (!filename.endsWith(".dat")) {
            filename += ".dat";
        }
        File file = new File(filename);
        BufferedInputStream bis = null;
        if (!file.exists()) {
            return false; // this prevents further fail
        }
        try {
            bis = new BufferedInputStream(new FileInputStream(file));
            bis.read(buffer);
            success = loadLevelFromBuffer();
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
        return success;
    }

    public void animate() {
        if (!working) {
            fluidChunks.animate();
        }
    }

    @Deprecated
    public boolean isCameraInFluid() {
        boolean yea = false;
        Vector3f obsCamPos = levelActors.getPlayer().getCamera().getPos();

        int currChunkId = Chunk.chunkFunc(obsCamPos);
        Chunk currFluidChunk = fluidChunks.getChunk(currChunkId);
        if (currFluidChunk != null) {
            for (Block fluidBLock : currFluidChunk.getBlockList()) {
                if (fluidBLock.containsInsideEqually(obsCamPos)) {
                    yea = true;
                    break;
                }
            }
        }

        return yea;
    }

    public boolean hasCollisionWithCritter(Critter critter) {
        boolean coll;
        coll = (!SKYBOX.containsInsideExactly(critter.getPredictor())
                || !SKYBOX.intersectsExactly(critter.getPredictor(), critter.getModel().getWidth(),
                        critter.getModel().getHeight(), critter.getModel().getDepth()));
        if (!coll) {
            OUTER:
            for (Chunk solidChunk : solidChunks.getChunkList()) {
                if (Chunk.chunkInverFunc(solidChunk.getId()).distance(critter.getPredictor()) <= Chunk.VISION) {
                    for (Block solidBlock : solidChunk.getBlockList()) {
                        if (solidBlock.containsInsideEqually(critter.getPredictor())
                                || solidBlock.intersectsExactly(critter.getPredictor(), critter.getModel().getWidth(),
                                        critter.getModel().getHeight(), critter.getModel().getDepth())) {
                            coll = true;
                            break OUTER;
                        }
                    }
                }
            }
        }
        return coll;
    }

    // thats what gravity does, object fells down if they don't have support below it (sky or other object)
    @Override
    public void gravityDo(float deltaTime) {
//        float value = (GRAVITY_CONSTANT * deltaTime * deltaTime) / 2.0f;
//        Map<Vector3f, Integer> solidMap = solidChunks.getPosMap();
//        for (Vector3f solidBlockPos : solidMap.keySet()) {
//            Vector3f bottom = new Vector3f(solidBlockPos);
//            bottom.y -= 1.0f;
//            boolean massBelow = false;
//            for (Vector3f otherSolidBlockPos : solidMap.keySet()) {
//                if (!solidBlockPos.equals(otherSolidBlockPos)
//                        && Block.containsOnXZEqually(otherSolidBlockPos, 2.0f, bottom)) {
//                    massBelow = true;
//                    break;
//                }
//            }
//            boolean inSkybox = LevelContainer.SKYBOX.containsInsideExactly(bottom);
//            if (!massBelow && inSkybox) {
//                solidBlockPos.y -= value;
//            }
//        }
//        solidChunks.setBuffered(false);
    }

    // method for determining visible chunks
    public void determineVisible() {
        if (visibleQueue.isEmpty() && invisibleQueue.isEmpty()) {
            Camera obsCamera = levelActors.getPlayer().getCamera();
            Chunk.determineVisible(visibleQueue, invisibleQueue, obsCamera.getPos(), obsCamera.getFront());
        }
    }

    // method for saving invisible chunks
    public void chunkOperations() {
        if (!working) {
            Pair<Integer, Float> vPair = visibleQueue.poll();
            if (vPair != null) {
                Integer visibleId = vPair.getKey();

                Chunk solidChunk = solidChunks.getChunk(visibleId);
                if (solidChunk != null) {
                    solidChunk.setTimeToLive(STD_TTL);
                } else if (Chunk.isCached(visibleId, true)) {
                    solidChunk = Chunk.loadFromDisk(visibleId, true);
                    solidChunks.getChunkList().add(solidChunk);
                    solidChunks.getChunkList().sort(Chunks.COMPARATOR);
                }

                Chunk fluidChunk = fluidChunks.getChunk(visibleId);
                if (fluidChunk != null) {
                    fluidChunk.setTimeToLive(STD_TTL);
                } else if (Chunk.isCached(visibleId, false)) {
                    fluidChunk = Chunk.loadFromDisk(visibleId, false);
                    fluidChunk.updateFluids();
                    fluidChunks.getChunkList().add(fluidChunk);
                    fluidChunks.getChunkList().sort(Chunks.COMPARATOR);
                }
            }
            //----------------------------------------------------------
            Pair<Integer, Float> iPair = invisibleQueue.poll();
            if (iPair != null) {
                Integer invisibleId = iPair.getKey();

                Chunk solidChunk = solidChunks.getChunk(invisibleId);
                if (solidChunk != null) {
                    if (solidChunk.isAlive()) {
                        solidChunk.decTimeToLive();
                    } else if (!solidChunk.isAlive()) {
                        solidChunk.unbuffer();
                        solidChunk.saveToDisk();
                        solidChunks.getChunkList().remove(solidChunk);
                    }
                }
                Chunk fluidChunk = fluidChunks.getChunk(invisibleId);
                if (fluidChunk != null) {
                    if (fluidChunk.isAlive()) {
                        fluidChunk.decTimeToLive();
                    } else if (!fluidChunk.isAlive()) {
                        fluidChunk.unbuffer();
                        fluidChunk.saveToDisk();
                        fluidChunks.getChunkList().remove(fluidChunk);
                    }
                }
            }
        }
    }

    public void update(float deltaTime) { // call it externally from the main thread 
        if (!working) { // don't update if working, it may screw up!
            SKYBOX.setrY(SKYBOX.getrY() + deltaTime / 2048.0f);
            Vector3f camPos = levelActors.getPlayer().getCamera().getPos();
            Pair<Integer, Float> pair = visibleQueue.peek();
            if (pair != null) {
                Chunk fluidChunk = fluidChunks.getChunk(pair.getKey());
                if (fluidChunk != null && Chunk.chunkInverFunc(fluidChunk.getId()).distance(camPos) <= 50.0f) {
                    fluidChunk.tstCameraInFluid(camPos);
                }
            }
        }
    }

    public void render() { // render for regular level rendering
        if (working) {
            return;
        }
        Camera obsCamera = levelActors.getPlayer().getCamera();
        levelActors.render();
        if (!SKYBOX.isBuffered()) {
            SKYBOX.bufferAll();
        }
        SKYBOX.render(ShaderProgram.getMainShader());

        Predicate<Block> predicate = new Predicate<Block>() {
            @Override
            public boolean test(Block t) {
                return (t.canBeSeenBy(obsCamera.getFront(), obsCamera.getPos())
                        && obsCamera.doesSee(t));
            }
        };

        // only visible & uncached are in chunk list
        for (Chunk solidChunk : solidChunks.getChunkList()) {
            if (!solidChunk.isBuffered()) {
                solidChunk.bufferAll();
            }

            solidChunk.renderIf(ShaderProgram.getMainShader(), obsCamera.getPos(), predicate);
        }

        // only visible & uncahed are in chunk list
        for (Chunk fluidChunk : fluidChunks.getChunkList()) {
            if (!fluidChunk.isBuffered()) {
                fluidChunk.bufferAll();
            }

            fluidChunk.prepare();
            fluidChunk.renderIf(ShaderProgram.getMainShader(), obsCamera.getPos(), predicate);
        }

        Block editorNew = Editor.getSelectedNew();
        if (editorNew != null) {
            editorNew.setLight(obsCamera.getPos());
            if (!editorNew.isBuffered()) {
                editorNew.bufferAll();
            }
            editorNew.render(ShaderProgram.getMainShader());
        }

        Block selectedNewWireFrame = Editor.getSelectedNewWireFrame();
        if (selectedNewWireFrame != null) {
            selectedNewWireFrame.setLight(obsCamera.getPos());
            if (!selectedNewWireFrame.isBuffered()) {
                selectedNewWireFrame.bufferAll();
            }
            selectedNewWireFrame.render(ShaderProgram.getMainShader());
        }

        Block selectedCurrFrame = Editor.getSelectedCurrWireFrame();
        if (selectedCurrFrame != null) {
            selectedCurrFrame.setLight(obsCamera.getPos());
            if (!selectedCurrFrame.isBuffered()) {
                selectedCurrFrame.bufferAll();
            }
            selectedCurrFrame.render(ShaderProgram.getMainShader());
        }
    }

    // -------------------------------------------------------------------------
    // -------------------------------------------------------------------------
    public boolean maxSolidReached() {
        return solidChunks.totalSize() == MAX_NUM_OF_SOLID_BLOCKS;
    }

    public boolean maxFluidReached() {
        return fluidChunks.totalSize() == MAX_NUM_OF_FLUID_BLOCKS;
    }

    public void incProgress(float increment) {
        if (progress < 100.0f) {
            progress += increment;
        }
    }

    public Window getMyWindow() {
        return GameObject.MY_WINDOW;
    }

    public float getProgress() {
        return progress;
    }

    public void setProgress(float progress) {
        this.progress = progress;
    }

    public boolean isWorking() { // damn this one!
        return working || progress > 0.0f;
    }

    public Chunks getSolidChunks() {
        return solidChunks;
    }

    public Chunks getFluidChunks() {
        return fluidChunks;
    }

    public GameObject getGameObject() {
        return gameObject;
    }

    public Queue<Pair<Integer, Float>> getVisibleQueue() {
        return visibleQueue;
    }

    public Queue<Pair<Integer, Float>> getInvisibleQueue() {
        return invisibleQueue;
    }

    public byte[] getBuffer() {
        return buffer;
    }

    public int getPos() {
        return pos;
    }

    public AudioPlayer getMusicPlayer() {
        return gameObject.getMusicPlayer();
    }

    public LevelActors getLevelActors() {
        return levelActors;
    }

}
