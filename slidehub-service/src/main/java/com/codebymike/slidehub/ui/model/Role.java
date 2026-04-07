package com.codebymike.slidehub.ui.model;

/**
 * Roles de usuario en SlideHub (AGENTS.md §2.2).
 * HOST: quien crea una cuenta y es dueño de sus presentaciones.
 * PRESENTER: quien se une con código QR o link para presentar, invitado por el HOST.
 * DEVELOPER: acceso al panel de gestión /mgr (operaciones internas).
 */
public enum Role {
    HOST,
    PRESENTER,
    DEVELOPER
}
