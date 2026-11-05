package br.com.arenadev.content.ingestion;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SourceAdapterContractTest {
    @Test
    void markdownProducesSourceNeutralBlocksAndPreservesDeepStructureAndCode() {
        var adapter = new MarkdownSourceAdapter();
        var document = adapter.normalize(request(ContentIngestionSource.MARKDOWN, """
                # Python
                ## Fundamentos
                Introdução.
                ### Variáveis
                Conceito.
                #### Boas práticas
                Use nomes claros.
                ```python
                # isto não é heading
                valor = 10
                ```
                """, "python.md"));

        assertThat(document.title()).isEqualTo("Python");
        assertThat(document.provenance().source()).isEqualTo(ContentIngestionSource.MARKDOWN);
        assertThat(document.provenance().sourceLabel()).isEqualTo("python.md");
        assertThat(document.metadata()).containsEntry("mappingProfile", "MARKDOWN_H2_H3");
        assertThat(document.blocks()).anySatisfy(block -> {
            assertThat(block).isInstanceOf(NormalizedDocument.Heading.class);
            var heading = (NormalizedDocument.Heading) block;
            assertThat(heading.level()).isEqualTo(2);
            assertThat(heading.text()).isEqualTo("Fundamentos");
        });
        assertThat(document.blocks()).anySatisfy(block -> {
            assertThat(block).isInstanceOf(NormalizedDocument.Content.class);
            assertThat(block.text()).contains("# isto não é heading", "valor = 10");
        });
    }

    @Test
    void notionTogglesRespectModulesStagesAndNestedSubtopics() {
        var adapter = new MarkdownSourceAdapter();
        var markdown = """
                # Portaria Digital
                <toggle heading="h2">
                Módulo 01 | Fundamentos

                <toggle heading="h3">
                Etapa 00 · Git

                Introdução.
                <toggle heading="h3">
                0.1 Git básico

                Exemplo.
                </toggle>
                ### 0.2 Próximos passos
                Mais texto.
                </toggle>

                <toggle heading="h3">
                Etapa 01 · Python

                Conhecimento inicial.
                </toggle>
                </toggle>
                """;
        var doc = adapter.normalize(request(ContentIngestionSource.MARKDOWN, markdown, "notion.md"));
        var headings = doc.blocks().stream().filter(b -> b instanceof NormalizedDocument.Heading)
                .map(b -> (NormalizedDocument.Heading) b).toList();
        assertThat(headings).extracting(NormalizedDocument.Heading::text)
                .containsExactly("Módulo 01 | Fundamentos", "Etapa 00 · Git",
                        "0.1 Git básico", "0.2 Próximos passos", "Etapa 01 · Python");
        assertThat(headings).extracting(NormalizedDocument.Heading::level)
                .containsExactly(2, 3, 4, 4, 3);
    }

    @Test
    void textAndExtractedFilesUseTheSameNeutralContract() {
        var adapter = new TextSourceAdapter();
        var text = adapter.normalize(request(ContentIngestionSource.TEXT, "Primeiro bloco.\n\nSegundo bloco.", "Texto colado"));
        var file = adapter.normalize(request(ContentIngestionSource.FILE, "Primeiro bloco.\n\nSegundo bloco.", "apostila.pdf"));

        assertThat(text.blocks()).extracting(NormalizedDocument.Block::text)
                .containsExactly("Primeiro bloco.", "Segundo bloco.");
        assertThat(file.blocks()).extracting(NormalizedDocument.Block::text)
                .containsExactlyElementsOf(text.blocks().stream().map(NormalizedDocument.Block::text).toList());
        assertThat(file.provenance().source()).isEqualTo(ContentIngestionSource.FILE);
        assertThat(file.provenance().sourceLabel()).isEqualTo("apostila.pdf");
    }

    private ContentIngestionService.PreviewRequest request(ContentIngestionSource source, String content, String label) {
        return new ContentIngestionService.PreviewRequest(UUID.randomUUID(), source, null, null, null, content, null, label);
    }
}
