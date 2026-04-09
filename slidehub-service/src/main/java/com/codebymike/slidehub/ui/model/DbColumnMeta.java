package com.codebymike.slidehub.ui.model;

public record DbColumnMeta(
        String name,
        String type,
        boolean nullable,
        String defaultValue,
        boolean primaryKey) {
}
