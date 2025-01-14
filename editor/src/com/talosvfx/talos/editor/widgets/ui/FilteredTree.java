/*******************************************************************************
 * Copyright 2019 See AUTHORS file.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.talosvfx.talos.editor.widgets.ui;


import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener.ChangeEvent;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.Layout;
import com.badlogic.gdx.scenes.scene2d.utils.Selection;
import com.badlogic.gdx.scenes.scene2d.utils.UIUtils;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.talosvfx.talos.editor.addons.scene.logic.GameObject;
import info.debatty.java.stringsimilarity.JaroWinkler;

import java.util.Comparator;

/**
 * A tree widget where each node has an icon, actor, and child nodes.
 * <p>
 * The preferred size of the tree is determined by the preferred size of the actors for the expanded nodes.
 * <p>
 * {@link ChangeEvent} is fired when the selected node changes.
 *
 * @author Nathan Sweet
 * @author Tom Wojciechowski
 */
public class FilteredTree<T> extends WidgetGroup {

    TreeStyle style;
    final Array<Node<T>> rootNodes = new Array();
    final Selection<Node<T>> selection;
    float ySpacing = 4, iconSpacingLeft = 2, iconSpacingRight = 2, padding = 0, indentSpacing;
    private float leftColumnWidth, prefWidth, prefHeight;
    private boolean sizeInvalid = true;
    private Node<T> foundNode;
    Node<T> overNode, rangeStart;
    private ClickListener clickListener;

    private DragAndDrop rootDrag = new DragAndDrop();

    private Skin skin;

    public boolean draggable;
    private int autoSelectionIndex = 0;

    public FilteredTree (Skin skin) {
        this(skin.get(TreeStyle.class));
        this.skin = skin;
    }

    private ItemListener itemListener;

    public static abstract class ItemListener {
        public void chosen(Node node) {

        }
        public void selected(Node node) {

        }
        public void rightClick (Node node) {

        }

        public void delete (Array<FilteredTree.Node> nodes) {

        }

        public void onNodeMove (Node parentToMoveTo, Node childThatHasMoved, int indexInParent, int indexOfPayloadInPayloadBefore) {

        }
    }

    public void setItemListener(ItemListener itemListener) {
        this.itemListener = itemListener;
    }

    public FilteredTree (Skin skin, String styleName) {
        this(skin.get(styleName, TreeStyle.class));
        this.skin = skin;
    }

    public FilteredTree (TreeStyle style) {
        selection = new Selection<Node<T>>() {
            protected void changed () {
                switch (size()) {
                    case 0:
                        rangeStart = null;
                        break;
                    case 1:
                        rangeStart = first();
                        break;
                }
            }
        };
        selection.setActor(this);
        selection.setMultiple(false);
        selection.setToggle(true);
        setStyle(style);
        initialize();
    }

    public void reportUserEnter() {
        Array<Node<T>> result = new Array<>();
        collectFilteredNodes(rootNodes, result);

        if(result.size == 0) return;

        if(itemListener != null) {
            itemListener.chosen(result.get(autoSelectionIndex));
        }
    }

    private void initialize () {
        addListener(clickListener = new ClickListener() {
            public void clicked (InputEvent event, float x, float y) {
                Node node = getNodeAt(y);
                if (node == null)
                    return;
                if (node != getNodeAt(getTouchDownY()))
                    return;
                if (selection.getMultiple() && selection.hasItems() && UIUtils.shift()) {
                    // Select range (shift).
                    if (rangeStart == null)
                        rangeStart = node;
                    Node rangeStart = FilteredTree.this.rangeStart;
                    if (!UIUtils.ctrl())
                        selection.clear();
                    float start = rangeStart.actor.getY(), end = node.actor.getY();
                    if (start > end)
                        selectNodes(rootNodes, end, start);
                    else {
                        selectNodes(rootNodes, start, end);
                        selection.items().orderedItems().reverse();
                    }

                    selection.fireChangeEvent();
                    FilteredTree.this.rangeStart = rangeStart;
                    return;
                }
                if (node.children.size > 0 && (!selection.getMultiple() || !UIUtils.ctrl())) {
                    // Toggle expanded.
                    float rowX = node.actor.getX();
                    if (node.icon != null)
                        rowX -= iconSpacingRight + node.icon.getMinWidth();
                    if (x < rowX) {
                        node.setExpanded(!node.expanded);
                        return;
                    }
                }
                if (!node.isSelectable())
                    return;
                selection.choose(node);
                if(itemListener != null) {
                    itemListener.chosen(node);
                }
                if (!selection.isEmpty())
                    rangeStart = node;
            }

            @Override
            public boolean touchDown (InputEvent event, float x, float y, int pointer, int button) {
                Node node = getNodeAt(y);
                if(itemListener != null) {
                    if(button == 1) {
                        // this is right click
                        itemListener.rightClick(node);
                        event.cancel();

                        return true;
                    }
                }

                return super.touchDown(event, x, y, pointer, button);
            }

            @Override
            public boolean keyDown (InputEvent event, int keycode) {
                if(itemListener != null) {
                    if (keycode == Input.Keys.DEL) {
                        if(!selection.isEmpty()) {
                            Array<FilteredTree.Node> nodes = new Array<>();
                            for(Object nodeObject: selection) {
                                FilteredTree.Node node = (FilteredTree.Node) nodeObject;
                                nodes.add(node);
                            }
                            itemListener.delete(nodes);
                        }
                    }
                }
                return super.keyDown(event, keycode);
            }

            public boolean mouseMoved (InputEvent event, float x, float y) {
                setOverNode(getNodeAt(y));
                return false;
            }

            public void exit (InputEvent event, float x, float y, int pointer, Actor toActor) {
                super.exit(event, x, y, pointer, toActor);
                if (toActor == null || !toActor.isDescendantOf(FilteredTree.this))
                    setOverNode(null);
            }
        });
    }

    public void setStyle (TreeStyle style) {
        this.style = style;
        indentSpacing = Math.max(style.plus.getMinWidth(), style.minus.getMinWidth()) + iconSpacingLeft;
    }

    public void add (Node<T> node) {
        insert(rootNodes.size, node);

        if (draggable) {
            addSource(node);
        }
    }

    private Node<T> previousSelected;

    private void addSource (final Node<T> node) {
        if(node.draggable) {

            node.actor.setUserObject(node);

            DragAndDrop.Source dragSource = new DragAndDrop.Source(node.actor) {
                @Override
                public DragAndDrop.Payload dragStart (InputEvent inputEvent, float v, float v1, int i) {
                    DragAndDrop.Payload payload = new DragAndDrop.Payload();

                    Actor dragging;

                    if (node.actor instanceof ActorCloneable) {
                        dragging = ((ActorCloneable) node.actor).copyActor(node.actor);
                    } else {
                        dragging = new Label("Dragging label", skin);
                    }

                    payload.setDragActor(dragging);
                    payload.setObject(node);

                    return payload;
                }
            };
            rootDrag.addSource(dragSource);
            DragAndDrop.Target targetSource = new DragAndDrop.Target(node.actor) {
                @Override
                public boolean drag (DragAndDrop.Source source, DragAndDrop.Payload payload, float x, float y, int pointer) {

                    Actor actor = getActor();

                    if (actor == source.getActor()) {
                        return false;
                    }

                    Node<T> payloadNode = (Node<T>) payload.getObject();
                    if (payloadNode.draggableInLayerOnly) {
                        if (payloadNode.getParent() != ((Node<T>) actor.getUserObject()).getParent()) {
                            return false;
                        }


                        float yAlpha = y / getActor().getHeight();

                        if (yAlpha > 0.3f && yAlpha < 0.7f) {
                            if(payloadNode.draggableInLayerOnly) {
                               return false;
                            }
                            //We are adding as a child, if we are not draggable, we ignore
                            if (previousSelected != null) {
                                previousSelected.underline = false;
                                previousSelected = ((Node<T>) actor.getUserObject());
                            }
                            return false;
                        }
                    }

                    if (previousSelected == null) {
                        previousSelected = ((Node<T>) actor.getUserObject());
                    } else if (previousSelected != actor.getUserObject()) {
                        previousSelected.underline = false;
                        previousSelected = ((Node<T>) actor.getUserObject());
                    }

                    if (previousSelected != null) {
                        float yAlpha = y / getActor().getHeight();
                        previousSelected.yAlpha = yAlpha;
                        previousSelected.underline = true;
                    }

                    return true;
                }

                @Override
                public void drop (DragAndDrop.Source source, DragAndDrop.Payload payload, float x, float y, int pointer) {
                    Actor targetActor = getActor();
                    Object userObject = targetActor.getUserObject();

                    Node<T> node = ((Node) userObject);
                    Node<T> parent = node.getParent();
                    Node<T> targetNodeToDrop = (Node) payload.getObject();

                    int indexInParent = -1;

                    indexInParent = getIndexInParent(node, parent);

                    Node<T> payloadParent = targetNodeToDrop.getParent();
                    Node<T> payloadNode = targetNodeToDrop;

                    boolean sameLayer = payloadParent == node.getParent();


                    //Lets check if its valid first before continuing

                    float yAlpha = y / getActor().getHeight();

                    int indexOfPayloadInParent = getIndexInParent(payloadNode, payloadParent);

                    payloadNode.remove();

                    if (yAlpha < 0.3f) {
                        //Always put it below current

                        if (sameLayer) {
                            if (indexOfPayloadInParent > indexInParent) {
                                //We are below it, doesnt change the index of the target
                            } else {
                                //We are above it, removing this node will pop the index of the child back
                                indexInParent -= 1;
                            }
                        } else {
                            //Always going to be the index of the component
                        }

                        if (parent != null) {
                            indexInParent = MathUtils.clamp(indexInParent + 1, 0, parent.children.size);
                            parent.insert(indexInParent, payloadNode);
                            onNodeMove(parent, payloadNode, indexInParent, indexOfPayloadInParent);
                        } else {
                            indexInParent = MathUtils.clamp(indexInParent + 1, 0, rootNodes.size);
                            insert(indexInParent, payloadNode);
                            onNodeMove(null, payloadNode, indexInParent, indexOfPayloadInParent);
                        }
                    } else if (yAlpha > 0.7f) {
                        //Always put it above

                        if (sameLayer) {
                            if (indexOfPayloadInParent > indexInParent) {
                                //We are below it, doesnt change the index of the target
                            } else {
                                //We are above it, removing this node will pop the index of the child back
                                indexInParent -= 1;
                            }
                        } else {
                            //Always going to be the index of the component
                        }

                        if (parent != null) {
                            indexInParent = MathUtils.clamp(indexInParent, 0, parent.children.size);
                            parent.insert(indexInParent, payloadNode);
                            onNodeMove(parent, payloadNode, indexInParent, indexOfPayloadInParent);
                        } else {
                            indexInParent = MathUtils.clamp(indexInParent, 0, rootNodes.size);
                            insert(indexInParent, payloadNode);
                            onNodeMove(null, payloadNode, indexInParent, indexOfPayloadInParent);
                        }
                    } else {
                        //Always put it as a child
                        if(!node.draggableInLayerOnly) {
                            node.insert(0, payloadNode);
                            node.setExpanded(true);

                            onNodeMove(node, payloadNode, 0, indexOfPayloadInParent);
                        }
                    }
                }

                @Override
                public void reset (DragAndDrop.Source source, DragAndDrop.Payload payload) {
                    if (previousSelected != null) {
                        previousSelected.underline = false;
                        previousSelected = null;
                    }
                }
            };
            rootDrag.addTarget(targetSource);
        }
        for (Node child : node.children) {
            addSource(child);
        }
    }

    protected void onNodeMove (Node<T> parentToMoveTo, Node<T> childThatHasMoved, int indexInParent, int indexOfPayloadInPayloadBefore) {
        if(itemListener != null) {
            itemListener.onNodeMove(parentToMoveTo, childThatHasMoved, indexInParent, indexOfPayloadInPayloadBefore);
        }
    }


    private int getIndexInParent (Node<T> node, Node<T> parent) {
        int indexInParent = -1;

        if (parent != null) {
            for (int i = 0; i < parent.children.size; i++) {
                Node<T> tNode = parent.children.get(i);
                if (tNode == node) {
                    indexInParent = i;
                    break;
                }
            }

            if (indexInParent == -1) {
                throw new GdxRuntimeException("nada");
            }
        } else {
            //Root node
            for (int i = 0; i < rootNodes.size; i++) {
                Node<T> tNode = rootNodes.get(i);
                if (tNode == node) {
                    indexInParent = i;
                    break;
                }
            }

            if (indexInParent == -1) {
                throw new GdxRuntimeException("nada");
            }
        }
        return indexInParent;
    }

    public void insert (int index, Node node) {
        remove(node);
        node.parent = null;
        rootNodes.insert(index, node);
        node.addToTree(this);
        invalidateHierarchy();
    }

    public void remove (Node node) {
        if (node.parent != null) {
            node.parent.remove(node);
            return;
        }
        rootNodes.removeValue(node, true);
        node.removeFromTree(this);
        invalidateHierarchy();
    }

    /**
     * Removes all tree nodes.
     */
    public void clearChildren () {
        super.clearChildren();
        setOverNode(null);
        rootNodes.clear();
        selection.clear();
    }

    public Array<Node<T>> getNodes () {
        return rootNodes;
    }

    public void invalidate () {
        super.invalidate();
        sizeInvalid = true;
    }

    private void computeSize () {
        sizeInvalid = false;
        prefWidth = style.plus.getMinWidth();
        prefWidth = Math.max(prefWidth, style.minus.getMinWidth());
        prefHeight = getHeight();
        leftColumnWidth = 0;
        computeSize(rootNodes, indentSpacing);
        leftColumnWidth += iconSpacingLeft + padding;
        prefWidth += leftColumnWidth + padding;
        prefHeight = getHeight() - prefHeight;
    }

    private void computeSize (Array<Node<T>> nodes, float indent) {
        float ySpacing = this.ySpacing;
        float spacing = iconSpacingLeft + iconSpacingRight;
        for (int i = 0, n = nodes.size; i < n; i++) {
            Node node = nodes.get(i);

            if (node.filtered)
                continue;

            float rowWidth = indent + iconSpacingRight;
            Actor actor = node.actor;
            if (actor instanceof Layout) {
                Layout layout = (Layout)actor;
                rowWidth += layout.getPrefWidth();
                node.height = layout.getPrefHeight();
                layout.pack();
            } else {
                rowWidth += actor.getWidth();
                node.height = actor.getHeight();
            }
            if (node.icon != null) {
                rowWidth += spacing + node.icon.getMinWidth();
                node.height = Math.max(node.height, node.icon.getMinHeight());
            }
            prefWidth = Math.max(prefWidth, rowWidth);
            prefHeight -= node.height + ySpacing;
            if (node.expanded)
                computeSize(node.children, indent + indentSpacing);
        }
    }

    public void layout () {
        if (sizeInvalid)
            computeSize();
        layout(rootNodes, leftColumnWidth + indentSpacing + iconSpacingRight, getHeight() - ySpacing / 2);
    }

    public void filter (String filter) {
        filter(filter, false);
    }

    public void filter (String filter, boolean endsWithLogic) {
        filter(rootNodes, filter.toLowerCase(), endsWithLogic);
        expandAll();

        autoSelectionIndex = 0;
        selectFilteredNodeByIndex();
    }

    public void selectNextFilteredNode() {
        autoSelectionIndex++;
        selectFilteredNodeByIndex();
    }

    public void selectPrevFilteredNode() {
        autoSelectionIndex--;
        selectFilteredNodeByIndex();
    }

    public void selectFilteredNodeByIndex() {
        Array<Node<T>> result = new Array<>();
        collectFilteredNodes(rootNodes, result);

        if(result.size == 0) return;

        if(autoSelectionIndex < 0) autoSelectionIndex = result.size - 1;
        if(autoSelectionIndex > result.size - 1) autoSelectionIndex = 0;

        selection.clear();
        Node node = result.get(autoSelectionIndex);
        selection.add(node);
        if(node.parent != null) node.parent.setExpanded(true);

        if(itemListener != null) {
            itemListener.selected(node);
        }
    }

    public void collectFilteredNodes(Array<Node<T>> nodes, Array<Node<T>> result) {
        for (int i = 0; i < nodes.size; i++) {
            Node node = nodes.get(i);
            if(node.children.size == 0) {
                if(node.parent != null) {
                    if (!node.filtered) {
                        // select it!
                        result.add(node);
                    }
                }
            } else {
                collectFilteredNodes(node.children, result);
            }
        }
    }

    private class MatchingNode<C> {
        public Node<C> node;
        public double score;
        public boolean contained;
        public MatchingNode(Node<C> node, double score, boolean contained) {
            this.node = node;
            this.score = score;
            this.contained = contained;
        }

        @Override
        public int hashCode() {
            return node.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            return super.equals(obj);
        }

        public boolean filterPositive() {
            if(contained) return true;

            if(score > 0.7f) {
                return true;
            }

            return false;
        }
    }

    private double getSimilarityScore(String s1, String s2) {
        JaroWinkler jaroWinkler = new JaroWinkler();
        double value = jaroWinkler.similarity(s1, s2);

        return value;
    }


    public void gatherMatchingScores (Array<Node<T>> nodes, String filter, Array<MatchingNode<T>> results) {
        for (int i = 0; i < nodes.size; i++) {
            MatchingNode<T> mNode = new MatchingNode<>(nodes.get(i),
                    getSimilarityScore(nodes.get(i).getName().toLowerCase(), filter.toLowerCase()),
                    nodes.get(i).getName().toLowerCase().contains(filter.toLowerCase()));
            if(!results.contains(mNode, false)) {
                results.add(mNode);
            }
            gatherMatchingScores(nodes.get(i).children, filter, results);
        }
    }

    public void smartFilter(String filter) {
        Array<MatchingNode<T>> results = new Array<>();
        gatherMatchingScores(rootNodes, filter, results);
        smartFilter(results);

        expandAll();

        autoSelectionIndex = 0;
        selectFilteredNodeByIndex();
    }

    private void smartFilter (Array<MatchingNode<T>> nodes) {
        for (int i = 0; i < nodes.size; i++) {
            if (nodes.get(i).filterPositive()) {
                nodes.get(i).node.filtered = false;
                nodes.get(i).node.actor.setVisible(true);
                setAllParentsNotFiltered(nodes.get(i).node);
                setAllChildrenNotFiltered(nodes.get(i).node);
            } else {
                nodes.get(i).node.filtered = true;
                nodes.get(i).node.actor.setVisible(false);
            }
        }
    }

    public void filter (Array<Node<T>> nodes, String filter) {
        filter(nodes, filter, false);
    }

    public void filter (Array<Node<T>> nodes, String filter, boolean endsWithLogic) {
        for (int i = 0; i < nodes.size; i++) {
            boolean statement = nodes.get(i).name.toLowerCase().contains(filter);
            if(endsWithLogic) {
                statement = nodes.get(i).name.toLowerCase().endsWith(filter);
            }
            if (statement) {
                filter(nodes.get(i).children, filter, endsWithLogic);

                nodes.get(i).filtered = false;
                nodes.get(i).actor.setVisible(true);
                setAllParentsNotFiltered(nodes.get(i));
                setAllChildrenNotFiltered(nodes.get(i));
            } else {
                nodes.get(i).filtered = true;
                nodes.get(i).actor.setVisible(false);
                filter(nodes.get(i).children, filter, endsWithLogic);
            }

        }
    }

    private void setAllChildrenNotFiltered (Node<T> node) {
        for (int i = 0; i < node.children.size; i++) {
            node.children.get(i).filtered = false;
            node.children.get(i).actor.setVisible(true);
            setAllChildrenNotFiltered(node.children.get(i));
        }
    }

    private void setAllParentsNotFiltered (Node node) {
        Node parent;
        while ((parent = node.parent) != null) {
            parent.filtered = false;
            parent.actor.setVisible(true);
            node = parent;
        }
    }

    private float layout (Array<Node<T>> nodes, float indent, float y) {
        float ySpacing = this.ySpacing;
        for (int i = 0, n = nodes.size; i < n; i++) {
            Node<T> node = nodes.get(i);
            if (node.filtered)
                continue;
            float x = indent;
            if (node.icon != null)
                x += node.icon.getMinWidth();
            y -= node.height;
            node.actor.setPosition(x, y);
            y -= ySpacing;
            if (node.expanded)
                y = layout(node.children, indent + indentSpacing, y);
        }
        return y;
    }

    public void draw (Batch batch, float parentAlpha) {
        Color color = getColor();
        batch.setColor(color.r, color.g, color.b, color.a * parentAlpha);
        if (style.background != null)
            style.background.draw(batch, getX(), getY(), getWidth(), getHeight());
        draw(batch, rootNodes, leftColumnWidth);
        super.draw(batch, parentAlpha); // Draw actors.
    }

    /**
     * Draws selection, icons, and expand icons.
     */
    private void draw (Batch batch, Array<Node<T>> nodes, float indent) {
        Drawable plus = style.plus, minus = style.minus;
        float x = getX(), y = getY();
        for (int i = 0, n = nodes.size; i < n; i++) {
            Node<T> node = nodes.get(i);
            Actor actor = node.actor;

            if (node.filtered)
                continue;

            if (node.underline) {
                if (node.yAlpha < 0.3f) {
                    style.underline.draw(batch, x + node.actor.getX() - iconSpacingRight, y + actor.getY() - ySpacing / 2, getWidth() - node.actor.getX() - iconSpacingRight, 3);
                } else if (node.yAlpha > 0.7f) {
                    style.underline.draw(batch, x + node.actor.getX() - iconSpacingRight, y + actor.getY() + actor.getHeight() - ySpacing / 2, getWidth() - node.actor.getX() - iconSpacingRight, 3);
                } else {
                    style.underline.draw(batch, x + node.actor.getX() - iconSpacingRight, y + actor.getY() - ySpacing / 2, getWidth() - node.actor.getX() - iconSpacingRight, node.height + ySpacing);
                }

            }

            if (selection.contains(node) && style.selection != null) {
                style.selection.draw(batch, x, y + actor.getY() - ySpacing / 2, getWidth(), node.height + ySpacing);
            } else if (node == overNode && style.over != null) {
                style.over.draw(batch, x, y + actor.getY() - ySpacing / 2, getWidth(), node.height + ySpacing);
            }

            if (node.icon != null) {
                float iconY = actor.getY() + Math.round((node.height - node.icon.getMinHeight()) / 2);
                batch.setColor(actor.getColor());
                node.icon.draw(batch, x + node.actor.getX() - iconSpacingRight - node.icon.getMinWidth(), y + iconY,
                        node.icon.getMinWidth(), node.icon.getMinHeight());
                batch.setColor(Color.WHITE);
            }

            if (node.children.size == 0)
                continue;

            Drawable expandIcon = node.expanded ? minus : plus;
            float iconY = actor.getY() + Math.round((node.height - expandIcon.getMinHeight()) / 2);
            expandIcon.draw(batch, x + indent - iconSpacingLeft, y + iconY, expandIcon.getMinWidth(), expandIcon.getMinHeight());
            if (node.expanded)
                draw(batch, node.children, indent + indentSpacing);
        }
    }

    /**
     * @return May be null.
     */
    public Node<T> getNodeAt (float y) {
        foundNode = null;
        getNodeAt(rootNodes, y, getHeight());
        return foundNode;
    }

    private float getNodeAt (Array<Node<T>> nodes, float y, float rowY) {
        for (int i = 0, n = nodes.size; i < n; i++) {
            Node node = nodes.get(i);
            if (node.filtered)
                continue;

            if (y >= rowY - node.height - ySpacing && y < rowY) {
                foundNode = node;
                return -1;
            }
            rowY -= node.height + ySpacing;
            if (node.expanded) {
                rowY = getNodeAt(node.children, y, rowY);
                if (rowY == -1)
                    return -1;
            }
        }
        return rowY;
    }

    void selectNodes (Array<Node<T>> nodes, float low, float high) {
        for (int i = 0, n = nodes.size; i < n; i++) {
            Node<T> node = nodes.get(i);
            if (node.actor.getY() < low)
                break;
            if (!node.isSelectable())
                continue;
            if (node.actor.getY() <= high)
                selection.add(node);
            if (node.expanded)
                selectNodes(node.children, low, high);
        }
    }

    public Selection<Node<T>> getSelection () {
        return selection;
    }

    public TreeStyle getStyle () {
        return style;
    }

    public Array<Node<T>> getRootNodes () {
        return rootNodes;
    }

    /**
     * @return May be null.
     */
    public Node<T> getOverNode () {
        return overNode;
    }

    /**
     * @return May be null.
     */
    public Object getOverObject () {
        if (overNode == null)
            return null;
        return overNode.getObject();
    }

    /**
     * @param overNode May be null.
     */
    public void setOverNode (Node overNode) {
        this.overNode = overNode;
    }

    /**
     * Sets the amount of horizontal space between the nodes and the left/right edges of the tree.
     */
    public void setPadding (float padding) {
        this.padding = padding;
    }

    /**
     * Returns the amount of horizontal space for indentation level.
     */
    public float getIndentSpacing () {
        return indentSpacing;
    }

    /**
     * Sets the amount of vertical space between nodes.
     */
    public void setYSpacing (float ySpacing) {
        this.ySpacing = ySpacing;
    }

    public float getYSpacing () {
        return ySpacing;
    }

    /**
     * Sets the amount of horizontal space between the node actors and icons.
     */
    public void setIconSpacing (float left, float right) {
        this.iconSpacingLeft = left;
        this.iconSpacingRight = right;
    }

    public float getPrefWidth () {
        if (sizeInvalid)
            computeSize();
        return prefWidth;
    }

    public float getPrefHeight () {
        if (sizeInvalid)
            computeSize();
        return prefHeight;
    }

    public void findExpandedObjects (Array<T> objects) {
        findExpandedObjects(rootNodes, objects);
    }

    public void restoreExpandedObjects (Array<T> objects) {
        for (int i = 0, n = objects.size; i < n; i++) {
            Node<T> node = findNode(objects.get(i));
            if (node != null) {
                node.setExpanded(true);
                node.expandTo();
            }
        }
    }

    static <T> boolean findExpandedObjects (Array<Node<T>> nodes, Array<T> objects) {
        boolean expanded = false;
        for (int i = 0, n = nodes.size; i < n; i++) {
            Node<T> node = nodes.get(i);
            if (node.expanded && !findExpandedObjects(node.children, objects))
                objects.add(node.object);
        }
        return expanded;
    }

    /**
     * Returns the node with the specified object, or null.
     */
    public Node<T> findNode (T object) {
        if (object == null)
            throw new IllegalArgumentException("object cannot be null.");
        return findNode(rootNodes, object);
    }

    static <T> Node<T> findNode (Array<Node<T>> nodes, T object) {
        for (int i = 0, n = nodes.size; i < n; i++) {
            Node node = nodes.get(i);
            if (object.equals(node.object))
                return node;
        }
        for (int i = 0, n = nodes.size; i < n; i++) {
            Node node = nodes.get(i);
            Node found = findNode(node.children, object);
            if (found != null)
                return found;
        }
        return null;
    }

    public void collapseAll () {
        collapseAll(rootNodes);
    }

    static <T> void collapseAll (Array<Node<T>> nodes) {
        for (int i = 0, n = nodes.size; i < n; i++) {
            Node<T> node = nodes.get(i);
            node.setExpanded(false);
            collapseAll(node.children);
        }
    }

    public void expandAll () {
        expandAll(rootNodes);
    }

    static <T> void expandAll (Array<Node<T>> nodes) {
        for (int i = 0, n = nodes.size; i < n; i++)
            nodes.get(i).expandAll();
    }

    /**
     * Returns the click listener the tree uses for clicking on nodes and the over node.
     */
    public ClickListener getClickListener () {
        return clickListener;
    }

    public void sortAlphabetical () {
        rootNodes.sort(alphabeticalSorter);

        for (int i = 0; i < rootNodes.size; i++) {
            sortChildrenAlphabetical(rootNodes.get(i));
        }
    }

    private Comparator<Node<T>> alphabeticalSorter = new Comparator<Node<T>>() {
        @Override
        public int compare (Node<T> o1, Node<T> o2) {
            return o1.name.compareTo(o2.name);
        }
    };

    private void sortChildrenAlphabetical (Node<T> node) {
        node.children.sort(alphabeticalSorter);

        Array<Node<T>> children = node.getChildren();

        for (int i = 0; i < children.size; i++) {
            sortChildrenAlphabetical(children.get(i));
        }
    }

    static public class Node<T> {
        final Actor actor;
        Node<T> parent;
        public final Array<Node<T>> children = new Array<>(0);
        boolean selectable = true;
        boolean expanded;
        Drawable icon;
        float height;
        T object;
        public String name;
        boolean filtered = false;
        boolean underline = false;
        float yAlpha = 0f;
        public boolean draggable;
        public boolean draggableInLayerOnly;

        public Node (String name, Actor actor) {
            if (actor == null)
                throw new IllegalArgumentException("actor cannot be null.");
            this.name = name;
            this.actor = actor;
        }

        public void setExpanded (boolean expanded) {
            if (expanded == this.expanded)
                return;
            this.expanded = expanded;
            if (children.size == 0)
                return;
            FilteredTree tree = getTree();
            if (tree == null)
                return;
            if (expanded) {
                for (int i = 0, n = children.size; i < n; i++)
                    children.get(i).addToTree(tree);
            } else {
                for (int i = 0, n = children.size; i < n; i++)
                    children.get(i).removeFromTree(tree);
            }
            tree.invalidateHierarchy();
        }

        /**
         * Called to add the actor to the tree when the node's parent is expanded.
         */
        protected void addToTree (FilteredTree tree) {
            tree.addActor(actor);
            if (!expanded)
                return;
            for (int i = 0, n = children.size; i < n; i++)
                children.get(i).addToTree(tree);
        }

        /**
         * Called to remove the actor from the tree when the node's parent is collapsed.
         */
        protected void removeFromTree (FilteredTree tree) {
            tree.removeActor(actor);
            if (!expanded)
                return;
            Object[] children = this.children.items;
            for (int i = 0, n = this.children.size; i < n; i++)
                ((Node)children[i]).removeFromTree(tree);
        }

        public void add (Node node) {
            insert(children.size, node);
        }

        public void addAll (Array<Node> nodes) {
            for (int i = 0, n = nodes.size; i < n; i++)
                insert(children.size, nodes.get(i));
        }

        public void insert (int index, Node node) {
            node.parent = this;
            children.insert(index, node);
            updateChildren();
        }

        public void remove () {
            FilteredTree tree = getTree();
            if (tree != null)
                tree.remove(this);
            else if (parent != null) //
                parent.remove(this);
        }

        public void remove (Node node) {
            children.removeValue(node, true);
            if (!expanded)
                return;
            FilteredTree tree = getTree();
            if (tree == null)
                return;
            node.removeFromTree(tree);
            if (children.size == 0)
                expanded = false;
        }

        public void removeAll () {
            FilteredTree tree = getTree();
            if (tree != null) {
                for (int i = 0, n = children.size; i < n; i++)
                    children.get(i).removeFromTree(tree);
            }
            children.clear();
        }

        /**
         * Returns the tree this node is currently in, or null.
         */
        public FilteredTree getTree () {
            Group parent = actor.getParent();
            if (!(parent instanceof FilteredTree))
                return null;
            return (FilteredTree)parent;
        }

        public Actor getActor () {
            return actor;
        }

        public boolean isExpanded () {
            return expanded;
        }

        /**
         * If the children order is changed, {@link #updateChildren()} must be called.
         */
        public Array<Node<T>> getChildren () {
            return children;
        }

        public void updateChildren () {
            if (!expanded)
                return;
            FilteredTree tree = getTree();
            if (tree == null)
                return;
            for (int i = 0, n = children.size; i < n; i++)
                children.get(i).addToTree(tree);
        }

        /**
         * @return May be null.
         */
        public Node<T> getParent () {
            return parent;
        }

        /**
         * Sets an icon that will be drawn to the left of the actor.
         */
        public void setIcon (Drawable icon) {
            this.icon = icon;
        }

        public T getObject () {
            return object;
        }

        /**
         * Sets an application specific object for this node.
         */
        public void setObject (T object) {
            this.object = object;
        }

        public Drawable getIcon () {
            return icon;
        }

        public int getLevel () {
            int level = 0;
            Node current = this;
            do {
                level++;
                current = current.getParent();
            } while (current != null);
            return level;
        }

        /**
         * Returns this node or the child node with the specified object, or null.
         */
        public Node<T> findNode (T object) {
            if (object == null)
                throw new IllegalArgumentException("object cannot be null.");
            if (object.equals(this.object))
                return this;
            return FilteredTree.findNode(children, object);
        }

        /**
         * Collapses all nodes under and including this node.
         */
        public void collapseAll () {
            setExpanded(false);
            FilteredTree.collapseAll(children);
        }

        /**
         * Expands all nodes under and including this node.
         */
        public void expandAll () {
            setExpanded(true);
            if (children.size > 0)
                FilteredTree.expandAll(children);
        }

        /**
         * Expands all parent nodes of this node.
         */
        public void expandTo () {
            Node node = parent;
            while (node != null) {
                node.setExpanded(true);
                node = node.parent;
            }
        }

        public boolean isSelectable () {
            return selectable && !filtered;
        }

        public void setSelectable (boolean selectable) {
            this.selectable = selectable;
        }

        public void findExpandedObjects (Array<T> objects) {
            if (expanded && !FilteredTree.findExpandedObjects(children, objects))
                objects.add(object);
        }

        public void restoreExpandedObjects (Array<T> objects) {
            for (int i = 0, n = objects.size; i < n; i++) {
                Node<T> node = findNode(objects.get(i));
                if (node != null) {
                    node.setExpanded(true);
                    node.expandTo();
                }
            }
        }

        public String getName () {
            return name;
        }
    }

    /**
     * The style for a {@link FilteredTree}.
     *
     * @author Nathan Sweet
     */
    static public class TreeStyle {
        public Drawable plus, minus;
        /**
         * Optional.
         */
        public Drawable over, selection, background, underline;

        public TreeStyle () {
        }

        public TreeStyle (Drawable plus, Drawable minus, Drawable selection) {
            this.plus = plus;
            this.minus = minus;
            this.selection = selection;
        }

        public TreeStyle (TreeStyle style) {
            this.plus = style.plus;
            this.minus = style.minus;
            this.selection = style.selection;
        }
    }
}
