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
package rs.alexanderstojanovich.evgl.level;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
import rs.alexanderstojanovich.evgl.util.Vector3fUtils;

/**
 *
 * @author Coa
 */
public class LevelContainer implements GravityEnviroment {

    private final GameObject gameObject;

    public static final Block SKYBOX = new Block(false, "night");

    private final Chunks solidChunks = new Chunks();
    private final Chunks fluidChunks = new Chunks();

    private List<Integer> visibleChunks = new ArrayList<>();

    private final byte[] buffer = new byte[0x1000000]; // 16 MB Buffer
    private int pos = 0;

    public static final float BASE = 8.0f;
    public static final float SKYBOX_SCALE = BASE * BASE * BASE;
    public static final float SKYBOX_WIDTH = 2.0f * SKYBOX_SCALE;
    public static final Vector3f SKYBOX_COLOR = new Vector3f(0.25f, 0.5f, 0.75f); // cool bluish color for SKYBOX

    public static final int MAX_NUM_OF_SOLID_BLOCKS = 25000;
    public static final int MAX_NUM_OF_FLUID_BLOCKS = 25000;

    private float progress = 0.0f;

    private boolean working = false;

    private final LevelActors levelActors = new LevelActors();
    private final RandomLevelGenerator randomLevelGenerator;

    // position of all the solid blocks
    public static final Set<Vector3f> ALL_SOLID_POS = new HashSet<>(MAX_NUM_OF_SOLID_BLOCKS);
    // position of all the fluid blocks
    public static final Set<Vector3f> ALL_FLUID_POS = new HashSet<>(MAX_NUM_OF_FLUID_BLOCKS);

    static {
        // setting SKYBOX     
        SKYBOX.setPrimaryColor(SKYBOX_COLOR);
        SKYBOX.setUVsForSkybox(false);
        SKYBOX.setScale(SKYBOX_SCALE);
    }

    public LevelContainer(GameObject gameObject) {
        this.gameObject = gameObject;
        this.randomLevelGenerator = new RandomLevelGenerator(this);
    }

    public static void printPositionSets() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        sb.append("SOLID POSITION SET");
        sb.append("(size = ").append(ALL_SOLID_POS.size()).append(")\n");
        for (Vector3f solidPos : ALL_SOLID_POS) {
            sb.append(solidPos);
            sb.append("\n");
        }
        sb.append("---------------------------");
        sb.append("FLUID POSITION SET");
        sb.append("(size = ").append(ALL_FLUID_POS.size()).append(")\n");
        for (Vector3f fluidPos : ALL_FLUID_POS) {
            sb.append(fluidPos);
            sb.append("\n");
        }
        sb.append("---------------------------");
        DSLogger.reportInfo(sb.toString(), null);
    }

    public boolean startNewLevel() {
        if (working || progress > 0.0f) {
            return false;
        }
        boolean success = false;
        working = true;
        progress = 0.0f;
        levelActors.freeze();
        gameObject.getMusicPlayer().play(AudioFile.INTERMISSION, true);

        solidChunks.getChunkList().clear();
        fluidChunks.getChunkList().clear();

        ALL_SOLID_POS.clear();
        ALL_FLUID_POS.clear();

        for (int i = 0; i <= 2; i++) {
            for (int j = 0; j <= 2; j++) {
                Block entity = new Block(false, "doom0");

                entity.getPos().x = 4.0f * i;
                entity.getPos().y = 4.0f * j;
                entity.getPos().z = 3.0f;

                entity.getPrimaryColor().x = 0.5f * i + 0.25f;
                entity.getPrimaryColor().y = 0.5f * j + 0.25f;
                entity.getPrimaryColor().z = 0.0f;

                solidChunks.addBlock(entity);

                progress += 100.0f / 9.0f;
            }
        }

        solidChunks.saveAllToMemory();

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
        gameObject.getMusicPlayer().play(AudioFile.AMBIENT, true);
        return success;
    }

    public boolean generateRandomLevel(int numberOfBlocks) {
        if (working || progress > 0.0f) {
            return false;
        }
        working = true;
        levelActors.freeze();
        boolean success = false;
        progress = 0.0f;
        gameObject.getMusicPlayer().play(AudioFile.INTERMISSION, true);

        solidChunks.getChunkList().clear();
        fluidChunks.getChunkList().clear();

        ALL_SOLID_POS.clear();
        ALL_FLUID_POS.clear();

        if (numberOfBlocks > 0 && numberOfBlocks <= MAX_NUM_OF_SOLID_BLOCKS + MAX_NUM_OF_FLUID_BLOCKS) {
            randomLevelGenerator.setNumberOfBlocks(numberOfBlocks);
            randomLevelGenerator.generate();
            success = true;
        }

        solidChunks.saveAllToMemory();
        fluidChunks.saveAllToMemory();

        progress = 100.0f;
        working = false;
        levelActors.unfreeze();
        gameObject.getMusicPlayer().play(AudioFile.AMBIENT, true);
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

        int solidNum = solidChunks.totalSize();
        buffer[pos++] = (byte) (solidNum);
        buffer[pos++] = (byte) (solidNum >> 8);

        solidChunks.loadAllFromMemory();
        List<Block> solidBlocks = solidChunks.getTotalList();

        fluidChunks.loadAllFromMemory();
        List<Block> fluidBlocks = fluidChunks.getTotalList();

        //----------------------------------------------------------------------
        for (Block solidBlock : solidBlocks) {
            if (gameObject.getMyWindow().shouldClose()) {
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
            if (gameObject.getMyWindow().shouldClose()) {
                break;
            }
            byte[] byteArrayFluid = fluidBlock.toByteArray();
            System.arraycopy(byteArrayFluid, 0, buffer, pos, 29);
            pos += 29;
            progress += 100.0f / (fluidBlocks.size() + fluidBlocks.size());
        }

        buffer[pos++] = 'E';
        buffer[pos++] = 'N';
        buffer[pos++] = 'D';

        levelActors.unfreeze();
        progress = 100.0f;

        if (progress == 100.0f && !gameObject.getMyWindow().shouldClose()) {
            success = true;
        }
        working = false;
        gameObject.getMusicPlayer().play(AudioFile.AMBIENT, true);
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

            ALL_SOLID_POS.clear();
            ALL_FLUID_POS.clear();

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
                for (int i = 0; i < solidNum && !gameObject.getMyWindow().shouldClose(); i++) {
                    byte[] byteArraySolid = new byte[29];
                    System.arraycopy(buffer, pos, byteArraySolid, 0, 29);
                    Block solidBlock = Block.fromByteArray(byteArraySolid, true);
                    solidChunks.addBlock(solidBlock);
                    pos += 29;
                    progress += 50.0f / solidNum;
                }
                solidChunks.saveAllToMemory();

                char[] fluid = new char[5];
                for (int i = 0; i < fluid.length; i++) {
                    fluid[i] = (char) buffer[pos++];
                }
                String strFluid = String.valueOf(fluid);

                if (strFluid.equals("FLUID")) {
                    int fluidNum = ((buffer[pos + 1] & 0xFF) << 8) | (buffer[pos] & 0xFF);
                    pos += 2;
                    for (int i = 0; i < fluidNum && !gameObject.getMyWindow().shouldClose(); i++) {
                        byte[] byteArrayFluid = new byte[29];
                        System.arraycopy(buffer, pos, byteArrayFluid, 0, 29);
                        Block fluidBlock = Block.fromByteArray(byteArrayFluid, false);
                        fluidChunks.addBlock(fluidBlock);
                        pos += 29;
                        progress += 50.0f / fluidNum;
                    }
                    fluidChunks.saveAllToMemory();

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
        gameObject.getMusicPlayer().play(AudioFile.AMBIENT, true);
        return success;
    }

    public boolean saveLevelToFile(String filename) {
        if (working || progress > 0.0f) {
            return false;
        }
        boolean success = false;
        if (!filename.endsWith(".dat")) {
            filename += ".dat";
        }
        FileOutputStream fos = null;
        File file = new File(filename);
        if (file.exists()) {
            file.delete();
        }
        Arrays.fill(buffer, (byte) 0);
        success = storeLevelToBuffer(); // saves level to bufferVertices first
        try {
            fos = new FileOutputStream(file);
            fos.write(buffer, 0, pos); // save bufferVertices to file at pos mark
        } catch (FileNotFoundException ex) {
            DSLogger.reportFatalError(ex.getMessage(), ex);
        } catch (IOException ex) {
            DSLogger.reportFatalError(ex.getMessage(), ex);
        }
        if (fos != null) {
            try {
                fos.close();
            } catch (IOException ex) {
                DSLogger.reportFatalError(ex.getMessage(), ex);
            }
        }
        return success;
    }

    public boolean loadLevelFromFile(String filename) {
        if (working || progress > 0.0f) {
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
        FileInputStream fis = null;
        if (!file.exists()) {
            return false; // this prevents further fail
        }
        Arrays.fill(buffer, (byte) 0);
        try {
            fis = new FileInputStream(file);
            fis.read(buffer);
            success = loadLevelFromBuffer();
        } catch (FileNotFoundException ex) {
            DSLogger.reportFatalError(ex.getMessage(), ex);
        } catch (IOException ex) {
            DSLogger.reportFatalError(ex.getMessage(), ex);
        }
        if (fis != null) {
            try {
                fis.close();
            } catch (IOException ex) {
                DSLogger.reportFatalError(ex.getMessage(), ex);
            }
        }
        return success;
    }

    public void animate() {
        fluidChunks.animate();
    }

    public boolean isCameraInFluid() {
        boolean yea = false;
        Vector3f obsCamPos = levelActors.getPlayer().getCamera().getPos();

        int currChunkId = Chunk.chunkFunc(obsCamPos);
        Chunk currFluidChunk = fluidChunks.getChunk(currChunkId);
        if (currFluidChunk != null) {
            for (Block fluidBLock : currFluidChunk.getBlocks().getBlockList()) {
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
            for (Vector3f vector : ALL_SOLID_POS) {
                if (Block.containsInsideEqually(vector, 2.0f, 2.0f, 2.0f, critter.getPredictor())
                        || Block.intersectsEqually(critter.getPredictor(), critter.getModel().getWidth(),
                                critter.getModel().getHeight(), critter.getModel().getDepth(), vector, 2.0f, 2.0f, 2.0f)) {
                    coll = true;
                    break;
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

    private void patch() {
        for (Chunk solidChunk : solidChunks.getChunkList()) {
            if (solidChunk != null) {
                solidChunk.setVisible(visibleChunks.contains(solidChunk.getId()));
                if (solidChunk.isVisible() && solidChunk.isCached()) {
                    solidChunk.loadFromMemory();
                } else if (!solidChunk.isVisible() && !solidChunk.isCached()) {
                    solidChunk.saveToMemory();
                }
            }
        }

        for (Chunk fluidChunk : fluidChunks.getChunkList()) {
            if (fluidChunk != null) {
                fluidChunk.setVisible(visibleChunks.contains(fluidChunk.getId()));
                if (fluidChunk.isVisible() && fluidChunk.isCached()) {
                    fluidChunk.loadFromMemory();
                    fluidChunks.updateFluids(fluidChunk, true);
                } else if (!fluidChunk.isVisible() && !fluidChunk.isCached()) {
                    fluidChunk.saveToMemory();
                }
            }
        }
    }

    public void update(float deltaTime) { // call it externally from the main thread 
        if (working || progress > 0.0f || levelActors.getPlayer() == null) {
            return; // don't update if working, it may screw up!
        }

        SKYBOX.setrY(SKYBOX.getrY() + deltaTime / 64.0f);

        Camera obsCamera = levelActors.getPlayer().getCamera();
        // determine current chunks where player is
        int chunkId = Chunk.chunkFunc(obsCamera.getPos());
        Chunk solidChunk = solidChunks.getChunk(chunkId);
        Chunk fluidChunk = fluidChunks.getChunk(chunkId);

        if (fluidChunk != null) {
            fluidChunk.setCameraInFluid(isCameraInFluid());
        }

        // if they are not visible call patch
        if (solidChunk != null && !solidChunk.isVisible()
                || fluidChunk != null && !fluidChunk.isVisible()) {
            // is list of estimated visible chunks (by the camera pos and front)
            visibleChunks = Chunk.determineVisible(obsCamera.getPos(), obsCamera.getFront());
            patch();
        }
    }

    public void render() { // render for regular level rendering
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

        for (Chunk solidChunk : solidChunks.getChunkList()) {
            if (solidChunk.isVisible() && !solidChunk.isBuffered()) {
                solidChunk.bufferAll();
            } else if (!solidChunk.isVisible() && solidChunk.isBuffered()) {
                solidChunk.release();
            } else if (solidChunk.isVisible() && solidChunk.isBuffered()) {
                solidChunk.renderIf(ShaderProgram.getMainShader(), obsCamera.getPos(), predicate);
            }
        }

        for (Chunk fluidChunk : fluidChunks.getChunkList()) {
            if (fluidChunk.isVisible() && !fluidChunk.isBuffered()) {
                fluidChunk.bufferAll();
            } else if (!fluidChunk.isVisible() && fluidChunk.isBuffered()) {
                fluidChunk.release();
            } else if (fluidChunk.isVisible() && fluidChunk.isVisible()) {
                fluidChunk.prepare();
                fluidChunk.renderIf(ShaderProgram.getMainShader(), obsCamera.getPos(), predicate);
            }
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
        return gameObject.getMyWindow();
    }

    public float getProgress() {
        return progress;
    }

    public void setProgress(float progress) {
        this.progress = progress;
    }

    public boolean isWorking() {
        return working;
    }

    public Chunks getSolidChunks() {
        return solidChunks;
    }

    public Chunks getFluidChunks() {
        return fluidChunks;
    }

    public RandomLevelGenerator getRandomLevelGenerator() {
        return randomLevelGenerator;
    }

    public List<Integer> getVisibleChunks() {
        return visibleChunks;
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
