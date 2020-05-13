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
package rs.alexanderstojanovich.evgl.intrface;

import java.util.List;
import org.joml.Vector2f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWCursorPosCallback;
import org.lwjgl.glfw.GLFWKeyCallback;
import org.lwjgl.glfw.GLFWMouseButtonCallback;
import rs.alexanderstojanovich.evgl.core.Combo;
import rs.alexanderstojanovich.evgl.core.Window;
import rs.alexanderstojanovich.evgl.main.Game;
import rs.alexanderstojanovich.evgl.texture.Texture;
import rs.alexanderstojanovich.evgl.util.Pair;

/**
 *
 * @author Coa
 */
public abstract class OptionsMenu extends Menu {

    private Text[] values; // correct and current values we display
    private Combo[] options; // options we can set we display

    public OptionsMenu(Window window, String title, List<Pair<String, Boolean>> itemPairs, String textureFileName) {
        super(window, title, itemPairs, textureFileName);
        init();
    }

    public OptionsMenu(Window window, String title, List<Pair<String, Boolean>> itemPairs, String textureFileName, Vector2f pos, float scale) {
        super(window, title, itemPairs, textureFileName, pos, scale);
        init();
    }

    private void init() {
        values = new Text[items.size()];
        options = new Combo[items.size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = new Text(myWindow, Texture.FONT, "");
            values[i].getQuad().getPos().x = items.get(i).getQuad().getPos().x;
            values[i].getQuad().getPos().x += (items.get(i).getContent().length() + 1) * items.get(i).getQuad().giveRelativeWidth();
            values[i].getQuad().getPos().y = items.get(i).getQuad().getPos().y;
        }
    }

    protected abstract void refreshValues();

    @Override
    public void open() {
        enabled = true;
        GLFW.glfwSetInputMode(myWindow.getWindowID(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
        GLFW.glfwSetCursorPosCallback(myWindow.getWindowID(), new GLFWCursorPosCallback() {
            @Override
            public void invoke(long window, double xpos, double ypos) {
                // get the new values
                float new_xposGL = (float) (xpos / myWindow.getWidth() - 0.5f) * 2.0f;
                float new_yposGL = (float) (0.5f - ypos / myWindow.getHeight()) * 2.0f;

                // if new and prev values aren't the same user moved the mouse
                if (new_xposGL != xposGL || new_yposGL != yposGL) {
                    useMouse = true;
                }

                // assign the new values (remember them)
                xposGL = new_xposGL;
                yposGL = new_yposGL;
            }
        });
        GLFW.glfwSetCharCallback(myWindow.getWindowID(), null);
        GLFW.glfwSetKeyCallback(myWindow.getWindowID(), new GLFWKeyCallback() {
            @Override
            public void invoke(long window, int key, int scancode, int action, int mods) {
                if (key == GLFW.GLFW_KEY_ESCAPE && action == GLFW.GLFW_PRESS) {
                    enabled = false;
                    GLFW.glfwSetKeyCallback(window, Game.getDefaultKeyCallback());
                    GLFW.glfwSetCharCallback(window, null);
                    GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
                    GLFW.glfwSetCursorPosCallback(window, Game.getDefaultCursorCallback());
                    GLFW.glfwSetMouseButtonCallback(window, Game.getDefaultMouseButtonCallback());
                    GLFW.glfwSetCursorPos(myWindow.getWindowID(), Game.getLastX(), Game.getLastY());
                    leave();
                } else if (key == GLFW.GLFW_KEY_UP && (action == GLFW.GLFW_PRESS || action == GLFW.GLFW_REPEAT)) {
                    selectPrev();
                } else if (key == GLFW.GLFW_KEY_DOWN && (action == GLFW.GLFW_PRESS || action == GLFW.GLFW_REPEAT)) {
                    selectNext();
                } else if (key == GLFW.GLFW_KEY_LEFT && (action == GLFW.GLFW_PRESS || action == GLFW.GLFW_REPEAT)) {
                    if (options[selected] != null) {
                        options[selected].selectPrev();
                        execute();
                    }
                } else if (key == GLFW.GLFW_KEY_RIGHT && (action == GLFW.GLFW_PRESS || action == GLFW.GLFW_REPEAT)) {
                    if (options[selected] != null) {
                        options[selected].selectNext();
                        execute();
                    }
                } else if (key == GLFW.GLFW_KEY_ENTER && action == GLFW.GLFW_PRESS) {
                    if (options[selected] != null) {
                        options[selected].selectNext();
                        execute();
                    }
                }
            }
        });

        GLFW.glfwSetMouseButtonCallback(myWindow.getWindowID(), new GLFWMouseButtonCallback() {
            @Override
            public void invoke(long window, int button, int action, int mods) {
                if (button == GLFW.GLFW_MOUSE_BUTTON_1 && action == GLFW.GLFW_PRESS) {
                    if (options[selected] != null) {
                        options[selected].selectNext();
                        execute();
                    }
                } else if (button == GLFW.GLFW_MOUSE_BUTTON_2 && action == GLFW.GLFW_PRESS) {
                    if (options[selected] != null) {
                        enabled = false;
                        GLFW.glfwSetKeyCallback(window, Game.getDefaultKeyCallback());
                        GLFW.glfwSetCharCallback(window, null);
                        GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
                        GLFW.glfwSetCursorPosCallback(window, Game.getDefaultCursorCallback());
                        GLFW.glfwSetMouseButtonCallback(window, Game.getDefaultMouseButtonCallback());
                        GLFW.glfwSetCursorPos(myWindow.getWindowID(), Game.getLastX(), Game.getLastY());
                        leave();
                    }
                }
            }
        });
    }

    @Override
    public void render() {
        if (enabled) {
            refreshValues();
            int longest = longestWord();
            title.getQuad().getPos().x = (alignmentAmount * (longest - title.getContent().length()) - longest / 2)
                    * title.getQuad().giveRelativeWidth() * itemScale + pos.x;
            title.getQuad().getPos().y = Text.LINE_SPACING * title.getQuad().giveRelativeHeight() * itemScale + pos.y;
            title.render();
            int index = 0;
            for (Text item : items) {
                int itemDiff = longest - item.getContent().length();
                item.getQuad().getPos().x = (alignmentAmount * itemDiff - longest / 2) * item.getQuad().giveRelativeWidth() * itemScale + pos.x;
                item.getQuad().getPos().y = -Text.LINE_SPACING * itemScale * (index + 1) * item.getQuad().giveRelativeHeight() + pos.y;
                item.render();
                values[index].getQuad().getPos().x = item.getQuad().getPos().x;
                values[index].getQuad().getPos().x += (item.getContent().length() + 1) * item.getQuad().giveRelativeWidth() * itemScale;
                values[index].getQuad().getPos().y = item.getQuad().getPos().y;
                values[index].render();
                index++;
            }
            iterator.getPos().x = items.get(selected).getQuad().getPos().x;
            iterator.getPos().x -= 2.0f * items.get(selected).getQuad().giveRelativeWidth() * itemScale;
            iterator.getPos().y = items.get(selected).getQuad().getPos().y;
            iterator.setColor(items.get(selected).getQuad().getColor());
            if (!iterator.isBuffered()) {
                iterator.buffer();
            }
            iterator.render();
        }
    }

    public Text[] getValues() {
        return values;
    }

    public void setValues(Text[] values) {
        this.values = values;
    }

    public Combo[] getOptions() {
        return options;
    }

    public void setOptions(Combo[] options) {
        this.options = options;
    }

}
