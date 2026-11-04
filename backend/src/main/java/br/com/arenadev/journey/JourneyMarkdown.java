package br.com.arenadev.journey;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Lossless stage-body editor: H1/H2 are context, H3 delimit existing stages. */
final class JourneyMarkdown {
    private static final Pattern HEADING = Pattern.compile("^###\\s+(.+?)\\s*$");
    private static final Pattern FENCE = Pattern.compile("^\\s*(`{3,}|~{3,}).*$");

    private JourneyMarkdown() {}

    record Part(String title, String body) {}

    static String render(String title, String description, List<Part> parts, List<String> modules) {
        StringBuilder result = new StringBuilder("# ").append(title).append("\n\n");
        if (description != null && !description.isBlank()) result.append(description.strip()).append("\n\n");
        String lastModule = null;
        for (int i = 0; i < parts.size(); i++) {
            String module = modules.get(i);
            if (!Objects.equals(module, lastModule)) {
                result.append("## ").append(module).append("\n\n");
                lastModule = module;
            }
            Part part = parts.get(i);
            result.append("### ").append(part.title()).append("\n\n")
                    .append(part.body() == null ? "" : part.body().strip()).append("\n\n");
        }
        return result.toString().strip() + "\n";
    }

    static List<Part> parse(String markdown, List<String> expectedTitles) {
        if (markdown == null || markdown.isBlank() || markdown.length() > 1_000_000)
            throw new IllegalArgumentException("Markdown vazio ou acima do limite de 1 milhão de caracteres.");
        List<Part> parts = new ArrayList<>();
        String heading = null;
        StringBuilder content = new StringBuilder();
        String fenceChar = null;
        int fenceLength = 0;
        for (String line : markdown.replace("\r\n", "\n").split("\n", -1)) {
            Matcher fence = FENCE.matcher(line);
            if (fence.matches()) {
                String sequence = fence.group(1);
                if (fenceChar == null) { fenceChar = sequence.substring(0, 1); fenceLength = sequence.length(); }
                else if (sequence.startsWith(fenceChar) && sequence.length() >= fenceLength) fenceChar = null;
            }
            Matcher marker = HEADING.matcher(line);
            String candidate = marker.matches() ? marker.group(1).strip() : null;
            int expectedIndex = heading == null ? 0 : parts.size() + 1;
            if (fenceChar == null && candidate != null && expectedIndex < expectedTitles.size()
                    && candidate.equals(expectedTitles.get(expectedIndex))) {
                if (heading != null) parts.add(new Part(heading, content.toString().strip()));
                heading = candidate;
                content.setLength(0);
            } else if (heading != null) content.append(line).append('\n');
        }
        if (heading != null) parts.add(new Part(heading, content.toString().strip()));
        if (parts.size() != expectedTitles.size())
            throw new IllegalArgumentException("A estrutura mudou: são esperados " + expectedTitles.size() + " conteúdos H3, mas foram encontrados " + parts.size() + ". Preserve os módulos e títulos existentes.");
        for (int i = 0; i < parts.size(); i++) {
            if (!parts.get(i).title().equals(expectedTitles.get(i)))
                throw new IllegalArgumentException("O conteúdo " + (i + 1) + " deve manter o título H3: " + expectedTitles.get(i));
            if (parts.get(i).body().isBlank())
                throw new IllegalArgumentException("O conteúdo " + (i + 1) + " não pode ficar vazio.");
        }
        return parts;
    }
}
