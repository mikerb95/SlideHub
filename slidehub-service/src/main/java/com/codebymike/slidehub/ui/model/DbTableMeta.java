package com.codebymike.slidehub.ui.model;

import java.util.List;

public record DbTableMeta(
        String name,
        long rowCount,
        List<DbColumnMeta> columns) {
}
