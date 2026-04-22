package me.bintanq.quantumclan.gui;

/**
 * Functional interface representing a navigation action — typically
 * "go back to the previous GUI". Passed into GUIs that can be opened
 * from a parent menu to support a Back button.
 *
 * If null, the GUI shows a Close button instead of a Back button.
 */
@FunctionalInterface
public interface GUINavigation {
    /**
     * Execute the navigation action (e.g., reopen the parent GUI).
     * Always runs on the main server thread.
     */
    void navigate();
}