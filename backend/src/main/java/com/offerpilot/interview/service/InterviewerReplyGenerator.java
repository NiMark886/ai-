package com.offerpilot.interview.service;

import com.offerpilot.interview.domain.InterviewMessage;
import com.offerpilot.interview.domain.InterviewQuestion;
import com.offerpilot.interview.domain.ReplyType;

import java.util.List;

public interface InterviewerReplyGenerator {

    String generate(InterviewQuestion question, ReplyType type, String candidateAnswer,
                    List<InterviewMessage> history);
}
