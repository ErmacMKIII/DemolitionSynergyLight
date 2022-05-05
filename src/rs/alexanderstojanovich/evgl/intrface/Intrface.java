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

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.FutureTask;
import org.joml.Vector2f;
import org.joml.Vector3f;
import rs.alexanderstojanovich.evgl.audio.AudioPlayer;
import rs.alexanderstojanovich.evgl.level.Editor;
import rs.alexanderstojanovich.evgl.main.Game;
import rs.alexanderstojanovich.evgl.main.Game.Mode;
import rs.alexanderstojanovich.evgl.main.GameObject;
import rs.alexanderstojanovich.evgl.main.Renderer;
import rs.alexanderstojanovich.evgl.shaders.ShaderProgram;
import rs.alexanderstojanovich.evgl.texture.Texture;
import rs.alexanderstojanovich.evgl.util.Pair;
import rs.alexanderstojanovich.evgl.util.PlainTextReader;
import rs.alexanderstojanovich.evgl.util.Vector3fColors;

/**
 *
 * @author Alexander Stojanovich <coas91@rocketmail.com>
 */
public class Intrface {

    private final GameObject gameObject;

    private Quad crosshair;
    private Text updText; // displays updates
    private Text fpsText; // displays framerates
    private Text posText; // display position
    private Text chunkText; // display current chunk (player)
    private Text collText; // collision info
    private Text helpText; // displays the help (toggle)
    private Text progText; // progress text;
    private Text screenText; // screenshot information
    private Text gameModeText; // displays game mode {EDITOR, SINGLE_PLAYER or MUTLIPLAYER}
    private boolean showHelp = false;

    private ConcurrentDialog saveDialog;
    private ConcurrentDialog loadDialog;
    private ConcurrentDialog randLvlDialog;
    private ConcurrentDialog singlePlayerDialog;

    private Menu mainMenu;
    private OptionsMenu optionsMenu;
    private Menu editorMenu;
    private Menu creditsMenu;
    private Menu randLvlMenu;
    private Menu loadLvlMenu;

    private int numBlocks = 0;

    public static final String FONT_IMG = "font.png"; // modified Hack font

    private final Console console = new Console();

    public Intrface(GameObject gameObject) {
        this.gameObject = gameObject;
        initIntrface();
    }

    private void initIntrface() {
        AudioPlayer musicPlayer = gameObject.getMusicPlayer();
        AudioPlayer soundFXPlayer = gameObject.getSoundFXPlayer();

        updText = new Text(Texture.FONT, "", Vector3fColors.GREEN, new Vector2f(-1.0f, 1.0f));
        updText.alignToNextChar();
        fpsText = new Text(Texture.FONT, "", Vector3fColors.GREEN, new Vector2f(-1.0f, 0.85f));
        fpsText.alignToNextChar();

        posText = new Text(Texture.FONT, "", Vector3fColors.GREEN, new Vector2f(1.0f, -1.0f));
        posText.setAlignment(Text.ALIGNMENT_RIGHT);
        posText.alignToNextChar();

        chunkText = new Text(Texture.FONT, "", Vector3fColors.GREEN, new Vector2f(1.0f, -0.85f));
        chunkText.setAlignment(Text.ALIGNMENT_RIGHT);
        chunkText.alignToNextChar();

        collText = new Text(Texture.FONT, "No Collision", Vector3fColors.GREEN, new Vector2f(-1.0f, -1.0f));
        collText.alignToNextChar();
        helpText = new Text(Texture.FONT, PlainTextReader.readFromFile(Game.INTRFACE_ENTRY, "help.txt"), Vector3fColors.WHITE, new Vector2f(-1.0f, 0.9f));
        helpText.setScale(0.625f);
        helpText.alignToNextChar();
        helpText.setEnabled(false);
        progText = new Text(Texture.FONT, "", Vector3fColors.YELLOW, new Vector2f(-1.0f, -0.9f));
        progText.alignToNextChar();
        screenText = new Text(Texture.FONT, "", Vector3fColors.WHITE, new Vector2f(-1.0f, -0.7f));
        screenText.setScale(0.625f);
        screenText.alignToNextChar();
        gameModeText = new Text(Texture.FONT, Game.getCurrentMode().name(), Vector3fColors.GREEN, new Vector2f(1.0f, 1.0f));
        gameModeText.setAlignment(Text.ALIGNMENT_RIGHT);
        gameModeText.alignToNextChar();

        crosshair = new Quad(27, 27, Texture.CROSSHAIR, true); // it ignores resolution changes and doesn't scale
        crosshair.setColor(Vector3fColors.WHITE);
        List<Pair<String, Boolean>> mainMenuPairs = new ArrayList<>();
        mainMenuPairs.add(new Pair<>("SINGLE PLAYER", true));
        mainMenuPairs.add(new Pair<>("MULTIPLAYER", false));
        mainMenuPairs.add(new Pair<>("EDITOR", true));
        mainMenuPairs.add(new Pair<>("OPTIONS", true));
        mainMenuPairs.add(new Pair<>("CREDITS", true));
        mainMenuPairs.add(new Pair<>("EXIT", true));
        mainMenu = new Menu("", mainMenuPairs, FONT_IMG, new Vector2f(0.0f, 0.5f), 2.0f) {
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
                    case "CREDITS":
                        creditsMenu.open();
                        break;
                    case "EXIT":
                        GameObject.MY_WINDOW.close();
                        break;
                }
            }
        };
        Quad logo = new Quad(232, 100, Texture.LOGO);
        logo.setColor(new Vector3f(1.0f, 0.7f, 0.1f));
        mainMenu.setLogo(logo);
        mainMenu.setAlignmentAmount(Text.ALIGNMENT_CENTER);

        saveDialog = new ConcurrentDialog(Texture.FONT, new Vector2f(-0.95f, 0.65f),
                "SAVE LEVEL TO FILE: ", "LEVEL SAVED SUCESSFULLY!", "SAVING LEVEL FAILED!") {
            @Override
            protected boolean execute(String command) {
                Editor.deselect();
                progText.enabled = true;
                boolean ok = gameObject.saveLevelToFile(command);
                if (ok) {
                    Game.setCurrentMode(Mode.EDITOR);
                }
                return ok;
            }
        };

        loadDialog = new ConcurrentDialog(Texture.FONT, new Vector2f(-0.95f, 0.65f),
                "LOAD LEVEL FROM FILE: ", "LEVEL LOADED SUCESSFULLY!", "LOADING LEVEL FAILED!") {
            @Override
            protected boolean execute(String command) {
                Editor.deselect();
                progText.enabled = true;
                boolean ok = gameObject.loadLevelFromFile(command);
                if (ok) {
                    Game.setCurrentMode(Mode.EDITOR);
                }
                return ok;
            }
        };
        loadDialog.dialog.alignToNextChar();

        File currFile = new File("./");
        String[] datFileList = currFile.list(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.toLowerCase().endsWith(".dat");
            }
        });

        List<Pair<String, Boolean>> loadLvlMenuPairs = new ArrayList<>();
        for (String datFile : datFileList) {
            loadLvlMenuPairs.add(new Pair<>(datFile, true));
        }

        loadLvlMenu = new Menu("LOAD LEVEL", loadLvlMenuPairs, FONT_IMG, new Vector2f(0.0f, 0.5f), 2.0f) {
            @Override
            protected void leave() {
                editorMenu.open();
            }

            @Override
            protected void execute() {
                String chosen = loadLvlMenu.getItems().get(loadLvlMenu.getSelected()).getContent();
                gameObject.loadLevelFromFile(chosen);
            }
        };
        loadLvlMenu.setAlignmentAmount(Text.ALIGNMENT_LEFT);

        randLvlDialog = new ConcurrentDialog(Texture.FONT, new Vector2f(-0.95f, 0.65f),
                "GENERATE RANDOM LEVEL\n(TIME-CONSUMING OPERATION) (Y/N)? ", "LEVEL GENERATED SUCESSFULLY!", "LEVEL GENERATION FAILED!") {
            @Override
            protected boolean execute(String command) {
                boolean ok = false;
                if (!gameObject.isWorking() && (command.equalsIgnoreCase("yes") || command.equalsIgnoreCase("y"))) {
                    Editor.deselect();
                    ok = gameObject.generateRandomLevel(numBlocks);
                    if (ok) {
                        Game.setCurrentMode(Mode.EDITOR);
                    }
                }
                return ok;
            }
        };

        List<Pair<String, Boolean>> randLvlMenuPairs = new ArrayList<>();
        randLvlMenuPairs.add(new Pair<>("SMALL  (25000  blocks)", true));
        randLvlMenuPairs.add(new Pair<>("MEDIUM (50000  blocks)", true));
        randLvlMenuPairs.add(new Pair<>("LARGE  (100000 blocks)", true));
        randLvlMenuPairs.add(new Pair<>("HUGE   (131070 blocks)", true));
        randLvlMenu = new Menu("GENERATE RANDOM LEVEL", randLvlMenuPairs, FONT_IMG, new Vector2f(0.0f, 0.5f), 2.0f) {
            @Override
            protected void leave() {
                mainMenu.open();
            }

            @Override
            protected void execute() {
                String str = randLvlMenu.items.get(selected).getContent();
                String[] split = str.split("\\s+");
                switch (split[0]) {
                    case "SMALL":
                        numBlocks = 25000;
                        break;
                    case "MEDIUM":
                        numBlocks = 50000;
                        break;
                    case "LARGE":
                        numBlocks = 100000;
                        break;
                    case "HUGE":
                        numBlocks = 131070;
                        break;
                    default:
                        numBlocks = 0;
                        break;
                }

                if (numBlocks != 0) {
                    randLvlDialog.open();
                }
            }
        };
        randLvlDialog.dialog.alignToNextChar();

        singlePlayerDialog = new ConcurrentDialog(Texture.FONT, new Vector2f(-0.95f, 0.65f), "START NEW GAME (Y/N)? ", "OK!", "ERROR!") {
            @Override
            protected boolean execute(String command) {
                boolean ok = false;
                if (!gameObject.isWorking() && (command.equalsIgnoreCase("yes") || command.equalsIgnoreCase("y"))) {
                    Editor.deselect();
                    Game.setCurrentMode(Mode.SINGLE_PLAYER);
                    ok = true;
                }
                return ok;
            }
        };
        singlePlayerDialog.dialog.alignToNextChar();

        List<Pair<String, Boolean>> optionsMenuPairs = new ArrayList<>();
        optionsMenuPairs.add(new Pair<>("FPS CAP", true));
        optionsMenuPairs.add(new Pair<>("RESOLUTION", true));
        optionsMenuPairs.add(new Pair<>("FULLSCREEN", true));
        optionsMenuPairs.add(new Pair<>("VSYNC", true));
        optionsMenuPairs.add(new Pair<>("MOUSE SENSITIVITY", true));
        optionsMenuPairs.add(new Pair<>("MUSIC VOLUME", true));
        optionsMenuPairs.add(new Pair<>("SOUND VOLUME", true));
        optionsMenu = new OptionsMenu("OPTIONS", optionsMenuPairs, FONT_IMG, new Vector2f(0.0f, 0.5f), 2.0f) {
            @Override
            protected void leave() {
                mainMenu.open();
            }

            @Override
            protected void getValues() {
                options.get(0).getKey().setContent(String.valueOf(Game.getFpsMax()));
                options.get(1).getKey().setContent(String.valueOf(GameObject.MY_WINDOW.getWidth()) + "x" + String.valueOf(GameObject.MY_WINDOW.getHeight()));
                options.get(2).getKey().setContent(GameObject.MY_WINDOW.isFullscreen() ? "ON" : "OFF");
                options.get(3).getKey().setContent(GameObject.MY_WINDOW.isVsync() ? "ON" : "OFF");
                options.get(4).getKey().setContent(String.valueOf(Game.getMouseSensitivity()));
                options.get(5).getKey().setContent(String.valueOf(musicPlayer.getGain()));
                options.get(6).getKey().setContent(String.valueOf(soundFXPlayer.getGain()));
            }

            @Override
            protected void execute() {
                Command command = Command.NOP;
                switch (selected) {
                    case 0:
                        command = Command.FPS_MAX;
                        command.getArgs().add(options.get(selected).getValue().giveCurrent());
                        command.setMode(Command.Mode.SET);
                        Command.execute(command);
                        break;
                    case 1:
                        command = Command.RESOLUTION;
                        String giveCurrent = (String) options.get(selected).getValue().giveCurrent();
                        String things[] = giveCurrent.split("x");
                        command.getArgs().add(Integer.parseInt(things[0]));
                        command.getArgs().add(Integer.parseInt(things[1]));
                        command.setMode(Command.Mode.SET);
                        Command.execute(command);
                        break;
                    case 2:
                        String fullscreen = (String) options.get(selected).getValue().giveCurrent();
                        switch (fullscreen) {
                            case "ON":
                                command = Command.FULLSCREEN;
                                break;
                            case "OFF":
                                command = Command.WINDOWED;
                                break;
                        }
                        command.setMode(Command.Mode.SET);
                        Command.execute(command);
                        break;
                    case 3:
                        String vsync = (String) options.get(selected).getValue().giveCurrent();
                        command = Command.VSYNC;
                        switch (vsync) {
                            case "ON":
                                command.getArgs().add(true);
                                break;
                            case "OFF":
                                command.getArgs().add(false);
                                break;
                        }
                        FutureTask<Object> task = new FutureTask<Object>(command);
                        command.setMode(Command.Mode.SET);
                        Renderer.TASK_QUEUE.add(task);
                        break;
                    case 4:
                        float msens = (float) options.get(selected).getValue().giveCurrent();
                        command = Command.MOUSE_SENSITIVITY;
                        command.getArgs().add(msens);
                        command.setMode(Command.Mode.SET);
                        Command.execute(command);
                        break;
                    case 5:
                        command = Command.MUSIC_VOLUME;
                        command.getArgs().add(options.get(selected).getValue().giveCurrent());
                        command.setMode(Command.Mode.SET);
                        Command.execute(command);
                        break;
                    case 6:
                        command = Command.SOUND_VOLUME;
                        command.getArgs().add(options.get(selected).getValue().giveCurrent());
                        command.setMode(Command.Mode.SET);
                        Command.execute(command);
                        break;
                }
            }
        };
        Object[] fpsCaps = {35, 60, 75, 100, 200, 300};
        Object[] resolutions = GameObject.MY_WINDOW.giveAllResolutions();
        Object[] swtch = {"OFF", "ON"};
        Object[] mouseSens = {1.0f, 1.5f, 2.0f, 2.5f, 3.0f, 3.5f, 4.0f, 5.0f, 5.5f, 6.0f, 6.5f, 7.0f, 7.5f, 8.0f, 8.5f, 9.0f, 9.5f, 10.0f};
        Object[] volume = new Float[21];
        int k = 0;
        for (float i = 0.0f; i < 1.05f; i += 0.05f) {
            volume[k++] = Math.round(i * 100.0f) / 100.f; // rounding to two decimal places
        }

        optionsMenu.options.get(0).getValue().fetchFromArray(fpsCaps, 3);
        optionsMenu.options.get(1).getValue().fetchFromArray(resolutions, 0);
        optionsMenu.options.get(2).getValue().fetchFromArray(swtch, 0);
        optionsMenu.options.get(3).getValue().fetchFromArray(swtch, 0);
        optionsMenu.options.get(4).getValue().fetchFromArray(mouseSens, 1);
        optionsMenu.options.get(5).getValue().fetchFromArray(volume, 10);
        optionsMenu.options.get(6).getValue().fetchFromArray(volume, 10);
        optionsMenu.setAlignmentAmount(Text.ALIGNMENT_RIGHT);

        List<Pair<String, Boolean>> editorMenuPairs = new ArrayList<>();
        editorMenuPairs.add(new Pair<>("START NEW LEVEL", true));
        editorMenuPairs.add(new Pair<>("GENERATE RANDOM LEVEL", true));
        editorMenuPairs.add(new Pair<>("SAVE LEVEL TO FILE", true));
        editorMenuPairs.add(new Pair<>("LOAD LEVEL FROM FILE", true));

        editorMenu = new Menu("EDITOR", editorMenuPairs, FONT_IMG, new Vector2f(0.0f, 0.5f), 2.0f) {
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
                        gameObject.startNewLevel();
                        Game.setCurrentMode(Mode.EDITOR);
                        break;
                    case "GENERATE RANDOM LEVEL":
                        progText.setEnabled(true);
                        //randLvlDialog.open();
                        randLvlMenu.open();
                        break;
                    case "SAVE LEVEL TO FILE":
                        progText.setEnabled(true);
                        saveDialog.open();
                        break;
                    case "LOAD LEVEL FROM FILE":
                        progText.setEnabled(true);
                        loadLvlMenu.open();
                        break;
                }
            }
        };
        editorMenu.setAlignmentAmount(Text.ALIGNMENT_LEFT);

        List<Pair<String, Boolean>> creditsMenuPairs = new ArrayList<>();
        creditsMenuPairs.add(new Pair<>("Programmer", true));
        creditsMenuPairs.add(new Pair<>("Alexander \"Ermac\" Stojanovich", true));
        creditsMenuPairs.add(new Pair<>("Testers", true));
        creditsMenuPairs.add(new Pair<>("Jesse \"13\" Collins", true));
        creditsMenuPairs.add(new Pair<>("Edmund \"HellBlade64\" Alby", true));
        creditsMenuPairs.add(new Pair<>("Art", true));
        creditsMenuPairs.add(new Pair<>("Alexander \"Ermac\" Stojanovich", true));
        creditsMenuPairs.add(new Pair<>("Music/FX", true));
        creditsMenuPairs.add(new Pair<>("Jordan \"Erokia\" Powell", true));

        creditsMenu = new Menu("CREDITS", creditsMenuPairs, FONT_IMG, new Vector2f(0.0f, 0.5f), 2.0f) {
            @Override
            protected void leave() {
                mainMenu.open();
            }

            @Override
            protected void execute() {

            }

        };

        int index = 0;
        for (Text item : creditsMenu.items) {
            if (index == 3 || index < 3 && (index & 1) != 0 || index > 3 && (index & 1) == 0) {
                item.setColor(Vector3fColors.WHITE);
                item.setBuffered(false);
                item.scale = 1.0f;
            }
            index++;
        }
        creditsMenu.iterator.setEnabled(false);
        creditsMenu.setAlignmentAmount(Text.ALIGNMENT_CENTER);
    }

    public void setCollText(boolean mode) {
        if (mode) {
            collText.setContent("Collision!");
            collText.setColor(Vector3fColors.RED);
        } else {
            collText.setContent("No Collision");
            collText.setColor(Vector3fColors.GREEN);
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

    public void render(ShaderProgram ifcShaderProgram) {
        saveDialog.render(ifcShaderProgram);
        loadDialog.render(ifcShaderProgram);
        randLvlDialog.render(ifcShaderProgram);
        singlePlayerDialog.render(ifcShaderProgram);
        if (!updText.isBuffered()) {
            updText.bufferAll();
        }
        updText.render(ifcShaderProgram);
        if (!fpsText.isBuffered()) {
            fpsText.bufferAll();
        }
        fpsText.render(ifcShaderProgram);

        if (!posText.isBuffered()) {
            posText.bufferAll();
        }
        posText.render(ifcShaderProgram);
        if (!chunkText.isBuffered()) {
            chunkText.bufferAll();
        }
        chunkText.render(ifcShaderProgram);

        if (!collText.isBuffered()) {
            collText.bufferAll();
        }
        collText.render(ifcShaderProgram);
        if (!helpText.isBuffered()) {
            helpText.bufferAll();
        }
        helpText.render(ifcShaderProgram);
        if (!gameModeText.isBuffered()) {
            gameModeText.bufferAll();
        }
        gameModeText.render(ifcShaderProgram);
        if (!progText.isBuffered()) {
            progText.bufferAll();
        }
        progText.render(ifcShaderProgram);
        if (!screenText.isBuffered()) {
            screenText.bufferAll();
        }
        screenText.render(ifcShaderProgram);
        mainMenu.render(ifcShaderProgram);
        optionsMenu.render(ifcShaderProgram);
        editorMenu.render(ifcShaderProgram);
        creditsMenu.render(ifcShaderProgram);
        randLvlMenu.render(ifcShaderProgram);
        loadLvlMenu.render(ifcShaderProgram);

        if (!mainMenu.isEnabled() && !loadLvlMenu.isEnabled() && !optionsMenu.isEnabled() && !editorMenu.isEnabled()
                && !creditsMenu.isEnabled() && !randLvlMenu.isEnabled() && !showHelp) {
            if (!crosshair.isBuffered()) {
                crosshair.bufferAll();
            }
            crosshair.render(ifcShaderProgram);
        }
        console.render(ifcShaderProgram);
    }

    // update menu components
    public void update() {
        mainMenu.update();
        optionsMenu.update();
        editorMenu.update();
    }

    public GameObject getGameObject() {
        return gameObject;
    }

    public Quad getCrosshair() {
        return crosshair;
    }

    public Text getUpdText() {
        return updText;
    }

    public Text getFpsText() {
        return fpsText;
    }

    public Text getPosText() {
        return posText;
    }

    public Text getChunkText() {
        return chunkText;
    }

    public Menu getCreditsMenu() {
        return creditsMenu;
    }

    public Menu getRandLvlMenu() {
        return randLvlMenu;
    }

    public int getNumBlocks() {
        return numBlocks;
    }

    public Text getCollText() {
        return collText;
    }

    public Text getHelpText() {
        return helpText;
    }

    public Text getScreenText() {
        return screenText;
    }

    public boolean isShowHelp() {
        return showHelp;
    }

    public ConcurrentDialog getSaveDialog() {
        return saveDialog;
    }

    public ConcurrentDialog getLoadDialog() {
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

    public Text getGameModeText() {
        return gameModeText;
    }

    public ConcurrentDialog getSinglePlayerDialog() {
        return singlePlayerDialog;
    }

    public Console getConsole() {
        return console;
    }

}
