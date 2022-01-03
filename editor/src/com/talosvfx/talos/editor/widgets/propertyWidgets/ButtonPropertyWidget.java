package com.talosvfx.talos.editor.widgets.propertyWidgets;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.talosvfx.talos.TalosMain;
import com.talosvfx.talos.editor.widgets.ui.common.SquareButton;

public class ButtonPropertyWidget<T> extends PropertyWidget<T> {

    private T payload;
    private Label buttonLabel;
    private SquareButton button;
    private ButtonListener btnListener;

    public interface ButtonListener<T> {
        void clicked(ButtonPropertyWidget<T> widget);
    }

    public ButtonPropertyWidget(String name, String text, ButtonListener btnListener) {
        super(name);
        setButtonText(text);
        this.btnListener = btnListener;

    }

    @Override
    public T getValue () {
        return payload;
    }

    @Override
    public void updateWidget (T value) {
        payload = value;
    }

    @Override
    public Actor getSubWidget () {
        Skin skin = TalosMain.Instance().getSkin();
        Table table = new Table();

        buttonLabel = new Label("Edit", skin);
        button = new SquareButton(skin, buttonLabel);

        table.add(button).expand().right();

        button.addListener(new ClickListener() {
            @Override
            public void clicked (InputEvent event, float x, float y) {
                if(btnListener != null) {
                    btnListener.clicked(ButtonPropertyWidget.this);
                }
            }
        });

        return table;
    }

    public void externalDataChange(T payload) {
        callValueChanged(payload);
    }

    public void setButtonText(String text) {
        buttonLabel.setText(text);
    }
}