/*
 * Copyright (C) 2022 coas9
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

import rs.alexanderstojanovich.evgl.shaders.ShaderProgram;
import rs.alexanderstojanovich.evgl.texture.Texture;

/**
 *
 * @author Alexander Stojanovich <coas91@rocketmail.com>
 */
public final class SingleValue implements MenuValue {

    protected final Text valueText;
    private Object value = new Object();
    protected Type type;

    public SingleValue(Object value, Type type) {
        this.type = type;
        this.valueText = new Text(Texture.FONT, String.valueOf(value));
        this.setCurrentValue(value);
    }

    @Override
    public Object getCurrentValue() {
        return value;
    }

    @Override
    public void setCurrentValue(Object object) {
        if (object != null) {
            this.value = object;
            valueText.setContent(String.valueOf(value));
            valueText.unbuffer();
        }
    }

    @Override
    public Text getValueText() {
        return valueText;
    }

    @Override
    public void render(ShaderProgram shaderProgram) {
        if (!valueText.isBuffered()) {
            valueText.bufferAll();
        }
        valueText.render(shaderProgram);
    }

    @Override
    public Type getType() {
        return type;
    }

}
