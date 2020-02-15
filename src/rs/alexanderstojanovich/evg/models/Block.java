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
package rs.alexanderstojanovich.evg.models;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import rs.alexanderstojanovich.evg.main.Game;
import rs.alexanderstojanovich.evg.shaders.ShaderProgram;
import rs.alexanderstojanovich.evg.texture.Texture;

/**
 *
 * @author Coa
 */
public class Block extends Model {

    public static final int NONE = -1;
    public static final int LEFT = 0;
    public static final int RIGHT = 1;
    public static final int BOTTOM = 2;
    public static final int TOP = 3;
    public static final int BACK = 4;
    public static final int FRONT = 5;
    // which faces we enabled for rendering and which we disabled
    private final boolean[] enabledFaces = new boolean[6];

    private final Map<Integer, Integer> adjacentBlockMap = new HashMap<>(); // helps locating neighbours blocks           
    private boolean verticesReversed = false;

    public static final List<Vector3f> FACE_NORMALS = new ArrayList<>();

    public static final int VERTEX_COUNT = 24;
    public static final int INDICES_COUNT = 36;

    public static final Block CONST = new Block(true);

    public static final Comparator<Block> Y_AXIS_COMP = new Comparator<Block>() {
        @Override
        public int compare(Block o1, Block o2) {
            if (o1.getPos().y > o2.getPos().y) {
                return 1;
            } else if (o1.getPos().y == o2.getPos().y) {
                return 0;
            } else {
                return -1;
            }
        }
    };

    static {
        FACE_NORMALS.add(new Vector3f(-1.0f, 0.0f, 0.0f));
        FACE_NORMALS.add(new Vector3f(1.0f, 0.0f, 0.0f));
        FACE_NORMALS.add(new Vector3f(0.0f, -1.0f, 0.0f));
        FACE_NORMALS.add(new Vector3f(0.0f, 1.0f, 0.0f));
        FACE_NORMALS.add(new Vector3f(0.0f, 0.0f, -1.0f));
        FACE_NORMALS.add(new Vector3f(0.0f, 0.0f, 1.0f));
    }

    public Block(boolean selfBuffer) {
        super();
        Arrays.fill(enabledFaces, true);
        readFromTxtFile("cube.txt");
        if (selfBuffer) {
            bufferVertices();
            bufferIndices();
            buffered = true;
        }
        calcDims();
    }

    public Block(boolean selfBuffer, Texture primaryTexture) {
        super();
        this.primaryTexture = primaryTexture;
        Arrays.fill(enabledFaces, true);
        readFromTxtFile("cube.txt");
        if (selfBuffer) {
            bufferVertices();
            bufferIndices();
            buffered = true;
        }
        calcDims();
    }

    public Block(boolean selfBuffer, Texture primaryTexture, Vector3f pos, Vector4f primaryColor, boolean solid) {
        super();
        this.primaryTexture = primaryTexture;
        Arrays.fill(enabledFaces, true);
        this.pos = pos;
        this.primaryColor = primaryColor;
        this.solid = solid;
        readFromTxtFile("cube.txt");
        if (selfBuffer) {
            bufferVertices();
            bufferIndices();
            buffered = true;
        }
        calcDims();
    }

//    private void readFromTxtFile(String fileName) {
//        File file = new File(Game.DATA_ZIP);
//        if (!file.exists()) {
//            DSLogger.reportError("Cannot find zip archive " + Game.DATA_ZIP + "!");
//            return;
//        }
//        ZipFile zipFile = null;
//        BufferedReader br = null;
//        try {
//            zipFile = new ZipFile(file);
//            InputStream txtInput = null;
//            for (ZipEntry zipEntry : Collections.list(zipFile.entries())) {
//                if (zipEntry.getName().equals(Game.WORLD_ENTRY + fileName)) {
//                    txtInput = zipFile.getInputStream(zipEntry);
//                }
//            }
//            if (txtInput == null) {
//                DSLogger.reportError("Cannot find resource " + Game.WORLD_ENTRY + fileName + "!");
//                return;
//            }
//            br = new BufferedReader(new InputStreamReader(txtInput));
//            String line;
//            while ((line = br.readLine()) != null) {
//                if (line.startsWith("v:")) {
//                    String[] things = line.split(" ");
//                    Vector3f pos = new Vector3f(Float.parseFloat(things[1]), Float.parseFloat(things[2]), Float.parseFloat(things[3]));
//                    Vector3f normal = new Vector3f(Float.parseFloat(things[5]), Float.parseFloat(things[6]), Float.parseFloat(things[7]));
//                    Vector2f uv = new Vector2f(Float.parseFloat(things[9]), Float.parseFloat(things[10]));
//                    Vertex v = new Vertex(pos, normal, uv);
//                    vertices.add(v);
//                } else if (line.startsWith("i:")) {
//                    String[] things = line.split(" ");
//                    indices.add(Integer.parseInt(things[1]));
//                    indices.add(Integer.parseInt(things[2]));
//                    indices.add(Integer.parseInt(things[3]));
//                }
//            }
//        } catch (FileNotFoundException ex) {
//            DSLogger.reportFatalError(ex.getMessage, ex);
//        } catch (IOException ex) {
//            DSLogger.reportFatalError(ex.getMessage, ex);
//        } finally {
//            if (zipFile != null) {
//                try {
//                    zipFile.close();
//                } catch (IOException ex) {
//                    DSLogger.reportFatalError(ex.getMessage, ex);
//                }
//            }
//        }
//    }
    private void readFromTxtFile(String fileName) {
        InputStream in = getClass().getResourceAsStream(Game.RESOURCES_DIR + fileName);
        BufferedReader br = null;
        try {
            br = new BufferedReader(new InputStreamReader(in));
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("v:")) {
                    String[] things = line.replace("v:", "").trim().split(",|->", -1);
                    Vector3f pos = new Vector3f(Float.parseFloat(things[0]), Float.parseFloat(things[1]), Float.parseFloat(things[2]));
                    Vector3f normal = new Vector3f(Float.parseFloat(things[3]), Float.parseFloat(things[4]), Float.parseFloat(things[5]));
                    Vector2f uv = new Vector2f(Float.parseFloat(things[6]), Float.parseFloat(things[7]));
                    Vertex v = new Vertex(pos, normal, uv);
                    vertices.add(v);
                } else if (line.startsWith("i:")) {
                    String[] things = line.replace("i:", "").trim().split(" ", -1);
                    indices.add(Integer.parseInt(things[0]));
                    indices.add(Integer.parseInt(things[1]));
                    indices.add(Integer.parseInt(things[2]));
                }
            }
        } catch (FileNotFoundException ex) {
            Logger.getLogger(Block.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(Block.class.getName()).log(Level.SEVERE, null, ex);
        }
        if (br != null) {
            try {
                br.close();
            } catch (IOException ex) {
                Logger.getLogger(Block.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    private void bufferVertices() {
        // storing vertices and FACE_NORMALS in the buffer
        FloatBuffer fb = BufferUtils.createFloatBuffer(vertices.size() * Vertex.SIZE);
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
        // storing vertices and FACE_NORMALS buffer on the graphics card
        vbo = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, fb, GL15.GL_STATIC_DRAW);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
    }

    private void bufferIndices() {
        // storing indices in the buffer
        IntBuffer ib = createIntBuffer(getFaceBits());
        // storing indices buffer on the graphics card
        ibo = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, ibo);
        GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, ib, GL15.GL_STATIC_DRAW);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
    }

    @Override
    public void bufferAll() { // explicit call to buffer unbuffered before the rendering
        bufferVertices();
        bufferIndices();
        buffered = true;
    }

    private void calcDims() {
        Vector3f vect = vertices.get(0).getPos();
        float xMin = vect.x;
        float yMin = vect.y;
        float zMin = vect.z;

        float xMax = vect.x;
        float yMax = vect.y;
        float zMax = vect.z;

        for (int i = 1; i < vertices.size(); i++) {
            vect = vertices.get(i).getPos();
            xMin = Math.min(xMin, vect.x);
            yMin = Math.min(yMin, vect.y);
            zMin = Math.min(zMin, vect.z);

            xMax = Math.max(xMax, vect.x);
            yMax = Math.max(yMax, vect.y);
            zMax = Math.max(zMax, vect.z);
        }

        width = Math.abs(xMax - xMin) * scale;
        height = Math.abs(yMax - yMin) * scale;
        depth = Math.abs(zMax - zMin) * scale;
    }

    @Override
    public String toString() {
        return "Block{" + "texture=" + primaryTexture.getImage().getFileName() + ", pos=" + pos + ", scale=" + scale + ", color=" + primaryColor + ", solid=" + solid + '}';
    }

    public int faceAdjacentBy(Block block) { // which face of "this" is adjacent to compared "block"
        int faceNum = NONE;
        if (((this.pos.x - this.width / 2.0f) - (block.pos.x + block.width / 2.0f)) == 0.0f
                && (this.getPos().y - block.getPos().y) == 0.0f
                && (this.getPos().z - block.getPos().z) == 0.0f) {
            faceNum = LEFT;
        } else if (((this.pos.x + this.width / 2.0f) - (block.pos.x - block.width / 2.0f)) == 0.0f
                && (this.getPos().y - block.getPos().y) == 0.0f
                && (this.getPos().z - block.getPos().z) == 0.0f) {
            faceNum = RIGHT;
        } else if (((this.pos.y - this.height / 2.0f) - (block.pos.y + block.height / 2.0f)) == 0.0f
                && (this.getPos().z - block.getPos().z) == 0.0f
                && (this.getPos().x - block.getPos().x) == 0.0f) {
            faceNum = BOTTOM;
        } else if (((this.pos.y + this.height / 2.0f) - (block.pos.y - block.height / 2.0f)) == 0.0f
                && (this.getPos().z - block.getPos().z) == 0.0f
                && (this.getPos().x - block.getPos().x) == 0.0f) {
            faceNum = TOP;
        } else if (((this.pos.z - this.depth / 2.0f) - (block.pos.z + block.depth / 2.0f)) == 0.0f
                && (this.getPos().x - block.getPos().x) == 0.0f
                && (this.getPos().y - block.getPos().y) == 0.0f) {
            faceNum = BACK;
        } else if (((this.pos.z + this.depth / 2.0f) - (block.pos.z - block.depth / 2.0f)) == 0.0f
                && (this.getPos().x - block.getPos().x) == 0.0f
                && (this.getPos().y - block.getPos().y) == 0.0f) {
            faceNum = FRONT;
        }
        return faceNum;
    }

    public List<Vertex> getFaceVertices(int faceNum) {
        return vertices.subList(4 * faceNum, 4 * (faceNum + 1));
    }

    public static List<Integer> getConstFaceIndices(int faceNum) {
        return CONST.indices.subList(6 * faceNum, 6 * (faceNum + 1));
    }

    public boolean canBeSeenBy(Vector3f front, Vector3f pos) {
        boolean bool = false;
        int counter = 0;
        for (Vector3f normal : FACE_NORMALS) {
            Vector3f temp1 = new Vector3f();
            Vector3f vx = normal.add(this.pos, temp1).normalize(temp1);
            Vector3f temp2 = new Vector3f();
            Vector3f vy = front.add(pos, temp2).normalize(temp2);
            if (Math.abs(vx.dot(vy)) >= 0.25f) {
                counter++;
                break;
            }
        }
        if (counter >= 1 && counter <= 3) {
            bool = true;
        }
        return bool;
    }

    public void disableFace(int faceNum, boolean selfBuffer) {
        for (Vertex vertex : getFaceVertices(faceNum)) {
            vertex.setEnabled(false);
        }
        this.enabledFaces[faceNum] = false;
        if (selfBuffer) {
            bufferVertices();
        }
    }

    public void enableFace(int faceNum, boolean selfBuffer) {
        for (Vertex vertex : getFaceVertices(faceNum)) {
            vertex.setEnabled(true);
        }
        this.enabledFaces[faceNum] = true;
        if (selfBuffer) {
            bufferVertices();
        }
    }

    public void enableAllFaces(boolean selfBuffer) {
        for (Vertex vertex : vertices) {
            vertex.setEnabled(true);
        }
        Arrays.fill(enabledFaces, true);
        if (selfBuffer) {
            bufferVertices();
        }
    }

    public void disableAllFaces(boolean selfBuffer) {
        for (Vertex vertex : vertices) {
            vertex.setEnabled(false);
        }
        Arrays.fill(enabledFaces, false);
        if (selfBuffer) {
            bufferVertices();
        }
    }

    public void reverseFaceVertexOrder(boolean selfBuffer) {
        for (int j = 0; j <= 5; j++) {
            Collections.reverse(getFaceVertices(j));
        }
        verticesReversed = !verticesReversed;
        if (selfBuffer) {
            bufferVertices();
        }
    }

    public void setUVsForSkybox() {
        revertGroupsOfVertices();
        // LEFT
        vertices.get(4 * LEFT).getUv().x = 0.5f;
        vertices.get(4 * LEFT).getUv().y = 1.0f / 3.0f;

        vertices.get(4 * LEFT + 1).getUv().x = 0.25f;
        vertices.get(4 * LEFT + 1).getUv().y = 1.0f / 3.0f;

        vertices.get(4 * LEFT + 2).getUv().x = 0.25f;
        vertices.get(4 * LEFT + 2).getUv().y = 2.0f / 3.0f;

        vertices.get(4 * LEFT + 3).getUv().x = 0.5f;
        vertices.get(4 * LEFT + 3).getUv().y = 2.0f / 3.0f;
        // BACK
        for (int i = 0; i < 4; i++) {
            vertices.get(4 * BACK + i).getUv().x = vertices.get(4 * LEFT + i).getUv().x - 0.25f;
            vertices.get(4 * BACK + i).getUv().y = vertices.get(4 * LEFT + i).getUv().y;
        }
        // FRONT
        for (int i = 0; i < 4; i++) {
            vertices.get(4 * FRONT + i).getUv().x = vertices.get(4 * LEFT + i).getUv().x + 0.25f;
            vertices.get(4 * FRONT + i).getUv().y = vertices.get(4 * LEFT + i).getUv().y;
        }
        // RIGHT
        for (int i = 0; i < 4; i++) {
            vertices.get(4 * RIGHT + i).getUv().x = vertices.get(4 * FRONT + i).getUv().x + 0.25f;
            vertices.get(4 * RIGHT + i).getUv().y = vertices.get(4 * FRONT + i).getUv().y;
        }
        // TOP
        for (int i = 0; i < 4; i++) {
            vertices.get(4 * TOP + i).getUv().x = vertices.get(4 * LEFT + i).getUv().x;
            vertices.get(4 * TOP + i).getUv().y = vertices.get(4 * LEFT + i).getUv().y - 1.0f / 3.0f;
        }
        // BOTTOM
        for (int i = 0; i < 4; i++) {
            vertices.get(4 * BOTTOM + i).getUv().x = vertices.get(4 * LEFT + i).getUv().x;
            vertices.get(4 * BOTTOM + i).getUv().y = vertices.get(4 * LEFT + i).getUv().y + 1.0f / 3.0f;
        }
        bufferVertices();
    }

    private void revertGroupsOfVertices() {
        Collections.reverse(vertices.subList(4 * LEFT, 4 * LEFT + 3));
        Collections.reverse(vertices.subList(4 * RIGHT, 4 * RIGHT + 3));
        Collections.reverse(vertices.subList(4 * BOTTOM, 4 * BOTTOM + 3));
        Collections.reverse(vertices.subList(4 * TOP, 4 * TOP + 3));
        Collections.reverse(vertices.subList(4 * BACK, 4 * BACK + 3));
        Collections.reverse(vertices.subList(4 * FRONT, 4 * FRONT + 3));
    }

    public boolean hasFaces() {
        boolean arg = false;
        for (Boolean bool : enabledFaces) {
            arg = arg || bool;
            if (arg) {
                break;
            }
        }
        return arg;
    }

    public int getNumOfEnabledFaces() {
        int num = 0;
        for (int i = 0; i <= 5; i++) {
            if (enabledFaces[i]) {
                num++;
            }
        }
        return num;
    }

    public int getNumOfEnabledVertices() {
        int num = 0;
        for (Vertex vertex : vertices) {
            if (vertex.isEnabled()) {
                num++;
            }
        }
        return num;
    }

    public boolean[] getEnabledFaces() {
        return enabledFaces;
    }

    public Map<Integer, Integer> getAdjacentBlockMap() {
        return adjacentBlockMap;
    }

    public boolean isVerticesReversed() {
        return verticesReversed;
    }

    // used in Blocks Series to get face represenation in bits form
    public int getFaceBits() {
        int bits = 0;
        for (int j = 0; j <= 5; j++) {
            if (enabledFaces[j]) {
                int mask = 1 << j;
                bits |= mask;
            }
        }
        return bits;
    }

    // make int buffer base on bits form of faces
    public static IntBuffer createIntBuffer(int faceBits) {
        // creating indices
        List<Integer> indices = new ArrayList<>();
        int j = 0; // is face number (which increments after the face is added)
        while (faceBits > 0) {
            int bit = faceBits & 1; // compare the rightmost bit with one and assign it to bit
            if (bit == 1) {
                indices.add(4 * j);
                indices.add(4 * j + 1);
                indices.add(4 * j + 2);

                indices.add(4 * j + 2);
                indices.add(4 * j + 3);
                indices.add(4 * j);

                j++;
            }
            faceBits >>= 1; // move bits to the right so they are compared again            
        }
        // storing indices in the buffer
        IntBuffer intBuff = BufferUtils.createIntBuffer(indices.size());
        for (Integer index : indices) {
            intBuff.put(index);
        }
        intBuff.flip();
        return intBuff;
    }

    // returns array of adjacent free face numbers (those faces without adjacent neighbor nearby)
    public int[] getAdjacentFreeFaceNumbers() {
        int[] result = new int[6 - adjacentBlockMap.size()];
        int face = -1;
        for (int i = 0; i <= 5; i++) {
            if (adjacentBlockMap.get(i) == null) {
                result[++face] = i;
            }
        }
        return result;
    }

    // assuming that blocks are the same scale
    public Vector3f getAdjacentPos(Vector3f pos, int faceNum) {
        Vector3f result = new Vector3f();
        result.x = pos.x;
        result.y = pos.y;
        result.z = pos.z;

        switch (faceNum) {
            case Block.LEFT:
                result.x -= 2.0f;
                break;
            case Block.RIGHT:
                result.x += 2.0f;
                break;
            case Block.BOTTOM:
                result.y -= 2.0f;
                break;
            case Block.TOP:
                result.y += 2.0f;
                break;
            case Block.BACK:
                result.z -= 2.0f;
                break;
            case Block.FRONT:
                result.z += 2.0f;
                break;
            default:
                break;
        }

        return result;
    }

}
