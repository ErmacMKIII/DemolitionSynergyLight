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

import rs.alexanderstojanovich.evgl.audio.AudioPlayer;
import rs.alexanderstojanovich.evgl.core.MasterRenderer;
import rs.alexanderstojanovich.evgl.core.Window;
import rs.alexanderstojanovich.evgl.critter.Critter;
import rs.alexanderstojanovich.evgl.intrface.Intrface;
import rs.alexanderstojanovich.evgl.level.LevelContainer;
import rs.alexanderstojanovich.evgl.level.RandomLevelGenerator;

/**
 *
 * @author Alexander Stojanovich <coas91@rocketmail.com>
 */
public final class GameObject { // is mutual object for {Main, Renderer, Random Level Generator}
    // this class protects levelContainer, waterRenderer & Random Level Generator between the threads

    public static final String TITLE = "Demolition Synergy - v21 VIVID LIGHT";

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

    private GameObject() {
        this.levelContainer = new LevelContainer(this);
        this.randomLevelGenerator = new RandomLevelGenerator(levelContainer);
        this.intrface = new Intrface(this);
    }

    /**
     * Get shared Game Object instance. Game Object is controller for the game.
     *
     * @return Game Object instance
     */
    public static GameObject getInstance() {
        if (instance == null) {
            instance = new GameObject();
        }
        return instance;
    }

    // -------------------------------------------------------------------------
    /**
     * Update Game Object stuff (call only from main)
     *
     * @param deltaTime game object environment update time
     */
    public void update(float deltaTime) {
        if (!levelContainer.isWorking()) { // working check avoids locking the monitor
            levelContainer.update(deltaTime);
        }
        intrface.update();
        intrface.setCollText(assertCollision);
    }

    /**
     * Renderer method. Requires context to be set in the proper thread (call
     * only from renderer)
     */
    public void render() {
        MasterRenderer.render(); // it clears color bit and depth buffer bit
        if (levelContainer.isWorking()) { // working check avoids locking the monitor
            intrface.getProgText().setEnabled(true);
            intrface.getProgText().setContent("Loading progress: " + Math.round(levelContainer.getProgress()) + "%");
        } else {
            levelContainer.render();
            intrface.getProgText().setEnabled(false);
        }
        intrface.getGameModeText().setContent(Game.getCurrentMode().name());
        intrface.render();
        MY_WINDOW.render();
    }

    // -------------------------------------------------------------------------
    /**
     * Hint to the render that objects should be buffered
     */
    public void unbuffer() {
        levelContainer.getSolidChunks().setBuffered(false);
        levelContainer.getFluidChunks().setBuffered(false);
    }

    /**
     * Calls chunk functions to determine visible chunks
     */
    public void determineVisibleChunks() {
        levelContainer.determineVisible();
    }

    /**
     * Auto load/save level container chunks
     */
    public void chunkOperations() {
        levelContainer.chunkOperations();
    }

    /**
     * Animation for water (and other fluids)
     *
     */
    public void animate() {
        levelContainer.animate();
    }

    // -------------------------------------------------------------------------
    // Called from concurrent thread
    public void startNewLevel() {
        levelContainer.startNewLevel();
    }

    // Called from concurrent thread
    public boolean loadLevelFromFile(String fileName) {
        boolean ok = levelContainer.loadLevelFromFile(fileName);
        return ok;
    }

    // Called from concurrent thread
    public boolean saveLevelToFile(String fileName) {
        boolean ok = levelContainer.saveLevelToFile(fileName);
        return ok;
    }

    // Called from concurrent thread
    public boolean generateRandomLevel(int numberOfBlocks) {
        boolean ok = levelContainer.generateRandomLevel(randomLevelGenerator, numberOfBlocks);
        return ok;
    }

    // Checked from main and Renderer
    public boolean isWorking() {
        return levelContainer.isWorking();
    }
    // -------------------------------------------------------------------------

    /*    
    * Load the window context and destroyes the window.
     */
    public void destroy() {
        GameObject.MY_WINDOW.loadContext();
        GameObject.MY_WINDOW.destroy();
    }

    // collision detection - critter against solid obstacles
    public boolean hasCollisionWithCritter(Critter critter) {
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
