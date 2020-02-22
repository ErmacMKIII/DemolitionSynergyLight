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

import java.util.ArrayList;
import java.util.List;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import rs.alexanderstojanovich.evgl.audio.AudioPlayer;
import rs.alexanderstojanovich.evgl.core.Combo;
import rs.alexanderstojanovich.evgl.core.MasterRenderer;
import rs.alexanderstojanovich.evgl.core.PerspectiveRenderer;
import rs.alexanderstojanovich.evgl.core.Window;
import rs.alexanderstojanovich.evgl.level.Editor;
import rs.alexanderstojanovich.evgl.level.LevelContainer;
import rs.alexanderstojanovich.evgl.main.Game;
import rs.alexanderstojanovich.evgl.main.Game.Mode;
import rs.alexanderstojanovich.evgl.main.Renderer;
import rs.alexanderstojanovich.evgl.texture.Texture;
import rs.alexanderstojanovich.evgl.util.Pair;
import rs.alexanderstojanovich.evgl.util.PlainTextReader;

/**
 *
 * @author Coa
 */
public class Intrface {

    private final Window myWindow;

    private Quad crosshair;
    private Text infoText; // displays update and framerate
    private Text collText; // collision info
    private Text helpText; // displays the help (toggle)
    private Text progText; // progress text;
    private Text gameModeText; // displays game mode {EDITOR, SINGLE_PLAYER or MUTLIPLAYER}
    private boolean showHelp = false;

    private ConcurrentDialog commandDialog;
    private ConcurrentDialog saveDialog;
    private ConcurrentDialog loadDialog;
    private ConcurrentDialog randLvlDialog;
    private ConcurrentDialog singlePlayerDialog;

    private Menu mainMenu;
    private OptionsMenu optionsMenu;
    private Menu editorMenu;

    private final LevelContainer levelContainer;

    public static final String FONT_IMG = "hack.png";

    private final Object objMutex;

    private final AudioPlayer musicPlayer;
    private final AudioPlayer soundFXPlayer;

    public Intrface(Window myWindow, LevelContainer levelContainer, Object objMutex, AudioPlayer musicPlayer, AudioPlayer soundFXPlayer) {
        this.myWindow = myWindow;
        this.levelContainer = levelContainer;
        this.objMutex = objMutex;
        this.musicPlayer = musicPlayer;
        this.soundFXPlayer = soundFXPlayer;
        initIntrface();
    }

    private void initIntrface() {
        infoText = new Text(myWindow, Texture.FONT, "Welcome to Demolition Synergy!", new Vector3f(0.0f, 1.0f, 0.0f), new Vector2f(-1.0f, 1.0f));
        infoText.setOffset(new Vector2f(1.0f, 1.0f));
        collText = new Text(myWindow, Texture.FONT, "No Collision", new Vector3f(0.0f, 1.0f, 0.0f), new Vector2f(-1.0f, -1.0f));
        collText.setOffset(new Vector2f(1.0f, -1.0f));
        helpText = new Text(myWindow, Texture.FONT, PlainTextReader.readFromFile(Game.INTRFACE_ENTRY, "help.txt"), new Vector3f(1.0f, 1.0f, 1.0f), new Vector2f(-1.0f, 0.9f));
        helpText.setOffset(new Vector2f(1.0f, 1.0f));
        helpText.getQuad().setScale(0.75f);
        helpText.setEnabled(false);
        progText = new Text(myWindow, Texture.FONT, "", new Vector3f(1.0f, 1.0f, 0.0f), new Vector2f(-1.0f, -0.9f));
        progText.setOffset(new Vector2f(1.0f, -1.0f));
        gameModeText = new Text(myWindow, Texture.FONT, Game.getCurrentMode().name(), new Vector3f(0.0f, 1.0f, 0.0f), new Vector2f(1.0f, 1.0f));

        crosshair = new Quad(myWindow, 27, 27, Texture.CROSSHAIR, true); // it ignores resolution changes and doesn't scale
        List<Pair<String, Boolean>> mainMenuPairs = new ArrayList<>();
        mainMenuPairs.add(new Pair<>("SINGLE PLAYER", true));
        mainMenuPairs.add(new Pair<>("MULTIPLAYER", false));
        mainMenuPairs.add(new Pair<>("EDITOR", true));
        mainMenuPairs.add(new Pair<>("OPTIONS", true));
        mainMenuPairs.add(new Pair<>("EXIT", true));
        mainMenu = new Menu(myWindow, "", mainMenuPairs, FONT_IMG, new Vector2f(0.0f, 0.5f), 2.0f) {
            @Override
            protected void leave() {

            }

            @Override
            protected void execute() {
                String s = mainMenu.getItems().get(mainMenu.getSelected()).getContent();
                switch (s) {
                    case "SINGLE PLAYER":
                        singlePlayerDialog.open();
                        break;
                    case "EDITOR":
                        editorMenu.open();
                        break;
                    case "OPTIONS":
                        optionsMenu.open();
                        break;
                    case "EXIT":
                        GLFW.glfwSetWindowShouldClose(myWindow.getWindowID(), true);
                        break;
                }
            }
        };
        Quad logo = new Quad(myWindow, 180, 100, Texture.LOGO);
        logo.getColor().x = 1.0f;
        logo.getColor().y = 0.7f;
        logo.getColor().z = 0.1f;
        mainMenu.setLogo(logo);
        mainMenu.setAlignmentAmount(Menu.ALIGNMENT_CENTER);

        commandDialog = new ConcurrentDialog(myWindow, Texture.FONT, new Vector2f(-0.95f, 0.85f),
                "ENTER COMMAND: ", "OK", "ERROR!") {
            @Override
            protected boolean execute(String command) {
                boolean success = false;
                String[] things = command.split(" ");
                if (things.length > 0) {
                    switch (things[0].toLowerCase()) {
                        case "fps_max":
                        case "fpsmax":
                            if (things.length == 2) {
                                int num = Integer.parseInt(things[1]);
                                if (num > 0) {
                                    Game.setFpsMax(num);
                                    Renderer.setFpsTicks(0.0);
                                    success = true;
                                }
                            }
                            break;
                        case "resolution":
                        case "res":
                            if (things.length == 3) {
                                int width = Integer.parseInt(things[1]);
                                int height = Integer.parseInt(things[2]);
                                synchronized (objMutex) {
                                    myWindow.loadContext();
                                    GL.setCapabilities(MasterRenderer.getGlCaps());
                                    success = myWindow.setResolution(width, height);
                                    myWindow.centerTheWindow();
                                    PerspectiveRenderer.updatePerspective(myWindow);
                                    GL.setCapabilities(null);
                                    Window.unloadContext();
                                }
                            }
                            break;
                        case "fullscreen":
                            synchronized (objMutex) {
                                myWindow.loadContext();
                                myWindow.fullscreen();
                                myWindow.centerTheWindow();
                                Window.unloadContext();
                            }
                            success = true;
                            break;
                        case "windowed":
                            synchronized (objMutex) {
                                myWindow.loadContext();
                                myWindow.windowed();
                                myWindow.centerTheWindow();
                                Window.unloadContext();
                            }
                            success = true;
                            break;
                        case "v_sync":
                        case "vsync":
                            if (things.length == 2) {
                                synchronized (objMutex) {
                                    myWindow.loadContext();
                                    if (Boolean.parseBoolean(things[1])) {
                                        myWindow.enableVSync();
                                    } else {
                                        myWindow.disableVSync();
                                    }
                                    Window.unloadContext();
                                }
                                success = true;
                            }
                            break;
                        case "msens":
                        case "mouse_sensitivity":
                            if (things.length == 2) {
                                Game.setMouseSensitivity(Float.parseFloat(things[1]));
                                success = true;
                            }
                            break;
                        case "music":
                        case "musicVolume":
                            if (things.length == 2) {
                                float volume = Float.parseFloat(things[1]);
                                if (volume >= 0.0f && volume <= 1.0f) {
                                    musicPlayer.setGain(volume);
                                    success = true;
                                }
                            }
                            break;
                        case "sound":
                        case "soundVolume":
                            if (things.length == 2) {
                                float volume = Float.parseFloat(things[1]);
                                if (volume >= 0.0f && volume <= 1.0f) {
                                    soundFXPlayer.setGain(volume);
                                    success = true;
                                }
                            }
                            break;
                        default:
                            success = false;
                            break;
                    }
                }
                return success;
            }
        };

        saveDialog = new ConcurrentDialog(myWindow, Texture.FONT, new Vector2f(-0.95f, 0.85f),
                "SAVE LEVEL TO FILE: ", "LEVEL SAVED SUCESSFULLY!", "SAVING LEVEL FAILED!") {
            @Override
            protected boolean execute(String command) {
                Editor.deselect();
                progText.enabled = true;
                boolean ok = levelContainer.saveLevelToFile(command);
                if (ok) {
                    Game.setCurrentMode(Mode.EDITOR);
                }
                return ok;
            }
        };

        loadDialog = new ConcurrentDialog(myWindow, Texture.FONT, new Vector2f(-0.95f, 0.85f),
                "LOAD LEVEL FROM FILE: ", "LEVEL LOADED SUCESSFULLY!", "LOADING LEVEL FAILED!") {
            @Override
            protected boolean execute(String command) {
                Editor.deselect();
                progText.enabled = true;
                boolean ok = levelContainer.loadLevelFromFile(command);
                if (ok) {
                    Game.setCurrentMode(Mode.EDITOR);
                }
                return ok;
            }
        };

        randLvlDialog = new ConcurrentDialog(myWindow, Texture.FONT, new Vector2f(-0.95f, 0.85f),
                "ENTER NUMBER OF BLOCKS (LIMIT 20000): ", "LEVEL GENERATED SUCESSFULLY", "LEVEL GENERATION FAILED!") {
            @Override
            protected boolean execute(String command) {
                Editor.deselect();
                progText.enabled = true;
                boolean ok = levelContainer.generateRandomLevel(Integer.valueOf(command));
                if (ok) {
                    Game.setCurrentMode(Mode.EDITOR);
                }
                return ok;
            }
        };

        singlePlayerDialog = new ConcurrentDialog(myWindow, Texture.FONT, new Vector2f(-0.95f, 0.85f), "START NEW GAME (Y/N)? ", "OK!", "ERROR!") {
            @Override
            protected boolean execute(String command) {
                boolean ok = false;
                if (!levelContainer.isWorking() && (command.equalsIgnoreCase("yes") || command.equalsIgnoreCase("y"))) {
                    Game.setCurrentMode(Mode.SINGLE_PLAYER);
                    ok = true;
                }
                return ok;
            }
        };

        List<Pair<String, Boolean>> optionsMenuPairs = new ArrayList<>();
        optionsMenuPairs.add(new Pair<>("FPS CAP", true));
        optionsMenuPairs.add(new Pair<>("RESOLUTION", true));
        optionsMenuPairs.add(new Pair<>("FULLSCREEN", true));
        optionsMenuPairs.add(new Pair<>("VSYNC", true));
        optionsMenuPairs.add(new Pair<>("MOUSE SENSITIVITY", true));
        optionsMenuPairs.add(new Pair<>("MUSIC VOLUME", true));
        optionsMenuPairs.add(new Pair<>("SOUND VOLUME", true));
        optionsMenu = new OptionsMenu(myWindow, "OPTIONS", optionsMenuPairs, FONT_IMG, new Vector2f(0.0f, 0.5f), 2.0f) {
            @Override
            protected void leave() {
                mainMenu.open();
            }

            @Override
            protected void refreshValues() {
                getValues()[0].setContent(String.valueOf(Game.getFpsMax()));
                getValues()[1].setContent(String.valueOf(myWindow.getWidth()) + "x" + String.valueOf(myWindow.getHeight()));
                getValues()[2].setContent(myWindow.isFullscreen() ? "ON" : "OFF");
                getValues()[3].setContent(myWindow.isVsync() ? "ON" : "OFF");
                getValues()[4].setContent(String.valueOf(Game.getMouseSensitivity()));
                getValues()[5].setContent(String.valueOf(musicPlayer.getGain()));
                getValues()[6].setContent(String.valueOf(soundFXPlayer.getGain()));
            }

            @Override
            protected void execute() {
                if (getOptions()[0].giveCurrent() != null) {
                    Game.setFpsMax((int) getOptions()[0].giveCurrent());
                    Renderer.setFpsTicks(0.0);
                }
                //--------------------------------------------------------------
                if (getOptions()[1].giveCurrent() != null) {
                    String[] things = getOptions()[1].giveCurrent().toString().split("x");
                    synchronized (objMutex) {
                        myWindow.loadContext();
                        GL.setCapabilities(MasterRenderer.getGlCaps());
                        myWindow.setResolution(Integer.parseInt(things[0]), Integer.parseInt(things[1]));
                        PerspectiveRenderer.updatePerspective(myWindow);
                        GL.setCapabilities(null);
                        Window.unloadContext();
                    }
                }
                //--------------------------------------------------------------
                if (getOptions()[2].giveCurrent() != null) {
                    switch (getOptions()[2].giveCurrent().toString()) {
                        case "OFF":
                            synchronized (objMutex) {
                                myWindow.loadContext();
                                myWindow.windowed();
                                myWindow.centerTheWindow();
                                Window.unloadContext();
                            }
                            break;
                        case "ON":
                            synchronized (objMutex) {
                                myWindow.loadContext();
                                myWindow.fullscreen();
                                myWindow.centerTheWindow();
                                Window.unloadContext();
                            }
                            break;
                    }
                }
                //--------------------------------------------------------------
                if (getOptions()[3].giveCurrent() != null) {
                    switch (getOptions()[3].giveCurrent().toString()) {
                        case "OFF":
                            synchronized (objMutex) {
                                myWindow.loadContext();
                                myWindow.disableVSync();
                                Window.unloadContext();
                            }
                            break;
                        case "ON":
                            synchronized (objMutex) {
                                myWindow.loadContext();
                                myWindow.enableVSync();
                                Window.unloadContext();
                            }
                            break;
                    }
                }
                //--------------------------------------------------------------                
                if (getOptions()[4].giveCurrent() != null) {
                    Game.setMouseSensitivity(Float.parseFloat(getOptions()[4].giveCurrent().toString()));
                }
                //--------------------------------------------------------------
                if (getOptions()[5].giveCurrent() != null) {
                    musicPlayer.setGain(Float.parseFloat(getOptions()[5].giveCurrent().toString()));
                }
                //--------------------------------------------------------------
                if (getOptions()[6].giveCurrent() != null) {
                    soundFXPlayer.setGain(Float.parseFloat(getOptions()[6].giveCurrent().toString()));
                }
            }
        };
        Object[] fpsCaps = {35, 60, 75, 100, 200, 300};
        Object[] resolutions = myWindow.giveAllResolutions();
        Object[] swtch = {"OFF", "ON"};
        Object[] mouseSens = {1.0f, 1.5f, 2.0f, 2.5f, 3.0f, 3.5f, 4.0f, 5.0f, 5.5f, 6.0f, 6.5f, 7.0f, 7.5f, 8.0f, 8.5f, 9.0f, 9.5f, 10.0f};
        Object[] volume = new Float[21];
        int k = 0;
        for (float i = 0.0f; i < 1.05f; i += 0.05f) {
            volume[k++] = Math.round(i * 100.0f) / 100.f; // rounding to two decimal places
        }
        optionsMenu.getOptions()[0] = new Combo(fpsCaps, 3);
        optionsMenu.getOptions()[1] = new Combo(resolutions, 0);
        optionsMenu.getOptions()[2] = new Combo(swtch, 0);
        optionsMenu.getOptions()[3] = new Combo(swtch, 0);
        optionsMenu.getOptions()[4] = new Combo(mouseSens, 4);
        optionsMenu.getOptions()[5] = new Combo(volume, 4);
        optionsMenu.getOptions()[6] = new Combo(volume, 4);
        optionsMenu.setAlignmentAmount(Menu.ALIGNMENT_LEFT);

        List<Pair<String, Boolean>> editorMenuPairs = new ArrayList<>();
        editorMenuPairs.add(new Pair<>("START NEW LEVEL", true));
        editorMenuPairs.add(new Pair<>("GENERATE RANDOM LEVEL", true));
        editorMenuPairs.add(new Pair<>("SAVE LEVEL TO FILE", true));
        editorMenuPairs.add(new Pair<>("LOAD LEVEL FROM FILE", true));

        editorMenu = new Menu(myWindow, "EDITOR", editorMenuPairs, FONT_IMG, new Vector2f(0.0f, 0.5f), 2.0f) {
            @Override
            protected void leave() {
                mainMenu.open();
            }

            @Override
            protected void execute() {
                String s = editorMenu.getItems().get(editorMenu.getSelected()).getContent();
                switch (s) {
                    case "START NEW LEVEL":
                        progText.setEnabled(true);
                        levelContainer.startNewLevel();
                        Game.setCurrentMode(Mode.EDITOR);
                        break;
                    case "GENERATE RANDOM LEVEL":
                        progText.setEnabled(true);
                        randLvlDialog.open();
                        break;
                    case "SAVE LEVEL TO FILE":
                        progText.setEnabled(true);
                        saveDialog.open();
                        break;
                    case "LOAD LEVEL FROM FILE":
                        progText.setEnabled(true);
                        loadDialog.open();
                        break;
                }
            }
        };
        editorMenu.setAlignmentAmount(Menu.ALIGNMENT_LEFT);
    }

    public void setCollText(boolean mode) {
        if (mode) {
            collText.setContent("Collision!");
            collText.getQuad().getColor().x = 1.0f;
            collText.getQuad().getColor().y = 0.0f;
            collText.getQuad().getColor().z = 0.0f;
        } else {
            collText.setContent("No Collision");
            collText.getQuad().getColor().x = 0.0f;
            collText.getQuad().getColor().y = 1.0f;
            collText.getQuad().getColor().z = 0.0f;
        }
    }

    public void toggleShowHelp() {
        showHelp = !showHelp;
        if (showHelp) {
            helpText.setEnabled(true);
            collText.setEnabled(false);
        } else {
            helpText.setEnabled(false);
            collText.setEnabled(true);
        }
    }

    public void render() {
        commandDialog.render();
        saveDialog.render();
        loadDialog.render();
        randLvlDialog.render();
        singlePlayerDialog.render();
        infoText.render();
        collText.render();
        helpText.render();
        gameModeText.render();
        mainMenu.render();
        optionsMenu.render();
        editorMenu.render();
        if (!mainMenu.isEnabled() && !optionsMenu.isEnabled() && !editorMenu.isEnabled() && !showHelp) {
            if (!crosshair.isBuffered()) {
                crosshair.buffer();
            }
            crosshair.render();
        }
    }

    public Window getMyWindow() {
        return myWindow;
    }

    public Quad getCrosshair() {
        return crosshair;
    }

    public Text getInfoText() {
        return infoText;
    }

    public Text getCollText() {
        return collText;
    }

    public Text getHelpText() {
        return helpText;
    }

    public boolean isShowHelp() {
        return showHelp;
    }

    public Dialog getCommandDialog() {
        return commandDialog;
    }

    public Dialog getSaveDialog() {
        return saveDialog;
    }

    public Dialog getLoadDialog() {
        return loadDialog;
    }

    public Menu getMainMenu() {
        return mainMenu;
    }

    public OptionsMenu getOptionsMenu() {
        return optionsMenu;
    }

    public Menu getEditorMenu() {
        return editorMenu;
    }

    public static String getFONT_IMG() {
        return FONT_IMG;
    }

    public void setShowHelp(boolean showHelp) {
        this.showHelp = showHelp;
    }

    public ConcurrentDialog getRandLvlDialog() {
        return randLvlDialog;
    }

    public Text getProgText() {
        return progText;
    }

    public Object getObjMutex() {
        return objMutex;
    }

    public Text getGameModeText() {
        return gameModeText;
    }

    public ConcurrentDialog getSinglePlayerDialog() {
        return singlePlayerDialog;
    }

}