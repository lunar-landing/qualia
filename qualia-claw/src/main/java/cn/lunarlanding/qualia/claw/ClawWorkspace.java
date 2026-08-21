package cn.lunarlanding.qualia.claw;

import cn.lunarlanding.qualia.core.tool.impl.harness.LocalWorkspace;

import java.nio.file.Path;

/**
 * Claw 智能体工作区
 *
 * 与 LocalWorkspace 的差异：
 * 1. 会话记忆不落在工作区内（.qualia/memory），而是跟随智能体身份
 *    存放到 ~/.qualia/claw/agents/{agentId}/memory，清空工作区不会丢失历史会话
 * 2. 禁用工作区级 AGENT.md 与 skills：人设由 definition.role 接管，
 *    技能统一由产品级全局目录（~/.qualia/claw/skills）+ 智能体白名单管理，
 *    工作区只承载智能体的产出物
 */
public class ClawWorkspace extends LocalWorkspace {

    private final Path memoryDir;

    public ClawWorkspace(Path rootPath, String agentId) {
        super(rootPath);
        this.memoryDir = memoryDirFor(agentId);
    }

    /**
     * 智能体记忆目录：~/.qualia/claw/agents/{agentId}/memory（按稳定 id 隔离，改名不迁移）
     */
    public static Path memoryDirFor(String agentId) {
        return ClawConfig.GLOBAL_CONFIG_DIR.resolve("agents").resolve(agentId).resolve("memory");
    }

    @Override
    public Path getMemoryDir() {
        return memoryDir;
    }

    /**
     * 禁用工作区级技能（技能统一由全局目录 + 智能体白名单管理）
     */
    @Override
    public Path getSkillsDir() {
        return null;
    }

    /**
     * 禁用工作区级提示词（人设由 definition.role 接管）
     */
    @Override
    public Path getAgentFile() {
        return null;
    }
}
