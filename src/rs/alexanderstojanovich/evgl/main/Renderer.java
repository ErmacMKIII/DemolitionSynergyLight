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

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import org.lwjgl.glfw.GLFW;
import rs.alexanderstojanovich.evgl.core.MasterRenderer;
import rs.alexanderstojanovich.evgl.core.PerspectiveRenderer;
import rs.alexanderstojanovich.evgl.core.Window;
import rs.alexanderstojanovich.evgl.level.Editor;
import rs.alexanderstojanovich.evgl.level.LevelContainer;
import rs.alexanderstojanovich.evgl.shaders.ShaderProgram;
import rs.alexanderstojanovich.evgl.texture.Texture;
import rs.alexanderstojanovich.evgl.util.DSLogger;

/**
 *
 * @author Alexander Stojanovich <coas91@rocketmail.com>
 */
public class Renderer extends Thread implements Executor {

    private final GameObject gameObject;

    private static double fpsTicks = 0.0;
    private static int fps = 0;

    private int widthGL = Window.MIN_WIDTH;
    private int heightGL = Window.MIN_HEIGHT;
    protected static double alpha = 0.0;

    public static final Queue<FutureTask<Boolean>> TASK_QUEUE = new ArrayDeque<>();

    public Renderer(GameObject gameObject) {
        super("Renderer");
        this.gameObject = gameObject;
    }

    @Override
    public void run() {
        MasterRenderer.initGL(GameObject.MY_WINDOW); // loads myWindow context, creates OpenGL context..
        MasterRenderer.setResolution(GameObject.MY_WINDOW.getWidth(), GameObject.MY_WINDOW.getHeight());
        ShaderProgram.initAllShaders(); // it's important that first GL is done and then this one 
        PerspectiveRenderer.updatePerspective(GameObject.MY_WINDOW); // updates perspective for all the existing shaders
        Texture.bufferAllTextures();

        double timer1 = GLFW.glfwGetTime();
        double timer2 = GLFW.glfwGetTime();

        fps = 0;

        double lastTime = GLFW.glfwGetTime();
        double currTime;
        double deltaTime = 0.0;

        while (!GameObject.MY_WINDOW.shouldClose()) {
            // changing resolution if necessary
            int width = GameObject.MY_WINDOW.getWidth();
            int height = GameObject.MY_WINDOW.getHeight();
            if (width != widthGL
                    || height != heightGL) {
                MasterRenderer.setResolution(width, height);
                PerspectiveRenderer.updatePerspective(GameObject.MY_WINDOW);
                widthGL = width;
                heightGL = height;
            }

            currTime = GLFW.glfwGetTime();
            deltaTime = currTime - lastTime;

            fpsTicks += deltaTime * Game.getFpsMax();
            lastTime = currTime;

            // Detecting critical status
            if (fps == 0 && deltaTime > Game.CRITICAL_TIME) {
                DSLogger.reportFatalError("Game status critical!", null);
                GameObject.MY_WINDOW.close();
                break;
            }

            if (fpsTicks >= 1.0 && Game.upsTicks < 1.0) {
                gameObject.render();
                fps++;
                fpsTicks--;
            }

            // update text which shows dialog every 5 seconds
            if (GLFW.glfwGetTime() > timer1 + 5.0) {
                if (gameObject.getIntrface().getSaveDialog().isDone()) {
                    gameObject.getIntrface().getSaveDialog().setEnabled(false);
                }
                if (gameObject.getIntrface().getLoadDialog().isDone()) {
                    gameObject.getIntrface().getLoadDialog().setEnabled(false);
                }
                if (gameObject.getIntrface().getLoadDialog().isDone()) {
                    gameObject.getIntrface().getLoadDialog().setEnabled(false);
                }
                if (gameObject.getIntrface().getRandLvlDialog().isDone()) {
                    gameObject.getIntrface().getRandLvlDialog().setEnabled(false);
                }

                if (gameObject.getIntrface().getSinglePlayerDialog().isDone()) {
                    gameObject.getIntrface().getSinglePlayerDialog().setEnabled(false);
                }

                gameObject.getIntrface().getCollText().setContent("");
                gameObject.getIntrface().getScreenText().setEnabled(false);

                timer1 += 5.0;
            }

            // update text which animates water every quarter of the second
            if (GLFW.glfwGetTime() > timer2 + 0.25) {
                if (gameObject.getLevelContainer().getProgress() == 100.0f) {
                    gameObject.getIntrface().getProgText().setEnabled(false);
                    gameObject.getLevelContainer().setProgress(0.0f);
                }

                if (!gameObject.isWorking()) {
                    if (Editor.getSelectedCurr() == null && Editor.getSelectedNew() == null) {
                        gameObject.animate();
                    }
                }
                timer2 += 0.25;
            }

            // lastly it executes the console tasks
            FutureTask<Boolean> task;
            while ((task = TASK_QUEUE.poll()) != null) {
                execute(task);
            }
        }

        // renderer is reaching end of life!
        Window.unloadContext();
    }

    @Override
    public void execute(Runnable command) {
        command.run();
    }

    public LevelContainer getLevelContainer() {
        return gameObject.getLevelContainer();
    }

    public static double getFpsTicks() {
        return fpsTicks;
    }

    public static void setFpsTicks(double fpsTicks) {
        Renderer.fpsTicks = fpsTicks;
    }

    public static int getFps() {
        return fps;
    }

    public static void setFps(int fps) {
        Renderer.fps = fps;
    }

    public GameObject getGameObject() {
        return gameObject;
    }

    public static double getAlpha() {
        return alpha;
    }

}
