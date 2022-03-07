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
package rs.alexanderstojanovich.evgl.texture;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import rs.alexanderstojanovich.evgl.main.Configuration;
import rs.alexanderstojanovich.evgl.main.Game;
import rs.alexanderstojanovich.evgl.shaders.ShaderProgram;
import rs.alexanderstojanovich.evgl.util.DSLogger;
import rs.alexanderstojanovich.evgl.util.Pair;

/**
 *
 * @author Alexander Stojanovich <coas91@rocketmail.com>
 */
public class Texture {

    private final BufferedImage image;
    private int textureID = 0;
    private boolean buffered = false;
    public static final int TEX_SIZE = Configuration.getInstance().getTextureSize();

    public static final String[] TEX_WORLD = {"crate", "doom0", "stone", "water", "reflc"};
    public static final int GRID_SIZE_WORLD = 3;
    public static final Texture WORLD = Texture.buildTextureAtlas(Game.WORLD_ENTRY, TEX_WORLD, GRID_SIZE_WORLD);
    public static final Texture DECAL = new Texture(Game.WORLD_ENTRY, "decal.png");
    public static final Texture QMARK = new Texture(Game.WORLD_ENTRY, "qmark.png");
    public static final Texture MARBLE = new Texture(Game.WORLD_ENTRY, "marble.png");
    public static final Texture NIGHT = new Texture(Game.WORLD_ENTRY, "night.png");

    public static final Texture LOGO = new Texture(Game.INTRFACE_ENTRY, "ds_title_gray.png");
    public static final Texture CROSSHAIR = new Texture(Game.INTRFACE_ENTRY, "crosshairUltimate.png");
    public static final Texture MINIGUN = new Texture(Game.INTRFACE_ENTRY, "minigun.png");
    public static final Texture FONT = new Texture(Game.INTRFACE_ENTRY, "font.png");
    public static final Texture CONSOLE = new Texture(Game.INTRFACE_ENTRY, "console.png");
    public static final Texture LIGHT_BULB = new Texture(Game.INTRFACE_ENTRY, "lbulb.png");

    public static final String[] TEX_PLAYER = {"pistol", "assault_rifle", "shotgun", "sub_machine_gun", "machine_gun", "sniper_rifle"};
    public static final int GRID_SIZE_PLAYER = 3;
    public static final Map<String, Pair<Texture, Integer>> TEX_MAP = new HashMap<>();
    public static final Texture PLAYER = Texture.buildTextureAtlas(Game.PLAYER_ENTRY, TEX_PLAYER, GRID_SIZE_PLAYER);

    static {
        // interface stuff
        TEX_MAP.put("logox", new Pair<>(LOGO, -1));
        TEX_MAP.put("xhair", new Pair<>(CROSSHAIR, -1));
        TEX_MAP.put("minigun", new Pair<>(MINIGUN, -1));
        TEX_MAP.put("font", new Pair<>(FONT, -1));
        TEX_MAP.put("console", new Pair<>(CONSOLE, -1));
        TEX_MAP.put("lbulb", new Pair<>(LIGHT_BULB, -1));
        // world stuff
        TEX_MAP.put("crate", new Pair<>(WORLD, 0));
        TEX_MAP.put("doom0", new Pair<>(WORLD, 1));
        TEX_MAP.put("stone", new Pair<>(WORLD, 2));
        TEX_MAP.put("water", new Pair<>(WORLD, 3));
        TEX_MAP.put("reflc", new Pair<>(WORLD, 4));
        TEX_MAP.put("marble", new Pair<>(MARBLE, -1));
        TEX_MAP.put("qmark", new Pair<>(QMARK, -1));
        TEX_MAP.put("decal", new Pair<>(DECAL, -1));
        TEX_MAP.put("night", new Pair<>(NIGHT, -1));
        // player stuff
        TEX_MAP.put("pistol", new Pair<>(PLAYER, 0));
        TEX_MAP.put("assrifle", new Pair<>(PLAYER, 1));
        TEX_MAP.put("shotgun", new Pair<>(PLAYER, 2));
        TEX_MAP.put("smg", new Pair<>(PLAYER, 3));
        TEX_MAP.put("machgun", new Pair<>(PLAYER, 4));
        TEX_MAP.put("sniper", new Pair<>(PLAYER, 5));
    }

    /**
     * Creates blank Texture (TEXSIZE x TEXSIZE)
     */
    public Texture() {
        this.image = new BufferedImage(TEX_SIZE, TEX_SIZE, BufferedImage.TYPE_INT_ARGB);
    }

    /**
     * Creates Texture from the zip entry (or extracted zip)
     *
     * @param subDir directory or entry where file is located
     * @param fileName filename of the image (future texture)
     */
    public Texture(String subDir, String fileName) {
        this.image = loadImage(subDir, fileName);
    }

    public static BufferedImage loadImage(String dirEntry, String fileName) {
        File extern = new File(dirEntry + fileName);
        File archive = new File(Game.DATA_ZIP);
        ZipFile zipFile = null;
        InputStream imgInput = null;
        if (extern.exists()) {
            try {
                imgInput = new FileInputStream(extern);
            } catch (FileNotFoundException ex) {
                DSLogger.reportFatalError(ex.getMessage(), ex);
            }
        } else if (archive.exists()) {
            try {
                zipFile = new ZipFile(archive);
                for (ZipEntry zipEntry : Collections.list(zipFile.entries())) {
                    if (zipEntry.getName().equals(dirEntry + fileName)) {
                        imgInput = zipFile.getInputStream(zipEntry);
                        break;
                    }
                }
            } catch (IOException ex) {
                DSLogger.reportFatalError(ex.getMessage(), ex);
            }
        } else {
            DSLogger.reportError("Cannot find zip archive " + Game.DATA_ZIP + " or relevant ingame files!", null);
        }
        //----------------------------------------------------------------------
        if (imgInput == null) {
            DSLogger.reportError("Cannot find resource " + dirEntry + fileName + "!", null);
            return null;
        } else {
            try {
                return ImageIO.read(imgInput);
            } catch (IOException ex) {
                DSLogger.reportError("Error during loading image " + dirEntry + fileName + "!", null);
                DSLogger.reportError(ex.getMessage(), ex);
            }
        }

        return null;
    }

    public void bufferAll() {
        loadTexture();
        buffered = true;
    }

    public static void bufferAllTextures() {
        // intrface
        LOGO.bufferAll();
        CROSSHAIR.bufferAll();
        MINIGUN.bufferAll();
        FONT.bufferAll();
        CONSOLE.bufferAll();
        LIGHT_BULB.bufferAll();
        // world
        DECAL.bufferAll();
        MARBLE.bufferAll();
        QMARK.bufferAll();
        WORLD.bufferAll();
        NIGHT.bufferAll();
        // player
        PLAYER.bufferAll();
    }

    private void loadTexture() {
        textureID = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureID);
        // Set the texture wrapping parameters
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);// Set texture wrapping to GL_REPEAT (usually basic wrapping method)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
        // Set texture filtering parameters
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);

        // get the content as ByteBuffer
        ByteBuffer imageDataBuffer = getImageDataBuffer(image);

        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, TEX_SIZE, TEX_SIZE, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, imageDataBuffer);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
    }

    /**
     * Binds this texture as active for use
     *
     * @param shaderProgram provided shader program
     * @param textureUniformName texture uniform name in the fragment shader
     */
    public void bind(ShaderProgram shaderProgram, String textureUniformName) {
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureID);
        int uniformLocation = GL20.glGetUniformLocation(shaderProgram.getProgram(), textureUniformName);
        GL20.glUniform1i(uniformLocation, 0);
    }

    /**
     * Binds this texture as active for use and specifying texture unit for it
     * (from 0 to 7)
     *
     * @param textureUnitNum texture unit number
     * @param shaderProgram provided shader program
     * @param textureUniformName texture uniform name in the fragment shader
     */
    public void bind(int textureUnitNum, ShaderProgram shaderProgram, String textureUniformName) {
        if (textureUnitNum >= 0 && textureUnitNum <= 7) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + textureUnitNum);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureID);
            int uniformLocation = GL20.glGetUniformLocation(shaderProgram.getProgram(), textureUniformName);
            GL20.glUniform1i(uniformLocation, textureUnitNum);
        }
    }

    /**
     * Unbinds this texture as active from use
     */
    public static void unbind() {
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
    }

    public static void unbind(int textureUnitNum) {
        if (textureUnitNum >= 0 && textureUnitNum <= 7) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + textureUnitNum);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        }
    }

    public static void enable() {
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    public static void disable() {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 73 * hash + Objects.hashCode(this.image);
        hash = 73 * hash + this.textureID;
        hash = 73 * hash + (this.buffered ? 1 : 0);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Texture other = (Texture) obj;
        if (this.textureID != other.textureID) {
            return false;
        }
        if (!Objects.equals(this.image, other.image)) {
            return false;
        }
        return true;
    }

    /**
     * Gets content of this image as Byte Buffer (for textures)
     *
     * @param srcImg source image
     * @return content as byte buffer for creating texture
     */
    public static ByteBuffer getImageDataBuffer(BufferedImage srcImg) {
        ByteBuffer imageBuffer;
        WritableRaster raster;
        BufferedImage texImage;

        ColorModel glAlphaColorModel = new ComponentColorModel(ColorSpace
                .getInstance(ColorSpace.CS_sRGB), new int[]{8, 8, 8, 8},
                true, false, Transparency.TRANSLUCENT, DataBuffer.TYPE_BYTE);

        raster = Raster.createInterleavedRaster(DataBuffer.TYPE_BYTE,
                TEX_SIZE, TEX_SIZE, 4, null);
        texImage = new BufferedImage(glAlphaColorModel, raster, false,
                new Hashtable());

        int width = srcImg.getWidth();
        int height = srcImg.getHeight();
        double sx = 1.0 / (1.0 + (width - TEX_SIZE) / (double) TEX_SIZE);
        double sy = 1.0 / (1.0 + (height - TEX_SIZE) / (double) TEX_SIZE);

        AffineTransform xform = new AffineTransform();
        xform.scale(sx, sy);
        AffineTransformOp atOp = new AffineTransformOp(xform, null);
        final BufferedImage dstImg = atOp.filter(srcImg, null);

        // copy the source image into the produced image
        Graphics2D g2d = (Graphics2D) texImage.getGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g2d.setRenderingHint(RenderingHints.KEY_DITHERING, RenderingHints.VALUE_DITHER_ENABLE);

        g2d.setColor(new Color(0.0f, 0.0f, 0.0f, 0.0f));
        g2d.drawImage(dstImg, 0, 0, null);

        // build a byte buffer from the temporary image
        // that be used by OpenGL to produce a texture.
        byte[] data = ((DataBufferByte) texImage.getRaster().getDataBuffer())
                .getData();

        imageBuffer = BufferUtils.createByteBuffer(data.length);
        imageBuffer.put(data, 0, data.length);
        imageBuffer.flip();

        return imageBuffer;
    }

    public static Texture buildTextureAtlas(String subDir, String[] texNames, int gridSize) {
        Texture result = new Texture();
        Graphics2D g2d = result.image.createGraphics();
        final int texUnitSize = Math.round(TEX_SIZE / (float) gridSize);
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g2d.setRenderingHint(RenderingHints.KEY_DITHERING, RenderingHints.VALUE_DITHER_ENABLE);
        g2d.setColor(new Color(0.0f, 0.0f, 0.0f, 0.0f));
        int index = 0;
        OUTER:
        for (String texName : texNames) {
            String fileName = texName;
            if (!fileName.toLowerCase().endsWith(".png")) {
                fileName += ".png";
            }
            BufferedImage image = loadImage(subDir, fileName);

            int row = index / gridSize;
            int col = index % gridSize;

            int x = row * texUnitSize;
            int y = col * texUnitSize;

            g2d.drawImage(image, x, y, texUnitSize, texUnitSize, null);

            index++;
        }

        return result;
    }

    public BufferedImage getImage() {
        return image;
    }

    public int getTextureID() {
        return textureID;
    }

    public void setTextureID(int textureID) {
        this.textureID = textureID;
    }

    public boolean isBuffered() {
        return buffered;
    }

}
