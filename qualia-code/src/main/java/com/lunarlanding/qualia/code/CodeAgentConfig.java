package com.lunarlanding.qualia.code;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 配置管理类
 */
public class CodeAgentConfig {
    
    /** 环境变量正则：${ENV_VAR} */
    private static final Pattern ENV_VAR_PATTERN = Pattern.compile("\\$\\{([a-zA-Z_][a-zA-Z0-9_]*)}");
    
    /** 全局配置目录 */
    private static final Path GLOBAL_CONFIG_DIR = Path.of(System.getProperty("user.home"), ".qualia");
    
    /** 全局配置文件 */
    private static final Path GLOBAL_CONFIG_FILE = GLOBAL_CONFIG_DIR.resolve("qualia-code.json");
    
    private final Path workspacePath;
    private final String defaultModel;
    private final List<CodeAgentModelConfig> models;
    private final List<CodeAgentMcpServerConfig> mcpServers;
    private final List<String> disabledSkills;
    private final List<String> disabledTools;
    
    public CodeAgentConfig(Path workspacePath, String defaultModel, List<CodeAgentModelConfig> models, List<CodeAgentMcpServerConfig> mcpServers, List<String> disabledSkills, List<String> disabledTools) {
        this.workspacePath = workspacePath;
        this.defaultModel = defaultModel;
        this.models = models != null ? models : new ArrayList<>();
        this.mcpServers = mcpServers != null ? mcpServers : new ArrayList<>();
        this.disabledSkills = disabledSkills != null ? disabledSkills : new ArrayList<>();
        this.disabledTools = disabledTools != null ? disabledTools : new ArrayList<>();
    }
    
    /**
     * 获取当前模型配置
     */
    public CodeAgentModelConfig getCurrentModel() {
        if (models.isEmpty()) {
            return null;
        }
        if (defaultModel != null && !defaultModel.isEmpty()) {
            for (CodeAgentModelConfig model : models) {
                if (defaultModel.equals(model.getName())) {
                    return model;
                }
            }
        }
        return models.get(0);
    }
    
    /**
     * 获取当前模型的 API Key
     * 
     * @throws IllegalStateException 配置文件不存在或未配置apiKey时抛出
     */
    public String getApiKey() {
        CodeAgentModelConfig model = getCurrentModel();
        if (model == null) {
            throw new IllegalStateException("配置文件不存在或未配置模型，请先运行 init 命令初始化配置");
        }
        if (model.getApiKey() == null || model.getApiKey().isEmpty()) {
            throw new IllegalStateException("模型 " + model.getName() + " 未配置 apiKey");
        }
        return model.getApiKey();
    }
    
    public Path getWorkspacePath() {
        return workspacePath;
    }
    
    public String getDefaultModel() {
        return defaultModel;
    }
    
    public List<CodeAgentModelConfig> getModels() {
        return models;
    }
    
    public List<CodeAgentMcpServerConfig> getMcpServers() {
        return mcpServers;
    }
    
    public List<String> getDisabledSkills() {
        return disabledSkills;
    }
    
    public List<String> getDisabledTools() {
        return disabledTools;
    }
    
    /**
     * 按模型名称获取配置
     * 
     * @param modelName 模型名称
     * @return 模型配置，如果不存在返回null
     */
    public CodeAgentModelConfig getModelByName(String modelName) {
        if (modelName == null || modelName.isEmpty()) {
            return null;
        }
        return models.stream()
                .filter(m -> modelName.equals(m.getName()))
                .findFirst()
                .orElse(null);
    }
    
    /**
     * 获取所有模型名称列表
     * 
     * @return 模型名称列表
     */
    public List<String> getModelNames() {
        return models.stream()
                .map(CodeAgentModelConfig::getName)
                .collect(Collectors.toList());
    }
    
    // ========== 配置加载方法 ==========
    
    /**
     * 从全局配置文件加载配置
     * 
     * @param workspacePath 工作区路径
     * @return 配置对象
     */
    public static CodeAgentConfig load(Path workspacePath) {
        // 初始化工作区目录
        initWorkspace(workspacePath);
        
        // 加载全局配置
        JSONObject config = loadJsonFile(GLOBAL_CONFIG_FILE);
        
        // 解析配置
        String defaultModel = config.getString("defaultModel");
        List<CodeAgentModelConfig> models = parseModels(config.getJSONArray("models"));
        List<CodeAgentMcpServerConfig> mcpServers = parseMcpServers(config.getJSONArray("mcpServers"));
        List<String> disabledSkills = parseDisabledSkills(config.getJSONArray("disabledSkills"));
        List<String> disabledTools = parseDisabledSkills(config.getJSONArray("disabledTools"));
        
        // 解析环境变量
        resolveEnvVars(models, mcpServers);
        
        return new CodeAgentConfig(workspacePath, defaultModel, models, mcpServers, disabledSkills, disabledTools);
    }
    
    /**
     * 初始化工作区目录
     */
    private static void initWorkspace(Path workspacePath) {
        // 启动时可能尚未选择工作区（前端强制选择流程），此时不做目录初始化
        if (workspacePath == null) {
            return;
        }
        Path qualiaDir = workspacePath.resolve(".qualia");
        if (!Files.exists(qualiaDir)) {
            try {
                Files.createDirectories(qualiaDir.resolve("memory"));
                Files.createDirectories(qualiaDir.resolve("skills"));
                System.out.println("已初始化工作区: " + qualiaDir);
            } catch (IOException e) {
                System.err.println("初始化工作区失败: " + e.getMessage());
            }
        }
    }
    
    /**
     * 加载 JSON 文件
     */
    private static JSONObject loadJsonFile(Path filePath) {
        if (!Files.exists(filePath)) {
            return new JSONObject();
        }
        try {
            String content = Files.readString(filePath, StandardCharsets.UTF_8);
            return JSON.parseObject(content);
        } catch (IOException e) {
            System.err.println("读取配置文件失败: " + filePath + " - " + e.getMessage());
            return new JSONObject();
        }
    }
    

    
    /**
     * 解析模型配置列表
     */
    private static List<CodeAgentModelConfig> parseModels(JSONArray modelsArray) {
        List<CodeAgentModelConfig> models = new ArrayList<>();
        if (modelsArray == null) {
            return models;
        }
        for (int i = 0; i < modelsArray.size(); i++) {
            JSONObject obj = modelsArray.getJSONObject(i);
            CodeAgentModelConfig model = new CodeAgentModelConfig();
            model.setName(obj.getString("name"));
            model.setProvider(obj.getString("provider"));
            model.setType(obj.getString("type"));
            model.setApiKey(obj.getString("apiKey"));
            model.setModel(obj.getString("model"));
            model.setBaseUrl(obj.getString("baseUrl"));
            models.add(model);
        }
        return models;
    }
    
    /**
     * 解析 MCP 服务器配置列表
     */
    private static List<CodeAgentMcpServerConfig> parseMcpServers(JSONArray mcpArray) {
        List<CodeAgentMcpServerConfig> servers = new ArrayList<>();
        if (mcpArray == null) {
            return servers;
        }
        for (int i = 0; i < mcpArray.size(); i++) {
            JSONObject obj = mcpArray.getJSONObject(i);
            CodeAgentMcpServerConfig server = new CodeAgentMcpServerConfig();
            server.setName(obj.getString("name"));
            server.setTransport(obj.getString("transport"));
            server.setEnabled(obj.getBooleanValue("enabled"));
            server.setUrl(obj.getString("url"));
            
            // 解析 headers
            JSONObject headersObj = obj.getJSONObject("headers");
            if (headersObj != null) {
                Map<String, String> headers = new HashMap<>();
                headersObj.forEach((key, value) -> headers.put(key, String.valueOf(value)));
                server.setHeaders(headers);
            }
            
            servers.add(server);
        }
        return servers;
    }
    
    /**
     * 解析禁用技能列表
     */
    private static List<String> parseDisabledSkills(JSONArray disabledArray) {
        List<String> disabled = new ArrayList<>();
        if (disabledArray == null) {
            return disabled;
        }
        for (int i = 0; i < disabledArray.size(); i++) {
            disabled.add(disabledArray.getString(i));
        }
        return disabled;
    }
    
    /**
     * 解析环境变量替换 ${ENV_VAR}
     */
    private static void resolveEnvVars(List<CodeAgentModelConfig> models, List<CodeAgentMcpServerConfig> mcpServers) {
        // 解析模型配置中的环境变量
        for (CodeAgentModelConfig model : models) {
            if (model.getApiKey() != null) {
                model.setApiKey(resolveEnvVar(model.getApiKey()));
            }
        }
        
        // 解析 MCP 服务器配置中的环境变量
        for (CodeAgentMcpServerConfig server : mcpServers) {
            if (server.getUrl() != null) {
                server.setUrl(resolveEnvVar(server.getUrl()));
            }
            Map<String, String> headers = server.getHeaders();
            if (headers != null) {
                Map<String, String> resolvedHeaders = new HashMap<>();
                headers.forEach((key, value) -> resolvedHeaders.put(key, resolveEnvVar(value)));
                server.setHeaders(resolvedHeaders);
            }
        }
    }
    
    /**
     * 解析单个环境变量
     */
    private static String resolveEnvVar(String value) {
        if (value == null) {
            return null;
        }
        Matcher matcher = ENV_VAR_PATTERN.matcher(value);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String envVarName = matcher.group(1);
            String envValue = System.getenv(envVarName);
            if (envValue != null) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(envValue));
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
    
    /**
     * 从环境变量加载配置（兼容旧方式）
     * 
     * @deprecated 使用 {@link #load(Path)} 代替
     */
    @Deprecated
    public static CodeAgentConfig loadFromEnvironment() {
        Path workspacePath = Path.of(System.getProperty("user.dir"));
        return new CodeAgentConfig(workspacePath, null, null, null, null, null);
    }
}
