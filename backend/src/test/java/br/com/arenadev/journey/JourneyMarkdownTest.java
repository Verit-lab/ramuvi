package br.com.arenadev.journey;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class JourneyMarkdownTest {
    @Test void roundTripPreservesOrderedBodies() {
        String markdown = JourneyMarkdown.render("Atividade SQL", "Descrição", List.of(
                new JourneyMarkdown.Part("SELECT", "Texto original\n\n```sql\nSELECT * FROM tabela;\n```"),
                new JourneyMarkdown.Part("WHERE", "Exercícios com filtros")
        ), List.of("Módulo 1", "Módulo 1"));
        var parts = JourneyMarkdown.parse(markdown, List.of("SELECT", "WHERE"));
        assertEquals("SELECT", parts.get(0).title());
        assertTrue(parts.get(0).body().contains("SELECT * FROM tabela"));
        assertEquals("Exercícios com filtros", parts.get(1).body());
    }

    @Test void rejectsMissingOrRenamedStages() {
        assertThrows(IllegalArgumentException.class, () -> JourneyMarkdown.parse("# Curso\n### Apenas um\nTexto", List.of("A", "B")));
        assertThrows(IllegalArgumentException.class, () -> JourneyMarkdown.parse("### Alterado\nTexto", List.of("Original")));
        assertThrows(IllegalArgumentException.class, () -> JourneyMarkdown.parse("### Original\n\n", List.of("Original")));
    }

    @Test void keepsNestedH3InsideExistingStage() {
        var parts = JourneyMarkdown.parse("### Primeiro\nIntrodução\n\n### Exemplo interno\nDetalhes\n\n### Segundo\nOutro conteúdo", List.of("Primeiro", "Segundo"));
        assertEquals(2, parts.size());
        assertTrue(parts.get(0).body().contains("### Exemplo interno"));
    }

    @Test void rejectsOutOfOrderExistingTitles() {
        assertThrows(IllegalArgumentException.class, () -> JourneyMarkdown.parse("### Segundo\nB\n### Primeiro\nA", List.of("Primeiro", "Segundo")));
    }

    @Test void ignoresFencedHeading() {
        var parts = JourneyMarkdown.parse("### Consulta\n\n```markdown\n### Não é etapa\n```\n\nTexto", List.of("Consulta"));
        assertTrue(parts.get(0).body().contains("### Não é etapa"));
    }
}
