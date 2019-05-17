/*******************************************************************************
 * Copyright (c) 2019 Kiel University and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.elk.core.debug;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;

import org.eclipse.elk.core.debug.actions.ClearExecutionsAction;
import org.eclipse.elk.core.debug.actions.CollapseExecutionTreeAction;
import org.eclipse.elk.core.debug.actions.ExpandExecutionTreeAction;
import org.eclipse.elk.core.debug.actions.FilterExecutionTreeAction;
import org.eclipse.elk.core.debug.model.ExecutionInfo;
import org.eclipse.elk.core.debug.model.ExecutionInfoContentProvider;
import org.eclipse.elk.core.debug.model.IExecutionInfoModelListener;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.dialogs.DialogSettings;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IPerspectiveDescriptor;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PerspectiveAdapter;
import org.eclipse.ui.part.ViewPart;

/**
 * All layout debug views should extend this class. It provides a tree viewer that shows {@link ExecutionInfo}
 * instances, updates when the list of {@link ExecutionInfo}s changes, saves and reloads state and generally does stuff
 * that would usually have to be done by every layout debug view anyway.
 */
public abstract class AbstractLayoutDebugView extends ViewPart implements IExecutionInfoModelListener {
    
    /** The ID of our view. Used to persist and reload view state through dialog settings. */
    private final String viewId;
    /** The content provider for our tree viewer. */
    private final ExecutionInfoContentProvider treeContentProvider = new ExecutionInfoContentProvider();
    
    // Actions
    private final ClearExecutionsAction clearExecutionsAction = new ClearExecutionsAction();
    private final FilterExecutionTreeAction filterTreeAction = new FilterExecutionTreeAction(this);
    private final CollapseExecutionTreeAction collapseTreeAction = new CollapseExecutionTreeAction(this);
    private final ExpandExecutionTreeAction expandTreeAction = new ExpandExecutionTreeAction(this);
    
    // Events
    private final ViewCloseListener viewCloseListener = new ViewCloseListener();
    
    // UI Controls
    private SashForm sashForm;
    private TreeViewer treeViewer;
    
    /**
     * Creates a new instance with the given view ID.
     */
    public AbstractLayoutDebugView(String viewId) {
        this.viewId = viewId;
    }
    
    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Execution Tree Handling
    
    /**
     * Called if the selection in the tree viewer has changed. Implementations should probably update any controls that
     * show content related to the tree viewer. Be sure to call the superclass implementation.
     */
    protected void treeSelectionChanged() {
        updateActionEnablement();
    }
    
    /**
     * Returns the (possibly empty) list of execution infos selected in the tree viewer.
     */
    public List<ExecutionInfo> getSelectedExecutionInfos() {
        List<ExecutionInfo> result = new ArrayList<>();
        
        Iterator<?> selectionIterator = treeViewer.getStructuredSelection().iterator();
        while (selectionIterator.hasNext()) {
            // Nothing but ExecutionInfo instances ever gets into this tree viewer!
            result.add((ExecutionInfo) selectionIterator.next());
        }
        
        return result;
    }
    
    public void setFilterTree(final boolean filter) {
        if (filter) {
            treeContentProvider.setFilter(getTreeFilter());
        } else {
            treeContentProvider.setFilter(null);
        }
        
        treeViewer.refresh();
        treeSelectionChanged();
    }
    
    protected abstract Predicate<ExecutionInfo> getTreeFilter();

    /**
     * Collapses all elements of the tree viewer.
     */
    public void collapseAllTreeViewerElements() {
        treeViewer.collapseAll();
        treeSelectionChanged();
    }

    /**
     * Expand all elements of the tree viewer.
     */
    public void expandAllTreeViewerElements() {
        treeViewer.expandAll();
    }
    
    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // UI Setup
    
    @Override
    public void createPartControl(Composite parent) {
        // Parent layout
        GridLayout gl = new GridLayout(1, false);
        gl.marginHeight = 0;
        gl.marginWidth = 0;
        parent.setLayout(gl);

        setupToolBar();
        updateActionEnablement();
        
        sashForm = new SashForm(parent, SWT.HORIZONTAL);
        sashForm.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        
        setupTreeViewer(sashForm);
        setupRemainingControls(sashForm);
        
        // Restore necessary settings
        loadState(retrieveDialogSettings());
        
        // Register to be notified when the view is closed
        getSite().getWorkbenchWindow().addPerspectiveListener(viewCloseListener);
        
        // Register for changes to the execution info model
        ElkDebugPlugin.getDefault().getModel().addExecutionInfoModelListener(this);
    }
    
    private void setupToolBar() {
        IToolBarManager toolBarManager = getViewSite().getActionBars().getToolBarManager();
        
        // Install custom actions
        toolBarManager.add(filterTreeAction);
        toolBarManager.add(clearExecutionsAction);
        toolBarManager.add(expandTreeAction);
        toolBarManager.add(collapseTreeAction);
    }
    
    /**
     * Called before common tool bar actions are added. Subclasses can override and add custom actions. The default
     * implementation does nothing.
     */
    protected void customizeToolBar(IToolBarManager toolBarManager) {
        // Do nothing
    }
    
    private void setupTreeViewer(Composite parent) {
        treeViewer = new TreeViewer(parent);
        treeViewer.setContentProvider(treeContentProvider);
        
        // React to selection changes by 
        treeViewer.addSelectionChangedListener(new ISelectionChangedListener() {
            @Override
            public void selectionChanged(SelectionChangedEvent event) {
                treeSelectionChanged();
            }
        });
        
        customizeTreeViewer(treeViewer);
        
        treeViewer.setInput(ElkDebugPlugin.getDefault().getModel().getExecutionInfos());
    }
    
    /**
     * Subclasses should implement this method to install a proper label provider and make any additional modifications
     * required for the tree viewer, such as installing table columns.
     */
    protected abstract void customizeTreeViewer(TreeViewer treeViewer);
    
    /**
     * Called once the tree viewer has been installed to add any remaining controls that need to be added to the view.
     * The default implementation does nothing.
     */
    protected void setupRemainingControls(Composite parent) {
        // Do nothing
    }
    
    @Override
    public void dispose() {
        super.dispose();
        
        getSite().getWorkbenchWindow().removePerspectiveListener(viewCloseListener);
    }
    
    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // State Persistence
    
    private static final String SASH_KEY = ".sashWeights";
    private static final String FILTER_KEY = ".filterState";
    
    /**
     * Load any state from previous invocations of the view. Called once the UI has been fully initialized.
     * 
     * @param viewSettings
     *            {@link IDialogSettings} instance specific to this view.
     */
    protected void loadState(IDialogSettings viewSettings) {
        // Reload sash state, if any
        if (viewSettings.get(SASH_KEY) != null) {
            int[] weights = new int[viewSettings.getInt(SASH_KEY)];
            boolean weightsLoadedSuccessfully = true;
            
            for (int i = 0; i < weights.length; i++) {
                String key = SASH_KEY + "." + i;
                if (viewSettings.get(key) != null) {
                    weights[i] = viewSettings.getInt(key);
                } else {
                    // At least one weight could not be properly loaded, so abandon our endeavor
                    weightsLoadedSuccessfully = false;
                    break;
                }
            }
            
            if (weightsLoadedSuccessfully) {
                sashForm.setWeights(weights);
            }
        }
        
        // Reload filter state
        if (viewSettings.get(FILTER_KEY) != null) {
            if (viewSettings.getBoolean(FILTER_KEY)) {
                filterTreeAction.setChecked(true);
                filterTreeAction.run();
            }
        }
    }
    
    /**
     * Save any state to be restored when the view is opened next time. Called before the UI is shut down.
     * 
     * @param viewSettings
     */
    protected void persistState(IDialogSettings viewSettings) {
        // Save sash state
        int[] weights = sashForm.getWeights();
        viewSettings.put(SASH_KEY, weights.length);
        
        // Save the array elements one by one
        for (int i = 0; i < weights.length; i++) {
            viewSettings.put(SASH_KEY + "." + i, weights[i]);
        }
        
        // Save filter state
        viewSettings.put(FILTER_KEY, filterTreeAction.isChecked());
    }
    
    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Events
    
    /**
     * Updates the enabled state of our actions depending on the tree viewer's state.
     */
    private void updateActionEnablement() {
        clearExecutionsAction.setEnabled(!ElkDebugPlugin.getDefault().getModel().getExecutionInfos().isEmpty());
    }

    @Override
    public void setFocus() {
        treeViewer.getControl().setFocus();
    }

    @Override
    public void executionInfoChanged() {
        // Notify viewer on UI thread
        getSite().getShell().getDisplay().asyncExec(new Runnable() {
            @Override
            public void run() {
                treeViewer.refresh();
                updateActionEnablement();
                treeSelectionChanged();
            }
        });
    }
    
    /**
     * Listens to perspective events to be notified whenever a view is about to close.
     */
    private class ViewCloseListener extends PerspectiveAdapter {
        @Override
        public void perspectiveChanged(IWorkbenchPage page, IPerspectiveDescriptor perspective, String changeId) {
            if (changeId.equals(IWorkbenchPage.CHANGE_VIEW_HIDE)) {
                // This event occurs whenever a view is hidden, not just our view, so it would be nice to have a
                // proper solution that only kicks in once our particular view is closed
                persistState(retrieveDialogSettings());
            }
        }
    }
    
    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Utilities
    
    private IDialogSettings retrieveDialogSettings() {
        return DialogSettings.getOrCreateSection(ElkDebugPlugin.getDefault().getDialogSettings(), viewId);
    }
    
}
