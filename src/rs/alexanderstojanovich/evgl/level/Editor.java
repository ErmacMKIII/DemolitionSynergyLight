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

import org.joml.Vector3f;
import rs.alexanderstojanovich.evgl.audio.AudioFile;
import rs.alexanderstojanovich.evgl.critter.Observer;
import rs.alexanderstojanovich.evgl.main.GameObject;
import rs.alexanderstojanovich.evgl.models.Block;
import rs.alexanderstojanovich.evgl.models.Chunk;
import rs.alexanderstojanovich.evgl.models.Model;
import rs.alexanderstojanovich.evgl.util.Vector3fColors;
import rs.alexanderstojanovich.evgl.util.Vector3fUtils;

/**
 *
 * @author Alexander Stojanovich <coas91@rocketmail.com>
 */
public class Editor {

    private static Block loaded = null;

    private static Block selectedNew = null;
    private static int blockColorNum = 0;

    private static Block selectedCurr = null;
    private static int selectedCurrIndex = -1;

    private static int value = 0; // value about which texture to use
    private static final int MIN_VAL = 0;
    private static final int MAX_VAL = 3;

    private static Block selectedNewWireFrame = null;
    private static Block selectedCurrWireFrame = null;

    public static void selectNew(GameObject gameObject) {
        deselect();
        if (loaded == null) // first time it's null
        {
            loaded = new Block();
            selectLoadedTexture();
        }
        selectedNew = loaded;

        // fetching..
        Observer obs = gameObject.getLevelContainer().getLevelActors().getPlayer();
        Vector3f pos = obs.getCamera().getPos();
        Vector3f front = obs.getCamera().getFront();
        final float skyboxWidth = LevelContainer.SKYBOX_WIDTH;
        // initial calculation (make it dependant to point player looking at)
        // and make it follows player camera        
        selectedNew.getPos().x = (Math.round(8.0f * front.x) + Math.round(pos.x) & 0xFFFFFFFE) % Math.round(skyboxWidth + 1);
        selectedNew.getPos().y = (Math.round(8.0f * front.y) + Math.round(pos.y) & 0xFFFFFFFE) % Math.round(skyboxWidth + 1);
        selectedNew.getPos().z = (Math.round(8.0f * front.z) + Math.round(pos.z) & 0xFFFFFFFE) % Math.round(skyboxWidth + 1);

        if (!cannotPlace(gameObject)) {
            selectedNewWireFrame = new Block("decal", new Vector3f(selectedNew.getPos()), Vector3fColors.GREEN, false);
        }

        gameObject.getSoundFXPlayer().play(AudioFile.BLOCK_SELECT, selectedNew.getPos());
    }

    public static void selectCurrSolid(GameObject gameObject) {
        deselect();
        Vector3f cameraPos = gameObject.getLevelContainer().getLevelActors().getPlayer().getCamera().getPos();
        Vector3f cameraFront = gameObject.getLevelContainer().getLevelActors().getPlayer().getCamera().getFront();
        float minDistanceOfSolid = Chunk.VISION;
        int currChunkId = Chunk.chunkFunc(cameraPos);
        Chunk currSolidChunk = gameObject.getLevelContainer().getSolidChunks().getChunk(currChunkId);

        int solidTargetIndex = -1;
        if (currSolidChunk != null) {
            int solidBlkIndex = 0;
            for (Block solidBlock : currSolidChunk.getBlockList()) {
                if (Block.intersectsRay(solidBlock.getPos(), cameraFront, cameraPos)) {
                    float distance = Vector3f.distance(cameraPos.x, cameraPos.y, cameraPos.z,
                            solidBlock.getPos().x, solidBlock.getPos().y, solidBlock.getPos().z);
                    if (distance < minDistanceOfSolid
                            && !Model.intersectsEqually(cameraPos, 2.0f, 2.0f, 2.0f, solidBlock.getPos(), 2.0f, 2.0f, 2.0f)) {
                        minDistanceOfSolid = distance;
                        solidTargetIndex = solidBlkIndex;
                    }
                }
                solidBlkIndex++;
            }

            if (solidTargetIndex != -1) {
                selectedCurr = currSolidChunk.getBlockList().get(solidTargetIndex);
                selectedCurrIndex = solidBlkIndex;
                selectedCurrWireFrame = new Block("decal", new Vector3f(selectedCurr.getPos()), Vector3fColors.YELLOW, false);
            }
        }

    }

    public static void selectCurrFluid(GameObject gameObject) {
        deselect();
        Vector3f cameraPos = gameObject.getLevelContainer().getLevelActors().getPlayer().getCamera().getPos();
        Vector3f cameraFront = gameObject.getLevelContainer().getLevelActors().getPlayer().getCamera().getFront();
        float minDistanceOfFluid = Chunk.VISION;
        int currChunkId = Chunk.chunkFunc(cameraPos);
        Chunk currFluidChunk = gameObject.getLevelContainer().getFluidChunks().getChunk(currChunkId);

        int fluidTargetIndex = -1;
        if (currFluidChunk != null) {
            int fluidBlkIndex = 0;
            for (Block fluidBlock : currFluidChunk.getBlockList()) {
                if (Block.intersectsRay(fluidBlock.getPos(), cameraFront, cameraPos)) {
                    float distance = Vector3f.distance(cameraPos.x, cameraPos.y, cameraPos.z,
                            fluidBlock.getPos().x, fluidBlock.getPos().y, fluidBlock.getPos().z);
                    if (distance < minDistanceOfFluid
                            && !Model.intersectsEqually(cameraPos, 2.0f, 2.0f, 2.0f, fluidBlock.getPos(), 2.0f, 2.0f, 2.0f)) {
                        minDistanceOfFluid = distance;
                        fluidTargetIndex = fluidBlkIndex;
                    }
                }
                fluidBlkIndex++;
            }

            if (fluidTargetIndex != -1) {
                selectedCurr = currFluidChunk.getBlockList().get(fluidTargetIndex);
                selectedCurrIndex = fluidBlkIndex;
                selectedCurrWireFrame = new Block("decal", new Vector3f(selectedCurr.getPos()), Vector3fColors.YELLOW, false);
            }
        }
    }

    public static void deselect() {
        selectedNew = selectedCurr = null;
        selectedCurrIndex = -1;
        selectedNewWireFrame = null;
        selectedCurrWireFrame = null;
    }

    public static void selectAdjacentSolid(GameObject gameObject, int position) {
        deselect();
        selectCurrSolid(gameObject);
        if (selectedCurr != null) {
            if (loaded == null) // first time it's null
            {
                loaded = new Block();
                selectLoadedTexture();
            }
            selectedNew = loaded;
            selectedNew.getPos().x = selectedCurr.getPos().x;
            selectedNew.getPos().y = selectedCurr.getPos().y;
            selectedNew.getPos().z = selectedCurr.getPos().z;

            switch (position) {
                case Block.LEFT:
                    selectedNew.getPos().x -= selectedCurr.getWidth() / 2.0f + selectedNew.getWidth() / 2.0f;
                    break;
                case Block.RIGHT:
                    selectedNew.getPos().x += selectedCurr.getWidth() / 2.0f + selectedNew.getWidth() / 2.0f;
                    break;
                case Block.BOTTOM:
                    selectedNew.getPos().y -= selectedCurr.getHeight() / 2.0f + selectedNew.getHeight() / 2.0f;
                    break;
                case Block.TOP:
                    selectedNew.getPos().y += selectedCurr.getHeight() / 2.0f + selectedNew.getHeight() / 2.0f;
                    break;
                case Block.BACK:
                    selectedNew.getPos().z -= selectedCurr.getDepth() / 2.0f + selectedNew.getDepth() / 2.0f;
                    break;
                case Block.FRONT:
                    selectedNew.getPos().z += selectedCurr.getDepth() / 2.0f + selectedNew.getDepth() / 2.0f;
                    break;
                default:
                    break;
            }

            if (!cannotPlace(gameObject)) {
                selectedNewWireFrame = new Block("decal", new Vector3f(selectedNew.getPos()), Vector3fColors.BLUE, false);
            }
        }
    }

    public static void selectAdjacentFluid(GameObject gameObject, int position) {
        deselect();
        selectCurrFluid(gameObject);
        if (selectedCurr != null) {
            if (loaded == null) // first time it's null
            {
                loaded = new Block();
                selectLoadedTexture();
            }
            selectedNew = loaded;
            selectedNew.getPos().x = selectedCurr.getPos().x;
            selectedNew.getPos().y = selectedCurr.getPos().y;
            selectedNew.getPos().z = selectedCurr.getPos().z;

            switch (position) {
                case Block.LEFT:
                    selectedNew.getPos().x -= selectedCurr.getWidth() / 2.0f + selectedNew.getWidth() / 2.0f;
                    break;
                case Block.RIGHT:
                    selectedNew.getPos().x += selectedCurr.getWidth() / 2.0f + selectedNew.getWidth() / 2.0f;
                    break;
                case Block.BOTTOM:
                    selectedNew.getPos().y -= selectedCurr.getHeight() / 2.0f + selectedNew.getHeight() / 2.0f;
                    break;
                case Block.TOP:
                    selectedNew.getPos().y += selectedCurr.getHeight() / 2.0f + selectedNew.getHeight() / 2.0f;
                    break;
                case Block.BACK:
                    selectedNew.getPos().z -= selectedCurr.getDepth() / 2.0f + selectedNew.getDepth() / 2.0f;
                    break;
                case Block.FRONT:
                    selectedNew.getPos().z += selectedCurr.getDepth() / 2.0f + selectedNew.getDepth() / 2.0f;
                    break;
                default:
                    break;
            }

            if (!cannotPlace(gameObject)) {
                selectedNewWireFrame = new Block("decal", new Vector3f(selectedNew.getPos()), Vector3fColors.BLUE, false);
            }
        }
    }

    private static boolean cannotPlace(GameObject gameObject) {
        boolean cant = false;
        boolean placeOccupied = LevelContainer.ALL_SOLID_MAP.containsKey(Vector3fUtils.hashCode(selectedNew.getPos()))
                || LevelContainer.ALL_FLUID_MAP.containsKey(Vector3fUtils.hashCode(selectedNew.getPos()));
        //----------------------------------------------------------------------
        boolean intsSolid = false;
        int currChunkId = Chunk.chunkFunc(selectedNew.getPos());
        Chunk currSolidChunk = gameObject.getLevelContainer().getSolidChunks().getChunk(currChunkId);
        if (currSolidChunk != null) {
            for (Block solidBlock : currSolidChunk.getBlockList()) {
                intsSolid = selectedNew.intersectsExactly(solidBlock);
                if (intsSolid) {
                    break;
                }
            }
        }
        //----------------------------------------------------------------------
        boolean intsFluid = false;
        if (!intsSolid) {
            Chunk currFluidChunk = gameObject.getLevelContainer().getFluidChunks().getChunk(currChunkId);
            if (currFluidChunk != null) {
                for (Block fluidBlock : currFluidChunk.getBlockList()) {
                    intsFluid = selectedNew.intersectsExactly(fluidBlock);
                    if (intsFluid) {
                        break;
                    }
                }
            }
        }
        //----------------------------------------------------------------------
        boolean leavesSkybox = !LevelContainer.SKYBOX.intersectsEqually(selectedNew);
        if (selectedNew.isSolid()) {
            cant = gameObject.getLevelContainer().maxSolidReached() || placeOccupied || intsSolid || intsFluid || leavesSkybox;
        } else {
            cant = gameObject.getLevelContainer().maxFluidReached() || placeOccupied || intsSolid || intsFluid || leavesSkybox;
        }
        if (cant) {
            selectedNewWireFrame = new Block("decal", new Vector3f(selectedNew.getPos()), Vector3fColors.RED, false);
        }
        return cant;
    }

    public static void add(GameObject gameObject) {
        if (selectedNew != null) {
            if (!cannotPlace(gameObject) && !gameObject.getLevelContainer().getLevelActors().getPlayer().getCamera().intersects(selectedNew)) {
                if (selectedNew.isSolid()) { // else if block is solid
                    gameObject.getLevelContainer().getSolidChunks().addBlock(selectedNew, true);
                } else { // if block is fluid                    
                    gameObject.getLevelContainer().getFluidChunks().addBlock(selectedNew, true);
                    gameObject.getLevelContainer().getFluidChunks().updateFluids();
                }
                gameObject.getSoundFXPlayer().play(AudioFile.BLOCK_ADD, selectedNew.getPos());
                loaded = new Block();
                selectLoadedTexture();
            }
        }
        deselect();
    }

    public static void remove(GameObject gameObject) {
        if (selectedCurr != null) {
            if (selectedCurr.isSolid()) {
                gameObject.getLevelContainer().getSolidChunks().removeBlock(selectedCurr, true);
            } else {
                gameObject.getLevelContainer().getFluidChunks().removeBlock(selectedCurr, true);
                gameObject.getLevelContainer().getFluidChunks().updateFluids();
            }
            gameObject.getSoundFXPlayer().play(AudioFile.BLOCK_REMOVE, selectedCurr.getPos());
        }
        deselect();
    }

    private static void selectLoadedTexture() {
        String texName = null;
        if (loaded != null) {
            switch (value) {
                case 0:
                    texName = "crate";
                    loaded.setSolid(true);
                    break;
                case 1:
                    texName = "stone";
                    loaded.setSolid(true);
                    break;
                case 2:
                    texName = "water";
                    loaded.setSolid(false);
                    break;
                case 3:
                    texName = "doom0";
                    loaded.setSolid(true);
                    break;
            }
            loaded.setTexName(texName);
        }
    }

    public static void selectPrevTexture(GameObject gameObject) {
        if (loaded != null) {
            if (value > MIN_VAL) {
                value--;
                selectLoadedTexture();
            }
        }
    }

    public static void selectNextTexture(GameObject gameObject) {
        if (loaded != null) {
            if (value < MAX_VAL) {
                value++;
                selectLoadedTexture();
            }
        }
    }

    public static void cycleBlockColor() {
        if (selectedNew != null) {
            switch (blockColorNum) {
                case 0:
                    selectedNew.setPrimaryColor(Vector3fColors.RED); // RED                
                    break;
                case 1:
                    selectedNew.setPrimaryColor(Vector3fColors.GREEN); // GREEN
                    break;
                case 2:
                    selectedNew.setPrimaryColor(Vector3fColors.BLUE); // BLUE
                    break;
                case 3:
                    selectedNew.setPrimaryColor(Vector3fColors.CYAN); // CYAN
                    break;
                case 4:
                    selectedNew.setPrimaryColor(Vector3fColors.MAGENTA); // MAGENTA
                    break;
                case 5:
                    selectedNew.setPrimaryColor(Vector3fColors.YELLOW); // YELLOW
                    break;
                case 6:
                    selectedNew.setPrimaryColor(Vector3fColors.WHITE); // WHITE
                    break;
            }
            if (blockColorNum < 6) {
                blockColorNum++;
            } else {
                blockColorNum = 0;
            }
        }
    }

    public static Block getSelectedNew() {
        return selectedNew;
    }

    public static Block getSelectedCurr() {
        return selectedCurr;
    }

    public static int getBlockColorNum() {
        return blockColorNum;
    }

    public static int getSelectedCurrIndex() {
        return selectedCurrIndex;
    }

    public static Block getSelectedNewWireFrame() {
        return selectedNewWireFrame;
    }

    public static Block getSelectedCurrWireFrame() {
        return selectedCurrWireFrame;
    }

}
