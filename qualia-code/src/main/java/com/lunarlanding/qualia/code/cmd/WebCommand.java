package com.lunarlanding.qualia.code.cmd;

import com.lunarlanding.qualia.code.WebApplication;
import com.lunarlanding.qualia.code.service.WorkspaceHistory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * 启动 Web 界面命令
 */
@Command(name = "web", description = "启动 Web 界面，提供可视化配置和问答")
public class WebCommand implements Callable<Integer> {

    @Option(names = {"-w", "--workspace"}, description = "工作区路径（默认复用最近打开的工作区，无则在页面中选择）")
    private Path workspace;

    @Option(names = {"-p", "--port"}, description = "服务端口（默认 8080）")
    private int port = 8080;

    @Override
    public Integer call() {
        try {
            // 未指定 -w 时静默复用最近仍存在的历史工作区；无历史则空启动，由页面强制选择
            Path workspacePath = workspace != null ? workspace : WorkspaceHistory.latestValid();

            System.out.println("正在启动 Web 服务...");
            System.out.println("Workspace: " + (workspacePath != null
                    ? workspacePath.toAbsolutePath().toString()
                    : "未指定（请在页面中选择）"));
            System.out.println("端口: " + port);
            System.out.println();
            
            // 启动 Spring Boot 应用
            WebApplication.start(workspacePath, port);
            
            return 0;
        } catch (Exception e) {
            System.err.println("启动 Web 服务失败: " + e.getMessage());
            e.printStackTrace();
            return 1;
        }
    }
}
