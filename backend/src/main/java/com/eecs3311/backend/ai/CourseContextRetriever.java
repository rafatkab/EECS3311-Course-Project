package com.eecs3311.backend.ai;

import com.eecs3311.backend.chat.ChatTurn;
import com.eecs3311.backend.material.LearningMaterial;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.regex.Pattern;

/** Bounded lexical retrieval across all supplied course documents; no model or vector service needed. */
@Component
public class CourseContextRetriever {
    private static final Pattern REFERENCE = Pattern.compile("(?iu)\\b(it|its|they|them|those|that|this|which|one|ones)\\b");
    private static final Pattern COURSE_SCOPE = Pattern.compile("(?iu)\\b(course|notes|materials?|documents?|overview|summary|summari[sz]e|topics|learn|learning)\\b");
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]{2,}");
    private static final Set<String> META = Set.of("course", "notes", "material", "materials", "document", "documents", "source", "sources", "say");
    private static final Set<String> OVERVIEW = Set.of("overview", "summary", "summarize", "summarise", "introduction", "introduce",
            "learn", "learning", "will", "cover", "covers", "covered", "topic", "topics", "main", "key", "important", "basics", "basic",
            "brief", "briefly", "information", "info", "description", "give", "know", "understand", "want", "need", "help", "start", "started",
            "something", "anything", "just", "pls", "plz", "bruh", "hey", "hello", "hi", "okay", "ok", "us", "we", "our", "objective", "objectives");
    private static final Set<String> STOP = Set.of("the", "and", "are", "was", "were", "for", "with", "from", "that", "this", "what", "which", "how", "why", "does", "can", "could", "would", "please", "explain", "about", "have", "has", "its", "they", "their", "them", "into", "than", "then", "you", "your", "these", "those", "is", "it", "an", "to", "of", "in", "on", "at", "be", "do", "me", "my", "tell", "more", "detail", "again", "simpler", "terms", "example", "examples", "compare", "comparison", "difference", "between");
    public record Excerpt(int id, UUID materialId, String fileName, int startOffset, int endOffset, String quote) {
        public ChatTurn.Citation citation() { return new ChatTurn.Citation(materialId, fileName, startOffset, endOffset, quote); }
    }
    private record Chunk(LearningMaterial material, int start, int end, Set<String> words) { }
    public boolean isOverview(String question) {
        var query = words(question);
        return query.isEmpty() || OVERVIEW.containsAll(query);
    }
    public boolean isFollowUp(String question, List<ChatTurn> history) {
        if (isOverview(question) && COURSE_SCOPE.matcher(question).find()) return false;
        if (history.isEmpty() || !history.get(history.size() - 1).answerable()) return false;
        var query = words(question);
        if (query.isEmpty()) return true;
        if (!REFERENCE.matcher(question).find()) return false;
        var topics = new HashSet<String>();
        history.stream().skip(Math.max(0, history.size() - 2)).forEach(turn -> {
            topics.addAll(words(turn.question())); topics.addAll(words(turn.answer()));
        });
        return !Collections.disjoint(query, topics);
    }
    public List<Excerpt> retrieve(List<LearningMaterial> materials, String question, List<ChatTurn> history) {
        var query = words(question);
        var previous = new HashSet<String>();
        history.stream().skip(Math.max(0, history.size() - 2)).forEach(turn -> previous.addAll(words(turn.question())));
        var chunks = new ArrayList<Chunk>();
        long total = 0;
        for (var material : materials) {
            String text = material.getContent();
            total += text.length();
            if (total > 1_000_000) throw new AiFailure(413, "Course chat supports up to 1,000,000 extracted characters across its materials.");
            for (int start = 0; start < text.length();) {
                int end = Math.min(text.length(), start + 700);
                if (end < text.length() && Character.isHighSurrogate(text.charAt(end - 1))) end--;
                String quote = text.substring(start, end);
                if (quote.strip().length() >= 20) chunks.add(new Chunk(material, start, end, words(quote)));
                if (end == text.length()) break;
                start = end - 100;
                if (start > 0 && Character.isLowSurrogate(text.charAt(start))) start++;
            }
        }
        if (chunks.isEmpty()) throw new AiFailure(400, "There is not enough readable course material to answer questions. Upload learning materials first.");
        var frequency = new HashMap<String, Integer>();
        for (var chunk : chunks) for (String word : chunk.words()) frequency.merge(word, 1, Integer::sum);
        java.util.function.ToDoubleFunction<Chunk> score = chunk -> {
            double value = 0;
            for (String word : chunk.words()) {
                double rarity = 1 + Math.log(1 + (double) chunks.size() / frequency.get(word));
                if (query.contains(word)) value += rarity;
                if (previous.contains(word)) value += rarity * .2;
            }
            return value;
        };
        // Stable sorting keeps deterministic source order for equal scores, including zero lexical overlap.
        // Recent topics help a generic follow-up, but must not turn a new unrelated topic into a match.
        Set<String> required = query.isEmpty() ? previous : query;
        var ranked = chunks.stream().filter(chunk -> !Collections.disjoint(chunk.words(), required))
                .sorted(Comparator.comparingDouble(score).reversed()).toList();
        // Broad questions do not contain topic keywords. Supply a spread of real course text instead.
        if (isOverview(question) && (history.isEmpty() || ranked.isEmpty())) ranked = overviewOrder(chunks);
        var selected = new ArrayList<Chunk>();
        for (var chunk : ranked) {
            boolean overlap = selected.stream().anyMatch(other -> other.material().materialId().equals(chunk.material().materialId())
                    && Math.min(other.end(), chunk.end()) - Math.max(other.start(), chunk.start()) > 350);
            if (!overlap) selected.add(chunk);
            if (selected.size() == 6) break;
        }
        var result = new ArrayList<Excerpt>();
        for (var chunk : selected) result.add(new Excerpt(result.size() + 1, chunk.material().materialId(), chunk.material().fileName(),
                chunk.start(), chunk.end(), chunk.material().getContent().substring(chunk.start(), chunk.end())));
        return List.copyOf(result);
    }
    private List<Chunk> overviewOrder(List<Chunk> chunks) {
        var byMaterial = new LinkedHashMap<UUID, List<Chunk>>();
        for (var chunk : chunks) byMaterial.computeIfAbsent(chunk.material().materialId(), ignored -> new ArrayList<>()).add(chunk);
        var groups = List.copyOf(byMaterial.values());
        var spread = new LinkedHashSet<Chunk>();
        int documents = Math.min(6, groups.size());
        for (int i = 0; i < documents; i++) {
            int index = documents == 1 ? 0 : i * (groups.size() - 1) / (documents - 1);
            spread.add(groups.get(index).get(0));
        }
        for (int i = 0; i < 6; i++) spread.add(chunks.get(i * (chunks.size() - 1) / 5));
        spread.addAll(chunks);
        return List.copyOf(spread);
    }
    private static Set<String> words(String value) {
        var result = new HashSet<String>();
        var matcher = WORD.matcher(value.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            String word = matcher.group();
            if (STOP.contains(word) || META.contains(word)) continue;
            result.add(word);
            // Common plural variants should not turn an otherwise relevant question into a refusal.
            if (word.endsWith("ies") && word.length() > 5) result.add(word.substring(0, word.length() - 3) + "y");
            else if (word.endsWith("s") && !word.endsWith("ss") && word.length() > 4) result.add(word.substring(0, word.length() - 1));
        }
        return result;
    }
}
