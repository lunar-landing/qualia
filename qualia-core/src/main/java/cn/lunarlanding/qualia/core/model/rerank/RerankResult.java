package cn.lunarlanding.qualia.core.model.rerank;

/**
 * 重排结果项
 */
public class RerankResult {
    private Integer index;
    private Double score;
    private String document;
    public RerankResult() {}

    public RerankResult(Integer index, Double score) {
        this.index = index;
        this.score = score;
    }

    public RerankResult(Integer index, Double score, String document) {
        this.index = index;
        this.document = document;
        this.score = score;
    }

    public Integer getIndex() {
        return index;
    }

    public void setIndex(Integer index) {
        this.index = index;
    }

    public String getDocument() {
        return document;
    }

    public void setDocument(String document) {
        this.document = document;
    }

    public Double getScore() {
        return score;
    }

    public void setScore(Double score) {
        this.score = score;
    }

    @Override
    public String toString() {
        return "RerankResult{" + "index=" + index + ", score=" + score + ", document='" + document + '\'' + '}';
    }
}
