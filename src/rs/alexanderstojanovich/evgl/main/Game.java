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
package rs.alexanderstojanovich.evgl.main;

import java.util.Arrays;
import java.util.concurrent.FutureTask;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWCursorPosCallback;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWKeyCallback;
import org.lwjgl.glfw.GLFWMouseButtonCallback;
import rs.alexanderstojanovich.evgl.audio.AudioFile;
import rs.alexanderstojanovich.evgl.critter.Observer;
import rs.alexanderstojanovich.evgl.intrface.Command;
import rs.alexanderstojanovich.evgl.level.Editor;
import rs.alexanderstojanovich.evgl.level.LevelContainer;
import rs.alexanderstojanovich.evgl.models.Block;
import rs.alexanderstojanovich.evgl.util.DSLogger;
import rs.alexanderstojanovich.evgl.util.Vector3fColors;

/**
 *
 * @author Alexander Stojanovich <coas91@rocketmail.com>
 */
public class Game {

    private static final Configuration cfg = Configuration.getInstance();

    public static final int TPS = 80; // TICKS PER SECOND GENERATED

    public static final float AMOUNT = 0.05f;
    public static final float ANGLE = (float) (Math.PI / 180);

    public static final int FORWARD = 0;
    public static final int BACKWARD = 1;
    public static final int LEFT = 2;
    public static final int RIGHT = 3;

    public static final float EPSILON = 0.0001f;

    private static int ups; // current update per second    
    private static int fpsMax = cfg.getFpsCap();

    // if this is reach game will close without exception!
    public static final double CRITICAL_TIME = 15.0;

    private final GameObject gameObject;

    private final boolean[] keys = new boolean[512];

    private static float lastX = 0.0f;
    private static float lastY = 0.0f;
    private static float xoffset = 0.0f;
    private static float yoffset = 0.0f;
    private static float mouseSensitivity = 1.5f;
    private boolean moveMouse = false;

    private int crosshairColorNum = 0;

    private final boolean[] mouseButtons = new boolean[8];

    private static GLFWKeyCallback defaultKeyCallback;
    private static GLFWCursorPosCallback defaultCursorCallback;
    private static GLFWMouseButtonCallback defaultMouseButtonCallback;

    public static final String ROOT = "/";
    public static final String CURR = "./";
    public static final String RESOURCES_DIR = "/rs/alexanderstojanovich/evgl/resources/";

    public static final String DATA_ZIP = "dsynergy_light.zip";

    public static final String SCREENSHOTS = "screenshots";
    public static final String CACHE = "cache";

    public static final String INTRFACE_ENTRY = "intrface/";
    public static final String PLAYER_ENTRY = "player/";
    public static final String WORLD_ENTRY = "world/";
    public static final String EFFECTS_ENTRY = "effects/";
    public static final String SOUND_ENTRY = "sound/";

    protected static double upsTicks = 0.0;

    public static enum Mode {
        FREE, SINGLE_PLAYER, MULTIPLAYER, EDITOR
    };
    private static Mode currentMode = Mode.FREE;

    /**
     * Construct new game view
     *
     * @param gameObject game object control
     */
    public Game(GameObject gameObject) {
        this.gameObject = gameObject;
        Arrays.fill(keys, false);
        initCallbacks();
    }

    /**
     * Handles input for observer (or player)
     */
    private void observerDo() {
        if (keys[GLFW.GLFW_KEY_W] || keys[GLFW.GLFW_KEY_UP]) {
            Observer obs = gameObject.getLevelContainer().getLevelActors().getPlayer();
            obs.movePredictorForward(AMOUNT);
            if (gameObject.hasCollisionWithCritter(obs)) {
                obs.movePredictorBackward(AMOUNT);
                gameObject.setAssertCollision(true);
            } else {
                obs.moveForward(AMOUNT);
                gameObject.setAssertCollision(false);
            }
        }
        if (keys[GLFW.GLFW_KEY_S] || keys[GLFW.GLFW_KEY_DOWN]) {
            Observer obs = gameObject.getLevelContainer().getLevelActors().getPlayer();
            obs.movePredictorBackward(AMOUNT);
            if (gameObject.hasCollisionWithCritter(obs)) {
                obs.movePredictorForward(AMOUNT);
                gameObject.setAssertCollision(true);
            } else {
                obs.moveBackward(AMOUNT);
                gameObject.setAssertCollision(false);
            }

        }
        if (keys[GLFW.GLFW_KEY_A]) {
            Observer obs = gameObject.getLevelContainer().getLevelActors().getPlayer();
            obs.movePredictorLeft(AMOUNT);
            if (gameObject.hasCollisionWithCritter(obs)) {
                obs.movePredictorRight(AMOUNT);
                gameObject.setAssertCollision(true);
            } else {
                obs.moveLeft(AMOUNT);
                gameObject.setAssertCollision(false);
            }
        }
        if (keys[GLFW.GLFW_KEY_D]) {
            Observer obs = gameObject.getLevelContainer().getLevelActors().getPlayer();
            obs.movePredictorRight(AMOUNT);
            if (gameObject.hasCollisionWithCritter(obs)) {
                obs.movePredictorLeft(AMOUNT);
                gameObject.setAssertCollision(true);
            } else {
                obs.moveRight(AMOUNT);
                gameObject.setAssertCollision(false);
            }
        }
        if (keys[GLFW.GLFW_KEY_LEFT]) {
            gameObject.getLevelContainer().getLevelActors().getPlayer().turnLeft(ANGLE);
        }
        if (keys[GLFW.GLFW_KEY_RIGHT]) {
            gameObject.getLevelContainer().getLevelActors().getPlayer().turnRight(ANGLE);
        }
        if (moveMouse) {
            gameObject.getLevelContainer().getLevelActors().getPlayer().lookAt(mouseSensitivity, xoffset, yoffset);
            moveMouse = false;
        }
    }

    /**
     * Handles input for editor (when in editor mode)
     */
    private void editorDo() {
        if (keys[GLFW.GLFW_KEY_N]) {
            Editor.selectNew(gameObject);
        }
        //----------------------------------------------------------------------
        if (mouseButtons[GLFW.GLFW_MOUSE_BUTTON_LEFT] && !keys[GLFW.GLFW_KEY_LEFT_SHIFT]) {
            Editor.selectCurrSolid(gameObject);
        }

        if (mouseButtons[GLFW.GLFW_MOUSE_BUTTON_LEFT] && keys[GLFW.GLFW_KEY_LEFT_SHIFT]) {
            Editor.selectCurrFluid(gameObject);
        }
        //----------------------------------------------------------------------
        if (keys[GLFW.GLFW_KEY_1] && !keys[GLFW.GLFW_KEY_LEFT_SHIFT]) {
            Editor.selectAdjacentSolid(gameObject, Block.LEFT);
        }
        if (keys[GLFW.GLFW_KEY_2] && !keys[GLFW.GLFW_KEY_LEFT_SHIFT]) {
            Editor.selectAdjacentSolid(gameObject, Block.RIGHT);
        }
        if (keys[GLFW.GLFW_KEY_3] && !keys[GLFW.GLFW_KEY_LEFT_SHIFT]) {
            Editor.selectAdjacentSolid(gameObject, Block.BOTTOM);
        }
        if (keys[GLFW.GLFW_KEY_4] && !keys[GLFW.GLFW_KEY_LEFT_SHIFT]) {
            Editor.selectAdjacentSolid(gameObject, Block.TOP);
        }
        if (keys[GLFW.GLFW_KEY_5] && !keys[GLFW.GLFW_KEY_LEFT_SHIFT]) {
            Editor.selectAdjacentSolid(gameObject, Block.BACK);
        }
        if (keys[GLFW.GLFW_KEY_6] && !keys[GLFW.GLFW_KEY_LEFT_SHIFT]) {
            Editor.selectAdjacentSolid(gameObject, Block.FRONT);
        }
        //----------------------------------------------------------------------
        if (keys[GLFW.GLFW_KEY_1] && keys[GLFW.GLFW_KEY_LEFT_SHIFT]) {
            Editor.selectAdjacentFluid(gameObject, Block.LEFT);
        }
        if (keys[GLFW.GLFW_KEY_2] && keys[GLFW.GLFW_KEY_LEFT_SHIFT]) {
            Editor.selectAdjacentFluid(gameObject, Block.RIGHT);
        }
        if (keys[GLFW.GLFW_KEY_3] && keys[GLFW.GLFW_KEY_LEFT_SHIFT]) {
            Editor.selectAdjacentFluid(gameObject, Block.BOTTOM);
        }
        if (keys[GLFW.GLFW_KEY_4] && keys[GLFW.GLFW_KEY_LEFT_SHIFT]) {
            Editor.selectAdjacentFluid(gameObject, Block.TOP);
        }
        if (keys[GLFW.GLFW_KEY_5] && keys[GLFW.GLFW_KEY_LEFT_SHIFT]) {
            Editor.selectAdjacentFluid(gameObject, Block.BACK);
        }
        if (keys[GLFW.GLFW_KEY_6] && keys[GLFW.GLFW_KEY_LEFT_SHIFT]) {
            Editor.selectAdjacentFluid(gameObject, Block.FRONT);
        }
        //----------------------------------------------------------------------
        if (keys[GLFW.GLFW_KEY_0] || keys[GLFW.GLFW_KEY_F]) {
            Editor.deselect();
        }
        if (mouseButtons[GLFW.GLFW_MOUSE_BUTTON_RIGHT]) {
            Editor.add(gameObject);
        }
        if (keys[GLFW.GLFW_KEY_R]) {
            Editor.remove(gameObject);
        }
    }

    /**
     * Handle input for player (Single player mode)
     */
    public void playerDo() {
        if (mouseButtons[GLFW.GLFW_MOUSE_BUTTON_LEFT]) {

        }

        if (keys[GLFW.GLFW_KEY_1]) {
            gameObject.getLevelContainer().getLevelActors().getPlayer().switchWeapon(1);
        }
        if (keys[GLFW.GLFW_KEY_2]) {
            gameObject.getLevelContainer().getLevelActors().getPlayer().switchWeapon(2);
        }
        if (keys[GLFW.GLFW_KEY_3]) {
            gameObject.getLevelContainer().getLevelActors().getPlayer().switchWeapon(3);
        }
        if (keys[GLFW.GLFW_KEY_4]) {
            gameObject.getLevelContainer().getLevelActors().getPlayer().switchWeapon(4);
        }
        if (keys[GLFW.GLFW_KEY_5]) {
            gameObject.getLevelContainer().getLevelActors().getPlayer().switchWeapon(5);
        }
        if (keys[GLFW.GLFW_KEY_6]) {
            gameObject.getLevelContainer().getLevelActors().getPlayer().switchWeapon(6);
        }

        if (keys[GLFW.GLFW_KEY_R]) {

        }
    }

    private void setCrosshairColor(Vector3f color) {
        gameObject.getIntrface().getCrosshair().setColor(color);
    }

    private void cycleCrosshairColor() {
        switch (crosshairColorNum) {
            case 0:
                setCrosshairColor(Vector3fColors.RED); // RED                
                break;
            case 1:
                setCrosshairColor(Vector3fColors.GREEN); // GREEN
                break;
            case 2:
                setCrosshairColor(Vector3fColors.BLUE); // BLUE
                break;
            case 3:
                setCrosshairColor(Vector3fColors.CYAN); // CYAN
                break;
            case 4:
                setCrosshairColor(Vector3fColors.MAGENTA); // MAGENTA
                break;
            case 5:
                setCrosshairColor(Vector3fColors.YELLOW); // YELLOW
                break;
            case 6:
                setCrosshairColor(Vector3fColors.WHITE); // WHITE
                break;
        }
        if (crosshairColorNum < 6) {
            crosshairColorNum++;
        } else {
            crosshairColorNum = 0;
        }
    }

    /**
     * Init input (keyboard & mouse)
     */
    private void initCallbacks() {
        GLFWErrorCallback.createPrint(System.err).set();

        defaultKeyCallback = new GLFWKeyCallback() {
            @Override
            public void invoke(long window, int key, int scancode, int action, int mods) {
                if (key == GLFW.GLFW_KEY_ESCAPE && action == GLFW.GLFW_PRESS) {
                    Arrays.fill(keys, false);
                    gameObject.getIntrface().setShowHelp(false);
                    gameObject.getIntrface().getHelpText().setEnabled(false);
                    gameObject.getIntrface().getCollText().setEnabled(true);
                    gameObject.getIntrface().getMainMenu().open();
                } else if (key == GLFW.GLFW_KEY_GRAVE_ACCENT && action == GLFW.GLFW_PRESS) {
                    Arrays.fill(keys, false);
                    gameObject.getIntrface().getConsole().open();
                } else if (key == GLFW.GLFW_KEY_F1 && action == GLFW.GLFW_PRESS) {
                    Arrays.fill(keys, false);
                    gameObject.getIntrface().toggleShowHelp();
                } else if (key == GLFW.GLFW_KEY_F2 && action == GLFW.GLFW_PRESS) {
                    Arrays.fill(keys, false);
                    gameObject.getIntrface().getSaveDialog().open();
                } else if (key == GLFW.GLFW_KEY_F3 && action == GLFW.GLFW_PRESS) {
                    Arrays.fill(keys, false);
                    gameObject.getIntrface().getLoadDialog().open();
                } else if (key == GLFW.GLFW_KEY_F4 && action == GLFW.GLFW_PRESS) {
                    Arrays.fill(keys, false);
                    gameObject.printInfo();
                } else if (key == GLFW.GLFW_KEY_F5 && action == GLFW.GLFW_PRESS) {
                    Arrays.fill(keys, false);
                    LevelContainer.printPositionMaps();
                } else if (key == GLFW.GLFW_KEY_F6 && action == GLFW.GLFW_PRESS) {
                    Arrays.fill(keys, false);
                    gameObject.getLevelContainer().printPriorityQueues();
                } else if (key == GLFW.GLFW_KEY_F12 && action == GLFW.GLFW_PRESS) {
                    Arrays.fill(keys, false);
                    FutureTask<Object> task = new FutureTask<Object>(Command.SCREENSHOT);
                    Renderer.TASK_QUEUE.add(task);
                } else if (key == GLFW.GLFW_KEY_P && (action == GLFW.GLFW_PRESS || action == GLFW.GLFW_REPEAT)) {
                    cycleCrosshairColor();
                } else if (key == GLFW.GLFW_KEY_M && (action == GLFW.GLFW_PRESS || action == GLFW.GLFW_REPEAT)) {
                    Editor.cycleBlockColor();
                } else if (key == GLFW.GLFW_KEY_LEFT_BRACKET && (action == GLFW.GLFW_PRESS || action == GLFW.GLFW_REPEAT)) {
                    Editor.selectPrevTexture(gameObject);
                } else if (key == GLFW.GLFW_KEY_RIGHT_BRACKET && (action == GLFW.GLFW_PRESS || action == GLFW.GLFW_REPEAT)) {
                    Editor.selectNextTexture(gameObject);
                } else if (key != -1) {
                    if (action == GLFW.GLFW_PRESS) {
                        keys[key] = true;
                    } else if (action == GLFW.GLFW_RELEASE) {
                        keys[key] = false;
                    }
                }
            }
        };
        GLFW.glfwSetKeyCallback(GameObject.MY_WINDOW.getWindowID(), defaultKeyCallback);

        GLFW.glfwSetInputMode(GameObject.MY_WINDOW.getWindowID(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
        GLFW.glfwSetCursorPos(GameObject.MY_WINDOW.getWindowID(), GameObject.MY_WINDOW.getWidth() / 2.0, GameObject.MY_WINDOW.getHeight() / 2.0);
        defaultCursorCallback = new GLFWCursorPosCallback() {
            @Override
            public void invoke(long window, double xpos, double ypos) {
                float xposGL = (float) (xpos / GameObject.MY_WINDOW.getWidth() - 0.5f) * 2.0f;
                float yposGL = (float) (0.5f - ypos / GameObject.MY_WINDOW.getHeight()) * 2.0f;

                xoffset = xposGL - lastX;
                yoffset = yposGL - lastY;

                if (xoffset != 0.0f || yoffset != 0.0f) {
                    moveMouse = true;
                }

                lastX = (float) xposGL;
                lastY = (float) yposGL;
            }
        };
        GLFW.glfwSetCursorPosCallback(GameObject.MY_WINDOW.getWindowID(), defaultCursorCallback);

        defaultMouseButtonCallback = new GLFWMouseButtonCallback() {
            @Override
            public void invoke(long window, int button, int action, int mods) {
                if (action == GLFW.GLFW_PRESS) {
                    mouseButtons[button] = true;
                } else if (action == GLFW.GLFW_RELEASE) {
                    mouseButtons[button] = false;
                }
            }
        };
        GLFW.glfwSetMouseButtonCallback(GameObject.MY_WINDOW.getWindowID(), defaultMouseButtonCallback);
    }

    /**
     * Starts the main (update) loop
     */
    public void go() {
        // start the music
        AudioFile audioFile = AudioFile.AMBIENT;
        gameObject.getMusicPlayer().play(audioFile, true);

        ups = 0;

        double lastTime = GLFW.glfwGetTime();
        double currTime;
        double deltaTime;
        double acc = 0.0; // accumulator

        int index = 0; // track index

        while (!GameObject.MY_WINDOW.shouldClose()) {
            currTime = GLFW.glfwGetTime();
            deltaTime = currTime - lastTime;
            upsTicks += -Math.expm1(-deltaTime * Game.TPS);
            acc += deltaTime;
            lastTime = currTime;

            // Detecting critical status
            if (ups == 0 && deltaTime > CRITICAL_TIME) {
                DSLogger.reportFatalError("Game status critical!", null);
                GameObject.MY_WINDOW.close();
                break;
            }

            while (upsTicks >= 1.0) {
                GLFW.glfwPollEvents();
                if (!gameObject.musicPlayer.isPlaying()) {
                    gameObject.musicPlayer.play(AudioFile.TRACKS[index++], false);

                    if (index == AudioFile.TRACKS.length) {
                        index = 0;
                    }
                }
                gameObject.determineVisibleChunks();
                gameObject.update((float) (Math.floorMod(Math.round(upsTicks), TPS)));
                if (currentMode == Mode.SINGLE_PLAYER) {
                    playerDo();
                    observerDo();
                } else if (currentMode == Mode.EDITOR) {
                    gameObject.getLevelContainer().getLevelActors().getPlayer().setCurrWeapon(null);
                    editorDo();
                    observerDo();
                }
                ups++;
                upsTicks--;
                acc -= 1.0 / TPS;
            }

            gameObject.chunkOperations();
            Renderer.alpha = acc * TPS;
        }
        // stops the music        
        gameObject.getMusicPlayer().stop();
    }

    /**
     * Creates configuration from settings
     *
     * @return Configuration cfg
     */
    public Configuration makeConfig() {
        Configuration cfg = Configuration.getInstance();
        cfg.setFpsCap(fpsMax);
        cfg.setWidth(GameObject.MY_WINDOW.getWidth());
        cfg.setHeight(GameObject.MY_WINDOW.getHeight());
        cfg.setFullscreen(GameObject.MY_WINDOW.isFullscreen());
        cfg.setVsync(GameObject.MY_WINDOW.isVsync());
        cfg.setMouseSensitivity(mouseSensitivity);
        cfg.setMusicVolume(gameObject.getMusicPlayer().getGain());
        cfg.setSoundFXVolume(gameObject.getSoundFXPlayer().getGain());
        return cfg;
    }

    public static GLFWKeyCallback getDefaultKeyCallback() {
        return defaultKeyCallback;
    }

    public static GLFWCursorPosCallback getDefaultCursorCallback() {
        return defaultCursorCallback;
    }

    public static GLFWMouseButtonCallback getDefaultMouseButtonCallback() {
        return defaultMouseButtonCallback;
    }

    public static int getUps() {
        return ups;
    }

    public static void setUps(int ups) {
        Game.ups = ups;
    }

    public static int getFpsMax() {
        return fpsMax;
    }

    public static void setFpsMax(int fpsMax) {
        Game.fpsMax = fpsMax;
    }

    public static float getMouseSensitivity() {
        return mouseSensitivity;
    }

    public static void setMouseSensitivity(float mouseSensitivity) {
        Game.mouseSensitivity = mouseSensitivity;
    }

    public static double getUpsTicks() {
        return upsTicks;
    }

    public static Mode getCurrentMode() {
        return currentMode;
    }

    public static void setCurrentMode(Mode currentMode) {
        Game.currentMode = currentMode;
    }

    public static float getLastX() {
        return lastX;
    }

    public static float getLastY() {
        return lastY;
    }

    public static float getXoffset() {
        return xoffset;
    }

    public static float getYoffset() {
        return yoffset;
    }

}
