<div align="center">

# Qualia

**エンタープライズ向け Java AI エージェントフレームワーク**

*ReAct パターンと MCP ツールチェーン統合により、LLM 駆動のインテリジェントアプリケーションを迅速に構築*

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-17+-orange.svg)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-green.svg)](https://spring.io/projects/spring-boot)
[![Maven](https://img.shields.io/badge/Maven-3.8+-C71A36.svg)](https://maven.apache.org/)
[![MCP](https://img.shields.io/badge/MCP-Protocol-purple.svg)](https://modelcontextprotocol.io/)

[クイックスタート](#-クイックスタート) · [ショーケース](#ショーケース) · [リリース](#-リリース)

[English](README.md) | [简体中文](README.zh-CN.md) | **日本語** | [한국어](README.ko.md)

</div>

---

## 概要

Qualia は、LLM ベースのエージェントアプリケーション構築のための軽量でモジュラーな Java フレームワークです。マルチモデルアクセス、ツール呼び出し、ナレッジベース検索、スキル拡張機能を備えた完全なエージェント開発フレームワークを提供し、エンタープライズ AI アシスタント、インテリジェントカスタマーサービス、コードアシスタントなどの構築に適しています。

ReAct（Reasoning + Acting）パラダイムに基づき、思考-行動-観察ループによる複雑なタスク分解をサポート。統一インターフェースで DashScope、OpenAI、Claude などの主要 LLM に適応。アノテーション駆動 + MCP プロトコル統合のツールシステムにより、ツール機能の拡張が容易。JSON/MySQL ベースの会話メモリ管理は、スライディングウィンドウとサマリー圧縮をサポート。再利用可能なプロンプトテンプレートとスクリプト実行のスキルシステムは、段階的な読み込みをサポートします。

## モジュール

```
qualia/
├── qualia-core/          # コアモジュール
├── qualia-code/          # Code モジュール（Web サービス含む）
├── qualia-code-desktop/  # Code デスクトップアプリ
├── qualia-claw/          # Claw モジュール（Web サービス含む）
├── qualia-claw-desktop/  # Claw デスクトップアプリ
└── qualia-docs/          # プロジェクトドキュメント
```

**Qualia-core** フレームワークコアモジュール。エージェントコアアーキテクチャ（ReActAgent、HarnessAgent）、モデルプロトコル抽象レイヤー（ChatModel、EmbeddingModel、RerankModel）、ツールシステムと MCP 統合、会話メモリ管理、RAG 検索パイプライン、スキルシステムを提供します。

**Qualia-code** Qualia フレームワーク上に構築された AI コーディングアシスタント製品。完全な Web IDE エクスペリエンスを提供し、マルチセッション管理とワークスペース切り替え、コード生成と分析、ファイル読み書きと検索、ターミナルコマンド実行、ツール呼び出しの可視化、MCP サーバー管理、マルチモデル設定と動的切り替え、ストリーミングレスポンスとリアルタイムインタラクションをサポート。デスクトップアプリと Web の2つのデプロイモードをサポートします。

**Qualia-code-desktop** SWT ベースのデスクトップアプリケーションモジュール。内蔵ブラウザで Web IDE UI を読み込み、ネイティブデスクトップエクスペリエンスを提供。ウィンドウ状態の永続化（サイズ、位置、最大化状態）、システムタイトルバーのテーマ連動（ダーク/ライト自動適応）、クロスプラットフォームサポート（Windows/macOS）を実現します。

**Qualia-claw** Qualia フレームワーク上に構築されたマルチエージェント協調製品。各エージェントは独立したワークステーション（システム管理ワークスペース + 独立会話メモリ）を持ち、ロールペルソナ設定、マルチエージェント並行会話、グローバルスキルと MCP サーバーのエージェント別ホワイトリスト参照、ワークスペースファイルの参照とプレビュー、セッション別トークン使用量統計をサポート。設定はユーザーディレクトリに製品別に集約され、デスクトップアプリと Web の両方のデプロイモードをサポートします。

**Qualia-claw-desktop** Claw デスクトップアプリケーションモジュール。Qualia-code-desktop と同じアーキテクチャ（SWT + システム WebView）。ロックファイル、ウィンドウ状態、クラッシュログは製品ディレクトリに分離され、Qualia Code デスクトップアプリと同時に実行できます。

## 🚀 クイックスタート

### インストール

qualia-core は GitHub Packages で公開されています。依存関係の使用にはリポジトリ認証とリポジトリ設定が必要です：

#### 1. 認証設定

Maven の `settings.xml`（またはプロジェクトレベルの `settings.xml`）に GitHub 認証情報を追加：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<settings>
    <servers>
        <server>
            <id>github</id>
            <username>your-github-username</username>
            <password>your-github-token (read:packages スコープが必要)</password>
        </server>
    </servers>
</settings>
```

#### 2. リポジトリと依存関係の追加

プロジェクトの `pom.xml` に以下を追加：

```xml
<repositories>
    <repository>
        <id>github</id>
        <url>https://maven.pkg.github.com/lunar-landing/qualia</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.lunarlanding</groupId>
    <artifactId>qualia-core</artifactId>
    <version>0.1.0</version>
</dependency>
```

### 5分間チュートリアル

```java
// 1. モデルとメモリの初期化
ChatModel model = new DashscopeChatModel("your-api-key", "qwen-turbo");
Memory memory = new InMemoryMemory();

// 2. エージェントの作成とツール登録
ReActAgent agent = new ReActAgent(model, memory);
agent.setSystemPrompt("あなたはプロフェッショナルなインテリジェントアシスタントです。");

// ツール登録（アノテーション駆動）
agent.registerToolsFrom(new MyTools());

// 3. 会話実行
AgentResponse response = agent.call("session-1", "最新のAIの進捗を検索してください");
System.out.println(response.getAnswer());

// 4. ストリーミング呼び出し
Flux<AgentResponse> stream = agent.callStream("session-1", "量子コンピューティングを説明してください");
stream.subscribe(step -> System.out.println(step.getAnswer()));

// ツールクラス定義
public class MyTools {
    @AsFunctionTool(name = "search", description = "インターネット情報を検索")
    public String search(@Param("query") String query) {
        // 検索ロジック
        return "検索結果";
    }
}
```

### ショーケース

**Qualia Code** は Qualia Core フレームワーク上に構築された AI コーディングアシスタント製品で、完全な Web IDE エクスペリエンスを提供します。マルチセッション管理とワークスペース切り替え、コード生成と分析、ファイル読み書きと検索、ターミナルコマンド実行をサポートし、ツール呼び出しプロセスを可視化します。MCP サーバー管理、マルチモデル設定と動的切り替え、グローバルおよびワークスペースレベルのスキル拡張も提供し、デスクトップアプリと Web の2つのデプロイモードをサポートします。

![img.png](docs/images/img.png)

![img_1.png](docs/images/img_1.png)

![img_2.png](docs/images/img_2.png)

**Qualia Claw** は Qualia Core フレームワーク上に構築されたマルチエージェント協調製品です。各エージェントは独立したワークステーション（システム管理ワークスペース + 独立会話メモリ）を持ち、ロールペルソナ設定とマルチエージェント並行会話をサポートします。グローバルスキルと MCP サーバーはエージェント別ホワイトリストで参照され、ワークスペースファイルの参照とプレビュー、セッション別トークン使用量統計が組み込まれています。設定はユーザーディレクトリに製品別に集約され、デスクトップアプリと Web の両方のデプロイモードをサポートします。

![img_3.png](docs/images/img-3.png)

![img_4.png](docs/images/img-4.png)

![img_5.png](docs/images/img-5.png)

## 📦 リリース

### GitHub Packages

このプロジェクトは GitHub Packages 経由で Maven パッケージを公開しています。認証とリポジトリ設定については[クイックスタート・インストール](#インストール)を参照してください。

```bash
# qualia-core を公開
mvn clean deploy -pl qualia-core -DskipTests -s settings.xml

# すべてのモジュールを公開
mvn clean deploy -DskipTests -s settings.xml
```

### デスクトップアプリのビルド

```powershell
# 実行可能 jar のビルド
mvn -pl qualia-code-desktop -am clean package -DskipTests
mvn -pl qualia-claw-desktop -am clean package -DskipTests

# Windows アプリケーションとしてパッケージ（ポータブル、インストーラー不要）
.\qualia-code-desktop\packaging\package-win.ps1
.\qualia-claw-desktop\packaging\package-win.ps1
```

成果物：`qualia-code-desktop\target\dist\<version>\Qualia Code\Qualia Code.exe` および `qualia-claw-desktop\target\dist\<version>\Qualia Claw\Qualia Claw.exe`

## 📄 ライセンス

このプロジェクトは [Apache License 2.0](LICENSE) の下でライセンスされています。
