package com.windrunner.server.tools.attention;

import com.windrunner.server.attention.AttentionService;
import com.windrunner.server.tools.Tool;
import com.windrunner.server.tools.ToolAuthorizationService;
import com.windrunner.server.tools.ToolExecutionContext;
import com.windrunner.server.utils.FileUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FetchMyAttentionTool implements Tool<FetchMyAttentionTool.Parameters> {

    private final AttentionService attentionService;
    private final ToolAuthorizationService authorization;

    @Override
    public String name() {
        return "fetch_my_attention";
    }

    @Override
    public String description() {
        return FileUtils.loadSystemPrompt("fetch-my-attention-tool.md");
    }

    @Override
    public Class<Parameters> parametersType() {
        return Parameters.class;
    }

    @Override
    public Object execute(Parameters parameters, ToolExecutionContext context) {
        return attentionService.getSummary(authorization.requireActor(context));
    }

    @Override
    public boolean parallelSafe() {
        return true;
    }

    public record Parameters() {
    }
}
