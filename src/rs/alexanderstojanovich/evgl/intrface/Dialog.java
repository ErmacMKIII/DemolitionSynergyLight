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

import org.joml.Vector2f;
import rs.alexanderstojanovich.evgl.core.Window;
import rs.alexanderstojanovich.evgl.main.Game;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWCharCallback;
import org.lwjgl.glfw.GLFWKeyCallback;
import rs.alexanderstojanovich.evgl.texture.Texture;

/**
 *
 * @author Coa
 */
public abstract class Dialog {

    protected final Window myWindow;
    protected final Text dialog;
    protected final StringBuilder input = new StringBuilder(); // this is the answer we type from keyboard
    protected boolean enabled;
    protected boolean done;

    protected final String question; // question message
    protected final String success; // message if succesful execution
    protected final String fail; // message if failure

    public Dialog(Window window, Texture texture, Vector2f pos,
            String question, String success, String fail) {
        this.myWindow = window;
        this.dialog = new Text(myWindow, texture, "");
        this.dialog.setPos(pos);
        this.enabled = false;
        this.done = false;
        this.question = question;
        this.success = success;
        this.fail = fail;
    }

    protected abstract boolean execute(String command); // we need to override this upon creation of the dialog        

    public void open() {
        if (input.length() == 0) {
            enabled = true;
            done = false;
            dialog.setContent(question + "_");
            dialog.getColor().x = 1.0f;
            dialog.getColor().y = 1.0f;
            dialog.getColor().z = 1.0f;
            GLFW.glfwSetInputMode(myWindow.getWindowID(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
            GLFW.glfwSetCursorPosCallback(myWindow.getWindowID(), null);
            GLFW.glfwSetKeyCallback(myWindow.getWindowID(), new GLFWKeyCallback() {
                @Override
                public void invoke(long window, int key, int scancode, int action, int mods) {
                    if (key == GLFW.GLFW_KEY_ESCAPE && action == GLFW.GLFW_PRESS) {
                        GLFW.glfwSetKeyCallback(window, Game.getDefaultKeyCallback());
                        GLFW.glfwSetCharCallback(window, null);
                        GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
                        GLFW.glfwSetCursorPosCallback(window, Game.getDefaultCursorCallback());
                        dialog.setContent("");
                        input.setLength(0);
                        enabled = false;
                        done = true;
                    } else if (key == GLFW.GLFW_KEY_BACKSPACE && (action == GLFW.GLFW_PRESS || action == GLFW.GLFW_REPEAT)) {
                        if (input.length() > 0) {
                            input.deleteCharAt(input.length() - 1);
                            dialog.setContent(question + input + "_");
                        }
                    } else if (key == GLFW.GLFW_KEY_ENTER && action == GLFW.GLFW_PRESS) {
                        GLFW.glfwSetKeyCallback(window, Game.getDefaultKeyCallback());
                        GLFW.glfwSetCharCallback(window, null);
                        GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
                        GLFW.glfwSetCursorPosCallback(window, Game.getDefaultCursorCallback());
                        if (!input.toString().equals("")) {
                            boolean execStatus = execute(input.toString());
                            if (execStatus) {
                                dialog.setContent(success);
                                dialog.getColor().x = 0.0f;
                                dialog.getColor().y = 1.0f;
                                dialog.getColor().z = 0.0f;
                            } else {
                                dialog.setContent(fail);
                                dialog.getColor().x = 1.0f;
                                dialog.getColor().y = 0.0f;
                                dialog.getColor().z = 0.0f;
                            }
                        } else {
                            dialog.setContent("");
                            enabled = false;
                        }
                        input.setLength(0);
                        done = true;
                        // pls use getter for done and setter for enabled outside
                        // using timer to determine when to stop showing dialog 
                        // to set enabled to false
                    }
                }
            });
            GLFW.glfwWaitEvents();
            GLFW.glfwSetCharCallback(myWindow.getWindowID(), new GLFWCharCallback() {
                @Override
                public void invoke(long window, int codepoint) {
                    input.append((char) codepoint);
                    dialog.setContent(question + input + "_");
                }
            });
        }
    }

    public void render() {
        if (enabled) {
            if (!dialog.isBuffered()) {
                dialog.bufferAll();
            }
            dialog.render();
        }
    }

    public Window getMyWindow() {
        return myWindow;
    }

    public Text getDialog() {
        return dialog;
    }

    public StringBuilder getInput() {
        return input;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isDone() {
        return done;
    }

    public void setDone(boolean done) {
        this.done = done;
    }

    public String getQuestion() {
        return question;
    }

    public String getSuccess() {
        return success;
    }

    public String getFail() {
        return fail;
    }

}
