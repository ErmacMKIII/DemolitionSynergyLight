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
package rs.alexanderstojanovich.evgl.texture;

import de.matthiasmann.twl.utils.PNGDecoder;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import rs.alexanderstojanovich.evgl.main.Game;
import rs.alexanderstojanovich.evgl.util.DSLogger;

/**
 *
 * @author Coa
 */
public class Image { // only png format is supported

    private String fileName;
    private int width;
    private int height;
    private ByteBuffer content;

    public Image(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public Image(String dirEntry, String fileName) {
        this.fileName = fileName;
        loadImage(dirEntry, fileName);
    }

    private void loadImage(String dirEntry, String fileName) {
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
            return;
        }
        try {
            PNGDecoder decoder = new PNGDecoder(imgInput);
            // Set the width and height of the image
            width = decoder.getWidth();
            height = decoder.getHeight();
            // Decode the PNG file in a ByteBuffer
            content = ByteBuffer.allocateDirect(4 * width * height);
            decoder.decode(content, width * 4, PNGDecoder.Format.RGBA);
            content.flip();
        } catch (IOException ex) {
            DSLogger.reportFatalError(ex.getMessage(), ex);
        } finally {
            try {
                imgInput.close();
            } catch (IOException ex) {
                DSLogger.reportFatalError(ex.getMessage(), ex);
            }
            if (zipFile != null) {
                try {
                    zipFile.close();
                } catch (IOException ex) {
                    DSLogger.reportFatalError(ex.getMessage(), ex);
                }
            }
        }
    }

    public String getFileName() {
        return fileName;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public ByteBuffer getContent() {
        return content;
    }

    public void setContent(ByteBuffer content) {
        this.content = content;
    }

}