package io.github.kwd421.lumitoolbridge;

import java.util.Map;

public interface Tool {
    String name();
    Map<String, Object> definition();
    Object execute(Map<String, Object> arguments, ToolContext context) throws Exception;
}
