<div align="center">

# Qualia

**엔터프라이즈급 Java AI 에이전트 프레임워크**

*ReAct 패턴과 MCP 도구 체인 통합으로 LLM 기반 지능형 애플리케이션을 신속하게 구축*

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-17+-orange.svg)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-green.svg)](https://spring.io/projects/spring-boot)
[![Maven](https://img.shields.io/badge/Maven-3.8+-C71A36.svg)](https://maven.apache.org/)
[![MCP](https://img.shields.io/badge/MCP-Protocol-purple.svg)](https://modelcontextprotocol.io/)

[빠른 시작](#-빠른-시작) · [쇼케이스](#쇼케이스) · [릴리스](#-릴리스)

[English](README.md) | [简体中文](README.zh-CN.md) | [日本語](README.ja.md) | **한국어**

</div>

---

## 개요

Qualia는 LLM 기반 에이전트 애플리케이션 구축을 위한 경량 모듈형 Java 프레임워크입니다. 멀티모델 접근, 도구 호출, 지식 베이스 검색, 스킬 확장 기능을 갖춘 완전한 에이전트 개발 프레임워크를 제공하며, 엔터프라이즈 AI 어시스턴트, 지능형 고객 서비스, 코드 어시스턴트 등의 구축에 적합합니다.

ReAct(Reasoning + Acting) 패러다임을 기반으로 사고-행동-관찰 루프를 통한 복잡한 작업 분해를 지원합니다. 통합 인터페이스로 DashScope, OpenAI, Claude 등 주요 LLM에 적응하며, 어노테이션 기반 + MCP 프로토콜 통합 도구 시스템으로 도구 기능 확장이 용이합니다. JSON/MySQL 기반 대화 메모리 관리는 슬라이딩 윈도우와 요약 압축을 지원하며, 재사용 가능한 프롬프트 템플릿과 스크립트 실행의 스킬 시스템은 점진적 로딩을 지원합니다.

## 모듈

```
qualia/
├── qualia-core/          # 코어 모듈
├── qualia-code/          # Code 모듈 (Web 서비스 포함)
├── qualia-code-desktop/  # Code 데스크톱 앱
├── qualia-claw/          # Claw 모듈 (Web 서비스 포함)
├── qualia-claw-desktop/  # Claw 데스크톱 앱
└── qualia-docs/          # 프로젝트 문서
```

**Qualia-core** 프레임워크 코어 모듈. 에이전트 코어 아키텍처(ReActAgent, HarnessAgent), 모델 프로토콜 추상 레이어(ChatModel, EmbeddingModel, RerankModel), 도구 시스템과 MCP 통합, 대화 메모리 관리, RAG 검색 파이프라인, 스킬 시스템을 제공합니다.

**Qualia-code** Qualia 프레임워크 위에 구축된 AI 코딩 어시스턴트 제품. 완전한 Web IDE 경험을 제공하며, 멀티세션 관리와 워크스페이스 전환, 코드 생성과 분석, 파일 읽기/쓰기와 검색, 터미널 명령 실행, 도구 호출 시각화, MCP 서버 관리, 멀티모델 설정과 동적 전환, 스트리밍 응답과 실시간 상호작용을 지원합니다. 데스크톱 앱과 Web 두 가지 배포 모드를 지원합니다.

**Qualia-code-desktop** SWT 기반 데스크톱 애플리케이션 모듈. 내장 브라우저로 Web IDE UI를 로드하며 네이티브 데스크톱 경험을 제공합니다. 윈도우 상태 영속화(크기, 위치, 최대화 상태), 시스템 제목 표시줄 테마 연동(다크/라이트 자동 적응), 크로스 플랫폼 지원(Windows/macOS)을 구현합니다.

**Qualia-claw** Qualia 프레임워크 위에 구축된 멀티에이전트 협업 제품. 각 에이전트는 독립적인 워크스테이션(시스템 관리 워크스페이스 + 독립 대화 메모리)을 가지며, 롤 페르소나 설정, 멀티에이전트 병렬 대화, 글로벌 스킬과 MCP 서버의 에이전트별 화이트리스트 참조, 워크스페이스 파일 탐색과 미리보기, 세션별 토큰 사용량 통계를 지원합니다. 설정은 사용자 디렉토리에 제품별로 집약되며, 데스크톱 앱과 Web 양쪽 배포 모드를 지원합니다.

**Qualia-claw-desktop** Claw 데스크톱 애플리케이션 모듈. Qualia-code-desktop과 동일한 아키텍처(SWT + 시스템 WebView). 잠금 파일, 윈도우 상태, 크래시 로그는 제품 디렉토리에 분리되어 Qualia Code 데스크톱 앱과 동시에 실행할 수 있습니다.

## 🚀 빠른 시작

### 설치

qualia-core는 GitHub Packages를 통해发布됩니다. 의존성 사용에는 리포지토리 인증과 리포지토리 설정이 필요합니다:

#### 1. 인증 설정

Maven의 `settings.xml`(또는 프로젝트 수준의 `settings.xml`)에 GitHub 인증 정보를 추가:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<settings>
    <servers>
        <server>
            <id>github</id>
            <username>your-github-username</username>
            <password>your-github-token (read:packages 스코프 필요)</password>
        </server>
    </servers>
</settings>
```

#### 2. 리포지토리와 의존성 추가

프로젝트 `pom.xml`에 다음을 추가:

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

### 5분 튜토리얼

```java
// 1. 모델과 메모리 초기화
ChatModel model = new DashscopeChatModel("your-api-key", "qwen-turbo");
Memory memory = new InMemoryMemory();

// 2. 에이전트 생성과 도구 등록
ReActAgent agent = new ReActAgent(model, memory);
agent.setSystemPrompt("당신은 전문적인 지능형 어시스턴트입니다.");

// 도구 등록 (어노테이션 기반)
agent.registerToolsFrom(new MyTools());

// 3. 대화 실행
AgentResponse response = agent.call("session-1", "최신 AI 진전을 검색해 주세요");
System.out.println(response.getAnswer());

// 4. 스트리밍 호출
Flux<AgentResponse> stream = agent.callStream("session-1", "양자 컴퓨팅을 설명해 주세요");
stream.subscribe(step -> System.out.println(step.getAnswer()));

// 도구 클래스 정의
public class MyTools {
    @AsFunctionTool(name = "search", description = "인터넷 정보 검색")
    public String search(@Param("query") String query) {
        // 검색 로직
        return "검색 결과";
    }
}
```

### 쇼케이스

**Qualia Code**는 Qualia Core 프레임워크 위에 구축된 AI 코딩 어시스턴트 제품으로, 완전한 Web IDE 경험을 제공합니다. 멀티세션 관리와 워크스페이스 전환, 코드 생성과 분석, 파일 읽기/쓰기와 검색, 터미널 명령 실행을 지원하며, 도구 호출 프로세스를 시각화합니다. MCP 서버 관리, 멀티모델 설정과 동적 전환, 글로벌 및 워크스페이스 수준의 스킬 확장도 제공하며, 데스크톱 앱과 Web 두 가지 배포 모드를 지원합니다.

![img.png](docs/images/img.png)

![img_1.png](docs/images/img_1.png)

![img_2.png](docs/images/img_2.png)

**Qualia Claw**는 Qualia Core 프레임워크 위에 구축된 멀티에이전트 협업 제품입니다. 각 에이전트는 독립적인 워크스테이션(시스템 관리 워크스페이스 + 독립 대화 메모리)을 가지며, 롤 페르소나 설정과 멀티에이전트 병렬 대화를 지원합니다. 글로벌 스킬과 MCP 서버는 에이전트별 화이트리스트로 참조되며, 워크스페이스 파일 탐색과 미리보기, 세션별 토큰 사용량 통계가 내장되어 있습니다. 설정은 사용자 디렉토리에 제품별로 집약되며, 데스크톱 앱과 Web 양쪽 배포 모드를 지원합니다.

![img_3.png](docs/images/img-3.png)

![img_4.png](docs/images/img-4.png)

![img_5.png](docs/images/img-5.png)

## 📦 릴리스

### GitHub Packages

이 프로젝트는 GitHub Packages를 통해 Maven 패키지를发布합니다. 인증과 리포지토리 설정은 [빠른 시작 · 설치](#설치)를 참조하세요.

```bash
# qualia-core发布
mvn clean deploy -pl qualia-core -DskipTests -s settings.xml

# 모든 모듈发布
mvn clean deploy -DskipTests -s settings.xml
```

### 데스크톱 앱 빌드

```powershell
# 실행 가능한 jar 빌드
mvn -pl qualia-code-desktop -am clean package -DskipTests
mvn -pl qualia-claw-desktop -am clean package -DskipTests

# Windows 애플리케이션으로 패키징 (포터블, 설치 프로그램 불필요)
.\qualia-code-desktop\packaging\package-win.ps1
.\qualia-claw-desktop\packaging\package-win.ps1
```

산출물: `qualia-code-desktop\target\dist\<version>\Qualia Code\Qualia Code.exe` 및 `qualia-claw-desktop\target\dist\<version>\Qualia Claw\Qualia Claw.exe`

## 📄 라이선스

이 프로젝트는 [Apache License 2.0](LICENSE)에 따라 라이선스가 부여됩니다.
