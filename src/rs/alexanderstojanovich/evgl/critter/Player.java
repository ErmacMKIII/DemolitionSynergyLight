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
package rs.alexanderstojanovich.evgl.critter;

import java.util.List;
import org.joml.Vector3f;
import rs.alexanderstojanovich.evgl.core.Camera;
import rs.alexanderstojanovich.evgl.level.LevelContainer;
import rs.alexanderstojanovich.evgl.main.Game;
import rs.alexanderstojanovich.evgl.models.Model;
import rs.alexanderstojanovich.evgl.shaders.ShaderProgram;

/**
 *
 * @author Alexander Stojanovich <coas91@rocketmail.com>
 */
public class Player extends ModelCritter {

    private double hitPoints = 100.0;
    private Model currWeapon;
    private final Camera camera;
    
    public static final Model PISTOL = Model.readFromObjFile(Game.PLAYER_ENTRY, "pistol.obj", "pistol");
    public static final Model SUB_MACHINE_GUN = Model.readFromObjFile(Game.PLAYER_ENTRY, "sub_machine_gun.obj", "smg");
    public static final Model SHOTGUN = Model.readFromObjFile(Game.PLAYER_ENTRY, "shotgun.obj", "shotgun");
    public static final Model ASSAULT_RIFLE = Model.readFromObjFile(Game.PLAYER_ENTRY, "assault_rifle.obj", "assrifle");
    public static final Model MACHINE_GUN = Model.readFromObjFile(Game.PLAYER_ENTRY, "machine_gun.obj", "machgun");
    public static final Model SNIPER_RIFLE = Model.readFromObjFile(Game.PLAYER_ENTRY, "sniper_rifle.obj", "sniper");
    public static final Model[] WEAPONS = {PISTOL, SUB_MACHINE_GUN, SHOTGUN, ASSAULT_RIFLE, MACHINE_GUN, SNIPER_RIFLE};

    static {
        for (Model weapon : WEAPONS) {
            weapon.setPos(new Vector3f(1.0f, -1.0f, 3.0f));
            weapon.setPrimaryColor(LevelContainer.SKYBOX_COLOR);
            weapon.setScale(6.0f);
            weapon.setrY((float) (-Math.PI / 2.0f));
        }
    }

    public Player(Camera camera, Model currWeapon, Model model) {
        super(model);
        this.camera = camera;
        this.currWeapon = currWeapon;
        linkDirectionVectors();
    }   

    private void linkDirectionVectors() {
        this.yaw = camera.getYaw();
        this.pitch = camera.getPitch();
        
        this.front = camera.getFront();
        this.right = camera.getRight();
        this.up = camera.getUp();
    }
    
    public void switchWeapon(int num) {
        currWeapon = WEAPONS[num - 1];
    }

    @Override
    public void render(List<Vector3f> lightSrc, ShaderProgram shaderProgram) {
        // super.render(lightSrc, shaderProgram);
        if (currWeapon != null) {
            if (!currWeapon.isBuffered()) {
                currWeapon.bufferAll();
            }
            currWeapon.render(lightSrc, ShaderProgram.getPlayerShader());
        }
        camera.render(shaderProgram);
    }

    public double getHitPoints() {
        return hitPoints;
    }

    public Model getCurrWeapon() {
        return currWeapon;
    }

    public void setCurrWeapon(Model currWeapon) {
        this.currWeapon = currWeapon;
    }

    public Camera getCamera() {
        return camera;
    }

    @Override
    public void turnRight(float angle) {
        super.turnRight(angle);
        camera.turnRight(angle);
        linkDirectionVectors();
    }

    @Override
    public void turnLeft(float angle) {
        super.turnLeft(angle);
        camera.turnLeft(angle);
        linkDirectionVectors();
    }

    @Override
    public void moveRight(float amount) {
        super.moveRight(amount); 
        camera.moveRight(amount);
        linkDirectionVectors();
    }

    @Override
    public void moveLeft(float amount) {
        super.moveLeft(amount);
        camera.moveRight(amount);
        linkDirectionVectors();
    }

    @Override
    public void moveBackward(float amount) {
        super.moveBackward(amount); 
        camera.moveBackward(amount);
        linkDirectionVectors();
    }

    @Override
    public void moveForward(float amount) {
        super.moveForward(amount);
        camera.moveForward(amount);
        linkDirectionVectors();
    }

    @Override
    public void setPitch(float pitch) {
        super.setPitch(pitch);
        linkDirectionVectors();
    }

    @Override
    public void setYaw(float yaw) {
        super.setYaw(yaw); 
        linkDirectionVectors();
    }

    @Override
    public void setRight(Vector3f right) {
        super.setRight(right);
        linkDirectionVectors();
    }

    @Override
    public void setUp(Vector3f up) {
        super.setUp(up); 
        linkDirectionVectors();
    }

    @Override
    public void setFront(Vector3f front) {
        super.setFront(front);
        linkDirectionVectors();
    }

    
    
}
