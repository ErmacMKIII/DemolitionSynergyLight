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
import org.joml.Vector3f;
import rs.alexanderstojanovich.evgl.models.Block;
import rs.alexanderstojanovich.evgl.util.DSLogger;
import rs.alexanderstojanovich.evgl.util.MathUtils;
import rs.alexanderstojanovich.evgl.util.Pair;
import rs.alexanderstojanovich.evgl.util.Vector3fColors;

/**
 *
 * @author Alexander Stojanovich <coas91@rocketmail.com>
 */
public class RandomLevelGenerator {

    public static final int POS_MAX = (Math.round(LevelContainer.SKYBOX_WIDTH - 2.0f)) & 0xFFFFFFFE;
    public static final int POS_MIN = (Math.round(-LevelContainer.SKYBOX_WIDTH + 2.0f)) & 0xFFFFFFFE;

    public static final float CUBIC = 1.07E-14f;
    public static final float QUADRATIC = -3.2E-9f;
    public static final float LINEAR = 4.3E-4f;
    public static final float CONST = 15.0f;

    public static final float ONE_OVER_POS_MAX = 1.0f / POS_MAX;
    public static final float ONE_OVER_POS_MIN = 1.0f / POS_MIN;

    private final Random random = new Random(0x123456789L);

    private final LevelContainer levelContainer;

    private int numberOfBlocks = 0;

    // MAX NUMER OF LIGHTS MUST NOT REACH 255 (+1 Reserved for player)
    public static int numOfLights = 0;
    public static int maxNumOfLights = 0;

    public RandomLevelGenerator(LevelContainer levelContainer) {
        this.levelContainer = levelContainer;
    }

    public RandomLevelGenerator(LevelContainer levelContainer, int numberOfBlocks) {
        this.levelContainer = levelContainer;
        this.numberOfBlocks = numberOfBlocks;
    }

    private String randomSolidTexture(boolean includingLight) {
        int randTexture = random.nextInt(includingLight ? 4 : 3);
        switch (randTexture) {
            case 0:
                return "stone";
            case 1:
                return "crate";
            case 2:
                return "doom0";
            case 3:
                if (numOfLights < maxNumOfLights) {
                    numOfLights++;
                    return "reflc";
                } else {
                    return "stone";
                }
        }

        return null;
    }

    private boolean repeatCondition(Vector3f pos) {
        return LevelContainer.ALL_SOLID_MAP.containsKey(pos)
                || LevelContainer.ALL_FLUID_MAP.containsKey(pos)
                || levelContainer.getLevelActors().getPlayer().getModel().containsInsideEqually(pos)
                || levelContainer.getLevelActors().getPlayer().getCamera().getPos().equals(pos)
                || levelContainer.getMyWindow().shouldClose();
    }

    private Block generateRandomSolidBlock(int posMin, int posMax) {
        float posx;
        float posy;
        float posz;
        Vector3f randPos;
        do {
            posx = (random.nextInt(posMax - posMin + 1) + posMin) & 0xFFFFFFFE;
            posy = (random.nextInt(posMax - posMin + 1) + posMin) & 0xFFFFFFFE;
            posz = (random.nextInt(posMax - posMin + 1) + posMin) & 0xFFFFFFFE;

            randPos = new Vector3f(posx, posy, posz);
        } while (repeatCondition(randPos) && !levelContainer.getMyWindow().shouldClose());

        Vector3f pos = randPos;
        // color chance
        Vector3f color = new Vector3f(1.0f, 1.0f, 1.0f);
        if (random.nextFloat() >= 0.95f) {
            Vector3f temp = new Vector3f();
            color = color.mul(random.nextFloat(), random.nextFloat(), random.nextFloat(), temp);
        }

        String tex = "stone";
        if (random.nextFloat() >= 0.5f) {
            tex = randomSolidTexture(true);
        }

        Block solidBlock = new Block(tex, pos, color, true);

        levelContainer.getSolidChunks().addBlock(solidBlock, true);
        return solidBlock;
    }

    private Block generateRandomFluidBlock(int posMin, int posMax) {
        float posx;
        float posy;
        float posz;
        Vector3f randPos;
        do {
            posx = (random.nextInt(posMax - posMin + 1) + posMin) & 0xFFFFFFFE;
            posy = (random.nextInt(posMax - posMin + 1) + posMin) & 0xFFFFFFFE;
            posz = (random.nextInt(posMax - posMin + 1) + posMin) & 0xFFFFFFFE;

            randPos = new Vector3f(posx, posy, posz);
        } while (repeatCondition(randPos) && !levelContainer.getMyWindow().shouldClose());

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
        } while (repeatCondition(adjPos) && !levelContainer.getMyWindow().shouldClose());

        Vector3f color = new Vector3f(1.0f, 1.0f, 1.0f);
        if (random.nextFloat() >= 0.95f) {
            Vector3f temp = new Vector3f();
            color = color.mul(random.nextFloat(), random.nextFloat(), random.nextFloat(), temp);
        }

        String adjTex = "stone";
        if (random.nextFloat() >= 0.5f) {
            adjTex = randomSolidTexture(false);
        }

        Block solidAdjBlock = new Block(adjTex, adjPos, color, true);

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
        } while (repeatCondition(adjPos) && !levelContainer.getMyWindow().shouldClose());

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

    //---------------------------------------------------------------------------------------------------------------------------
    //---------------------------------------------------------------------------------------------------------------------------
    //---------------------------------------------------------------------------------------------------------------------------
    private void generateByNoise(int solidBlocks, int fluidBlocks, int totalAmount, int posMin, int posMax) {
//        DSLogger.reportInfo("By Noise: solidBlks = " + solidBlocks + ", fluidBlks = " + fluidBlocks, null);
        // make "stone" terrain
        noise1:
        for (int x = posMin; x <= posMax; x += 2) {
            for (int z = posMin; z <= posMax; z += 2) {

                int yMid = Math.round(MathUtils.noise(16, x, z, 0.5f, 0.007f, posMin, posMax, 2.0f)) & 0xFFFFFFFE;
                int yTop = Math.round(MathUtils.noise(16, x, z, 0.5f, 0.007f, yMid, posMax, 2.0f)) & 0xFFFFFFFE;
                int yBottom = Math.round(MathUtils.noise(16, x, z, 0.5f, 0.007f, posMin, yMid, 2.0f)) & 0xFFFFFFFE;

                for (int y = yBottom; y <= yTop; y += 2) {
                    Vector3f pos = new Vector3f(x, y, z);

                    if (repeatCondition(pos)) {
                        continue;
                    }

                    // color chance
                    Vector3f color = new Vector3f(1.0f, 1.0f, 1.0f);
                    if (random.nextFloat() >= 0.95f) {
                        Vector3f tempc = new Vector3f();
                        color = color.mul(random.nextFloat(), random.nextFloat(), random.nextFloat(), tempc);
                    }

                    if (solidBlocks > 0) {
                        String tex = "stone";

                        if (random.nextFloat() >= 0.95f) {
                            tex = randomSolidTexture(false);
                        }

                        Block solidBlock = new Block(tex, pos, color, true);
                        levelContainer.getSolidChunks().addBlock(solidBlock, true);
                        levelContainer.incProgress(100.0f / (float) totalAmount);
                        solidBlocks--;

                        if (solidBlocks == 0) {
                            break noise1;
                        }
                    }
                }
            }
        }

        final int mask1 = 0x08; // bottom only mask
        final int mask2 = 0x17; // bottom exclusive mask
        // make water
        noise2:
        for (int x = posMin; x <= posMax; x += 2) {
            for (int z = posMin; z <= posMax; z += 2) {

                int yMid = Math.round(MathUtils.noise(16, x, z, 0.5f, 0.0035f, posMin, posMax, 2.0f)) & 0xFFFFFFFE;
                int yTop = Math.round(MathUtils.noise(16, x, z, 0.5f, 0.0035f, yMid, posMax, 2.0f)) & 0xFFFFFFFE;
                int yBottom = Math.round(MathUtils.noise(16, x, z, 0.5f, 0.0035f, posMin, yMid, 2.0f)) & 0xFFFFFFFE;

                for (int y = yBottom; y <= yTop; y += 2) {
                    Vector3f pos = new Vector3f(x, y, z);

                    if (repeatCondition(pos)) {
                        continue;
                    }

                    int sbits = 0;
                    Pair<String, Byte> spair = LevelContainer.ALL_SOLID_MAP.get(pos);
                    if (spair != null) {
                        sbits = spair.getValue();
                    }

                    int fbits = 0;
                    Pair<String, Byte> fpair = LevelContainer.ALL_FLUID_MAP.get(pos);
                    if (fpair != null) {
                        fbits = fpair.getValue();
                    }

                    int tbits = (sbits & mask1) | (~fbits & mask2);

                    // color chance
                    Vector3f color = new Vector3f(1.0f, 1.0f, 1.0f);
                    if (random.nextFloat() >= 0.95f) {
                        Vector3f tempc = new Vector3f();
                        color = color.mul(random.nextFloat(), random.nextFloat(), random.nextFloat(), tempc);
                    }

                    if (fluidBlocks > 0 && tbits != 0) {
                        String tex = "water";

                        Block fluidBlock = new Block(tex, pos, color, false);
                        levelContainer.getFluidChunks().addBlock(fluidBlock, true);
                        levelContainer.incProgress(100.0f / (float) totalAmount);
                        fluidBlocks--;
                    }

                    if (fluidBlocks == 0) {
                        break noise2;
                    }
                }
            }
        }
    }

    private void generateByRandom(int solidBlocks, int fluidBlocks, int totalAmount, int posMin, int posMax) {
//        DSLogger.reportInfo("By Random: solidBlks = " + solidBlocks + ", fluidBlks = " + fluidBlocks, null);
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
                        solidBlock = generateRandomSolidBlock(posMin, posMax);
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
                        } else {
                            solidBlock = null;
                        }
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
                        fluidBlock = generateRandomFluidBlock(posMin, posMax);
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
                        } else {
                            fluidBlock = null;
                        }
                    }
                }
            }
        }
    }

    private void generateFluidSeries(int solidBlocks) {
//        DSLogger.reportInfo("By Noise: solidBlks = " + solidBlocks, null);
        levelContainer.setProgress(0.0f);
        List<Block> totalFldBlkList = levelContainer.getFluidChunks().getTotalList();
        for (Block fluidBlock : totalFldBlkList) {
            if (levelContainer.getMyWindow().shouldClose()) {
                break;
            }

            if (solidBlocks == 0) {
                break;
            }

            List<Integer> freeFaces = fluidBlock.getAdjacentFreeFaceNumbers();
            for (int faceNum : freeFaces) {
                if (faceNum == Block.TOP && random.nextFloat() >= 0.5f) {
                    continue;
                }
                Vector3f spos = fluidBlock.getAdjacentPos(faceNum);
                Block solidBlock = new Block("stone", spos, Vector3fColors.WHITE, true);
                levelContainer.getSolidChunks().addBlock(solidBlock, true);
                solidBlocks--;
            }
            levelContainer.incProgress(100.0f / (float) totalFldBlkList.size());
        }
    }

    //---------------------------------------------------------------------------------------------------------------------------
    public void generate() {
        if (levelContainer.getProgress() == 0.0f) {
            DSLogger.reportInfo("Generating random level (" + numberOfBlocks + " blocks)..", null);
            // define alpha: solid to fluid ratio
            float alpha = 0.5f * random.nextFloat() + 0.2f;
            int solidBlocks = Math.min(Math.round(alpha * numberOfBlocks), LevelContainer.MAX_NUM_OF_SOLID_BLOCKS);
            int fluidBlocks = Math.min(numberOfBlocks - solidBlocks, LevelContainer.MAX_NUM_OF_FLUID_BLOCKS);

            final int totalAmount = solidBlocks + fluidBlocks;

            maxNumOfLights = Math.round(48.5f * totalAmount / 25000.0f);

            if (totalAmount > 0) {
                //---------------------------------------------------------------------------------------------------------------------------
                // define beta: noise to random ratio
                float beta = (1 + random.nextInt(3)) * 0.1f;
                // define gamma: random fluid to series solid ratio
                //---------------------------------------------------------------------------------------------------------------------------
                float valueN = MathUtils.polynomial(CUBIC, QUADRATIC, LINEAR, CONST, numberOfBlocks);
                final int posN_Min = Math.round(-valueN) & 0xFFFFFFFE;
                final int posN_Max = Math.round(valueN) & 0xFFFFFFFE;

                float valueR = 1.75f * valueN;
                final int posR_Min = Math.round(-valueR) & 0xFFFFFFFE;
                final int posR_Max = Math.round(valueR) & 0xFFFFFFFE;

                //---------------------------------------------------------------------------------------------------------------------------
                DSLogger.reportInfo("Generating Part I - Noise", null);
                // 1. Noise Part                                   
                generateByNoise(Math.round((1.0f - beta) * solidBlocks), Math.round((1.0f - beta) * fluidBlocks), totalAmount, posN_Min, posN_Max);
                DSLogger.reportInfo("Done.", null);
                // --------------------------------------------------------------
                DSLogger.reportInfo("Generating Part II - Random", null);
                // 2. Random Part                 
                generateByRandom(Math.round(beta * solidBlocks), Math.round(beta * fluidBlocks), totalAmount, posR_Min, posR_Max);
                DSLogger.reportInfo("Done.", null);
                // --------------------------------------------------------------
                DSLogger.reportInfo("Generating Part III - Fluid Series", null);
                // 3. Fluid Series
                generateFluidSeries(Math.round(beta * solidBlocks));
                DSLogger.reportInfo("Done.", null);
            }
        }

        DSLogger.reportInfo("All finished!", null);
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
