package br.com.arenadev.content.ingestion;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class MarkdownSourceAdapter implements ContentSourceAdapter {
    private static final Pattern NOTION_TOGGLE = Pattern.compile("^<toggle\\s+heading=\"h([1-4])\"[^>]*>\\s*$");
    private static final Pattern NOTION_TOGGLE_CLOSE = Pattern.compile("^</toggle>\\s*$");
    @Override
    public boolean supports(ContentIngestionSource source) { return source == ContentIngestionSource.MARKDOWN; }

    @Override
    public NormalizedDocument normalize(ContentIngestionService.PreviewRequest request) {
        String markdown = flattenNotionToggles(requireContent(request.content()));
        List<NormalizedDocument.Block> blocks = new ArrayList<>();
        String documentTitle = null;
        boolean fenced = false;
        StringBuilder content = new StringBuilder();
        for (String raw : markdown.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)) {
            String line = raw.stripTrailing();
            String trimmed = line.stripLeading();
            if (trimmed.startsWith("```")) {
                fenced = !fenced;
                content.append(line).append('\n');
                continue;
            }
            int level = fenced ? 0 : headingLevel(trimmed);
            if (level > 0) {
                flush(content, blocks);
                String text = trimmed.substring(level + 1).trim().replace("\\|", "|");
                if (level == 1 && documentTitle == null) documentTitle = text;
                else blocks.add(new NormalizedDocument.Heading(level, text));
            } else content.append(line).append('\n');
        }
        flush(content, blocks);
        return new NormalizedDocument(
                clean(request.title()) != null ? clean(request.title()) : documentTitle,
                blocks,
                new NormalizedDocument.Provenance(ContentIngestionSource.MARKDOWN, clean(request.sourceLabel()), clean(request.sourceUrl()), null, "MARKDOWN"),
                Map.of("mappingProfile", "MARKDOWN_H2_H3")
        );
    }


    /**
     * Flatten native Notion heading toggles into readable Markdown. A toggle
     * nested under a stage is part of that stage, not a new stage.
     * Plain Markdown is kept untouched.
     */
    static String flattenNotionToggles(String markdown) {
        if (!markdown.contains("<toggle heading=")) return markdown;
        String[] lines = markdown.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        StringBuilder flattened = new StringBuilder(markdown.length());
        Deque<String> hierarchy = new ArrayDeque<>();
        boolean code = false;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();
            if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) code = !code;
            if (!code) {
                Matcher opening = NOTION_TOGGLE.matcher(trimmed);
                if (opening.matches()) {
                    int next = i + 1;
                    while (next < lines.length && lines[next].isBlank()) next++;
                    if (next < lines.length && !lines[next].trim().startsWith("<")) {
                        String title = lines[next].trim();
                        boolean insideModule = hierarchy.contains("module");
                        boolean insideStage = hierarchy.contains("stage");
                        int level = Integer.parseInt(opening.group(1));
                        String role = insideStage ? "detail" : insideModule ? "stage"
                                : level == 2 ? "module" : "detail";
                        hierarchy.push(role);
                        int heading = role.equals("module") ? 2 : role.equals("stage") ? 3 : 4;
                        flattened.append("#".repeat(heading)).append(' ').append(title).append("\n\n");
                        i = next;
                        continue;
                    }
                    hierarchy.push("detail");
                    continue;
                }
                if (NOTION_TOGGLE_CLOSE.matcher(trimmed).matches()) {
                    if (!hierarchy.isEmpty()) hierarchy.pop();
                    continue;
                }
                if (hierarchy.contains("stage")) {
                    int heading = headingLevel(trimmed);
                    if (heading > 0 && heading <= 3) {
                        flattened.append("#### ").append(trimmed.substring(heading + 1)).append('\n');
                        continue;
                    }
                }
            }
            flattened.append(line).append('\n');
        }
        return flattened.toString();
    }

    private static int headingLevel(String line) {
        int count = 0;
        while (count < line.length() && count < 6 && line.charAt(count) == '#') count++;
        return count > 0 && count < line.length() && line.charAt(count) == ' ' ? count : 0;
    }
    private static void flush(StringBuilder value, List<NormalizedDocument.Block> blocks) {
        String text = value.toString().trim();
        if (!text.isEmpty()) blocks.add(new NormalizedDocument.Content(text));
        value.setLength(0);
    }
    private static String requireContent(String value) {
        String content = clean(value);
        if (content == null) throw new IllegalArgumentException("Forneça o conteúdo a ser analisado.");
        ContentIngestionLimits.requireWithinAnalysisLimit(content);
        return content;
    }
    private static String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
