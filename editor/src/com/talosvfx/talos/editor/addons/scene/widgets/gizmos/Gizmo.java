package com.talosvfx.talos.editor.addons.scene.widgets.gizmos;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.talosvfx.talos.editor.addons.scene.logic.GameObject;
import com.talosvfx.talos.editor.addons.scene.logic.components.IComponent;
import com.talosvfx.talos.editor.addons.scene.logic.components.TransformComponent;

public abstract class Gizmo<T extends IComponent> extends Actor {

    protected T component;
    protected GameObject gameObject;

    Vector2 tmp = new Vector2();

    protected float worldPerPixel;

    protected Rectangle hitBox = new Rectangle();

    public void setComponent (T component) {
        this.component = component;
    }

    public void setGameObject(GameObject gameObject) {
        this.gameObject = gameObject;
    }

    public void act(float delta) {
        super.act(delta);
        tmp.set(0, 0);
        getTransformPosition(tmp);
        setPosition(tmp.x, tmp.y);
    }

    @Override
    public void draw (Batch batch, float parentAlpha) {
    }

    public boolean hit (float x, float y) {
        getHitBox(hitBox);

        if (hitBox.contains(x, y)) {
            return true;
        }

        return false;
    }

    public Vector2 getTransformPosition(Vector2 pos) {
        if(gameObject.hasComponent(TransformComponent.class)) {
            TransformComponent transformComponent = gameObject.getComponent(TransformComponent.class);
            pos.set(transformComponent.position);

            return pos;
        } else {
            pos.set(0, 0);
        }

        return pos;
    }

    public void setWoldWidth (float worldWidth) {
        int screenPixels = Gdx.graphics.getWidth();
        worldPerPixel = worldWidth / screenPixels;
    }

    abstract void getHitBox(Rectangle rectangle);

    public GameObject getGameObject() {
        return gameObject;
    }

    public void touchDown (float x, float y, int button) {

    }

    public void touchDragged (float x, float y) {

    }

    public void touchUp (float x, float y) {

    }
}