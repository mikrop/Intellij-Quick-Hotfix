package com.intellij.plugin.quickhotfix;

import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.actions.VcsContext;
import com.intellij.openapi.vcs.changes.CommitExecutor;
import com.intellij.openapi.vcs.changes.actions.AbstractCommitChangesAction;

/**
 * @author Michal Hájek, <a href="mailto:michalhajek@centrum.cz">michalhajek@centrum.cz</a>
 * @since 8.2.12
 */
public class QuickHotfixAction extends AbstractCommitChangesAction {

    @Override
    protected String getActionName(final VcsContext vcsContext) {
        return "Quick hotfix";
    }

    public CommitExecutor getExecutor(final Project project) {
        return project.getComponent(QuickHotfixCommitExecutor.class);
    }

    protected void performUpdate(final Presentation presentation, final VcsContext vcsContext) {
        super.performUpdate(presentation, vcsContext);
        if (presentation.isEnabled()) {
            // noinspection ConstantConditions
            if (vcsContext.getSelectedChanges() == null || vcsContext.getSelectedChanges().length == 0) {
                presentation.setEnabled(false);
            }
        }
    }

}
