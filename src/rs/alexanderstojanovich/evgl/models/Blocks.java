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

import java.util.List;
import java.util.function.Predicate;
import org.joml.Vector3f;
import org.magicwerk.brownies.collections.GapList;
import rs.alexanderstojanovich.evgl.shaders.ShaderProgram;

/**
 *
 * @author Alexander Stojanovich <coas91@rocketmail.com>
 */
public class Blocks { // mutual class for both solid blocks and fluid blocks with improved rendering

    private final List<Block> blockList = new GapList<>();
    private boolean cameraInFluid = false;
    private boolean verticesReversed = false;
    // array with offsets in the big float buffer
    // this is maximum amount of blocks of the type game can hold
    private boolean buffered = false;

    public synchronized void bufferAll() { // buffer both, call it before any rendering
        for (Block block : blockList) {
            block.bufferAll();
        }
        buffered = true;
    }

    public synchronized void animate() { // call only for fluid blocks
        for (Block block : blockList) {
            block.animate();
            block.bufferAll();
        }
    }

    public synchronized void prepare() { // call only for fluid blocks before rendering
        if (Boolean.logicalXor(cameraInFluid, verticesReversed)) {
            for (Block block : blockList) {
                block.reverseFaceVertexOrder();
            }
            verticesReversed = !verticesReversed;
        }
    }

    // standard render all
    public synchronized void render(ShaderProgram shaderProgram, Vector3f lightSrc) {
        if (shaderProgram != null && !blockList.isEmpty()) {
            for (Block block : blockList) {
                block.light = lightSrc;
                block.render(shaderProgram);
            }
        }
    }

    // powerful render if block is visible by camera
    public synchronized void renderIf(ShaderProgram shaderProgram, Vector3f lightSrc, Predicate<Block> predicate) {
        if (shaderProgram != null && !blockList.isEmpty()) {
            for (Block block : blockList) {
                if (predicate.test(block)) {
                    block.light = lightSrc;
                    block.render(shaderProgram);
                }
            }
        }
    }

    public boolean isBuffered() {
        return buffered;
    }

    public List<Block> getBlockList() {
        return blockList;
    }

    public boolean isVerticesReversed() {
        return verticesReversed;
    }

    public boolean isCameraInFluid() {
        return cameraInFluid;
    }

    public void setCameraInFluid(boolean cameraInFluid) {
        this.cameraInFluid = cameraInFluid;
    }

    public void setVerticesReversed(boolean verticesReversed) {
        this.verticesReversed = verticesReversed;
    }

    public void setBuffered(boolean buffered) {
        this.buffered = buffered;
    }

}
