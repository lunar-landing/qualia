package com.lunarlanding.qualia.claw.cmd;

import com.lunarlanding.qualia.claw.ClawWebApplication;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

/**
 * 启动 Web 界面命令
 */
@Command(name = "web", description = "启动 Web 界面，管理多个职能智能体并进行对话")
public class WebCommand implements Callable<Integer> {

    @Option(names = {"-p", "--port"}, description = "服务端口（默认 8080）")
    private int port = 8080;

    @Override
    public Integer call() {
        try {
            System.out.println("正在启动 Web 服务...");
            System.out.println("端口: " + port);
            System.out.println();

            // 启动 Spring Boot 应用
            ClawWebApplication.start(port);

            return 0;
        } catch (Exception e) {
            System.err.println("启动 Web 服务失败: " + e.getMessage());
            e.printStackTrace();
            return 1;
        }
    }
}
