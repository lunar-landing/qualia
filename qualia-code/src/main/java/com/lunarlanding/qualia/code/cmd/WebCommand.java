package com.lunarlanding.qualia.code.cmd;

import com.lunarlanding.qualia.code.WebApplication;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * 启动 Web 界面命令
 */
@Command(name = "web", description = "启动 Web 界面，提供可视化配置和问答")
public class WebCommand implements Callable<Integer> {

    @Option(names = {"-w", "--workspace"}, description = "工作区路径（默认当前目录）")
    private Path workspace;

    @Option(names = {"-p", "--port"}, description = "服务端口（默认 8080）")
    private int port = 8080;

    @Override
    public Integer call() {
        try {
            Path workspacePath = workspace != null ? workspace : Path.of(System.getProperty("user.dir"));
            
            System.out.println("正在启动 Web 服务...");
            System.out.println("Workspace: " + workspacePath.toAbsolutePath());
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
