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

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL15;
import rs.alexanderstojanovich.evgl.level.LightSource;
import rs.alexanderstojanovich.evgl.shaders.ShaderProgram;

/**
 *
 * @author Alexander Stojanovich <coas91@rocketmail.com>
 */
public class Tuple extends Blocks { // tuple is distinct rendering object for instanced rendering
    // all blocks in the tuple have the same properties, 
    // like model matrices, color and texture name, and enabled faces in 6-bit represenation

    protected final String name;

    protected FloatBuffer fb;
    protected int vbo = 0;

    protected final IntBuffer intBuff;
    protected int ibo = 0;
    protected final List<Vertex> vertices = new ArrayList<>();
    protected final int indicesNum;

    public static final Comparator<Tuple> TUPLE_COMP = new Comparator<Tuple>() {
        @Override
        public int compare(Tuple o1, Tuple o2) {
            return o1.getName().compareTo(o2.getName());
        }
    };

    public Tuple(String texName, int faceEnBits) {
        this.name = String.format("%s%02d", texName, faceEnBits);
        Block.deepCopyTo(vertices, texName);
        Block.setFaceBits(vertices, faceEnBits);
        this.intBuff = Block.createIntBuffer(faceEnBits);
        int numberOfOnes = 0;
        for (int j = Block.LEFT; j <= Block.FRONT; j++) {
            int mask = 1 << j;
            if ((faceEnBits & mask) != 0) {
                numberOfOnes++;
            }
        }
        this.indicesNum = 6 * numberOfOnes;
    }

    // renderer does this stuff prior to any rendering
    @Override
    public void bufferAll() {
        bufferVertices();
        bufferIndices();
        buffered = true;
    }

    public void bufferVertices() {
        fb = BufferUtils.createFloatBuffer(vertices.size() * Vertex.SIZE);
        for (Vertex vertex : vertices) {
            if (vertex.isEnabled()) {
                fb.put(vertex.getPos().x);
                fb.put(vertex.getPos().y);
                fb.put(vertex.getPos().z);

                fb.put(vertex.getNormal().x);
                fb.put(vertex.getNormal().y);
                fb.put(vertex.getNormal().z);

                fb.put(vertex.getUv().x);
                fb.put(vertex.getUv().y);
            }
        }
        fb.flip();

        if (vbo == 0) {
            vbo = GL15.glGenBuffers();
        }
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, fb, GL15.GL_STATIC_DRAW);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
    }

    public void updateVertices() {
        for (Vertex vertex : vertices) {
            if (vertex.isEnabled()) {
                fb.put(vertex.getPos().x);
                fb.put(vertex.getPos().y);
                fb.put(vertex.getPos().z);

                fb.put(vertex.getNormal().x);
                fb.put(vertex.getNormal().y);
                fb.put(vertex.getNormal().z);

                fb.put(vertex.getUv().x);
                fb.put(vertex.getUv().y);
            }
        }
        fb.flip();

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0, fb);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
    }

    public void bufferIndices() {
        // storing indices buffer on the graphics card
        if (ibo == 0) {
            ibo = GL15.glGenBuffers();
        }
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ibo);
        GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, intBuff, GL15.GL_STATIC_DRAW);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
    }

    @Deprecated
    public void release() {
//        GL15.glDeleteBuffers(blocks.getBigVbo());
//        GL15.glDeleteBuffers(vec3Vbo);
//        GL15.glDeleteBuffers(mat4Vbo);
    }

    @Override
    public void render(ShaderProgram shaderProgram, List<LightSource> lightSrc) {
        // if tuple has any blocks to be rendered and
        // if face bits are greater than zero, i.e. tuple has something to be 
        String texName = name.substring(0, 5);
        int faceEnBits = Integer.parseInt(name.substring(5));
        if (buffered && !blockList.isEmpty() && faceEnBits > 0) {
            Block.render(blockList, texName, vbo, ibo, indicesNum, lightSrc, shaderProgram);
        }
    }

    @Override
    public void renderIf(ShaderProgram shaderProgram, List<LightSource> lightSrc, Predicate<Block> predicate) {
        // if tuple has any blocks to be rendered and
        // if face bits are greater than zero, i.e. tuple has something to be rendered
        String texName = name.substring(0, 5);
        int faceEnBits = Integer.parseInt(name.substring(5));
        if (buffered && !blockList.isEmpty() && faceEnBits > 0) {
            Block.renderIf(blockList, texName, vbo, ibo, indicesNum, lightSrc, shaderProgram, predicate);
        }
    }

    @Override
    public void prepare(boolean cameraInFluid) {
        if (Boolean.logicalXor(cameraInFluid, verticesReversed)) {
            Block.reverseFaceVertexOrder(vertices);
            verticesReversed = !verticesReversed;
        }
    }

    @Override
    public void animate() {
        Block.animate(vertices, Block.INDICES);
        if (fb == null) {
            bufferVertices();
        } else {
            updateVertices();
        }
    }

    public String texName() {
        return name.substring(0, 5);
    }

    public int faceBits() {
        return Integer.parseInt(name.substring(5));
    }

    public List<Vertex> getVertices() {
        return vertices;
    }

    public FloatBuffer getFb() {
        return fb;
    }

    public int getVbo() {
        return vbo;
    }

    public int getIbo() {
        return ibo;
    }

    public String getName() {
        return name;
    }

    public IntBuffer getIntBuff() {
        return intBuff;
    }

    @Override
    public String toString() {
        return "Tuple{" + "name=" + name + '}';
    }

}
