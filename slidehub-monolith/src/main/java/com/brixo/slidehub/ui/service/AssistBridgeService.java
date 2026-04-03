package com.brixo.slidehub.ui.service;

import com.brixo.slidehub.ai.service.AssistService;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class AssistBridgeService {

    private final AssistService assistService;

    public AssistBridgeService(AssistService assistService) {
        this.assistService = assistService;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> processAudio(byte[] audio,
            String filename,
            String contentType,
            String repoUrl,
            int slideNumber,
            String slideContext) {
        AssistService.AssistResult result = assistService.processAudio(
            audio,
            filename,
            contentType,
            repoUrl,
            slideNumber,
            slideContext);

        return Map.of(
            "success", true,
            "transcription", result.transcription(),
            "answer", result.answer());
    }
}
