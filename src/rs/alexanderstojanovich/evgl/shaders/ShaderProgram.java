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
package rs.alexanderstojanovich.evgl.shaders;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import rs.alexanderstojanovich.evgl.level.LightSource;
import rs.alexanderstojanovich.evgl.main.Game;
import rs.alexanderstojanovich.evgl.util.DSLogger;

/**
 *
 * @author Alexander Stojanovich <coas91@rocketmail.com>
 */
public class ShaderProgram {

    private final int program; // made to link all the shaders    
    private final List<Shader> shaders;

    private static ShaderProgram mainShader;
    private static ShaderProgram intrfaceShader;
    private static ShaderProgram playerShader;
    private static ShaderProgram weaponShader;

    public static final int SHADER_COUNT = 4;
    public static final ShaderProgram[] SHADER_PROGRAMS = new ShaderProgram[SHADER_COUNT];

    public static void initAllShaders() { // requires initialized OpenGL capabilities
        // 1. Init main shader (skybox, NPCs, items)
        Shader mainVertexShader = new Shader(Game.EFFECTS_ENTRY, "mainVS.glsl", Shader.VERTEX_SHADER);
        Shader mainFragmentShader = new Shader(Game.EFFECTS_ENTRY, "mainFS.glsl", Shader.FRAGMENT_SHADER);
        List<Shader> mainShaders = new ArrayList<>();
        mainShaders.add(mainVertexShader);
        mainShaders.add(mainFragmentShader);
        mainShader = new ShaderProgram(mainShaders);
        SHADER_PROGRAMS[0] = mainShader;
        // 2. Init interface shader (crosshair & fonts)
        Shader intrfaceVertexShader = new Shader(Game.EFFECTS_ENTRY, "intrfaceVS.glsl", Shader.VERTEX_SHADER);
        Shader intrfaceFragmentShader = new Shader(Game.EFFECTS_ENTRY, "intrfaceFS.glsl", Shader.FRAGMENT_SHADER);
        List<Shader> intrfaceShaders = new ArrayList<>();
        intrfaceShaders.add(intrfaceVertexShader);
        intrfaceShaders.add(intrfaceFragmentShader);
        intrfaceShader = new ShaderProgram(intrfaceShaders);
        SHADER_PROGRAMS[1] = intrfaceShader;
        // 3. Init player shader (camera)
        Shader playerVertexShader = new Shader(Game.EFFECTS_ENTRY, "playerVS.glsl", Shader.VERTEX_SHADER);
        Shader playerFragmentShader = new Shader(Game.EFFECTS_ENTRY, "playerFS.glsl", Shader.FRAGMENT_SHADER);
        List<Shader> playerShaders = new ArrayList<>();
        playerShaders.add(playerVertexShader);
        playerShaders.add(playerFragmentShader);
        playerShader = new ShaderProgram(playerShaders);
        SHADER_PROGRAMS[2] = playerShader;
        // 4. Init weapon shader (player weapons)
        Shader weaponVertexShader = new Shader(Game.EFFECTS_ENTRY, "weaponVS.glsl", Shader.VERTEX_SHADER);
        Shader weaponFragmentShader = new Shader(Game.EFFECTS_ENTRY, "weaponFS.glsl", Shader.FRAGMENT_SHADER);
        List<Shader> weaponShaders = new ArrayList<>();
        weaponShaders.add(weaponVertexShader);
        weaponShaders.add(weaponFragmentShader);
        weaponShader = new ShaderProgram(weaponShaders);
        SHADER_PROGRAMS[3] = weaponShader;
    }

    public ShaderProgram(List<Shader> shaders) {
        program = GL20.glCreateProgram();
        this.shaders = shaders;
        initProgram();
    }

    public void attachShader(int shader) {
        GL20.glAttachShader(program, shader);
    }

    public void linkProgram() {
        GL20.glLinkProgram(program);
        if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            DSLogger.reportError(GL20.glGetShaderInfoLog(program, 1024), null);
            System.exit(1);
        }
    }

    public void validateProgram() {
        GL20.glValidateProgram(program);
        if (GL20.glGetProgrami(program, GL20.GL_VALIDATE_STATUS) == GL11.GL_FALSE) {
            DSLogger.reportError(GL20.glGetShaderInfoLog(program, 1024), null);
            System.exit(1);
        }
    }

    private void initProgram() {
        // attaching all the shaders
        for (int i = 0; i < shaders.size(); i++) {
            attachShader(shaders.get(i).getShader());
        }
        // linking program
        linkProgram();
        // validating program
        validateProgram();
    }

    public void bind() {
        GL20.glUseProgram(program);
    }

    public static void unbind() {
        GL20.glUseProgram(0);
    }

    public void bindAttribute(int attribute, String variableName) {
        GL20.glBindAttribLocation(program, attribute, variableName);
    }

    public void updateUniform(int value, String name) {
        int uniformLocation = GL20.glGetUniformLocation(program, name);
        GL20.glUniform1i(uniformLocation, value);
    }

    public void updateUniform(float value, String name) {
        int uniformLocation = GL20.glGetUniformLocation(program, name);
        GL20.glUniform1f(uniformLocation, value);
    }

    public void updateUniform(Vector2f vect, String name) {
        int uniformLocation = GL20.glGetUniformLocation(program, name);
        GL20.glUniform2f(uniformLocation, vect.x, vect.y);
    }

    public void updateUniform(Vector3f vect, String name) {
        int uniformLocation = GL20.glGetUniformLocation(program, name);
        GL20.glUniform3f(uniformLocation, vect.x, vect.y, vect.z);
    }

    public void updateUniform(Vector3f[] vectArr, String name) {
        for (int i = 0; i < vectArr.length; i++) {
            int uniformLocation = GL20.glGetUniformLocation(program, name + "[" + i + "]");
            GL20.glUniform3f(uniformLocation, vectArr[i].x, vectArr[i].y, vectArr[i].z);
        }
    }

    public void updateUniform(Vector4f vect, String name) {
        int uniformLocation = GL20.glGetUniformLocation(program, name);
        GL20.glUniform4f(uniformLocation, vect.x, vect.y, vect.z, vect.w);
    }

    public void updateUniform(Matrix4f mat, String name) {
        FloatBuffer fb = BufferUtils.createFloatBuffer(4 * 4);
        mat.get(fb);
        int uniformLocation = GL20.glGetUniformLocation(program, name);
        GL20.glUniformMatrix4fv(uniformLocation, false, fb);
    }

    public void updateUniform(LightSource[] lightSrc, String name) {
        for (int i = 0; i < lightSrc.length; i++) {
            int locPos = GL20.glGetUniformLocation(program, name + "[" + i + "].pos");
            GL20.glUniform3f(locPos, lightSrc[i].getPos().x, lightSrc[i].getPos().y, lightSrc[i].getPos().z);

            int locCol = GL20.glGetUniformLocation(program, name + "[" + i + "].color");
            GL20.glUniform3f(locCol, lightSrc[i].getColor().x, lightSrc[i].getColor().y, lightSrc[i].getColor().z);

            int locInt = GL20.glGetUniformLocation(program, name + "[" + i + "].intensity");
            GL20.glUniform1f(locInt, lightSrc[i].getIntensity());
        }
    }

    public void updateUniform(List<LightSource> lightSrc, String name) {
        int index = 0;
        for (LightSource ls : lightSrc) {
            int locPos = GL20.glGetUniformLocation(program, name + "[" + index + "].pos");
            GL20.glUniform3f(locPos, ls.getPos().x, ls.getPos().y, ls.getPos().z);

            int locCol = GL20.glGetUniformLocation(program, name + "[" + index + "].color");
            GL20.glUniform3f(locCol, ls.getColor().x, ls.getColor().y, ls.getColor().z);

            int locInt = GL20.glGetUniformLocation(program, name + "[" + index + "].intensity");
            GL20.glUniform1f(locInt, ls.getIntensity());

            index++;
        }
    }

    public int getProgram() {
        return program;
    }

    public List<Shader> getShaders() {
        return shaders;
    }

    public static ShaderProgram getMainShader() {
        return mainShader;
    }

    public static ShaderProgram getIntrfaceShader() {
        return intrfaceShader;
    }

    public static ShaderProgram getPlayerShader() {
        return playerShader;
    }

    public static ShaderProgram getWeaponShader() {
        return weaponShader;
    }

}
