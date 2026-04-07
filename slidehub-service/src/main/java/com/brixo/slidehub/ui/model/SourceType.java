package com.codebymike.slidehub.ui.model;

/**
 * Origen de los slides de una presentación.
 */
public enum SourceType {
    /** Importado desde una carpeta de Google Drive. */
    DRIVE,
    /** Subido manualmente como archivos PNG/JPG. */
    UPLOAD,
    /** Subido como archivo .pptx; convertido a PNG via AWS Lambda. */
    PPTX
}
