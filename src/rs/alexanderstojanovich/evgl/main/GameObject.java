/*
 * Copyright (C) 2020 Coa
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
import rs.alexanderstojanovich.evgl.audio.AudioPlayer;
import rs.alexanderstojanovich.evgl.core.MasterRenderer;
import rs.alexanderstojanovich.evgl.core.Window;
import rs.alexanderstojanovich.evgl.critter.Critter;
import rs.alexanderstojanovich.evgl.intrface.Intrface;
import rs.alexanderstojanovich.evgl.level.LevelContainer;
import rs.alexanderstojanovich.evgl.level.RandomLevelGenerator;

/**
 *
 * @author Coa
 */
public final class GameObject { // is mutual object for {Main, Renderer, Random Level Generator}
    // this class protects levelContainer, waterRenderer & Random Level Generator between the threads

    public static final String TITLE = "Demolition Synergy - v19 TITANIUM LSV";

    public static final Object OBJ_MUTEX = new Object(); // mutex for window, used for game and renderer

    // makes default window -> Renderer sets resolution from config
    public static final Window MY_WINDOW = new Window(Window.MIN_WIDTH, Window.MIN_HEIGHT, TITLE); // creating the window

    private final LevelContainer levelContainer;
    private final RandomLevelGenerator randomLevelGenerator;

    private final Intrface intrface;

    private final AudioPlayer musicPlayer = new AudioPlayer();
    private final AudioPlayer soundFXPlayer = new AudioPlayer();

    private boolean assertCollision = false;

    // everyone can access only one instance of the game object
    private static GameObject instance;

    private boolean concurrent = false;

    private GameObject() {
        this.levelContainer = new LevelContainer(this);
        this.randomLevelGenerator = new RandomLevelGenerator(levelContainer);
        this.intrface = new Intrface(this);
    }

    // lazy initialization allowing only one instance
    public static GameObject getInstance() {
        if (instance == null) {
            instance = new GameObject();
        }
        return instance;
    }

    // -------------------------------------------------------------------------
    // update Game Object stuff (call only from main)
    public synchronized void update(float deltaTime) {
        if (!levelContainer.isWorking() && !concurrent) { // working check avoids locking the monitor
            levelContainer.update(deltaTime);
        }
        intrface.update();
        intrface.setCollText(assertCollision);
    }

    // requires context to be set in the proper thread (call only from renderer)
    public synchronized void render() {
        MasterRenderer.render(); // it clears color bit and depth buffer bit
        if (levelContainer.isWorking() || concurrent) { // working check avoids locking the monitor
            intrface.getProgText().setEnabled(true);
            intrface.getProgText().setContent("Loading progress: " + Math.round(levelContainer.getProgress()) + "%");
        } else {
            levelContainer.render();
            intrface.getProgText().setEnabled(false);
        }
        intrface.getGameModeText().setContent(Game.getCurrentMode().name());
        intrface.getGameModeText().setOffset(new Vector2f(-Game.getCurrentMode().name().length(), 1.0f));
        intrface.render();
        MY_WINDOW.render();
    }

    // -------------------------------------------------------------------------
    // hint to the render that objects should be buffered
    public synchronized void unbuffer() {
        levelContainer.getSolidChunks().setBuffered(false);
        levelContainer.getFluidChunks().setBuffered(false);
    }

    // patch chunks
    public synchronized void patch() {
        levelContainer.patch();
    }

    // animation for water
    public synchronized void animate() {
        levelContainer.animate();
    }

    // -------------------------------------------------------------------------
    // Called from concurrent thread
    public void startNewLevel() {
        concurrent = true;
        levelContainer.startNewLevel();
        concurrent = false;
    }

    // Called from concurrent thread
    public boolean loadLevelFromFile(String fileName) {
        concurrent = true;
        boolean ok = levelContainer.loadLevelFromFile(fileName);
        concurrent = false;
        return ok;
    }

    // Called from concurrent thread
    public boolean saveLevelToFile(String fileName) {
        concurrent = true;
        boolean ok = levelContainer.saveLevelToFile(fileName);
        concurrent = false;
        return ok;
    }

    // Called from concurrent thread
    public boolean generateRandomLevel(int numberOfBlocks) {
        concurrent = true;
        boolean ok = levelContainer.generateRandomLevel(randomLevelGenerator, numberOfBlocks);
        concurrent = false;
        return ok;
    }

    public boolean isWorking() {
        return levelContainer.isWorking();
    }
    // -------------------------------------------------------------------------

    // destroys the window
    public synchronized void destroy() {
        GameObject.MY_WINDOW.loadContext();
        GameObject.MY_WINDOW.destroy();
    }

    // collision detection - critter against solid obstacles
    public synchronized boolean hasCollisionWithCritter(Critter critter) {
        return levelContainer.hasCollisionWithCritter(critter);
    }

    // prints general and detailed information about solid and fluid chunks
    public void printInfo() {
        levelContainer.getSolidChunks().printInfo();
        levelContainer.getFluidChunks().printInfo();
    }

    public LevelContainer getLevelContainer() {
        return levelContainer;
    }

    public AudioPlayer getMusicPlayer() {
        return musicPlayer;
    }

    public AudioPlayer getSoundFXPlayer() {
        return soundFXPlayer;
    }

    public boolean isAssertCollision() {
        return assertCollision;
    }

    public void setAssertCollision(boolean assertCollision) {
        this.assertCollision = assertCollision;
    }

    public Intrface getIntrface() {
        return intrface;
    }

    public RandomLevelGenerator getRandomLevelGenerator() {
        return randomLevelGenerator;
    }

}
