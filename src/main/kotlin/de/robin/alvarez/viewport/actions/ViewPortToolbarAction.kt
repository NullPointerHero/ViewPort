package de.robin.alvarez.viewport.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ex.ToolWindowManagerEx

class ViewPortToolbarAction : AnAction() {
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.getRequiredData(CommonDataKeys.PROJECT)
        openBrowser(project)
    }
    
    override fun update(e: AnActionEvent) {
        val project = e.getData(CommonDataKeys.PROJECT)
        e.presentation.isEnabledAndVisible = project != null
    }
    
    private fun openBrowser(project: Project) {
        val toolWindowManager = ToolWindowManagerEx.getInstanceEx(project)
        val toolWindow = toolWindowManager.getToolWindow("ViewPort Browser")
        
        toolWindow?.let {
            if (!it.isVisible) {
                it.show()
            } else {
                it.activate(null)
            }
        }
    }
}
