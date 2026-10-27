package com.offerpilot.resume.service;

import com.offerpilot.resume.domain.ResumeProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class ResumeProfileService {

    private static final Logger log = LoggerFactory.getLogger(ResumeProfileService.class);

    private final HeuristicResumeProfileParser heuristicParser;
    private final ObjectProvider<AiResumeProfileParser> aiParserProvider;

    public ResumeProfileService(HeuristicResumeProfileParser heuristicParser,
                                ObjectProvider<AiResumeProfileParser> aiParserProvider) {
        this.heuristicParser = heuristicParser;
        this.aiParserProvider = aiParserProvider;
    }

    public ResumeProfile parse(String rawText) {
        AiResumeProfileParser aiParser = aiParserProvider.getIfAvailable();
        if (aiParser != null) {
            try {
                return aiParser.parse(rawText);
            } catch (Exception ex) {
                log.warn("AI resume parser failed, using heuristic parser");
            }
        }
        return heuristicParser.parse(rawText);
    }
}
