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
package rs.alexanderstojanovich.evgl.main;

import org.joml.Vector2f;
import org.lwjgl.glfw.GLFW;
import rs.alexanderstojanovich.evgl.audio.AudioPlayer;
import rs.alexanderstojanovich.evgl.core.MasterRenderer;
import rs.alexanderstojanovich.evgl.core.PerspectiveRenderer;
import rs.alexanderstojanovich.evgl.core.Window;
import rs.alexanderstojanovich.evgl.intrface.Intrface;
import rs.alexanderstojanovich.evgl.level.LevelContainer;
import rs.alexanderstojanovich.evgl.shaders.ShaderProgram;
import rs.alexanderstojanovich.evgl.util.DSLogger;

/**
 *
 * @author Coa
 */
public class Renderer extends Thread {

    private final Window myWindow;
    private LevelContainer levelContainer;
    private Intrface intrface;

    private final Object objMutex; // got from the Game    

    private boolean assertCollision = false;

    private static double fpsTicks = 0.0;
    private static int renPasses = 0;
    public static final int REN_MAX_PASSES = 3;

    private final AudioPlayer musicPlayer;
    private final AudioPlayer soundFXPlayer;

    public Renderer(Window myWindow, Object objMutex, AudioPlayer musicPlayer, AudioPlayer soundFXPlayer) {
        super("Renderer");
        this.myWindow = myWindow;
        this.objMutex = objMutex;
        this.musicPlayer = musicPlayer;
        this.soundFXPlayer = soundFXPlayer;
    }

    @Override
    public void run() {

        synchronized (objMutex) {
            MasterRenderer.initGL(myWindow); // loads myWindow context, creates OpenGL context..
            ShaderProgram.initAllShaders(); // it's important that first GL is done and then this one 
            PerspectiveRenderer.updatePerspective(myWindow); // updates perspective for all the existing shaders

            levelContainer = new LevelContainer(myWindow, musicPlayer, soundFXPlayer);
            intrface = new Intrface(myWindow, levelContainer, objMutex, musicPlayer, soundFXPlayer);
            // wake up the main thread
            objMutex.notify();
        }

        double timer0 = GLFW.glfwGetTime();
        double timer1 = GLFW.glfwGetTime();
        double timer2 = GLFW.glfwGetTime();

        int fps = 0;

        double lastTime = GLFW.glfwGetTime();
        double currTime;
        double diff;

        while (!myWindow.shouldClose()) {
            currTime = GLFW.glfwGetTime();
            diff = currTime - lastTime;
            fpsTicks += diff * Game.getFpsMax();
            lastTime = currTime;

            // Detecting critical status
            if (fps == 0 && diff > Game.CRITICAL_TIME) {
                DSLogger.reportFatalError("Game status critical!", null);
                myWindow.close();
                break;
            }

            if (Game.getUpsTicks() < 1.0 && Game.getUpdPasses() == 0) {
                while (fpsTicks >= 1.0 && renPasses < REN_MAX_PASSES) {
                    synchronized (objMutex) {
                        myWindow.loadContext();
                        MasterRenderer.render(); // it clears color bit and depth buffer bit
                        if (!levelContainer.isWorking()) {
                            levelContainer.render();
                        } else {
                            intrface.getProgText().setContent("Loading progress: " + Math.round(levelContainer.getProgress()) + "%");
                            intrface.getProgText().render();
                        }
                        intrface.setCollText(assertCollision);
                        intrface.getGameModeText().setContent(Game.getCurrentMode().name());
                        intrface.getGameModeText().setOffset(new Vector2f(-Game.getCurrentMode().name().length(), 1.0f));
                        intrface.render();
                        myWindow.render();
                        fps++;
                        fpsTicks--;
                        renPasses++;
                        Window.unloadContext();
                    }
                }
                renPasses = 0;
            }
            // update text which shows ups and fps every second
            if (GLFW.glfwGetTime() > timer0 + 1.0) {
                intrface.getFpsText().setContent("fps: " + fps);
                fps = 0;
                timer0 += 1.0;
            }

            // update text which shows dialog every 5 seconds
            if (GLFW.glfwGetTime() > timer1 + 5.0) {
                if (intrface.getSaveDialog().isDone()) {
                    intrface.getSaveDialog().setEnabled(false);
                }
                if (intrface.getLoadDialog().isDone()) {
                    intrface.getLoadDialog().setEnabled(false);
                }
                if (intrface.getLoadDialog().isDone()) {
                    intrface.getLoadDialog().setEnabled(false);
                }
                if (intrface.getRandLvlDialog().isDone()) {
                    intrface.getRandLvlDialog().setEnabled(false);
                }

                if (intrface.getSinglePlayerDialog().isDone()) {
                    intrface.getSinglePlayerDialog().setEnabled(false);
                }

                intrface.getCollText().setContent("");
                intrface.getScreenText().setEnabled(false);

                timer1 += 5.0;
            }

            // update text which animates water every quarter of the second
            if (GLFW.glfwGetTime() > timer2 + 0.25) {
                if (levelContainer.getProgress() == 100) {
                    intrface.getProgText().setEnabled(false);
                    levelContainer.setProgress(0);
                }
                synchronized (objMutex) {
                    myWindow.loadContext();
                    if (levelContainer.getProgress() == 0.0f && !levelContainer.isWorking()) {
                        levelContainer.animate();
                    }
                    Window.unloadContext();
                }
                timer2 += 0.25;
            }
        }

    }

    public void update(float deltaTime) {
        synchronized (objMutex) {
            levelContainer.update(deltaTime);
            intrface.update();
        }
    }

    public Window getMyWindow() {
        return myWindow;
    }

    public LevelContainer getLevelContainer() {
        return levelContainer;
    }

    public Intrface getIntrface() {
        return intrface;
    }

    public Object getObjMutex() {
        return objMutex;
    }

    public boolean isAssertCollision() {
        return assertCollision;
    }

    public void setAssertCollision(boolean assertCollision) {
        this.assertCollision = assertCollision;
    }

    public static double getFpsTicks() {
        return fpsTicks;
    }

    public static void setFpsTicks(double fpsTicks) {
        Renderer.fpsTicks = fpsTicks;
    }

    public static int getRenPasses() {
        return renPasses;
    }

    public AudioPlayer getMusicPlayer() {
        return musicPlayer;
    }

    public AudioPlayer getSoundFXPlayer() {
        return soundFXPlayer;
    }

}
