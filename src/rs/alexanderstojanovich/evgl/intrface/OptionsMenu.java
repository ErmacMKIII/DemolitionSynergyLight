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
package rs.alexanderstojanovich.evgl.intrface;

import java.util.ArrayList;
import java.util.List;
import org.joml.Vector2f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWCursorPosCallback;
import org.lwjgl.glfw.GLFWKeyCallback;
import org.lwjgl.glfw.GLFWMouseButtonCallback;
import rs.alexanderstojanovich.evgl.main.Game;
import rs.alexanderstojanovich.evgl.main.GameObject;
import rs.alexanderstojanovich.evgl.shaders.ShaderProgram;
import rs.alexanderstojanovich.evgl.texture.Texture;
import rs.alexanderstojanovich.evgl.util.Pair;

/**
 *
 * @author Alexander Stojanovich <coas91@rocketmail.com>
 */
public abstract class OptionsMenu extends Menu {

    protected List<Pair<Text, Combo>> options = new ArrayList<>();

    public OptionsMenu(String title, List<Pair<String, Boolean>> itemPairs, String textureFileName) {
        super(title, itemPairs, textureFileName);
        init();
    }

    public OptionsMenu(String title, List<Pair<String, Boolean>> itemPairs, String textureFileName, Vector2f pos, float scale) {
        super(title, itemPairs, textureFileName, pos, scale);
        init();
    }

    private void init() {
        for (Text item : items) {
            Pair<Text, Combo> option = new Pair<>(new Text(Texture.FONT, ""), new Combo());
            option.getKey().getPos().x = item.getPos().x;
            option.getKey().getPos().y = item.getPos().y;
            options.add(option);
        }
    }

    protected abstract void getValues();

    @Override
    public void open() {
        enabled = true;
        GLFW.glfwSetInputMode(GameObject.MY_WINDOW.getWindowID(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
        GLFW.glfwSetCursorPosCallback(GameObject.MY_WINDOW.getWindowID(), new GLFWCursorPosCallback() {
            @Override
            public void invoke(long window, double xpos, double ypos) {
                // get the new options
                float new_xposGL = (float) (xpos / GameObject.MY_WINDOW.getWidth() - 0.5f) * 2.0f;
                float new_yposGL = (float) (0.5f - ypos / GameObject.MY_WINDOW.getHeight()) * 2.0f;

                // if new and prev options aren't the same user moved the mouse
                if (new_xposGL != xposGL || new_yposGL != yposGL) {
                    useMouse = true;
                }

                // assign the new options (remember them)
                xposGL = new_xposGL;
                yposGL = new_yposGL;
            }
        });
        GLFW.glfwSetCharCallback(GameObject.MY_WINDOW.getWindowID(), null);
        GLFW.glfwSetKeyCallback(GameObject.MY_WINDOW.getWindowID(), new GLFWKeyCallback() {
            @Override
            public void invoke(long window, int key, int scancode, int action, int mods) {
                if (key == GLFW.GLFW_KEY_ESCAPE && action == GLFW.GLFW_PRESS) {
                    enabled = false;
                    GLFW.glfwSetKeyCallback(window, Game.getDefaultKeyCallback());
                    GLFW.glfwSetCharCallback(window, null);
                    GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
                    GLFW.glfwSetCursorPosCallback(window, Game.getDefaultCursorCallback());
                    GLFW.glfwSetMouseButtonCallback(window, Game.getDefaultMouseButtonCallback());
                    GLFW.glfwSetCursorPos(GameObject.MY_WINDOW.getWindowID(), GameObject.MY_WINDOW.getWidth() / 2.0, GameObject.MY_WINDOW.getHeight() / 2.0);
                    leave();
                } else if (key == GLFW.GLFW_KEY_UP && (action == GLFW.GLFW_PRESS || action == GLFW.GLFW_REPEAT)) {
                    selectPrev();
                } else if (key == GLFW.GLFW_KEY_DOWN && (action == GLFW.GLFW_PRESS || action == GLFW.GLFW_REPEAT)) {
                    selectNext();
                } else if (key == GLFW.GLFW_KEY_LEFT && (action == GLFW.GLFW_PRESS || action == GLFW.GLFW_REPEAT)) {
                    if (options.get(selected) != null) {
                        options.get(selected).getValue().selectPrev();
                        execute();
                    }
                } else if (key == GLFW.GLFW_KEY_RIGHT && (action == GLFW.GLFW_PRESS || action == GLFW.GLFW_REPEAT)) {
                    if (options.get(selected) != null) {
                        options.get(selected).getValue().selectNext();
                        execute();
                    }
                } else if (key == GLFW.GLFW_KEY_ENTER && action == GLFW.GLFW_PRESS) {
                    if (options.get(selected) != null) {
                        options.get(selected).getValue().selectNext();
                        execute();
                    }
                }
            }
        });

        GLFW.glfwSetMouseButtonCallback(GameObject.MY_WINDOW.getWindowID(), new GLFWMouseButtonCallback() {
            @Override
            public void invoke(long window, int button, int action, int mods) {
                if (button == GLFW.GLFW_MOUSE_BUTTON_1 && action == GLFW.GLFW_PRESS) {
                    if (options.get(selected) != null) {
                        options.get(selected).getValue().selectNext();
                        execute();
                    }
                } else if (button == GLFW.GLFW_MOUSE_BUTTON_2 && action == GLFW.GLFW_PRESS) {
                    if (options.get(selected) != null) {
                        enabled = false;
                        GLFW.glfwSetKeyCallback(window, Game.getDefaultKeyCallback());
                        GLFW.glfwSetCharCallback(window, null);
                        GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
                        GLFW.glfwSetCursorPosCallback(window, Game.getDefaultCursorCallback());
                        GLFW.glfwSetMouseButtonCallback(window, Game.getDefaultMouseButtonCallback());
                        GLFW.glfwSetCursorPos(GameObject.MY_WINDOW.getWindowID(), GameObject.MY_WINDOW.getWidth() / 2.0, GameObject.MY_WINDOW.getHeight() / 2.0);
                        leave();
                    }
                }
            }
        });
    }

    @Override
    public void render(ShaderProgram shaderProgram) {
        if (enabled) {
            getValues();
            int longest = longestWord();
            title.setAlignment(alignmentAmount);
            title.getPos().x = (alignmentAmount - 0.5f) * (longest * itemScale * title.getRelativeCharWidth()) + pos.x;
            title.getPos().y = Text.LINE_SPACING * title.getRelativeCharHeight() * itemScale + pos.y;
            if (!title.isBuffered()) {
                title.bufferAll();
            }
            title.render(shaderProgram);
            int index = 0;
            for (Text item : items) {
                item.setAlignment(alignmentAmount);
                item.getPos().x = (alignmentAmount - 0.5f) * (longest * itemScale * item.getRelativeCharWidth()) + pos.x;
                item.getPos().y = -Text.LINE_SPACING * itemScale * (index + 1) * item.getRelativeCharHeight() + pos.y;

                if (!item.isBuffered()) {
                    item.bufferAll();
                }
                item.render(shaderProgram);
                options.get(index).getKey().getPos().x = item.getPos().x + itemScale * (item.getRelativeWidth() + item.getRelativeCharWidth()) * (1.0f - alignmentAmount);
                options.get(index).getKey().getPos().y = item.getPos().y;
                if (!options.get(index).getKey().isBuffered()) {
                    options.get(index).getKey().bufferAll();
                }
                options.get(index).getKey().render(shaderProgram);
                index++;
            }

            if (!iterator.isBuffered()) {
                iterator.bufferAll();
            }
            iterator.render(shaderProgram);
        }
    }

    public List<Pair<Text, Combo>> getOptions() {
        return options;
    }

}
