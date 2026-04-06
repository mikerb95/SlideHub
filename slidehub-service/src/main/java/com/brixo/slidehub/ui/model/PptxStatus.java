package com.brixo.slidehub.ui.model;

/**
 * Estado del proceso de conversión PPTX → PNG via AWS Lambda.
 * Solo relevante para presentaciones con {@link SourceType#PPTX}.
 */
public enum PptxStatus {
    /** Lambda recibió el PPTX y está procesando. */
    PROCESSING,
    /** Lambda terminó exitosamente; slides disponibles en S3. */
    READY,
    /** Lambda reportó un error; no hay slides. */
    FAILED
}
