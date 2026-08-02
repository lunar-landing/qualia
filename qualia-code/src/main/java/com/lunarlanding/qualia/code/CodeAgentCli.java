package com.lunarlanding.qualia.code;

import com.alibaba.fastjson.JSONObject;
import com.lunarlanding.qualia.core.agent.HarnessAgent;
import com.lunarlanding.qualia.core.agent.spec.AgentResponse;
import com.lunarlanding.qualia.core.agent.spec.AgentStep;
import com.lunarlanding.qualia.code.cmd.InitCommand;
import com.lunarlanding.qualia.code.cmd.WebCommand;
import com.lunarlanding.qualia.code.service.ChatService;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.Iterator;
import java.util.Scanner;
import java.util.concurrent.Callable;

/**
 * Qualia Code CLI - 交互层
 */
@Command(name = "qualia-code", mixinStandardHelpOptions = true, version = "1.0.0", description = "ReActAgent CLI - 本地开发智能助手",
        subcommands = {InitCommand.class, WebCommand.class})
public class CodeAgentCli implements Callable<Integer> {

    @Option(names = {"-w", "--workspace"}, description = "工作区路径（默认当前目录）")
    private Path workspace;

    @Option(names = {"-p", "--port"}, description = "Web服务端口（默认 8080）")
    private int port = 8080;

    @Option(names = {"--cli"}, description = "使用CLI交互模式（默认Web模式）")
    private boolean cliMode = false;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new CodeAgentCli()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() {
        try {
            Path workspacePath = workspace != null ? workspace : Path.of(System.getProperty("user.dir"));
            
            // 根据模式选择启动方式
            if (cliMode) {
                // CLI 交互模式 - 使用统一的 ChatService
                ChatService chatService = ChatService.getInstance(workspacePath);
                chatService.initialize();
                CodeAgentConfig config = chatService.getConfig();
                HarnessAgent agent = chatService.getAgent();
                
                if (config.getModels().isEmpty()) {
                    System.err.println("错误: 未配置模型，请先运行 'qualia-code init' 初始化配置");
                    return 1;
                }
                printWelcomeMessage(config, agent);
                runInteractiveLoop(chatService);
            } else {
                // Web 模式（默认）
                WebApplication.start(workspacePath, port);
            }
            return 0;
        } catch (Exception e) {
            System.err.println("错误: " + e.getMessage());
            return 1;
        }
    }
    
    private void printWelcomeMessage(CodeAgentConfig config, HarnessAgent agent) {
        System.out.println();
        System.out.println(" ██████╗ ██╗   ██╗ █████╗ ██╗     ██╗ █████╗      ██████╗ ██████╗ ██████╗ ███████╗");
        System.out.println("██╔═══██╗██║   ██║██╔══██╗██║     ██║██╔══██╗    ██╔════╝██╔═══██╗██╔══██╗██╔════╝");
        System.out.println("██║   ██║██║   ██║███████║██║     ██║███████║    ██║     ██║   ██║██║  ██║█████╗  ");
        System.out.println("██║▄▄ ██║██║   ██║██╔══██║██║     ██║██╔══██║    ██║     ██║   ██║██║  ██║██╔══╝  ");
        System.out.println("╚██████╔╝╚██████╔╝██║  ██║███████╗██║██║  ██║    ╚██████╗╚██████╔╝██████╔╝███████╗");
        System.out.println(" ╚══▀▀═╝  ╚═════╝ ╚═╝  ╚═╝╚══════╝╚═╝╚═╝  ╚═╝     ╚═════╝ ╚═════╝ ╚═════╝ ╚══════╝");
        System.out.println();
        System.out.println(" Workspace : " + config.getWorkspacePath().toAbsolutePath());
        System.out.println();
        System.out.println(" Tools: " + agent.getTools().size() + "  Skills: " + agent.getSkills().size() + "  Mcp: " + config.getMcpServers().size());
        System.out.println();
        System.out.println(" Type 'exit' to quit");
        System.out.println();
    }
    
    private void runInteractiveLoop(ChatService chatService) {
        Scanner scanner = new Scanner(System.in);
        String sessionId = null; // 延迟创建会话

        while (true) {
            System.out.print("> ");
            String input = scanner.nextLine().trim();

            if ("exit".equalsIgnoreCase(input)) {
                break;
            }

            if (input.isEmpty()) {
                continue;
            }

            // 首次输入时创建会话
            if (sessionId == null) {
                sessionId = chatService.createSession(null).id;
            }

            final int[] lastAnswerLen = {0};
            Iterator<AgentResponse> iterator = chatService.sendMessage(sessionId, input);
            while (iterator.hasNext()) {
                handleResponse(iterator.next(), lastAnswerLen);
            }
            System.out.println();
        }
    }
    
    private void handleResponse(AgentResponse response, int[] lastAnswerLen) {
        if ("step".equals(response.getResponseType())) {
            if (response.getSteps() != null && !response.getSteps().isEmpty()) {
                AgentStep step = response.getSteps().get(0);
                switch (step.getStepType()) {
                    case THOUGHT:
                        System.out.println("\n[思考] " + extractThought(step.getContent()));
                        break;
                    case ACTION:
                        System.out.println("\n[行动] 调用工具: " + step.getToolName());
                        break;
                    case OBSERVATION:
                        // 工具结果通常很长，不打印具体内容
                        break;
                    case ERROR:
                        System.out.println("\n[错误] " + step.getContent());
                        break;
                    default:
                        break;
                }
            }
        } else if ("answer".equals(response.getResponseType())) {
            String answer = response.getAnswer();
            if (answer != null && answer.length() > lastAnswerLen[0]) {
                System.out.print(answer.substring(lastAnswerLen[0]));
                lastAnswerLen[0] = answer.length();
            }
        }
    }
    
    /**
     * 从JSON响应中提取thought字段
     */
    private static String extractThought(String content) {
        if (content == null || content.isEmpty()) {
            return "";
        }
        try {
            JSONObject obj = JSONObject.parseObject(content);
            if (obj.containsKey("thought") && obj.get("thought") != null) {
                return obj.getString("thought");
            }
        } catch (Exception e) {
            // JSON解析失败，返回原始内容
        }
        return content.trim();
    }
}
