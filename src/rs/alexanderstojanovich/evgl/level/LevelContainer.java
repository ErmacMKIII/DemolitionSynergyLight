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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.glfw.GLFW;
import rs.alexanderstojanovich.evgl.audio.AudioFile;
import rs.alexanderstojanovich.evgl.audio.AudioPlayer;
import rs.alexanderstojanovich.evgl.core.Camera;
import rs.alexanderstojanovich.evgl.core.Window;
import rs.alexanderstojanovich.evgl.critter.Critter;
import rs.alexanderstojanovich.evgl.models.Block;
import rs.alexanderstojanovich.evgl.models.Chunk;
import rs.alexanderstojanovich.evgl.models.Chunks;
import rs.alexanderstojanovich.evgl.models.Model;
import rs.alexanderstojanovich.evgl.shaders.ShaderProgram;
import rs.alexanderstojanovich.evgl.texture.Texture;
import rs.alexanderstojanovich.evgl.util.DSLogger;
import rs.alexanderstojanovich.evgl.util.Vector3fUtils;

/**
 *
 * @author Coa
 */
public class LevelContainer implements GravityEnviroment {

    private final Window myWindow;
    public static final Block SKYBOX = new Block(true, Texture.NIGHT);

    private final Chunks solidChunks = new Chunks();
    private final Chunks fluidChunks = new Chunks();

    private final byte[] buffer = new byte[0x1000000]; // 16 MB Buffer
    private int pos = 0;

    public static final float SKYBOX_SCALE = 256.0f * 256.0f * 256.0f; // default 16.7M
    public static final float SKYBOX_WIDTH = 256.0f; // default 256
    public static final Vector4f SKYBOX_COLOR = new Vector4f(0.25f, 0.5f, 0.75f, 1.0f); // cool bluish color for SKYBOX

    public static final int MAX_NUM_OF_SOLID_BLOCKS = 10000;
    public static final int MAX_NUM_OF_FLUID_BLOCKS = 10000;

    private float progress = 0.0f;

    private boolean working = false;

    private final LevelActors levelActors = new LevelActors();
    private final RandomLevelGenerator randomLevelGenerator;

    private final AudioPlayer musicPlayer;
    private final AudioPlayer soundFXPlayer;

    public LevelContainer(Window myWindow, AudioPlayer musicPlayer, AudioPlayer soundFXPlayer) {
        this.myWindow = myWindow;
        this.randomLevelGenerator = new RandomLevelGenerator(this);
        this.musicPlayer = musicPlayer;
        this.soundFXPlayer = soundFXPlayer;
        // setting SKYBOX        
        SKYBOX.setPrimaryColor(SKYBOX_COLOR);
        SKYBOX.setUVsForSkybox();
        SKYBOX.setScale(SKYBOX_SCALE);
        // setting observer        
    }

    public boolean startNewLevel() {
        if (working || progress > 0.0f) {
            return false;
        }
        boolean success = false;
        working = true;
        progress = 0.0f;
        levelActors.freeze();
        musicPlayer.play(AudioFile.INTERMISSION, true);

        solidChunks.getChunkList().clear();
        fluidChunks.getChunkList().clear();

        solidChunks.getPosMap().clear();
        fluidChunks.getPosMap().clear();

        for (int i = 0; i <= 2; i++) {
            for (int j = 0; j <= 2; j++) {
                Block entity = new Block(false, Texture.DOOM0);

                entity.getPos().x = 4.0f * i;
                entity.getPos().y = 4.0f * j;
                entity.getPos().z = 3.0f;

                entity.getPrimaryColor().x = 0.5f * i + 0.25f;
                entity.getPrimaryColor().y = 0.5f * j + 0.25f;
                entity.getPrimaryColor().z = 0.0f;
                entity.getPrimaryColor().w = 1.0f;

                solidChunks.addBlock(entity);

                progress += 100.0f / 9.0f;
            }
        }

        solidChunks.setBuffered(false);
        fluidChunks.setBuffered(false);

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
        musicPlayer.play(AudioFile.AMBIENT, true);
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
        musicPlayer.play(AudioFile.INTERMISSION, true);

        solidChunks.getChunkList().clear();
        fluidChunks.getChunkList().clear();

        solidChunks.getPosMap().clear();
        fluidChunks.getPosMap().clear();
        if (numberOfBlocks > 0 && numberOfBlocks <= MAX_NUM_OF_SOLID_BLOCKS + MAX_NUM_OF_FLUID_BLOCKS) {
            randomLevelGenerator.setNumberOfBlocks(numberOfBlocks);
            randomLevelGenerator.generate();
            success = true;
        }

        solidChunks.setBuffered(false);
        fluidChunks.updateFluids();
        fluidChunks.setBuffered(false);

        progress = 100.0f;
        working = false;
        levelActors.unfreeze();
        musicPlayer.play(AudioFile.AMBIENT, true);
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
        musicPlayer.play(AudioFile.INTERMISSION, true);
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

        List<Block> solidBlocks = solidChunks.getTotalList();
        List<Block> fluidBlocks = fluidChunks.getTotalList();

        //----------------------------------------------------------------------
        for (Block solidBlock : solidBlocks) {
            if (myWindow.shouldClose()) {
                break;
            }

            byte[] texName = solidBlock.getPrimaryTexture().getImage().getFileName().getBytes();
            System.arraycopy(texName, 0, buffer, pos, 5);
            pos += 5;
            byte[] solidPos = Vector3fUtils.vec3fToByteArray(solidBlock.getPos());
            System.arraycopy(solidPos, 0, buffer, pos, solidPos.length);
            pos += solidPos.length;
            Vector4f primCol = solidBlock.getPrimaryColor();
            Vector3f col = new Vector3f(primCol.x, primCol.y, primCol.z);
            byte[] solidCol = Vector3fUtils.vec3fToByteArray(col);
            System.arraycopy(solidCol, 0, buffer, pos, solidCol.length);
            pos += solidCol.length;

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
            if (myWindow.shouldClose()) {
                break;
            }

            byte[] texName = fluidBlock.getPrimaryTexture().getImage().getFileName().getBytes();
            System.arraycopy(texName, 0, buffer, pos, 5);
            pos += 5;
            byte[] solidPos = Vector3fUtils.vec3fToByteArray(fluidBlock.getPos());
            System.arraycopy(solidPos, 0, buffer, pos, solidPos.length);
            pos += solidPos.length;
            Vector4f primCol = fluidBlock.getPrimaryColor();
            Vector3f col = new Vector3f(primCol.x, primCol.y, primCol.z);
            byte[] solidCol = Vector3fUtils.vec3fToByteArray(col);
            System.arraycopy(solidCol, 0, buffer, pos, solidCol.length);
            pos += solidCol.length;

            progress += 100.0f / (solidBlocks.size() + fluidBlocks.size());
        }

        buffer[pos++] = 'E';
        buffer[pos++] = 'N';
        buffer[pos++] = 'D';

        levelActors.unfreeze();
        progress = 100.0f;

        if (progress == 100.0f && !myWindow.shouldClose()) {
            success = true;
        }
        working = false;
        musicPlayer.play(AudioFile.AMBIENT, true);
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
        musicPlayer.play(AudioFile.INTERMISSION, true);
        pos = 0;
        if (buffer[0] == 'D' && buffer[1] == 'S') {
            solidChunks.getChunkList().clear();
            fluidChunks.getChunkList().clear();

            solidChunks.getPosMap().clear();
            fluidChunks.getPosMap().clear();

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
                for (int i = 0; i < solidNum && !myWindow.shouldClose(); i++) {
                    char[] texNameArr = new char[5];
                    for (int k = 0; k < texNameArr.length; k++) {
                        texNameArr[k] = (char) buffer[pos++];
                    }
                    String texName = String.valueOf(texNameArr);

                    byte[] blockPosArr = new byte[12];
                    System.arraycopy(buffer, pos, blockPosArr, 0, blockPosArr.length);
                    Vector3f blockPos = Vector3fUtils.vec3fFromByteArray(blockPosArr);
                    pos += blockPosArr.length;

                    byte[] blockPosCol = new byte[12];
                    System.arraycopy(buffer, pos, blockPosCol, 0, blockPosCol.length);
                    Vector3f blockCol = Vector3fUtils.vec3fFromByteArray(blockPosCol);
                    pos += blockPosCol.length;

                    Vector4f primaryColor = new Vector4f(blockCol, 1.0f);

                    Block block = new Block(false, Texture.TEX_MAP.get(texName), blockPos, primaryColor, true);
                    solidChunks.addBlock(block);

                    progress += 50.0f / solidNum;
                }
                solidChunks.setBuffered(false);
            }

            char[] fluid = new char[5];
            for (int i = 0; i < fluid.length; i++) {
                fluid[i] = (char) buffer[pos++];
            }
            String strFluid = String.valueOf(fluid);

            if (strFluid.equals("FLUID")) {
                int fluidNum = ((buffer[pos + 1] & 0xFF) << 8) | (buffer[pos] & 0xFF);
                pos += 2;
                for (int i = 0; i < fluidNum && !myWindow.shouldClose(); i++) {
                    char[] texNameArr = new char[5];
                    for (int k = 0; k < texNameArr.length; k++) {
                        texNameArr[k] = (char) buffer[pos++];
                    }
                    String texName = String.valueOf(texNameArr);

                    byte[] blockPosArr = new byte[12];
                    System.arraycopy(buffer, pos, blockPosArr, 0, blockPosArr.length);
                    Vector3f blockPos = Vector3fUtils.vec3fFromByteArray(blockPosArr);
                    pos += blockPosArr.length;

                    byte[] blockPosCol = new byte[12];
                    System.arraycopy(buffer, pos, blockPosCol, 0, blockPosCol.length);
                    Vector3f blockCol = Vector3fUtils.vec3fFromByteArray(blockPosCol);
                    pos += blockPosCol.length;

                    Vector4f primaryColor = new Vector4f(blockCol, 0.5f);

                    Block block = new Block(false, Texture.TEX_MAP.get(texName), blockPos, primaryColor, false);
                    fluidChunks.addBlock(block);

                    progress += 50.0f / fluidNum;
                }
                fluidChunks.updateFluids();
                fluidChunks.setBuffered(false);
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
        levelActors.unfreeze();
        progress = 100.0f;
        working = false;
        musicPlayer.play(AudioFile.AMBIENT, true);
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
        Chunk fluidChunk = fluidChunks.getChunk(Chunk.chunkFunc(obsCamPos));
        if (fluidChunk != null) {
            for (Block fluidBlock : fluidChunk.getBlocks().getBlockList()) {
                if (fluidBlock.containsInsideEqually(obsCamPos)) {
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

        for (Vector3f solidPos : solidChunks.getPosMap().keySet()) {
            if (Model.containsInsideExactly(solidPos, 2.0f, 2.0f, 2.0f, critter.getPredictor())
                    || Model.intersectsEqually(critter.getPredictor(), critter.getModel().getWidth(),
                            critter.getModel().getHeight(), critter.getModel().getDepth(), solidPos, 2.0f, 2.0f, 2.0f)) {
                coll = true;
                break;
            }
        }

        return coll;
    }

    // thats what gravity does, object fells down if they don't have support below it (sky or other object)
    @Override
    public void gravityDo(float deltaTime) {
        float value = (GRAVITY_CONSTANT * deltaTime * deltaTime) / 2.0f;
        Map<Vector3f, Integer> solidMap = solidChunks.getPosMap();
        for (Vector3f solidBlockPos : solidMap.keySet()) {
            Vector3f bottom = new Vector3f(solidBlockPos);
            bottom.y -= 1.0f;
            boolean massBelow = false;
            for (Vector3f otherSolidBlockPos : solidMap.keySet()) {
                if (!solidBlockPos.equals(otherSolidBlockPos)
                        && Block.containsOnXZEqually(otherSolidBlockPos, 2.0f, bottom)) {
                    massBelow = true;
                    break;
                }
            }
            boolean inSkybox = LevelContainer.SKYBOX.containsInsideExactly(bottom);
            if (!massBelow && inSkybox) {
                solidBlockPos.y -= value;
            }
        }
        solidChunks.setBuffered(false);
    }

    public void update(float deltaTime) { // call it externally from the main thread 
        if (working || progress > 0.0f || levelActors.getPlayer() == null) {
            return; // don't update if working, it may screw up!
        }

        SKYBOX.setrY(SKYBOX.getrY() + deltaTime / 64.0f);

        Camera obsCamera = levelActors.getPlayer().getCamera();
        int currChunkId = Chunk.chunkFunc(obsCamera.getPos(), obsCamera.getFront());
        List<Integer> visibleList = Chunk.determineVisible(obsCamera.getPos(), obsCamera.getFront()); // is list of estimated visible chunks (by that chunk)
        Chunk currSolid = solidChunks.getChunk(currChunkId);
        if (currSolid != null) {
            currSolid.setVisible(true);
        }
        for (Chunk solidChunk : solidChunks.getChunkList()) {
            if (visibleList.contains(solidChunk.getId())) {
                solidChunk.setVisible(true);
            } else {
                solidChunk.setVisible(false);
            }
        }

        Chunk currFluid = fluidChunks.getChunk(currChunkId);
        if (currFluid != null) {
            currFluid.setVisible(true);
        }
        for (Chunk fluidChunk : fluidChunks.getChunkList()) {
            if (visibleList.contains(fluidChunk.getId())) {
                fluidChunk.setVisible(true);
            } else {
                fluidChunk.setVisible(false);
            }
        }

        fluidChunks.setCameraInFluid(isCameraInFluid());
    }

    public void render() { // render for regular level rendering
        levelActors.render();
        Camera obsCamera = levelActors.getPlayer().getCamera();
        // render SKYBOX
        obsCamera.render(ShaderProgram.getMainShader());
        float time = (float) GLFW.glfwGetTime();
        SKYBOX.setrY(time / 64.0f);
        SKYBOX.render(ShaderProgram.getMainShader());

        Block editorNew = Editor.getSelectedNew();
        if (editorNew != null) {
            editorNew.setLight(obsCamera.getPos());
            if (!editorNew.isBuffered()) {
                editorNew.bufferAll();
            }
            editorNew.render(ShaderProgram.getMainShader());
        }
        // copy uniforms from main shader to voxel shader
        ShaderProgram.getMainShader().bind();
        obsCamera.updateViewMatrix(ShaderProgram.getMainShader());
        obsCamera.updateCameraPosition(ShaderProgram.getMainShader());
        obsCamera.updateCameraFront(ShaderProgram.getMainShader());
        ShaderProgram.unbind();

        Predicate<Block> predicate = new Predicate<Block>() {
            @Override
            public boolean test(Block t) {
                return (obsCamera.doesSee(t) && t.canBeSeenBy(obsCamera.getFront(), obsCamera.getPos()));
            }
        };

        // render solid series         
        if (!solidChunks.isBuffered()) {
            solidChunks.bufferAll();
        }

        // render solid series                 
        solidChunks.renderIf(ShaderProgram.getMainShader(), obsCamera.getPos(), predicate);

        // render fluid blocks      
        if (!fluidChunks.isBuffered()) {
            fluidChunks.bufferAll();
        }

        // render fluid blocks              
        fluidChunks.prepare();
        fluidChunks.renderIf(ShaderProgram.getMainShader(), obsCamera.getPos(), predicate);
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
        return myWindow;
    }

    public Block getSKYBOX() {
        return SKYBOX;
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

    public byte[] getBuffer() {
        return buffer;
    }

    public int getPos() {
        return pos;
    }

    public AudioPlayer getMusicPlayer() {
        return musicPlayer;
    }

    public AudioPlayer getSoundFXPlayer() {
        return soundFXPlayer;
    }

    public LevelActors getLevelActors() {
        return levelActors;
    }

}
