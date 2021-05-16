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

import java.util.List;
import org.joml.Random;
import org.joml.SimplexNoise;
import org.joml.Vector3f;
import rs.alexanderstojanovich.evgl.models.Block;
import rs.alexanderstojanovich.evgl.models.Model;
import rs.alexanderstojanovich.evgl.util.DSLogger;
import rs.alexanderstojanovich.evgl.util.Vector3fUtils;

/**
 *
 * @author Alexander Stojanovich <coas91@rocketmail.com>
 */
public class RandomLevelGenerator {

    public static final int POS_MAX = (Math.round(LevelContainer.SKYBOX_WIDTH - 2.0f) >> 3) & 0xFFFFFFFE;
    public static final int POS_MIN = (Math.round(-LevelContainer.SKYBOX_WIDTH + 2.0f) >> 3) & 0xFFFFFFFE;

    public static final float ONE_OVER_POS_MAX = 1.0f / POS_MAX;
    public static final float ONE_OVER_POS_MIN = 1.0f / POS_MIN;
    public static final int POS_VAL = (POS_MAX - POS_MIN) / 2;

    private final Random random = new Random(0x123456789L);

    private final LevelContainer levelContainer;

    private int numberOfBlocks = 0;

    public RandomLevelGenerator(LevelContainer levelContainer) {
        this.levelContainer = levelContainer;
    }

    public RandomLevelGenerator(LevelContainer levelContainer, int numberOfBlocks) {
        this.levelContainer = levelContainer;
        this.numberOfBlocks = numberOfBlocks;
    }

    private String randomSolidTexture() {
        int randTexture = random.nextInt(3);
        switch (randTexture) {
            case 0:
                return "stone";
            case 1:
                return "crate";
            case 2:
                return "doom0";
        }
        return null;
    }

    private Block generateRandomSolidBlock() {
        float posx;
        float posy;
        float posz;
        Vector3f randPos;
        do {
            posx = (random.nextInt(POS_MAX - POS_MIN + 1) + POS_MIN) & 0xFFFFFFFE;
            posz = (random.nextInt(POS_MAX - POS_MIN + 1) + POS_MIN) & 0xFFFFFFFE;

            posy = Math.round(SimplexNoise.noise(posx / (float) POS_VAL, posz / (float) POS_VAL) * POS_VAL) & 0xFFFFFFFE;

            randPos = new Vector3f(posx, posy, posz);
        } while (LevelContainer.ALL_SOLID_MAP.containsKey(Vector3fUtils.hashCode(randPos))
                && LevelContainer.ALL_FLUID_MAP.containsKey(Vector3fUtils.hashCode(randPos))
                && levelContainer.getLevelActors().getPlayer().getModel().containsInsideEqually(randPos)
                && levelContainer.getLevelActors().getPlayer().getCamera().getPos().equals(randPos));

        Vector3f pos = randPos;
        // color chance
        Vector3f color = new Vector3f(1.0f, 1.0f, 1.0f);
        if (random.nextFloat() >= 0.75f) {
            Vector3f temp = new Vector3f();
            color = color.mul(random.nextFloat(), random.nextFloat(), random.nextFloat(), temp);
        }
        Block solidBlock = new Block(randomSolidTexture(), pos, color, true);

        levelContainer.getSolidChunks().addBlock(solidBlock, true);
        return solidBlock;
    }

    private Block generateRandomFluidBlock() {
        float posx;
        float posy;
        float posz;
        Vector3f randPos;
        do {
            posx = (random.nextInt(POS_MAX - POS_MIN + 1) + POS_MIN) & 0xFFFFFFFE;
            posz = (random.nextInt(POS_MAX - POS_MIN + 1) + POS_MIN) & 0xFFFFFFFE;

            posy = Math.round(SimplexNoise.noise(posx / (float) POS_VAL, posz / (float) POS_VAL) * POS_VAL) & 0xFFFFFFFE;

            randPos = new Vector3f(posx, posy, posz);
        } while (LevelContainer.ALL_SOLID_MAP.containsKey(Vector3fUtils.hashCode(randPos))
                && LevelContainer.ALL_FLUID_MAP.containsKey(Vector3fUtils.hashCode(randPos))
                && levelContainer.getLevelActors().getPlayer().getModel().containsInsideEqually(randPos)
                && levelContainer.getLevelActors().getPlayer().getCamera().getPos().equals(randPos));
        Vector3f pos = randPos;
        Vector3f color = new Vector3f(1.0f, 1.0f, 1.0f);
        if (random.nextFloat() >= 0.75f) {
            Vector3f temp = new Vector3f();
            color = color.mul(random.nextFloat(), random.nextFloat(), random.nextFloat(), temp);
        }
        Block fluidBlock = new Block("water", pos, color, false);

        levelContainer.getFluidChunks().addBlock(fluidBlock, true);
        return fluidBlock;
    }

    private Block generateRandomSolidBlockAdjacent(Block block) {
        List<Integer> possibleFaces = block.getAdjacentFreeFaceNumbers();
        if (possibleFaces.isEmpty()) {
            return null;
        }
        int randFace;
        Vector3f adjPos;
        do {
            randFace = possibleFaces.get(random.nextInt(possibleFaces.size()));
            adjPos = new Vector3f(block.getPos().x, block.getPos().y, block.getPos().z);
            switch (randFace) {
                case Block.LEFT:
                    adjPos.x -= block.getWidth() / 2.0f + block.getWidth() / 2.0f;
                    break;
                case Block.RIGHT:
                    adjPos.x += block.getWidth() / 2.0f + block.getWidth() / 2.0f;
                    break;
                case Block.BOTTOM:
                    adjPos.y -= block.getHeight() / 2.0f + block.getHeight() / 2.0f;
                    break;
                case Block.TOP:
                    adjPos.y += block.getHeight() / 2.0f + block.getHeight() / 2.0f;
                    break;
                case Block.BACK:
                    adjPos.z -= block.getDepth() / 2.0f + block.getDepth() / 2.0f;
                    break;
                case Block.FRONT:
                    adjPos.z += block.getDepth() / 2.0f + block.getDepth() / 2.0f;
                    break;
                default:
                    break;
            }
        } while (LevelContainer.ALL_SOLID_MAP.containsKey(Vector3fUtils.hashCode(adjPos))
                && LevelContainer.ALL_FLUID_MAP.containsKey(Vector3fUtils.hashCode(adjPos))
                && levelContainer.getLevelActors().getPlayer().getModel().containsInsideEqually(adjPos)
                && levelContainer.getLevelActors().getPlayer().getCamera().getPos().equals(adjPos));
        String adjTexture = randomSolidTexture();
        Vector3f color = new Vector3f(1.0f, 1.0f, 1.0f);
        if (random.nextFloat() >= 0.75f) {
            Vector3f temp = new Vector3f();
            color = color.mul(random.nextFloat(), random.nextFloat(), random.nextFloat(), temp);
        }
        Block solidAdjBlock = new Block(adjTexture, adjPos, color, true);

        levelContainer.getSolidChunks().addBlock(solidAdjBlock, true);
        return solidAdjBlock;
    }

    private Block generateRandomFluidBlockAdjacent(Block block) {
        List<Integer> possibleFaces = block.getAdjacentFreeFaceNumbers();
        if (possibleFaces.isEmpty()) {
            return null;
        }
        int randFace;
        Vector3f adjPos;
        do {
            randFace = possibleFaces.get(random.nextInt(possibleFaces.size()));
            adjPos = new Vector3f(block.getPos().x, block.getPos().y, block.getPos().z);
            switch (randFace) {
                case Block.LEFT:
                    adjPos.x -= block.getWidth() / 2.0f + block.getWidth() / 2.0f;
                    break;
                case Block.RIGHT:
                    adjPos.x += block.getWidth() / 2.0f + block.getWidth() / 2.0f;
                    break;
                case Block.BOTTOM:
                    adjPos.y -= block.getHeight() / 2.0f + block.getHeight() / 2.0f;
                    break;
                case Block.TOP:
                    adjPos.y += block.getHeight() / 2.0f + block.getHeight() / 2.0f;
                    break;
                case Block.BACK:
                    adjPos.z -= block.getDepth() / 2.0f + block.getDepth() / 2.0f;
                    break;
                case Block.FRONT:
                    adjPos.z += block.getDepth() / 2.0f + block.getDepth() / 2.0f;
                    break;
                default:
                    break;
            }
        } while (LevelContainer.ALL_SOLID_MAP.containsKey(Vector3fUtils.hashCode(adjPos))
                && LevelContainer.ALL_FLUID_MAP.containsKey(Vector3fUtils.hashCode(adjPos))
                && levelContainer.getLevelActors().getPlayer().getModel().containsInsideEqually(adjPos)
                && levelContainer.getLevelActors().getPlayer().getCamera().getPos().equals(adjPos));
        String adjTexture = "water";
        Vector3f color = new Vector3f(1.0f, 1.0f, 1.0f);
        if (random.nextFloat() >= 0.75f) {
            Vector3f temp = new Vector3f();
            color = color.mul(random.nextFloat(), random.nextFloat(), random.nextFloat(), temp);
        }
        Block fluidAdjBlock = new Block(adjTexture, adjPos, color, false);

        levelContainer.getFluidChunks().addBlock(fluidAdjBlock, true);
        return fluidAdjBlock;
    }

    public void generate() {
        if (levelContainer.getProgress() == 0.0f) {
            DSLogger.reportInfo("Generating random level (" + numberOfBlocks + " blocks)..", null);
            int solidBlocks = Math.min(random.nextInt(numberOfBlocks), LevelContainer.MAX_NUM_OF_SOLID_BLOCKS);
            int fluidBlocks = Math.min(numberOfBlocks - solidBlocks, LevelContainer.MAX_NUM_OF_FLUID_BLOCKS);

            final int totalAmount = solidBlocks + fluidBlocks;

            if (totalAmount > 0) {
                Model playerMdl = levelContainer.getLevelActors().getPlayer().getModel();
                Vector3f playerPos = levelContainer.getLevelActors().getPlayer().getCamera().getPos();

                // 1. Noise Part                                
                noise:
                for (int x = POS_MIN; x <= POS_MAX; x += 2) {
                    for (int z = POS_MIN; z <= POS_MAX; z += 2) {

                        // noise & y - coordinate
                        float noise = SimplexNoise.noise(x / (float) POS_VAL, z / (float) POS_VAL);
                        float y = Math.round(noise * POS_VAL) & 0xFFFFFFFE;

                        Vector3f pos = new Vector3f(x, y, z);

                        // prevent conflict (collision)
                        if (playerMdl.containsInsideEqually(pos) || playerPos.equals(pos)) {
                            continue;
                        }

                        // color chance
                        Vector3f color = new Vector3f(1.0f, 1.0f, 1.0f);
                        if (random.nextFloat() >= 0.75f) {
                            Vector3f tempc = new Vector3f();
                            color = color.mul(random.nextFloat(), random.nextFloat(), random.nextFloat(), tempc);
                        }

                        // solid chance
                        boolean useSolidChance = (solidBlocks > 0 && fluidBlocks > 0);

                        if (useSolidChance) {
                            float solidChance = 1.0f / (1.0f + Math.abs(noise) / 2.0f);
                            if (solidChance >= 0.5f) {
                                String tex = "stone";

                                if (random.nextFloat() >= 0.5f) {
                                    tex = randomSolidTexture();
                                }

                                Block solidBlock = new Block(tex, pos, color, true);
                                levelContainer.getSolidChunks().addBlock(solidBlock, true);
                                levelContainer.incProgress(100.0f / (float) totalAmount);
                                solidBlocks--;
                            } else if (fluidBlocks > 0) {
                                String tex = "water";

                                Block fluidBlock = new Block(tex, pos, color, false);
                                levelContainer.getFluidChunks().addBlock(fluidBlock, true);
                                levelContainer.incProgress(100.0f / (float) totalAmount);
                                fluidBlocks--;
                            }
                        } else if (solidBlocks > 0) {
                            String tex = "stone";
                            if (random.nextFloat() >= 0.75f) {
                                tex = randomSolidTexture();
                            }

                            Block solidBlock = new Block(tex, pos, color, true);
                            levelContainer.getSolidChunks().addBlock(solidBlock, true);
                            levelContainer.incProgress(100.0f / (float) totalAmount);
                            solidBlocks--;
                        } else if (fluidBlocks > 0) {
                            String tex = "water";

                            Block fluidBlock = new Block(tex, pos, color, false);
                            levelContainer.getFluidChunks().addBlock(fluidBlock, true);
                            levelContainer.incProgress(100.0f / (float) totalAmount);
                            fluidBlocks--;
                        }

                        if ((solidBlocks == 0 && fluidBlocks == 0)
                                || levelContainer.getMyWindow().shouldClose()) {
                            break noise;
                        }
                    }
                }
                // --------------------------------------------------------------
                // 2. Random part
                //beta 
                float beta = random.nextFloat();
                int maxSolidBatchSize = (int) ((1.0f - beta) * solidBlocks);
                int maxFluidBatchSize = (int) (beta * fluidBlocks);

                while ((solidBlocks > 0 || fluidBlocks > 0)
                        && !levelContainer.getMyWindow().shouldClose()) {
                    if (solidBlocks > 0) {
                        int solidBatch = 1 + random.nextInt(Math.min(maxSolidBatchSize, solidBlocks));
                        Block solidBlock = null;
                        Block solidAdjBlock = null;
                        while (solidBatch > 0
                                && !levelContainer.getMyWindow().shouldClose()) {
                            if (solidBlock == null) {
                                solidBlock = generateRandomSolidBlock();
                                solidAdjBlock = solidBlock;
                                solidBatch--;
                                solidBlocks--;
                                // this provides external monitoring of level generation progress                        
                                levelContainer.incProgress(100.0f / (float) totalAmount);
                            } else if (solidAdjBlock != null) {
                                solidAdjBlock = generateRandomSolidBlockAdjacent(solidBlock);
                                if (solidAdjBlock != null) {
                                    solidBatch--;
                                    solidBlocks--;
                                    // this provides external monitoring of level generation progress                        
                                    levelContainer.incProgress(100.0f / (float) totalAmount);
                                }
                                //--------------------------------------------------
                                if (random.nextInt(2) == 0) {
                                    solidBlock = solidAdjBlock;
                                }
                            } else {
                                solidBlock = null;
                            }
                        }
                    }

                    if (fluidBlocks > 0) {
                        int fluidBatch = 1 + random.nextInt(Math.min(maxFluidBatchSize, fluidBlocks));
                        Block fluidBlock = null;
                        Block fluidAdjBlock = null;
                        while (fluidBatch > 0
                                && !levelContainer.getMyWindow().shouldClose()) {
                            if (fluidBlock == null) {
                                fluidBlock = generateRandomFluidBlock();
                                fluidAdjBlock = fluidBlock;
                                fluidBatch--;
                                fluidBlocks--;
                                // this provides external monitoring of level generation progress                        
                                levelContainer.incProgress(100.0f / (float) totalAmount);
                            } else if (fluidAdjBlock != null) {
                                fluidAdjBlock = generateRandomFluidBlockAdjacent(fluidBlock);
                                if (fluidAdjBlock != null) {
                                    fluidBatch--;
                                    fluidBlocks--;
                                    // this provides external monitoring of level generation progress                        
                                    levelContainer.incProgress(100.0f / (float) totalAmount);
                                } //--------------------------------------------------
                                if (random.nextInt(2) == 0) {
                                    fluidBlock = fluidAdjBlock;
                                }
                            } else {
                                fluidBlock = null;
                            }
                        }
                    }
                }
            }
        }

        DSLogger.reportInfo("Finished!", null);
    }

    public LevelContainer getLevelContainer() {
        return levelContainer;
    }

    public int getNumberOfBlocks() {
        return numberOfBlocks;
    }

    public void setNumberOfBlocks(int numberOfBlocks) {
        this.numberOfBlocks = numberOfBlocks;
    }

}
