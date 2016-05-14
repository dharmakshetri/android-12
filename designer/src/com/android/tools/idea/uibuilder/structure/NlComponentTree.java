/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.uibuilder.structure;

import com.android.tools.idea.uibuilder.model.*;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.android.tools.idea.uibuilder.surface.DesignSurfaceListener;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.intellij.ide.DeleteProvider;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.IJSwingUtilities;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.Insets;
import java.awt.dnd.DropTarget;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.uibuilder.property.NlPropertiesManager.UPDATE_DELAY_MSECS;
import static com.android.tools.idea.uibuilder.structure.NlComponentTree.InsertionPoint.INSERT_INTO;
import static com.intellij.util.Alarm.ThreadToUse.SWING_THREAD;

public class NlComponentTree extends Tree implements DesignSurfaceListener, ModelListener, SelectionListener, DataProvider,
                                                     DeleteProvider {
  private static final Insets INSETS = new Insets(0, 6, 0, 6);

  private final Map<NlComponent, DefaultMutableTreeNode> myComponent2Node;
  private final AtomicBoolean mySelectionIsUpdating;
  private final MergingUpdateQueue myUpdateQueue;

  private ScreenView myScreenView;
  private NlModel myModel;
  private TreePath myInsertionPath;
  private InsertionPoint myInsertionPoint;
  private boolean mySkipWait;

  public NlComponentTree(@NotNull DesignSurface designSurface) {
    myComponent2Node = new IdentityHashMap<>();
    mySelectionIsUpdating = new AtomicBoolean(false);
    myUpdateQueue = new MergingUpdateQueue(
      "android.layout.structure-pane", UPDATE_DELAY_MSECS, true, null, null, null, SWING_THREAD);
    TreeNode rootNode = new DefaultMutableTreeNode();
    DefaultTreeModel treeModel = new DefaultTreeModel(rootNode);
    setModel(treeModel);
    getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
    setBorder(new EmptyBorder(INSETS));
    setRootVisible(true);
    setShowsRootHandles(false);
    setToggleClickCount(2);
    ToolTipManager.sharedInstance().registerComponent(this);
    TreeUtil.installActions(this);
    createCellRenderer();
    addTreeSelectionListener(new StructurePaneSelectionListener());
    new StructureSpeedSearch(this);
    enableDnD();
    setDesignSurface(designSurface);
    addMouseListener(new StructurePaneMouseListener());
  }

  private void enableDnD() {
    if (!ApplicationManager.getApplication().isHeadlessEnvironment()) {
      setDragEnabled(true);
      setTransferHandler(new TreeTransferHandler());
      setDropTarget(new DropTarget(this, new NlDropListener(this)));
    }
  }

  public void setDesignSurface(@Nullable DesignSurface designSurface) {
    setScreenView(designSurface != null ? designSurface.getCurrentScreenView() : null);
  }

  private void setScreenView(@Nullable ScreenView screenView) {
    myScreenView = screenView;
    setModel(screenView != null ? screenView.getModel() : null);
  }

  @NotNull
  Map<NlComponent, DefaultMutableTreeNode> getComponentToNode() {
    return myComponent2Node;
  }

  @Nullable
  public ScreenView getScreenView() {
    return myScreenView;
  }

  private void setModel(@Nullable NlModel model) {
    if (myModel != null) {
      myModel.removeListener(this);
      myModel.getSelectionModel().removeListener(this);
    }
    myModel = model;
    if (myModel != null) {
      myModel.addListener(this);
      myModel.getSelectionModel().addListener(this);
    }
    loadData();
  }

  @Nullable
  public NlModel getDesignerModel() {
    return myModel;
  }

  public void dispose() {
    if (myModel != null) {
      myModel.removeListener(this);
      myModel.getSelectionModel().removeListener(this);
      myModel = null;
    }
    Disposer.dispose(myUpdateQueue);
  }

  private void createCellRenderer() {
    ColoredTreeCellRenderer renderer = new ColoredTreeCellRenderer() {
      @Override
      public void customizeCellRenderer(@NotNull JTree tree,
                                        Object value,
                                        boolean selected,
                                        boolean expanded,
                                        boolean leaf,
                                        int row,
                                        boolean hasFocus) {
        NlComponent component = toComponent(value);

        if (component == null) {
          return;
        }

        StructureTreeDecorator.decorate(this, component);
      }
    };
    renderer.setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));
    setCellRenderer(renderer);
  }

  private void loadData() {
    updateHierarchy(true);
  }

  private void invalidateUI() {
    IJSwingUtilities.updateComponentTreeUI(this);
  }

  // ---- Methods for updating hierarchy while attempting to keep expanded nodes expanded ----

  private void updateHierarchy(final boolean firstLoad) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    setPaintBusy(true);
    myUpdateQueue.queue(new Update("updateComponentStructure") {
      @Override
      public void run() {
        NlLayoutType resourceType = myModel.getType();

        try {
          mySelectionIsUpdating.set(true);

          switch (resourceType) {
            case MENU:
            case PREFERENCE_SCREEN:
              updateHierarchy();
              break;
            case LAYOUT:
            default:
              updateLayoutHierarchy();
              break;
          }

          invalidateUI();
        }
        finally {
          setPaintBusy(false);
          mySelectionIsUpdating.set(false);
        }
        if (firstLoad && resourceType.isLayout()) {
          updateSelection();
        }
      }
    });
    if (mySkipWait) {
      mySkipWait = false;
      myUpdateQueue.flush();
    }
  }

  /**
   * Normally the outline pauses for a certain delay after a model change before updating itself
   * to reflect the new hierarchy. This method can be called to skip (just) the next update delay.
   * This is used to make operations performed <b>in</b> the outline feel more immediate.
   */
  void skipNextUpdateDelay() {
    mySkipWait = true;
  }

  private void updateHierarchy() {
    List<NlComponent> components = myModel.getComponents();

    // TODO See if the layout code path can also use NlComponentTreeModel
    setModel(components.isEmpty() ? new NlComponentTreeModel() : new NlComponentTreeModel(components.get(0)));

    // TODO Restore the expansion state of the nodes. Find a good way that both code paths can share.
    for (int row = 0, rowCount = getRowCount(); row < rowCount; row++) {
      expandRow(row);
    }
  }

  private void updateLayoutHierarchy() {
    boolean newRootNodeAdded = new HierarchyUpdater(this).execute();

    if (!newRootNodeAdded) {
      return;
    }
    final DefaultMutableTreeNode rootNode = (DefaultMutableTreeNode)getModel().getRoot();
    if (rootNode.isLeaf()) {
      return;
    }
    ApplicationManager.getApplication().invokeLater(() -> {
      DefaultMutableTreeNode nodeToExpand = rootNode;
      NlComponent component = findComponentToExpandTo();
      if (component != null) {
        nodeToExpand = myComponent2Node.get(component);
        if (nodeToExpand == null) {
          nodeToExpand = rootNode;
        }
      }
      TreePath path = new TreePath(nodeToExpand.getPath());
      expandPath(path);
      while (path != null) {
        path = path.getParentPath();
        expandPath(path);
      }
    });
  }

  // Find a component that it would be interesting to expand to when a new file is viewed.
  // If the file has an App Bar lookup something that may be user content.
  @Nullable
  private NlComponent findComponentToExpandTo() {
    if (myModel == null || myModel.getComponents().isEmpty()) {
      return null;
    }
    NlComponent root = myModel.getComponents().get(0);
    NlComponent childOfInterest = root;
    if (root.getTagName().equals(COORDINATOR_LAYOUT)) {
      // Find first child that is not an AppBarLayout and not anchored to anything.
      for (NlComponent child : root.getChildren()) {
        if (!child.getTagName().equals(APP_BAR_LAYOUT) && child.getTag().getAttribute(ATTR_LAYOUT_ANCHOR, AUTO_URI) == null) {
          // If this is a NestedScrollView look inside:
          if (child.getTagName().equals(CLASS_NESTED_SCROLL_VIEW) && child.children != null && !child.children.isEmpty()) {
            child = child.getChild(0);
          }
          childOfInterest = child;
          break;
        }
      }
    }

    if (childOfInterest == null || childOfInterest.children == null || childOfInterest.children.isEmpty()) {
      return childOfInterest;
    }
    return childOfInterest.children.get(childOfInterest.children.size() - 1);
  }

  private void updateSelection() {
    if (!mySelectionIsUpdating.compareAndSet(false, true)) {
      return;
    }
    try {
      clearSelection();
      if (myModel != null) {
        for (NlComponent component : myModel.getSelectionModel().getSelection()) {
          DefaultMutableTreeNode node = myComponent2Node.get(component);
          if (node != null) {
            TreePath path = new TreePath(node.getPath());
            expandPath(path);
            addSelectionPath(path);
          }
        }
      }
    }
    finally {
      mySelectionIsUpdating.set(false);
    }
  }

  @Override
  public void paint(Graphics g) {
    super.paint(g);
    if (myInsertionPath != null) {
      paintInsertionPoint(g);
    }
  }

  enum InsertionPoint {
    INSERT_INTO,
    INSERT_BEFORE,
    INSERT_AFTER
  }

  private void paintInsertionPoint(Graphics g) {
    if (myInsertionPath != null) {
      Rectangle pathBounds = getPathBounds(myInsertionPath);
      if (pathBounds == null) {
        return;
      }
      int y = pathBounds.y;
      switch (myInsertionPoint) {
        case INSERT_BEFORE:
          break;
        case INSERT_AFTER:
          y += pathBounds.height;
          break;
        case INSERT_INTO:
          y += pathBounds.height / 2;
          break;
      }
      Rectangle bounds = getBounds();
      Polygon triangle = new Polygon();
      triangle.addPoint(bounds.x + 6, y);
      triangle.addPoint(bounds.x, y + 3);
      triangle.addPoint(bounds.x, y - 3);
      g.setColor(UIUtil.getTreeForeground());
      if (myInsertionPoint != INSERT_INTO) {
        g.drawLine(bounds.x, y, bounds.x + bounds.width, y);
      }
      g.drawPolygon(triangle);
      g.fillPolygon(triangle);
    }
  }

  public void markInsertionPoint(@Nullable TreePath path, @NotNull InsertionPoint insertionPoint) {
    if (myInsertionPath != path || myInsertionPoint != insertionPoint) {
      myInsertionPath = path;
      myInsertionPoint = insertionPoint;
      repaint();
    }
  }

  @Override
  @SuppressWarnings("EmptyMethod")
  protected void clearToggledPaths() {
    super.clearToggledPaths();
  }

  public List<NlComponent> getSelectedComponents() {
    List<NlComponent> selected = new ArrayList<>();
    TreePath[] paths = getSelectionPaths();
    if (paths != null) {
      for (TreePath path : paths) {
        selected.add(toComponent(path.getLastPathComponent()));
      }
    }
    return selected;
  }

  // ---- Implemented SelectionListener ----
  @Override
  public void selectionChanged(@NotNull SelectionModel model, @NotNull List<NlComponent> selection) {
    if (!myModel.getType().isLayout()) {
      return;
    }

    UIUtil.invokeLaterIfNeeded(this::updateSelection);
  }

  // ---- Implemented ModelListener ----
  @Override
  public void modelChanged(@NotNull NlModel model) {
    UIUtil.invokeLaterIfNeeded(() -> updateHierarchy(false));
  }

  @Override
  public void modelRendered(@NotNull NlModel model) {
  }

  // ---- Implemented DesignSurfaceListener ----

  @Override
  public void componentSelectionChanged(@NotNull DesignSurface surface, @NotNull List<NlComponent> newSelection) {
  }

  @Override
  public void screenChanged(@NotNull DesignSurface surface, @Nullable ScreenView screenView) {
    setScreenView(screenView);
  }

  @Override
  public void modelChanged(@NotNull DesignSurface surface, @Nullable NlModel model) {
    if (model != null) {
      modelRendered(model);
    }
  }

  @Nullable
  static NlComponent toComponent(@NotNull Object node) {
    return (NlComponent)(node instanceof DefaultMutableTreeNode ? ((DefaultMutableTreeNode)node).getUserObject() : node);
  }

  private class StructurePaneMouseListener extends MouseAdapter {
    @Override
    public void mouseClicked(MouseEvent e) {
      handlePopup(e);
    }

    @Override
    public void mousePressed(MouseEvent e) {
      handlePopup(e);
    }

    @Override
    public void mouseReleased(MouseEvent e) {
      handlePopup(e);
    }

    private void handlePopup(MouseEvent e) {
      if (e.isPopupTrigger()) {
        TreePath path = getPathForLocation(e.getX(), e.getY());
        if (path != null) {
          Object pathComponent = path.getLastPathComponent();
          NlComponent component = toComponent(pathComponent);
          if (component != null) {
            // TODO: Ensure the node is selected first
            myScreenView.getSurface().getActionManager().showPopup(e, myScreenView, component);
          }
        }
      }
    }
  }

  private class StructurePaneSelectionListener implements TreeSelectionListener {
    @Override
    public void valueChanged(TreeSelectionEvent treeSelectionEvent) {
      if (!mySelectionIsUpdating.compareAndSet(false, true)) {
        return;
      }
      try {
        myModel.getSelectionModel().setSelection(getSelectedComponents());
      }
      finally {
        mySelectionIsUpdating.set(false);
      }
    }
  }

  private static final class StructureSpeedSearch extends TreeSpeedSearch {
    StructureSpeedSearch(@NotNull NlComponentTree tree) {
      super(tree);
    }

    @Override
    protected boolean isMatchingElement(Object element, String pattern) {
      if (pattern == null) {
        return false;
      }
      TreePath path = (TreePath)element;
      NlComponent component = toComponent(path.getLastPathComponent());
      return compare(component == null ? "" : StructureTreeDecorator.toString(component), pattern);
    }
  }

  // ---- Implements DataProvider ----

  @Override
  public Object getData(@NonNls String dataId) {
    if (PlatformDataKeys.DELETE_ELEMENT_PROVIDER.is(dataId)) {
      return this;
    }
    return null;
  }

  // ---- Implements DeleteProvider ----

  @Override
  public boolean canDeleteElement(@NotNull DataContext dataContext) {
    return true;
  }

  @Override
  public void deleteElement(@NotNull DataContext dataContext) {
    SelectionModel selectionModel = myScreenView.getSelectionModel();
    NlModel model = myScreenView.getModel();
    skipNextUpdateDelay();
    model.delete(selectionModel.getSelection());
  }
}
