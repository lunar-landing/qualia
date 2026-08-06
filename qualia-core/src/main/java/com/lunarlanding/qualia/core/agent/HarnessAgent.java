package com.lunarlanding.qualia.core.agent;

import com.lunarlanding.qualia.core.memory.Memory;
import com.lunarlanding.qualia.core.memory.impl.JsonMemory;
import com.lunarlanding.qualia.core.model.chat.ChatModel;
import com.lunarlanding.qualia.core.skill.Skill;
import com.lunarlanding.qualia.core.skill.loader.DirectorySkillLoader;
import com.lunarlanding.qualia.core.tool.impl.harness.Workspace;
import com.lunarlanding.qualia.core.tool.impl.file.*;
import com.lunarlanding.qualia.core.tool.impl.internet.HttpTool;
import com.lunarlanding.qualia.core.tool.impl.search.BaiduSearchTool;
import com.lunarlanding.qualia.core.tool.impl.internet.WebFetchTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * HarnessAgent - 本地开发智能体（ReActAgent 的浅封装）
 * 
 * 核心职责：
 * 1. 自动配置本地开发环境（Memory、工具、技能、提示词）
 * 2. 提供工作区抽象，管理文件系统边界
 * 3. 简化本地开发场景的使用门槛
 * 
 * 不负责：
 * - 推理循环逻辑（由 ReActAgent 实现）
 * - 工具执行逻辑（由具体工具实现）
 * - Memory 存储逻辑（由 Memory 实现）
 */
public class HarnessAgent extends ReActAgent {
    
    private static final Logger logger = LoggerFactory.getLogger(HarnessAgent.class);
    
    private final Workspace workspace;
    
    /**
     * 创建 HarnessAgent（使用 Workspace 接口）
     * 
     * @param model ChatModel 实例
     * @param workspace 工作区实例
     */
    public HarnessAgent(ChatModel model, Workspace workspace) {
        super(model);
        this.workspace = workspace;
        initializeAgent();
    }
    
    /**
     * 初始化 Agent（自动化配置）
     */
    private void initializeAgent() {

        Path rootPath = workspace.getRootPath();
        Path agentFile = workspace.getAgentFile();
        Path memoryDir = workspace.getMemoryDir();
        Path skillsDir = workspace.getSkillsDir();

        // 文件操作
        addTool(new ReadTool(rootPath));
        addTool(new GrepTool(rootPath));
        addTool(new GlobTool(rootPath));
        addTool(new ReplaceTool(rootPath));
        addTool(new WriteTool(rootPath));
        addTool(new DeleteTool(rootPath));
        addTool(new BashTool(rootPath));

        // 网络操作
        addTool(new WebFetchTool());
        addTool(new BaiduSearchTool());
        addTool(new HttpTool());

        this.setMemory(new JsonMemory(memoryDir));

        if (agentFile != null && Files.exists(agentFile)) {
            try {
                String prompt = Files.readString(agentFile);
                setSystemPrompt(prompt);
            } catch (IOException e) {
                logger.error("读取 AGENT.md 失败");
            }
        }

        if (skillsDir != null && Files.exists(skillsDir)) {
            DirectorySkillLoader loader = new DirectorySkillLoader(skillsDir);
            List<Skill> skills = loader.loadAll();
            skills.forEach(this::addSkill);
        }
    }
    
    /**
     * 获取工作区
     */
    public Workspace getWorkspace() {
        return workspace;
    }
    
    /**
     * 获取工作区根路径
     */
    public Path getWorkspaceRoot() {
        return workspace.getRootPath();
    }
}
