package com.talosvfx.talos.editor.addons.scene.logic;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import com.talosvfx.talos.editor.widgets.propertyWidgets.IPropertyProvider;
import com.talosvfx.talos.editor.widgets.propertyWidgets.PropertyWidget;

import java.util.function.Supplier;

public class MultiPropertyHolder implements IPropertyHolder {

    Array<IPropertyHolder> holderArray = new Array<>();
    private ObjectMap<Class<? extends IPropertyProvider>, MultiPropertyProvider>  mainMap;

    public MultiPropertyHolder(Array<? extends IPropertyHolder> holderArray) {
        this.holderArray.addAll(holderArray);

        generateLists();
    }

    public Array<IPropertyHolder> getHolders() {
        return holderArray;
    }

    private void generateLists() {
        mainMap = new ObjectMap<>();

        Array<Class<? extends IPropertyProvider>> allowList = new Array<>();
        Iterable<IPropertyProvider> firstProviders = holderArray.first().getPropertyProviders();
        for(IPropertyProvider provider: firstProviders) {
            allowList.add(provider.getClass());
        }

        for(IPropertyHolder holder: holderArray) {
            Iterable<IPropertyProvider> propertyProviders = holder.getPropertyProviders();
            for (IPropertyProvider provider : propertyProviders) {
                if (allowList.contains(provider.getClass(), true)) {
                    if (mainMap.get(provider.getClass()) == null) {
                        mainMap.put(provider.getClass(), new MultiPropertyProvider());
                    }
                    mainMap.get(provider.getClass()).addProvider(provider);
                }
            }
        }

        for(MultiPropertyProvider provider : mainMap.values()) {
            provider.initWidgets();
        }
    }

    @Override
    public Iterable<IPropertyProvider> getPropertyProviders() {
        Array<IPropertyProvider> list = new Array<>();
        for(IPropertyProvider provider : mainMap.values()) {
            list.add(provider);
        }
        return list;
    }

    public static class MultiPropertyProvider implements IPropertyProvider {

        private Array<IPropertyProvider> providers = new Array<>();
        private ObjectMap<Integer, Array<PropertyWidget>> map = new ObjectMap<>();

        private Array<PropertyWidget> widgets = new Array<>();

        public void initWidgets() {

            for(IPropertyProvider provider: providers) {
                Array<PropertyWidget> properties = provider.getListOfProperties();
                for(int i = 0; i < properties.size;  i++) {
                    PropertyWidget childWidget = properties.get(i);
                    if(map.get(i) == null) {
                        map.put(i, new Array<>());
                    }
                    map.get(i).add(childWidget);
                }
            }

            for(int i = 0; i < map.size; i++) {
                Array<PropertyWidget> children = map.get(i);
                PropertyWidget wrapper = children.first().clone();

                wrapper.set(new Supplier() {
                    @Override
                    public Object get() {
                        Object first = children.first().getValue();
                        boolean ambiguous = false;
                        for(PropertyWidget child: children) {
                            Object childValue = child.getValue();
                            if(first != null && childValue != null) {
                                if(!childValue.equals(first)) {
                                    ambiguous = true;
                                    break;
                                }
                            } else {
                                if(first != childValue) {
                                    ambiguous = true;
                                    break;
                                }
                            }
                        }

                        if(ambiguous) {
                            return null;
                        } else {
                            return children.first().getValue();
                        }
                    }
                }, new PropertyWidget.ValueChanged() {
                    @Override
                    public void report(Object value) {
                        for(PropertyWidget child: children) {
                            child.report(value);
                        }
                    }
                });

                widgets.add(wrapper);
            }
        }

        public void addProvider(IPropertyProvider provider) {
            providers.add(provider);
        }

        @Override
        public Array<PropertyWidget> getListOfProperties() {

            return widgets;
        }

        @Override
        public String getPropertyBoxTitle() {
            return providers.first().getPropertyBoxTitle();
        }

        @Override
        public int getPriority() {
            return providers.first().getPriority();
        }

        @Override
        public Class<? extends IPropertyProvider> getType() {
            return providers.first().getType();
        }
    }
}
