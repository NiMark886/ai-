package com.offerpilot.support;

import org.springframework.test.web.servlet.MvcResult;

import java.util.concurrent.TimeUnit;

/**
 * MockMvc 的 asyncDispatch 与 SSE 写线程存在竞态：写线程还在向
 * MockHttpServletResponse 发送事件时触发 dispatch 会抛
 * ConcurrentModificationException。这里等到最后一个事件写入后再 dispatch。
 */
public final class SseTestSupport {

    private static final long TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(10);

    private SseTestSupport() {
    }

    public static MvcResult awaitStreamCompletion(MvcResult result) throws Exception {
        long deadline = System.nanoTime() + TIMEOUT_NANOS;
        while (System.nanoTime() < deadline) {
            String body;
            try {
                body = result.getResponse().getContentAsString();
            } catch (RuntimeException ex) {
                Thread.sleep(10);
                continue;
            }
            if (body.contains("\"done\"") || body.contains("\"error\"")) {
                return result;
            }
            Thread.sleep(10);
        }
        return result;
    }
}
