package com.cornerstone.core.system.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.cornerstone.core.system.dto.SchemaDictionaryView;
import com.cornerstone.core.system.dto.SchemaTableView;
import com.cornerstone.core.system.lineage.DocumentEdge;
import com.cornerstone.core.system.lineage.DocumentLineageContributor;
import com.cornerstone.core.system.lineage.DocumentNode;
import com.cornerstone.core.system.schema.TableReference;
import com.cornerstone.core.system.service.SchemaDictionaryService;
import com.cornerstone.core.system.service.TableReferenceService;
import java.util.List;
import org.junit.jupiter.api.Test;

class DocumentLineageServiceImplTest {

    @Test
    void aggregatesContributorsAndSortsByModuleThenName() {
        DocumentLineageServiceImpl service = service(List.of(
            contributor("education", List.of(node("education.b", "乙单据", true)),
                List.of(edge("education.b", "education.b"))),
            contributor("core", List.of(node("core.a", "甲单据", true)), List.of())
        ));

        var view = service.lineage();

        assertThat(view.documentCount()).isEqualTo(2);
        assertThat(view.edgeCount()).isEqualTo(1);
        assertThat(view.documents()).extracting("code").containsExactly("core.a", "education.b");
        assertThat(view.documents().get(0).moduleKey()).isEqualTo("core");
        assertThat(view.note()).contains("不是某一次请求的实际数据流");
    }

    @Test
    void duplicateCodeKeepsFirstAndDropsOrphanEdges() {
        DocumentLineageServiceImpl service = service(List.of(
            contributor("first", List.of(node("dup", "先登记", true)), List.of()),
            contributor("second",
                List.of(node("dup", "后登记", true), node("other", "其他", true)),
                List.of(edge("other", "missing"), edge("dup", "other")))
        ));

        var view = service.lineage();

        assertThat(view.documents()).extracting("code").containsExactly("dup", "other");
        assertThat(view.documents().get(0).name()).isEqualTo("先登记");
        // other → missing 被丢弃；dup → other 保留
        assertThat(view.edgeCount()).isEqualTo(1);
        assertThat(view.edges().get(0).from()).isEqualTo("dup");
    }

    @Test
    void pendingCountCoversNodesAndEdges() {
        DocumentLineageServiceImpl service = service(List.of(
            contributor("education",
                List.of(node("a", "甲", false), node("b", "乙", true)),
                List.of(edge("a", "b", false)))
        ));

        var view = service.lineage();

        assertThat(view.pendingCount()).isEqualTo(2);
    }

    @Test
    void incompleteNodesAreSkippedInsteadOfBreakingThePage() {
        DocumentLineageServiceImpl service = service(List.of(
            contributor("education",
                List.of(new DocumentNode("", "缺编码", "t_demo", List.of(), null, null, null, true, null),
                    node("ok", "正常", true)),
                List.of())
        ));

        var view = service.lineage();

        assertThat(view.documents()).extracting("code").containsExactly("ok");
    }

    @Test
    void tablesWithoutDeclaredOwnerBecomeStandaloneNodes() {
        DocumentLineageServiceImpl service = new DocumentLineageServiceImpl(
            List.of(contributor("education", List.of(node("demo", "演示单据", true)), List.of())),
            dictionary(table("t_demo", "演示表", "education"), table("t_orphan", "孤立表", "other")),
            noReferences());

        var view = service.lineage();

        assertThat(view.documents()).extracting("code").containsExactly("demo", "table:t_orphan");
        assertThat(view.documents().get(1).name()).isEqualTo("孤立表");
        assertThat(view.documents().get(1).declared()).isFalse();
    }

    @Test
    void relatedTablesAreAlreadyCoveredByTheirDocument() {
        DocumentLineageContributor contributor = contributor("education",
            List.of(new DocumentNode("demo", "演示单据", "t_demo", List.of("t_child"),
                null, null, "说明", true, null)),
            List.of());
        DocumentLineageServiceImpl service = new DocumentLineageServiceImpl(
            List.of(contributor),
            dictionary(table("t_demo", "演示表", "education"), table("t_child", "附属表", "education")),
            noReferences());

        assertThat(service.lineage().documentCount()).isEqualTo(1);
    }

    @Test
    void foreignKeysBecomeReferenceEdgesBetweenNodes() {
        DocumentLineageServiceImpl service = new DocumentLineageServiceImpl(
            List.of(contributor("education", List.of(node("demo", "演示单据", true)), List.of())),
            dictionary(table("t_demo", "演示表", "education"), table("t_parent", "上级表", "other")),
            () -> List.of(new TableReference("t_demo", "parent_id", "t_parent", "id", "fk_demo_parent")));

        var view = service.lineage();

        assertThat(view.edgeCount()).isEqualTo(1);
        var edge = view.edges().get(0);
        assertThat(edge.from()).isEqualTo("demo");
        assertThat(edge.to()).isEqualTo("table:t_parent");
        assertThat(edge.relation()).isEqualTo("引用");
        assertThat(edge.declared()).isFalse();
        assertThat(edge.linkFields()).containsExactly("t_demo.parent_id → t_parent.id");
    }

    @Test
    void dictionaryFailureKeepsDeclaredNodes() {
        SchemaDictionaryService broken = () -> {
            throw new IllegalStateException("数据库不可用");
        };
        DocumentLineageServiceImpl service = new DocumentLineageServiceImpl(
            List.of(contributor("education", List.of(node("demo", "演示单据", true)), List.of())),
            broken,
            noReferences());

        var view = service.lineage();

        assertThat(view.documentCount()).isEqualTo(1);
        assertThat(view.documents().get(0).code()).isEqualTo("demo");
    }

    private static DocumentLineageServiceImpl service(List<DocumentLineageContributor> contributors) {
        return new DocumentLineageServiceImpl(contributors, dictionary(), noReferences());
    }

    private static SchemaDictionaryService dictionary(SchemaTableView... tables) {
        return () -> new SchemaDictionaryView("test", "", tables.length, 0, 0, List.of(tables));
    }

    private static TableReferenceService noReferences() {
        return () -> List.of();
    }

    private static SchemaTableView table(String name, String comment, String moduleKey) {
        return new SchemaTableView(name, comment, moduleKey, null, List.of(), null);
    }

    private static DocumentLineageContributor contributor(String moduleKey, List<DocumentNode> nodes,
                                                          List<DocumentEdge> edges) {
        return new DocumentLineageContributor() {
            @Override
            public String moduleKey() {
                return moduleKey;
            }

            @Override
            public List<DocumentNode> documents() {
                return nodes;
            }

            @Override
            public List<DocumentEdge> edges() {
                return edges;
            }
        };
    }

    private static DocumentNode node(String code, String name, boolean confirmed) {
        return new DocumentNode(code, name, "t_demo", List.of(), null, null, "说明", confirmed,
            confirmed ? null : "待确认原因");
    }

    private static DocumentEdge edge(String from, String to) {
        return edge(from, to, true);
    }

    private static DocumentEdge edge(String from, String to, boolean confirmed) {
        return new DocumentEdge(from, to, "生成", List.of("id"), null, null, "说明", confirmed);
    }
}
