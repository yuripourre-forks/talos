package com.talosvfx.talos.editor.addons.scene.logic.components;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;
import com.talosvfx.talos.TalosMain;
import com.talosvfx.talos.editor.addons.scene.SceneEditorAddon;
import com.talosvfx.talos.editor.addons.scene.events.ComponentUpdated;
import com.talosvfx.talos.editor.addons.scene.utils.importers.AssetImporter;
import com.talosvfx.talos.editor.addons.scene.utils.metadata.TlsMetadata;
import com.talosvfx.talos.editor.addons.scene.widgets.property.AssetSelectWidget;
import com.talosvfx.talos.editor.notifications.Notifications;
import com.talosvfx.talos.editor.project.ProjectController;
import com.talosvfx.talos.editor.widgets.propertyWidgets.ButtonPropertyWidget;
import com.talosvfx.talos.editor.widgets.propertyWidgets.IPropertyProvider;
import com.talosvfx.talos.editor.widgets.propertyWidgets.PropertyWidget;
import com.talosvfx.talos.runtime.ParticleEffectDescriptor;

import java.io.File;
import java.util.function.Supplier;

public class ParticleComponent extends RendererComponent {

    public ParticleEffectDescriptor descriptor;
    public String path = "";

    public String linkedTo = "";

    @Override
    public Class<? extends IPropertyProvider> getType() {
        return getClass();
    }

    @Override
    public Array<PropertyWidget> getListOfProperties () {
        Array<PropertyWidget> properties = new Array<>();

        AssetSelectWidget descriptorWidget = new AssetSelectWidget("Effect", "p", new Supplier<String>() {
            @Override
            public String get() {
                FileHandle fileHandle = Gdx.files.absolute(path);
                return fileHandle.path();
            }
        }, new PropertyWidget.ValueChanged<String>() {
            @Override
            public void report(String value) {
                path = value;
                reloadDescriptor();
            }
        });

        ButtonPropertyWidget<String> linkedToWidget = new ButtonPropertyWidget<String>("Effect Project", "Edit", new ButtonPropertyWidget.ButtonListener<String>() {
            @Override
            public void clicked(ButtonPropertyWidget<String> widget) {
                String link = widget.getValue();
                if (link.isEmpty()) {
                    FileHandle sample = Gdx.files.internal("addons/scene/missing/sample.tls");
                    FileHandle thisEffect = AssetImporter.get(path);
                    FileHandle destination;
                    if (!path.isEmpty() && thisEffect.exists()) {
                        // idk which scenario is this
                    } else {
                        String projectPath = SceneEditorAddon.get().workspace.getProjectPath();
                        destination = AssetImporter.attemptToImport(sample);
                        FileHandle pHandle = AssetImporter.makeSimilar(destination, "p");

                        FileHandle texture = Gdx.files.internal("addons/scene/missing/white.png");
                        FileHandle textureDst = Gdx.files.absolute(projectPath + File.separator + "assets/white.png");
                        texture.copyTo(textureDst);

                        path = pHandle.path();
                        linkedTo = destination.path();

                        Notifications.fireEvent(Notifications.obtainEvent(ComponentUpdated.class).set(ParticleComponent.this, false));

                        TalosMain.Instance().ProjectController().setProject(ProjectController.TLS);
                        TalosMain.Instance().ProjectController().loadProject(destination);
                    }
                } else {
                    FileHandle fileHandle = AssetImporter.get(link);
                    TalosMain.Instance().ProjectController().setProject(ProjectController.TLS);
                    TalosMain.Instance().ProjectController().loadProject(fileHandle);
                }
            }
        }, new Supplier<String>() {
            @Override
            public String get() {
                return linkedTo;
            }
        }, new PropertyWidget.ValueChanged<String>() {
            @Override
            public void report(String value) {
                linkedTo = value;
            }
        });

        properties.add(descriptorWidget);
        properties.add(linkedToWidget);
        properties.addAll(super.getListOfProperties());

        return properties;
    }

    @Override
    public String getPropertyBoxTitle () {
        return "Particle Effect";
    }

    @Override
    public int getPriority () {
        return 2;
    }

    @Override
    public void read (Json json, JsonValue jsonData) {
        path = jsonData.getString("path", "");
        linkedTo = jsonData.getString("linkedTo", "");

        reloadDescriptor();

        super.read(json, jsonData);
    }

    @Override
    public void write (Json json) {
        json.writeValue("path", path);
        json.writeValue("linkedTo", linkedTo);
        super.write(json);
    }

    public void reloadDescriptor () {
        FileHandle handle = AssetImporter.get(path);
        if(handle.exists() && handle.extension().equals("p")) {
            try {
                descriptor = new ParticleEffectDescriptor();
                descriptor.setAssetProvider(SceneEditorAddon.get().assetProvider);
                descriptor.load(handle);
            } catch (Exception e) {

            }
        } else {
            // load default
            descriptor = new ParticleEffectDescriptor();
            descriptor.setAssetProvider(SceneEditorAddon.get().assetProvider);
            descriptor.load(Gdx.files.internal("addons/scene/missing/sample.p"));
        }
    }

    @Override
    public boolean notifyAssetPathChanged (String oldPath, String newPath) {
        if(path.equals(oldPath)) {
            path = newPath;
            reloadDescriptor();

            return true;
        }

        return false;
    }
}
