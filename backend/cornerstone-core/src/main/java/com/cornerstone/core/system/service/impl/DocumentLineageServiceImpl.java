package com.cornerstone.core.system.service.impl;

import com.cornerstone.core.system.dto.DocumentLineageEdgeView;
import com.cornerstone.core.system.dto.DocumentLineageNodeView;
import com.cornerstone.core.system.dto.DocumentLineageView;
import com.cornerstone.core.system.dto.SchemaTableView;
import com.cornerstone.core.system.lineage.DocumentEdge;
import com.cornerstone.core.system.lineage.DocumentLineageContributor;
import com.cornerstone.core.system.lineage.DocumentNode;
import com.cornerstone.core.system.schema.TableReference;
import com.cornerstone.core.system.service.DocumentLineageService;
import com.cornerstone.core.system.service.SchemaDictionaryService;
import com.cornerstone.core.system.service.TableReferenceService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DocumentLineageServiceImpl implements DocumentLineageService {

    private static final Logger log = LoggerFactory.getLogger(DocumentLineageServiceImpl.class);

    /** 自动补全的全表节点编码前缀；模块显式登记的单据不会用这个前缀 */
    private static final String TABLE_NODE_PREFIX = "table:";

    static final String NOTE =
        "链路是各模块在代码里声明的业务关系（谁由谁生成、汇总或核销），不是某一次请求的实际数据流；"
            + "未登记上下游的表按数据字典补成「仅表」节点，数据库外键作为「引用」关系自动补全；"
            + "标记为「待确认」的项目表示业务口径尚未拍板，不能当成既定事实。";

    private final List<DocumentLineageContributor> contributors;
    private final SchemaDictionaryService schemaDictionaryService;
    private final TableReferenceService tableReferenceService;

    public DocumentLineageServiceImpl(List<DocumentLineageContributor> contributors,
                                      SchemaDictionaryService schemaDictionaryService,
                                      TableReferenceService tableReferenceService) {
        this.contributors = contributors == null ? List.of() : contributors;
        this.schemaDictionaryService = schemaDictionaryService;
        this.tableReferenceService = tableReferenceService;
    }

    @Override
    public DocumentLineageView lineage() {
        Map<String, DocumentLineageNodeView> documents = new LinkedHashMap<>();
        Map<String, String> codeByTable = new HashMap<>();
        List<DocumentLineageEdgeView> declaredEdges = new ArrayList<>();

        for (DocumentLineageContributor contributor : contributors) {
            String moduleKey = contributor.moduleKey();
            for (DocumentNode node : safeNodes(contributor)) {
                if (isBlank(node.code()) || isBlank(node.name()) || isBlank(node.primaryTable())) {
                    log.warn("跳过字段不完整的单据登记：module={}, code={}", moduleKey, node.code());
                    continue;
                }
                DocumentLineageNodeView view = toView(node, moduleKey, true);
                if (documents.putIfAbsent(node.code(), view) != null) {
                    log.warn("单据编码重复，保留先登记的一条：{}（{} 与 {}）",
                        node.code(), documents.get(node.code()).moduleKey(), moduleKey);
                    continue;
                }
                codeByTable.putIfAbsent(node.primaryTable(), node.code());
                if (node.relatedTables() != null) {
                    for (String table : node.relatedTables()) {
                        if (!isBlank(table)) {
                            codeByTable.putIfAbsent(table, node.code());
                        }
                    }
                }
            }
            for (DocumentEdge edge : safeEdges(contributor)) {
                declaredEdges.add(toView(edge, true));
            }
        }

        addFallbackTableNodes(documents, codeByTable);
        List<DocumentLineageEdgeView> allEdges = new ArrayList<>(declaredEdges);
        allEdges.addAll(fkEdges(codeByTable, declaredEdges));

        List<DocumentLineageEdgeView> validEdges = new ArrayList<>();
        for (DocumentLineageEdgeView edge : allEdges) {
            if (documents.containsKey(edge.from()) && documents.containsKey(edge.to())) {
                validEdges.add(edge);
            } else if (edge.declared()) {
                log.warn("跳过两端未登记的单据关系：{} → {}", edge.from(), edge.to());
            }
        }

        List<DocumentLineageNodeView> sortedDocuments = documents.values().stream()
            .sorted(Comparator.comparing(DocumentLineageNodeView::moduleKey)
                .thenComparing(DocumentLineageNodeView::name))
            .toList();
        List<DocumentLineageEdgeView> sortedEdges = dedupe(validEdges).stream()
            .sorted(Comparator.comparing(DocumentLineageEdgeView::declared).reversed()
                .thenComparing(DocumentLineageEdgeView::from)
                .thenComparing(DocumentLineageEdgeView::to)
                .thenComparing(DocumentLineageEdgeView::relation))
            .toList();

        int pendingCount = (int) sortedDocuments.stream().filter(node -> !node.confirmed()).count()
            + (int) sortedEdges.stream().filter(edge -> !edge.confirmed()).count();

        return new DocumentLineageView(
            LocalDateTime.now(),
            NOTE,
            sortedDocuments.size(),
            sortedEdges.size(),
            pendingCount,
            sortedDocuments,
            sortedEdges);
    }

    /**
     * 数据字典里的每张表都要在页面上有落点：没有模块登记归属的，补一个「仅表」节点。
     * 附属表已经通过 {@code relatedTables} 归到所属单据下，不重复列成孤立节点。
     */
    private void addFallbackTableNodes(Map<String, DocumentLineageNodeView> documents,
                                       Map<String, String> codeByTable) {
        List<SchemaTableView> tables;
        try {
            tables = schemaDictionaryService.dictionary().tables();
        } catch (RuntimeException ex) {
            log.warn("数据字典不可用，业务链路只展示已登记的单据：{}", ex.getMessage());
            return;
        }
        for (SchemaTableView table : tables) {
            if (codeByTable.containsKey(table.tableName())) {
                continue;
            }
            String code = TABLE_NODE_PREFIX + table.tableName();
            if (documents.containsKey(code)) {
                continue;
            }
            String name = isBlank(table.tableComment()) ? table.tableName() : table.tableComment();
            String pagePath = table.maintenance() == null ? null : table.maintenance().path();
            DocumentLineageNodeView node = new DocumentLineageNodeView(
                code,
                name,
                table.moduleKey(),
                table.tableName(),
                List.of(),
                pagePath,
                null,
                "数据字典里的表；尚未登记业务上下游关系，若属于某个单据请在模块 Contributor 中补充",
                true,
                null,
                false);
            documents.put(code, node);
            codeByTable.put(table.tableName(), code);
        }
    }

    /**
     * 外键自动补「引用」关系。按「来源节点 → 目标节点」聚合，
     * 同一对单据之间的多条外键合并成一条关系，关联字段列在 {@code linkFields} 里。
     */
    private List<DocumentLineageEdgeView> fkEdges(Map<String, String> codeByTable,
                                                  List<DocumentLineageEdgeView> declaredEdges) {
        Set<String> declaredReferencePairs = new HashSet<>();
        for (DocumentLineageEdgeView edge : declaredEdges) {
            if ("引用".equals(edge.relation())) {
                declaredReferencePairs.add(pairKey(edge.from(), edge.to()));
            }
        }
        List<TableReference> references;
        try {
            references = tableReferenceService.references();
        } catch (RuntimeException ex) {
            log.warn("外键信息不可用，业务链路只展示显式声明的关系：{}", ex.getMessage());
            return List.of();
        }

        Map<String, String[]> endsByPair = new LinkedHashMap<>();
        Map<String, Set<String>> linksByPair = new LinkedHashMap<>();
        for (TableReference reference : references) {
            String from = codeByTable.get(reference.childTable());
            String to = codeByTable.get(reference.parentTable());
            if (from == null || to == null || from.equals(to)) {
                continue;
            }
            String key = pairKey(from, to);
            if (declaredReferencePairs.contains(key)) {
                continue;
            }
            endsByPair.putIfAbsent(key, new String[]{from, to});
            linksByPair.computeIfAbsent(key, ignored -> new LinkedHashSet<>())
                .add(reference.childTable() + "." + reference.childColumn()
                    + " → " + reference.parentTable() + "." + reference.parentColumn());
        }

        List<DocumentLineageEdgeView> result = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : linksByPair.entrySet()) {
            String[] ends = endsByPair.get(entry.getKey());
            result.add(new DocumentLineageEdgeView(
                ends[0],
                ends[1],
                "引用",
                List.copyOf(entry.getValue()),
                null,
                null,
                "数据库外键自动推导",
                true,
                false));
        }
        return result;
    }

    private static List<DocumentLineageEdgeView> dedupe(List<DocumentLineageEdgeView> edges) {
        Map<String, DocumentLineageEdgeView> unique = new LinkedHashMap<>();
        for (DocumentLineageEdgeView edge : edges) {
            unique.putIfAbsent(edge.from() + "\u0000" + edge.to() + "\u0000"
                + edge.relation() + "\u0000" + edge.declared(), edge);
        }
        return new ArrayList<>(unique.values());
    }

    private static String pairKey(String from, String to) {
        return from + "\u0000" + to;
    }

    private static List<DocumentNode> safeNodes(DocumentLineageContributor contributor) {
        List<DocumentNode> nodes = contributor.documents();
        return nodes == null ? List.of() : nodes;
    }

    private static List<DocumentEdge> safeEdges(DocumentLineageContributor contributor) {
        List<DocumentEdge> edges = contributor.edges();
        return edges == null ? List.of() : edges;
    }

    private static DocumentLineageNodeView toView(DocumentNode node, String moduleKey, boolean declared) {
        return new DocumentLineageNodeView(
            node.code(),
            node.name(),
            moduleKey,
            node.primaryTable(),
            copy(node.relatedTables()),
            node.pagePath(),
            node.permissionCode(),
            node.description(),
            node.confirmed(),
            node.remark(),
            declared);
    }

    private static DocumentLineageEdgeView toView(DocumentEdge edge, boolean declared) {
        return new DocumentLineageEdgeView(
            edge.fromCode(),
            edge.toCode(),
            edge.relation(),
            copy(edge.linkFields()),
            edge.triggerLabel(),
            edge.triggerEndpoint(),
            edge.description(),
            edge.confirmed(),
            declared);
    }

    private static List<String> copy(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
