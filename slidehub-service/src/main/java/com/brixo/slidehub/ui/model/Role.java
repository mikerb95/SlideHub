package com.brixo.slidehub.ui.model;

/**
 * Roles de usuario en SlideHub (AGENTS.md §2.2).
 * PRESENTER: acceso a control de presentación.
 * ADMIN: acceso a panel de dispositivos + todo lo de PRESENTER.
 * DEVELOPER: acceso al panel de gestión /mgr (operaciones internas).
 */
public enum Role {
    PRESENTER,
    ADMIN,
    DEVELOPER
}
