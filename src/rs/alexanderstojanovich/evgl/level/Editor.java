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

import rs.alexanderstojanovich.evgl.critter.Observer;
import java.util.List;
import org.joml.Vector3f;
import rs.alexanderstojanovich.evgl.audio.AudioFile;
import rs.alexanderstojanovich.evgl.models.Block;
import rs.alexanderstojanovich.evgl.models.Chunk;
import rs.alexanderstojanovich.evgl.texture.Texture;

/**
 *
 * @author Coa
 */
public class Editor {

    private static Block loaded = null;

    private static Block selectedNew = null;

    private static Block selectedCurr = null;
    private static int selectedCurrIndex = -1;

    private static final Texture SELECTED_TEXTURE = Texture.MINIGUN;

    private static int value = 0; // value about which texture to use
    private static final int MIN_VAL = 0;
    private static final int MAX_VAL = 3;

    public static void selectNew(LevelContainer levelContainer) {
        deselect();
        if (loaded == null) // first time it's null
        {
            loaded = new Block(false);
            selectLoadedTexture();
        }
        selectedNew = loaded;

        // fetching..
        Observer obs = levelContainer.getLevelActors().getPlayer();
        Vector3f pos = obs.getCamera().getPos();
        Vector3f front = obs.getCamera().getFront();
        final float skyboxWidth = LevelContainer.SKYBOX_WIDTH;
        // initial calculation (make it dependant to point player looking at)
        // and make it follows player camera        
        selectedNew.getPos().x = (Math.round(8.0f * front.x) + Math.round(pos.x)) % Math.round(skyboxWidth);
        selectedNew.getPos().y = (Math.round(8.0f * front.y) + Math.round(pos.y)) % Math.round(skyboxWidth);
        selectedNew.getPos().z = (Math.round(8.0f * front.z) + Math.round(pos.z)) % Math.round(skyboxWidth);

        if (!cannotPlace(levelContainer)) {
            selectedNew.getSecondaryColor().x = 0.0f;
            selectedNew.getSecondaryColor().y = 1.0f;
            selectedNew.getSecondaryColor().z = 0.0f;
            selectedNew.getSecondaryColor().w = 1.0f;
            selectedNew.setSecondaryTexture(SELECTED_TEXTURE);
        }

        levelContainer.getSoundFXPlayer().play(AudioFile.BLOCK_SELECT, selectedNew.getPos());
    }

    public static void selectCurrSolid(LevelContainer levelContainer) {
        deselect();
        Vector3f cameraPos = levelContainer.getLevelActors().getPlayer().getCamera().getPos();
        Vector3f cameraFront = levelContainer.getLevelActors().getPlayer().getCamera().getFront();
        float minDistanceOfSolid = Float.POSITIVE_INFINITY;
        Vector3f targetSolidPos = null;
        for (Vector3f solidPos : levelContainer.getSolidChunks().getPosMap().keySet()) {
            if (Block.intersectsRay(solidPos, cameraFront, cameraPos)) {
                float distance = Vector3f.distance(cameraPos.x, cameraPos.y, cameraPos.z, solidPos.x, solidPos.y, solidPos.z);
                if (distance < minDistanceOfSolid) {
                    minDistanceOfSolid = distance;
                    targetSolidPos = solidPos;
                }
            }
        }
        if (targetSolidPos == null) {
            return;
        }
        int chunkId = Chunk.chunkFunc(targetSolidPos);
        Chunk solidChunk = levelContainer.getSolidChunks().getChunk(chunkId);
        if (solidChunk != null) {
            int solidBlkIndex = 0;
            for (Block solidBlock : solidChunk.getBlocks().getBlockList()) {
                if (solidBlock.getPos().equals(targetSolidPos)) {
                    selectedCurr = solidBlock;

                    selectedCurr.getSecondaryColor().x = 1.0f;
                    selectedCurr.getSecondaryColor().y = 1.0f;
                    selectedCurr.getSecondaryColor().z = 0.0f;
                    selectedCurr.getSecondaryColor().w = 1.0f;

                    selectedCurr.setSecondaryTexture(SELECTED_TEXTURE);

                    selectedCurrIndex = solidBlkIndex;
                    break;
                }
                solidBlkIndex++;
            }
        }
    }

    public static void selectCurrFluid(LevelContainer levelContainer) {
        deselect();
        Vector3f cameraPos = levelContainer.getLevelActors().getPlayer().getCamera().getPos();
        Vector3f cameraFront = levelContainer.getLevelActors().getPlayer().getCamera().getFront();
        float minDistanceOfFluid = Float.POSITIVE_INFINITY;
        Vector3f targetFluidPos = null;
        for (Vector3f fluidPos : levelContainer.getFluidChunks().getPosMap().keySet()) {
            if (Block.intersectsRay(fluidPos, cameraFront, cameraPos)) {
                float distance = Vector3f.distance(cameraPos.x, cameraPos.y, cameraPos.z, fluidPos.x, fluidPos.y, fluidPos.z);
                if (distance < minDistanceOfFluid) {
                    minDistanceOfFluid = distance;
                    targetFluidPos = fluidPos;
                }
            }
        }
        if (targetFluidPos == null) {
            return;
        }
        int chunkId = Chunk.chunkFunc(targetFluidPos);
        Chunk fluidChunk = levelContainer.getFluidChunks().getChunk(chunkId);
        if (fluidChunk != null) {
            int solidBlkIndex = 0;
            for (Block fluidBLock : fluidChunk.getBlocks().getBlockList()) {
                if (fluidBLock.getPos().equals(targetFluidPos)) {
                    selectedCurr = fluidBLock;

                    selectedCurr.getSecondaryColor().x = 1.0f;
                    selectedCurr.getSecondaryColor().y = 1.0f;
                    selectedCurr.getSecondaryColor().z = 0.0f;
                    selectedCurr.getSecondaryColor().w = 1.0f;

                    selectedCurr.setSecondaryTexture(SELECTED_TEXTURE);

                    selectedCurrIndex = solidBlkIndex;

                    selectedCurr.enableAllFaces(false);
                    break;
                }
                solidBlkIndex++;
            }
        }
    }

    public static void deselect() {
        if (selectedCurr != null) {
            selectedCurr.setSecondaryTexture(null);
            selectedCurr.getSecondaryColor().x = 1.0f;
            selectedCurr.getSecondaryColor().y = 1.0f;
            selectedCurr.getSecondaryColor().z = 1.0f;
            selectedCurr.getSecondaryColor().w = 1.0f;
        }
        selectedNew = selectedCurr = null;
        selectedCurrIndex = -1;
    }

    public static void selectAdjacentSolid(LevelContainer levelContainer, int position) {
        deselect();
        selectCurrSolid(levelContainer);
        if (selectedCurr != null) {
            if (loaded == null) // first time it's null
            {
                loaded = new Block(false);
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

            if (!cannotPlace(levelContainer)) {
                selectedNew.getSecondaryColor().x = 0.0f;
                selectedNew.getSecondaryColor().y = 0.0f;
                selectedNew.getSecondaryColor().z = 1.0f;
                selectedNew.getSecondaryColor().w = 1.0f;
                selectedNew.setSecondaryTexture(SELECTED_TEXTURE);
            }
        }
    }

    public static void selectAdjacentFluid(LevelContainer levelContainer, int position) {
        deselect();
        selectCurrFluid(levelContainer);
        if (selectedCurr != null) {
            if (loaded == null) // first time it's null
            {
                loaded = new Block(false);
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

            if (!cannotPlace(levelContainer)) {
                selectedNew.getSecondaryColor().x = 0.0f;
                selectedNew.getSecondaryColor().y = 0.0f;
                selectedNew.getSecondaryColor().z = 1.0f;
                selectedNew.getSecondaryColor().w = 1.0f;
                selectedNew.setSecondaryTexture(SELECTED_TEXTURE);
            }
        }
    }

    private static boolean cannotPlace(LevelContainer levelContainer) {
        boolean cant = false;
        boolean placeOccupied = levelContainer.getFluidChunks().getPosMap().get(selectedNew.getPos()) != null
                || levelContainer.getFluidChunks().getPosMap().get(selectedNew.getPos()) != null;
        //----------------------------------------------------------------------
        boolean intsSolid = false;
        for (Vector3f solidPos : levelContainer.getSolidChunks().getPosMap().keySet()) {
            intsSolid = selectedNew.intersectsExactly(solidPos, 2.0f, 2.0f, 2.0f);
            if (intsSolid) {
                break;
            }
        }

        //----------------------------------------------------------------------
        boolean intsFluid = false;
        if (!intsSolid) {
            for (Vector3f fluidPos : levelContainer.getFluidChunks().getPosMap().keySet()) {
                intsFluid = selectedNew.intersectsExactly(fluidPos, 2.0f, 2.0f, 2.0f);
                if (intsFluid) {
                    break;
                }
            }
        }
        //----------------------------------------------------------------------
        boolean leavesSkybox = !LevelContainer.SKYBOX.intersectsEqually(selectedNew);
        if (selectedNew.isSolid()) {
            cant = levelContainer.maxSolidReached() || placeOccupied || intsSolid || intsFluid || leavesSkybox;
        } else {
            cant = levelContainer.maxFluidReached() || placeOccupied || intsSolid || intsFluid || leavesSkybox;
        }
        if (cant) {
            selectedNew.getSecondaryColor().x = 1.0f;
            selectedNew.getSecondaryColor().y = 0.0f;
            selectedNew.getSecondaryColor().z = 0.0f;
            selectedNew.getSecondaryColor().w = 1.0f;
            selectedNew.setSecondaryTexture(SELECTED_TEXTURE);
        }
        return cant;
    }

    public static void add(LevelContainer levelContainer) {
        if (selectedNew != null) {
            if (!cannotPlace(levelContainer) && !levelContainer.getLevelActors().getPlayer().getCamera().intersects(selectedNew)) {
                selectedNew.setSecondaryTexture(null);
                if (selectedNew.isSolid()) { // else if block is solid
                    levelContainer.getSolidChunks().addBlock(selectedNew); // add the block to the solid blocks                    
                    levelContainer.getSolidChunks().setBuffered(false);
                    levelContainer.getSoundFXPlayer().play(AudioFile.BLOCK_ADD, selectedNew.getPos());
                    //----------------------------------------------------------
                } else { // if block is fluid                    
                    levelContainer.getFluidChunks().addBlock(selectedNew); // add the block to the fluid blocks 
                    levelContainer.getFluidChunks().updateFluids();
                    levelContainer.getFluidChunks().setBuffered(false);
                    levelContainer.getSoundFXPlayer().play(AudioFile.BLOCK_ADD, selectedNew.getPos());
                    //----------------------------------------------------------                   
                }
                loaded = new Block(false);
                selectLoadedTexture();
            }
        }
        deselect();
    }

    public static void remove(LevelContainer levelContainer) {
        if (selectedCurr != null) {
            if (selectedCurr.isSolid()) {
                //--------------------------------------------------------------
                levelContainer.getSolidChunks().removeBlock(selectedCurr);
                levelContainer.getSolidChunks().setBuffered(false);
                levelContainer.getSoundFXPlayer().play(AudioFile.BLOCK_REMOVE, selectedCurr.getPos());
            } else {
                //--------------------------------------------------------------                
                levelContainer.getFluidChunks().removeBlock(selectedCurr);
                levelContainer.getFluidChunks().updateFluids();
                levelContainer.getFluidChunks().setBuffered(false);
                levelContainer.getSoundFXPlayer().play(AudioFile.BLOCK_REMOVE, selectedCurr.getPos());
            }
        }
        deselect();
    }

    private static void selectLoadedTexture() {
        Texture texture = null;
        if (loaded != null) {
            switch (value) {
                case 0:
                    texture = Texture.CRATE;
                    loaded.setSolid(true);
                    loaded.getPrimaryColor().w = 1.0f;
                    break;
                case 1:
                    texture = Texture.STONE;
                    loaded.setSolid(true);
                    loaded.getPrimaryColor().w = 1.0f;
                    break;
                case 2:
                    texture = Texture.WATER;
                    loaded.setSolid(false);
                    loaded.getPrimaryColor().w = 0.5f;
                    break;
                case 3:
                    texture = Texture.DOOM0;
                    loaded.setSolid(true);
                    loaded.getPrimaryColor().w = 1.0f;
                    break;
            }
            loaded.setPrimaryTexture(texture);
        }
    }

    public static void selectPrevTexture(LevelContainer levelContainer) {
        if (loaded != null) {
            if (value > MIN_VAL) {
                value--;
                selectLoadedTexture();
            }
        }
    }

    public static void selectNextTexture(LevelContainer levelContainer) {
        if (loaded != null) {
            if (value < MAX_VAL) {
                value++;
                selectLoadedTexture();
            }
        }
    }

    public static Block getSelectedNew() {
        return selectedNew;
    }

    public static Block getSelectedCurr() {
        return selectedCurr;
    }

    public static int getSelectedCurrIndex() {
        return selectedCurrIndex;
    }

    public static Texture getSELECTED_TEXTURE() {
        return SELECTED_TEXTURE;
    }

}
