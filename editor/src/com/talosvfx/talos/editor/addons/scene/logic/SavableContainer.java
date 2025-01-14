package com.talosvfx.talos.editor.addons.scene.logic;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.*;
import com.talosvfx.talos.editor.addons.scene.logic.components.AComponent;
import com.talosvfx.talos.editor.addons.scene.utils.importers.AssetImporter;

import java.io.StringWriter;

public abstract class SavableContainer implements GameObjectContainer, Json.Serializable, IPropertyHolder {

    public String path;
    public GameObject root;

    public SavableContainer() {
        root = new GameObject();
    }

    public SavableContainer(String path) {
        root = new GameObject();
        this.path = path;
    }

    @Override
    public void write (Json json) {
        root.write(json);
    }

    @Override
    public void read (Json json, JsonValue jsonData) {
        root.read(json, jsonData);
    }

    @Override
    public String getName () {
        return root.getName();
    }

    @Override
    public void setName (String name) {
        root.setName(name);
    }

    @Override
    public Array<GameObject> getGameObjects () {
        return root.getGameObjects();
    }

    @Override
    public Iterable<AComponent> getComponents () {
        return null;
    }

    @Override
    public void addGameObject (GameObject gameObject) {
        root.addGameObject(gameObject);
    }

    @Override
    public Array<GameObject> deleteGameObject (GameObject gameObject) {
        return root.deleteGameObject(gameObject);
    }

    @Override
    public void removeObject (GameObject gameObject) {
        root.removeObject(gameObject);
    }

    @Override
    public void addComponent (AComponent component) {

    }

    @Override
    public boolean hasGOWithName (String name) {
        return root.hasGOWithName(name);
    }

    @Override
    public void clearChildren (Array<GameObject> tmp) {
        root.clearChildren(tmp);
    }

    @Override
    public GameObject getParent () {
        return null;
    }

    @Override
    public GameObject getSelfObject () {
        return root;
    }

    @Override
    public void setParent (GameObject gameObject) {
        // do nothing
    }

    protected void writeData (Json json) {
        json.writeArrayStart("gameObjects");
        Array<GameObject> gameObjects = getGameObjects();
        if(gameObjects != null) {
            for (GameObject gameObject : gameObjects) {
                json.writeValue(gameObject);
            }
        }
        json.writeArrayEnd();
    }

    public String getAsString () {
        try {
            FileHandle file = Gdx.files.absolute(path);

            StringWriter stringWriter = new StringWriter();
            Json json = new Json();
            json.setOutputType(JsonWriter.OutputType.json);
            json.setWriter(stringWriter);
            json.getWriter().object();

            writeData(json);

            String finalString = stringWriter.toString() + "}";

            return finalString;
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }

        return "";
    }

    public void load(String data) {
        JsonValue jsonValue = new JsonReader().parse(data);
        Json json = new Json();
        JsonValue gameObjectsJson = jsonValue.get("gameObjects");
        root = new GameObject();
        for(JsonValue gameObjectJson: gameObjectsJson) {
            GameObject gameObject = json.readValue(GameObject.class, gameObjectJson);
            root.addGameObject(gameObject);
        }
    }

    public void loadFromPath() {
        FileHandle dataFile = AssetImporter.get(path);
        load(dataFile.readString());
    }

    public void save() {
        FileHandle file = AssetImporter.get(path);
        String data = getAsString();
        file.writeString(data, false);
    }
}
