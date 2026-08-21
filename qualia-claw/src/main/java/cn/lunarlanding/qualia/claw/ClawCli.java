package cn.lunarlanding.qualia.claw;

import cn.lunarlanding.qualia.claw.cmd.InitCommand;
import cn.lunarlanding.qualia.claw.cmd.WebCommand;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

/**
 * Qualia Claw CLI - 交互层
 *
 * 多智能体职能办公：每个智能体拥有独立工作区，通过 Web 界面管理，
 * 因此不提供单会话 CLI 交互模式（qualia-code 的 --cli 在此无意义）
 */
@Command(name = "qualia-claw", mixinStandardHelpOptions = true, version = "1.0.0", description = "Qualia Claw - 多智能体职能办公",
        subcommands = {InitCommand.class, WebCommand.class})
public class ClawCli implements Callable<Integer> {

    @Option(names = {"-p", "--port"}, description = "Web服务端口（默认 8080）")
    private int port = 8090;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new ClawCli()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() {
        try {
            // 默认 Web 模式：智能体在页面中创建与管理
            ClawWebApplication.start(port);
            return 0;
        } catch (Exception e) {
            System.err.println("错误: " + e.getMessage());
            return 1;
        }
    }
}
